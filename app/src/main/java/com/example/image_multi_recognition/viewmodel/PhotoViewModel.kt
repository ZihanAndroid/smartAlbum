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
import com.example.image_multi_recognition.util.*
import com.example.image_multi_recognition.viewmodel.basic.ImageFileOperationSupport
import com.example.image_multi_recognition.viewmodel.basic.ImageFileOperationSupportViewModel
import com.example.image_multi_recognition.viewmodel.basic.ImagePagingFlowSupport
import com.example.image_multi_recognition.viewmodel.basic.ImagePagingFlowSupportImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltViewModel
open class PhotoViewModel @Inject constructor(
    private val repository: ImageRepository,
    imageFileOperationSupportViewModel: ImageFileOperationSupportViewModel,
    savedStateHandle: SavedStateHandle,
    imagePagingFlowSupportImpl: ImagePagingFlowSupportImpl
) : ViewModel(), ImagePagingFlowSupport by imagePagingFlowSupportImpl,
    ImageFileOperationSupport by imageFileOperationSupportViewModel {
    // "ImageFileOperationSupport by imageFileOperationSupportViewModel": you can use delegation to do things like multi-inheritance in Kotlin
    private val backgroundThreadPool: ExecutorService = Executors.newCachedThreadPool()

    // for startDestination, the "album" is null by default
    var currentAlbum: Long? = savedStateHandle.get<Long>("album")

    private val _pagingFlow: MutableStateFlow<Flow<PagingData<UiModel>>> = MutableStateFlow(emptyFlow())
    val pagingFlow: StateFlow<Flow<PagingData<UiModel>>>
        get() = _pagingFlow
    private var isFirstLoad: Boolean = true
    val scanRunner = ControlledRunner<Unit>()

    init {
        // check the argument of navigation route to decide on what purpose the PhotoViewModel is used
        // (on a first load when app is started or jumped from an album window)
        // We do the initialization only when the currentAlbum is null(in AlbumPhotoViewModel. the value is not null)
        if (currentAlbum == null) {
            viewModelScope.launch {
                // DCIM, the album shown in the "photos" screen (the first screen) when the app is started
                val dcimCameraDir =
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
                Log.d(getCallSiteInfo(), "DCIMAlbum: DCIMAlbum")
                // load DCIM first to avoid keeping users waiting
                currentAlbum = repository.addAlbumInfoIfNotExist(AlbumInfo(path = dcimCameraDir.absolutePath))
                AlbumPathDecoder.initDecoder(mapOf(currentAlbum!! to dcimCameraDir))
                val job = launch {
                    withContext(Dispatchers.IO) {
                        scanImages(listOf(currentAlbum!!), currentAlbum)
                    }
                }

                repository.mediaStoreChangeFlow.collectLatest { _ ->
                    // whenever a ContentResolver is changed (like the user takes a photo by another app),
                    // we detect that change and rescan the directory to check the potential updates

                    // multiple requests may be sent here in a short time for mediaStore change
                    scanRunner.joinPreviousOrRun {
                        // withContext(Dispatchers.IO) {
                        val albumList = repository.getAllImageDir()
                        // Log.d(getCallSiteInfoFunc(), "albumList: ${albumList.joinToString("\n")}")
                        // check album path existence
                        val previousAlbums =
                            repository.getAllAlbumInfo()   // Now the DCIM is definitely is in previousAlbums
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
                                // get auto-incremented id and set albumId with it
                                albumInfoToAdd[index].album = album
                            }
                        mutableMapOf<Long, File>().apply {
                            previousAlbums.forEach { this[it.album] = File(it.path) }
                            albumInfoToDelete.forEach { this.remove(it.album) }
                            albumInfoToAdd.forEach { this[it.album] = File(it.path) }
                            if (isFirstLoad) this[currentAlbum!!] = dcimCameraDir
                            AlbumPathDecoder.initDecoder(this)
                        }

                        if (AlbumPathDecoder.albums.isNotEmpty()) {
                            scanImages(
                                albums = if (isFirstLoad) AlbumPathDecoder.albums - currentAlbum!! else AlbumPathDecoder.albums,
                                prevJob = job
                            )
                        }
                        Log.d(getCallSiteInfoFunc(), "Initialization done!")
                    }
                }
            }
        } else {
            // the viewModel is used for AlbumPhotoComposable, etc.
            setImagePagingFlow(currentAlbum!!)
        }
    }

    private suspend fun scanImages(albums: List<Long>, albumToShow: Long? = null, prevJob: Job? = null) {
        // First, we compare the file info stored in DB and storage
        // Delete the inconsistent part in DB and add new info of files in storage to DB
        coroutineScope {   // suspend here
            val allImageInfoList = ConcurrentLinkedQueue<List<ImageInfo>>()
            albums.forEach { album ->
                withContext(Dispatchers.IO) {
                    val albumPath = AlbumPathDecoder.decode(album)
                    Log.d(getCallSiteInfo(), "Album path: ${albumPath.absolutePath}")
                    val imageInfoList = repository.getAllImageInfoByAlbum(album)
                    // Log.d(
                    //     getCallSiteInfo(),
                    //     "all the files inside album:\n${
                    //         albumPath.walk().map { it.absolutePath }.joinToString(separator = "\n")
                    //     }"
                    // )
                    val (addedFiles, deletedItems) = ((albumPath.listFiles())?.filter { file ->
                        // a file whose name start with "." is considered a trash file or something
                        file.isFile && AlbumPathDecoder.validImageSuffix.contains(file.extension) && !(file.name.startsWith(
                            "."
                        ))
                    } ?: emptyList()).getDifference(
                        list = imageInfoList,
                        keyExtractorThis = { it.absolutePath },
                        keyExtractorParam = { it.fullImageFile.absolutePath }
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
                    if (album == albumToShow && (isFirstLoad || deletedItems.isNotEmpty() || addedImageInfo.isNotEmpty())) {
                        // update paging flow only when we detect changes to update the screen
                        setImagePagingFlow(albumToShow)   // update image flow
                        isFirstLoad = false
                    }
                    // generate thumbnails
                    allImageInfoList.add(
                        // For images created with a more recent timestamp by camera, its file name tends to have a higher natural order
                        // And we show the more recent photos to the users first, and want it to be cached first here.
                        // So that when user scrolling the photos, it will have more chance to hit the cache
                        if (album == albumToShow) (imageInfoList + addedImageInfo).sortedByDescending { it.path }
                        else (imageInfoList + addedImageInfo)
                    )
                }
            }
            // let the thumbnails from DCIM generated first
            prevJob?.join()

            // allImageInfoList.forEach { imageInfoList ->
            //     imageInfoList.forEach { imageInfo ->
            //         if (!imageInfo.isThumbnailAvailable) {
            //             requestThumbnail(imageInfo.fullImageFile, imageInfo)
            //             // avoid getting ImageLoader overwhelmed so that it cannot handle the request from UI right away
            //             // (The onSuccess of "genImageRequest" runs in the UI main thread)
            //             delay(100)
            //         }
            //     }
            // }
        }
    }

    fun requestThumbnail(file: File, imageInfo: ImageInfo) {
        repository.genImageRequest(file, imageInfo)
    }

    fun setImagePagingFlow(album: Long) {
        _pagingFlow.value = repository.getImagePagingFlow(album)
            .convertImageInfoPagingFlow(ImagePagingFlowSupport.PagingSourceType.IMAGE)
            .cachedIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        backgroundThreadPool.shutdownNow()
    }
}