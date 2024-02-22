package com.example.image_multi_recognition.viewmodel

import android.os.Environment
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.example.image_multi_recognition.db.ImageIdPath
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.util.ExifHelper
import com.example.image_multi_recognition.util.getCallSiteInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PhotoViewModel @Inject constructor(
    private val repository: ImageRepository,
) : ViewModel() {
    // photoDir should come from DataStore
    val albums: List<String> = listOf(Environment.DIRECTORY_PICTURES)

    lateinit var currentAlbum: String

    private var _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean>
        get() = _isLoading

    private val _pagingFlow: MutableStateFlow<Flow<PagingData<UiModel>>> = MutableStateFlow(emptyFlow())
    val pagingFlow: StateFlow<Flow<PagingData<UiModel>>>
        get() = _pagingFlow


    // return List<File> that needed to be added to the DB and List<ImageIdPath> that needed to be deleted from the DB
    private fun getDifferences(
        files: List<File>,
        filesInfoFromDB: List<ImageIdPath>,
        album: String
    ): Pair<List<File>, List<Long>> {
        Log.d(getCallSiteInfo(), "files to be scanned: ${files.joinToString()}")
        val deletedImageIds = mutableListOf<Long>()
        val fileSet = mutableMapOf(*files.map { it.absolutePath to false }.toTypedArray())
        val albumDir = AlbumPathDecoder.decode(album)
        //val albumDir = File(album)
        filesInfoFromDB.forEach { imageIdPath: ImageIdPath ->
            val absolutePath = File(albumDir, imageIdPath.path).absolutePath
            Log.d(getCallSiteInfo(), "absolutePath: $absolutePath")
            if (fileSet.contains(absolutePath)) {
                fileSet[absolutePath] = true
            } else {
                deletedImageIds.add(imageIdPath.id)
            }
        }
        val addedFiles = fileSet.filterValues { !it }.keys.toList().map { File(it) }
        Log.d(
            getCallSiteInfo(),
            "addFiles: ${addedFiles.joinToString()}\ndeletedImageIds: ${deletedImageIds.joinToString()}"
        )
        return Pair(addedFiles, deletedImageIds)
    }

    // return whether the loading is needed
    fun scanImages() {
        _isLoading.value = true
        viewModelScope.launch {
            // First, we compare the file info stored in DB and storage
            // Delete the inconsistent part in DB and add new info of files in storage to DB
            var addedFiles = listOf<File>()
            var addedImageInfo = listOf<ImageInfo>()
            coroutineScope {
                withContext(Dispatchers.Default) {
                    albums.forEachIndexed { index, album ->
                        launch {
                            val albumPath = AlbumPathDecoder.decode(album)
                            Log.d(getCallSiteInfo(), "Album path: ${albumPath.absolutePath}")
                            val albumIdsPaths = repository.getAllImageOfAlbum(album)
                            Log.d(
                                getCallSiteInfo(),
                                "all the files inside album:\n${
                                    albumPath.walk().map { it.absolutePath }.joinToString(separator = "\n")
                                }"
                            )

                            val (addedFiles_, deletedItemIds) = getDifferences(
                                files = albumPath.walk()
                                    .filter { AlbumPathDecoder.validImageSuffix.contains(it.extension) }.toList(),
                                filesInfoFromDB = albumIdsPaths,
                                album = album
                            )
                            addedFiles = addedFiles_
                            // "addedImageInfo" is from DB, no cached images are added yet
                            addedImageInfo = repository.deleteAndAddImages(
                                deletedIds = deletedItemIds,
                                addedFilePaths = addedFiles,
                                album = album
                            )
                        }
                    }
                }
            }
            launch {
                // generate thumbnails
                addedFiles.forEachIndexed { index, file ->
                    sendImageRequestForThumbnail(file, addedImageInfo[index])
                    // avoid getting ImageLoader overwhelmed so that it cannot handle the request from UI right away
                    delay(100)
                }
            }
            // Then show "Picture" album in the first window
            setPagingFlow(Environment.DIRECTORY_PICTURES)
            _isLoading.value = false
        }
    }

    fun sendImageRequestForThumbnail(file: File, imageInfo: ImageInfo) {
        val (imageLoader, imageRequest) = repository.genImageRequest(file) { drawable ->
            Log.d(getCallSiteInfo(), "load image success: ${imageInfo.fullImageFile.absolutePath}")
            imageInfo.setImageCache(drawable.toBitmap())
        }
        imageLoader.enqueue(imageRequest)
    }

    fun setPagingFlow(album: String) {
        currentAlbum = album
        var count = 0
        _pagingFlow.value = repository.getImagePagingFlow(album)
            .map { pagingData ->
                pagingData.map { UiModel.Item(it, ++count) }
            }.map { pagingData ->
                var startNewDay = false
                pagingData.insertSeparators { before, after ->
                    if (after == null) null
                    else {
                        //Timestamp(before.imageInfo.timestamp)
                        val timeBefore = before?.imageInfo?.timestamp?.let { ExifHelper.timestampToLocalDataTime(it) }
                        val timeAfter = ExifHelper.timestampToLocalDataTime(after.imageInfo.timestamp)
                        // For the first scan, before is null, after is not null; for the last scan, after is null, before is not null
                        if (timeBefore == null || timeBefore.year != timeAfter.year || timeBefore.month != timeAfter.month) {
                            UiModel.ItemHeaderYearMonth(
                                year = timeAfter.year,
                                month = timeAfter.month,
                                dayOfMonth = timeAfter.dayOfMonth,
                                dayOfWeek = timeAfter.dayOfWeek
                            ).apply { Log.d(getCallSiteInfo(), "UiModel.ItemHeaderYearMonth is created: $this") }
                        } else if (timeBefore.dayOfMonth != timeAfter.dayOfMonth) {
                            UiModel.ItemHeaderDay(
                                year = timeAfter.year,
                                month = timeAfter.month,
                                dayOfMonth = timeAfter.dayOfMonth,
                                dayOfWeek = timeAfter.dayOfWeek
                            ).apply { Log.d(getCallSiteInfo(), "UiModel.ItemHeaderDay is created: $this") }
                        } else null
                    }
                }
            }.cachedIn(viewModelScope)
    }
}