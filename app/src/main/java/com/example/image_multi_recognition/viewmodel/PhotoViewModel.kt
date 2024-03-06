package com.example.image_multi_recognition.viewmodel

import android.os.Environment
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.example.image_multi_recognition.db.AlbumInfo
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.util.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltViewModel
open class PhotoViewModel @Inject constructor(
    private val repository: ImageRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val backgroundThreadPool = Executors.newCachedThreadPool()

    // photoDir should come from DataStore
    // val albums: List<String> = listOf(Environment.DIRECTORY_PICTURES)
    private var currentMediaStoreVersion: String? = null
    // for startDestination, the "album" is null by default
    var currentAlbum: Long? = savedStateHandle.get<Long>("album")

//    private var _isLoading = MutableStateFlow(false)
//    val isLoading: StateFlow<Boolean>
//        get() = _isLoading

    private val _pagingFlow: MutableStateFlow<Flow<PagingData<UiModel>>> = MutableStateFlow(emptyFlow())
    val pagingFlow: StateFlow<Flow<PagingData<UiModel>>>
        get() = _pagingFlow

    init {
        // check the argument of navigation route to decide on what purpose the PhotoViewModel is used
        // (on a first load when app is started or jumped from an album window)
        // We do the initialization only when the currentAlbum is null(in AlbumPhotoViewModel. the value is not null)
        if(currentAlbum == null) {
            Log.d(getCallSiteInfo(), "Null currentAlbum")
            viewModelScope.launch {
                // DCIM
                val dcimCameraDir =
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
                var dcimCameraAlbum = repository.getAlbumByPath(dcimCameraDir.absolutePath)
                if (dcimCameraAlbum != null) {
                    // show the old images first to avoid waiting
                    Log.d(getCallSiteInfo(), "DCIMAlbum: DCIMAlbum")
                    setImagePagingFlow(dcimCameraAlbum)
                    currentAlbum = dcimCameraAlbum
                }

                repository.mediaStoreVersionFlow.collect { mediaStoreVersion ->
                    Log.d(getCallSiteInfoFunc(), "collected MediaStore version: $mediaStoreVersion")
                    val newMediaStoreVersion = repository.checkMediaStoreUpdate(mediaStoreVersion)
                    if ((currentMediaStoreVersion == null && newMediaStoreVersion.isNotEmpty())
                        // This allows you to set the mediaStoreVersion in DataStore manually
                        // by calling MediaStore.getVersion(Context) in Composable to implement "refresh" functionality
                        || (currentMediaStoreVersion != null && currentMediaStoreVersion != mediaStoreVersion)
                    ) {
                        withContext(Dispatchers.IO) {
                            val albumList = repository.getAllImageDir()
                            Log.d(getCallSiteInfoFunc(), "00 albumList: ${albumList.joinToString("\n")}")
                            // check album path existence
                            val previousAlbums = repository.getAllAlbumInfo()
                            val (albumsToAdd, albumInfoToDelete) = albumList.getDifference(
                                list = previousAlbums,
                                keyExtractorThis = { it.absolutePath },
                                keyExtractorParam = { it.path }
                            )
                            Log.d(
                                getCallSiteInfo(),
                                "albumsToAdd: ${albumsToAdd.joinToString()} \nalbumsToDelete: ${albumInfoToDelete.joinToString()}"
                            )
                            val albumInfoToAdd = albumsToAdd.map { AlbumInfo(path = it.absolutePath) }
                            repository.deleteAndAddAlbums(albumInfoToDelete, albumInfoToAdd)
                                .forEachIndexed { index, album ->
                                    albumInfoToAdd[index].album = album
                                }
                            mutableMapOf<Long, File>().apply {
                                previousAlbums.forEach { this[it.album] = File(it.path) }
                                albumInfoToDelete.forEach { this.remove(it.album) }
                                albumInfoToAdd.forEach { this[it.album] = File(it.path) }
                                AlbumPathDecoder.initDecoder(this)
                            }
                            if (dcimCameraAlbum == null) {
                                currentAlbum =
                                    albumInfoToAdd.find { it.path == dcimCameraDir.absolutePath }?.album ?: 0L
                                Log.d(getCallSiteInfo(), "Default albumId to show: $currentAlbum")
                            }
                            if (AlbumPathDecoder.albums.isNotEmpty()) {
                                if (currentAlbum in AlbumPathDecoder.albums) {
                                    scanImages(listOf(currentAlbum!!), currentAlbum!!)
                                }
                                scanImages(AlbumPathDecoder.albums - currentAlbum!!)
                            }
                            // scanImages suspends to coroutineScope, when it is done, we can change the mediaStoreVersion in DataStore
                            currentMediaStoreVersion = newMediaStoreVersion
                            repository.updateMediaStoreVersion(newMediaStoreVersion)
                            Log.d(getCallSiteInfoFunc(), "Initialization done!")
                        }
                    } else {
                        mapOf(*(repository.getAllAlbumInfo().map { it.album to File(it.path) }).toTypedArray()).let {
                            AlbumPathDecoder.initDecoder(it)
                        }
                        Log.d(getCallSiteInfo(), "currentAlbum: $currentAlbum")
                        setImagePagingFlow(currentAlbum!!)
                    }
                }
            }
        }else{
            // the viewModel is used for AlbumPhotoComposable
            setImagePagingFlow(currentAlbum!!)
        }
    }

    // return List<File> that needed to be added to the DB and List<ImageIdPath> that needed to be deleted from the DB
//    private fun getDifferencesInAlbum(
//        files: List<File>,
//        filesInfoFromDB: List<ImageIdPath>,
//        album: String
//    ): Pair<List<File>, List<Long>> {
//        Log.d(getCallSiteInfo(), "files to be scanned: ${files.joinToString()}")
//        val deletedImageIds = mutableListOf<Long>()
//        val fileSet = mutableMapOf(*files.map { it.absolutePath to false }.toTypedArray())
//        val albumDir = AlbumPathDecoder.decode(album)
//        //val albumDir = File(album)
//        filesInfoFromDB.forEach { imageIdPath: ImageIdPath ->
//            val absolutePath = File(albumDir, imageIdPath.path).absolutePath
//            Log.d(getCallSiteInfo(), "absolutePath: $absolutePath")
//            if (fileSet.contains(absolutePath)) {
//                fileSet[absolutePath] = true
//            } else {
//                deletedImageIds.add(imageIdPath.id)
//            }
//        }
//        val addedFiles = fileSet.filterValues { !it }.keys.toList().map { File(it) }
//        Log.d(
//            getCallSiteInfo(),
//            "addFiles: ${addedFiles.joinToString()}\ndeletedImageIds: ${deletedImageIds.joinToString()}"
//        )
//        return Pair(addedFiles, deletedImageIds)
//    }

    // return whether the loading is needed
    private suspend fun scanImages(albums: List<Long>, albumToShow: Long? = null) {
        // First, we compare the file info stored in DB and storage
        // Delete the inconsistent part in DB and add new info of files in storage to DB
        coroutineScope {   // suspend here
            albums.forEach { album ->
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

//                            val (addedFiles_, deletedItemIds) = getDifferencesInAlbum(
//                                files = albumPath.walk()
//                                    .filter { AlbumPathDecoder.validImageSuffix.contains(it.extension) }.toList(),
//                                filesInfoFromDB = albumIdsPaths,
//                                album = album
//                            )
//                    val (addedFiles, deletedItems) = albumPath.walk()
//                        .filter { AlbumPathDecoder.validImageSuffix.contains(it.extension) }.toList()
//                        .getDifference(
//                            list = albumIdsPaths,
//                            keyExtractorThis = { it.absolutePath },
//                            keyExtractorParam = { it.path }
//                        )
                    val (addedFiles, deletedItems) = ((albumPath.listFiles())?.filter { file ->
                        file.isFile && AlbumPathDecoder.validImageSuffix.contains(file.extension) && !(file.startsWith("."))
                    } ?: emptyList()).getDifference(
                        list = albumIdsPaths,
                        keyExtractorThis = { it.absolutePath },
                        keyExtractorParam = { it.path }
                    )
                    Log.d(getCallSiteInfoFunc(), "addedFiles: ${addedFiles.joinToString("\n")}")
                    Log.d(getCallSiteInfoFunc(), "deletedFiles: ${deletedItems.joinToString("\n")}")

                    // "addedImageInfo" is from DB, no cached images are added yet
                    val addedImageInfo = repository.deleteAndAddImages(
                        deletedIds = deletedItems.map { it.id },
                        //deletedIds = deletedItemIds,
                        addedFilePaths = addedFiles,
                        album = album
                    )
                    Log.d(getCallSiteInfo(), "album: $album, albumToShow: $albumToShow")
                    if (album == albumToShow) {
                        setImagePagingFlow(albumToShow)   // update image flow
                    }

                    // generate thumbnails
                    addedFiles.forEachIndexed { index, file ->
                        requestThumbnail(file, addedImageInfo[index])
                        // avoid getting ImageLoader overwhelmed so that it cannot handle the request from UI right away
                        delay(100)
                    }
                }
            }
        }
    }

    fun requestThumbnail(file: File, imageInfo: ImageInfo) {
        val (imageLoader, imageRequest) = repository.genImageRequest(
            file = file,
            onSuccess = { drawable ->
                // Log.d(getCallSiteInfo(), "Current Thread: ${Thread.currentThread().name}")
                Log.d(getCallSiteInfo(), "load image success: ${imageInfo.fullImageFile.absolutePath}")
                backgroundThreadPool.submit {
                    imageInfo.setImageCache(drawable.toBitmap())
                }
            },
        )
        if (!imageInfo.isThumbnailAvailable) imageLoader.enqueue(imageRequest)

        //repository.genThumbnail(file, imageInfo)
    }

