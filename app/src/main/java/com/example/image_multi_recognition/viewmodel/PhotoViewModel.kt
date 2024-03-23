package com.example.image_multi_recognition.viewmodel

import android.os.Environment
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.image_multi_recognition.db.AlbumInfo
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.util.getDifference
import com.example.image_multi_recognition.viewmodel.basic.ImageFileOperationSupport
import com.example.image_multi_recognition.viewmodel.basic.ImageFileOperationSupportViewModel
import com.example.image_multi_recognition.viewmodel.basic.ImagePagingFlowSupport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltViewModel
open class PhotoViewModel @Inject constructor(
    private val repository: ImageRepository,
    imageFileOperationSupportViewModel: ImageFileOperationSupportViewModel,
    savedStateHandle: SavedStateHandle,
) : ViewModel(), ImagePagingFlowSupport, ImageFileOperationSupport by imageFileOperationSupportViewModel {
    // "ImageFileOperationSupport by imageFileOperationSupportViewModel": you can use delegation to achieve something like multi-inheritance in Kotlin
    private val backgroundThreadPool: ExecutorService = Executors.newCachedThreadPool()

    override val imageIdOriginalIndexMap: MutableMap<Long, Int> = mutableMapOf()
    private var currentMediaStoreVersion: String? = null
    // for startDestination, the "album" is null by default
    var currentAlbum: Long? = savedStateHandle.get<Long>("album")

    private val _pagingFlow: MutableStateFlow<Flow<PagingData<UiModel>>> = MutableStateFlow(emptyFlow())
    val pagingFlow: StateFlow<Flow<PagingData<UiModel>>>
        get() = _pagingFlow

    init {
        // check the argument of navigation route to decide on what purpose the PhotoViewModel is used
        // (on a first load when app is started or jumped from an album window)
        // We do the initialization only when the currentAlbum is null(in AlbumPhotoViewModel. the value is not null)
        if (currentAlbum == null) {
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
        } else {
            // the viewModel is used for AlbumPhotoComposable
            setImagePagingFlow(currentAlbum!!)
        }
    }

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
                        // deletedIds = deletedItemIds,
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
        repository.genImageRequest(file, imageInfo)
    }

    fun setImagePagingFlow(album: Long) {
        _pagingFlow.value = repository.getImagePagingFlow(album)
            .convertImageInfoPagingFlow()
            .cachedIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        backgroundThreadPool.shutdownNow()
    }
}