//    fun sendImageRequestForThumbnail(file: File, imageInfo: ImageInfo) {
//        viewModelScope.launch {
//            withContext(Dispatchers.Default) {
//                repository.genThumbnail(file, imageInfo)
//            }
//        }
//    }

    fun setImagePagingFlow(album: Long) {
        var count = 0
        val epochTime = LocalDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC"))
        _pagingFlow.value = repository.getImagePagingFlow(album)
            .map { pagingData ->
                pagingData.map { UiModel.Item(it, ++count) }
            }.map { pagingData ->
                pagingData.insertSeparators { before, after ->
                    if (after == null) null
                    else {
                        //Timestamp(before.imageInfo.timestamp)
                        val timeBefore = before?.imageInfo?.timestamp?.let { ExifHelper.timestampToLocalDataTime(it) }
                        val timeAfter = ExifHelper.timestampToLocalDataTime(after.imageInfo.timestamp)
                        // For the first scan, before is null, after is not null; for the last scan, after is null, before is not null
                        if (timeBefore == null || timeBefore.year != timeAfter.year || timeBefore.month != timeAfter.month) {
                            if(timeAfter != epochTime) {    // exclude UTC time: 1970/1/1 ...
                                UiModel.ItemHeaderYearMonth(
                                    year = timeAfter.year,
                                    month = timeAfter.month,
                                    dayOfMonth = timeAfter.dayOfMonth,
                                    dayOfWeek = timeAfter.dayOfWeek
                                ).apply { Log.d(getCallSiteInfo(), "UiModel.ItemHeaderYearMonth is created: $this") }
                            }else null
                        } else if (timeBefore.dayOfMonth != timeAfter.dayOfMonth) {
                            if(timeAfter != epochTime) {
                                UiModel.ItemHeaderDay(
                                    year = timeAfter.year,
                                    month = timeAfter.month,
                                    dayOfMonth = timeAfter.dayOfMonth,
                                    dayOfWeek = timeAfter.dayOfWeek
                                ).apply { Log.d(getCallSiteInfo(), "UiModel.ItemHeaderDay is created: $this") }
                            }else null
                        } else null
                    }
                }
            }.cachedIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        backgroundThreadPool.shutdownNow()
    }
}