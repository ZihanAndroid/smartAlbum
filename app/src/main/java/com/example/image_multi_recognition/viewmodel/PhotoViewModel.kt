package com.example.image_multi_recognition.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.image_multi_recognition.db.AlbumInfo
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.domain.GetImageChangeUseCase
import com.example.image_multi_recognition.model.UiModel
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.repository.UserSettingRepository
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
    settingRepository: UserSettingRepository,
    private val getImageChangeUseCase: GetImageChangeUseCase,
    imageFileOperationSupportViewModel: ImageFileOperationSupportViewModel,
    imagePagingFlowSupportImpl: ImagePagingFlowSupportImpl,
    savedStateHandle: SavedStateHandle,
) : ViewModel(), ImagePagingFlowSupport by imagePagingFlowSupportImpl,
    ImageFileOperationSupport by imageFileOperationSupportViewModel {
    // "ImageFileOperationSupport by imageFileOperationSupportViewModel": you can use delegation to do things like multi-inheritance in Kotlin
    private val backgroundThreadPool: ExecutorService = Executors.newCachedThreadPool()

    // for startDestination, the "album" is null by default
    var currentAlbum: Long? = savedStateHandle.get<Long>("album")

    private val _pagingFlow: MutableStateFlow<Flow<PagingData<UiModel>>> = MutableStateFlow(emptyFlow())
    val pagingFlow: StateFlow<Flow<PagingData<UiModel>>>
        get() = _pagingFlow

    val imagePerRowFlow = settingRepository.imagesPerRowFlow
    private var thumbnailQuality: Float = 0.1f

    private var isFirstLoad: Boolean? = null
    // the default of replay in SharedFlow is zero,
    // so that the previous emitted values will not be handled again when "currentThumbnailHandler" is changed
    private val thumbnailRequestFlow = MutableSharedFlow<ThumbnailRequest?>()
    private var currentThumbnailHandler: Job? = null
    private var thumbnailRequestSent: Int = 0
    private var thumbnailRequestHandled: Int = 0

    init {
        currentThumbnailHandler = collectThumbnailRequest()
        // check the argument of navigation route to decide on what purpose the PhotoViewModel is used
        // (on a first load when app is started or jumped from an album window)
        // We do the initialization only when the currentAlbum is null(in AlbumPhotoViewModel. the value is not null)
        if (currentAlbum == null) {
            viewModelScope.launch {
                // DCIM, the album shown in the "photos" screen (the first screen) when the app is started
                // val dcimCameraDir =
                //     File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera")
                val dcimCameraDir = File(settingRepository.defaultAlbumPathFlow.first())  // use can set default albums
                // load DCIM first to avoid keeping users waiting
                currentAlbum = repository.addAlbumInfoIfNotExist(AlbumInfo(path = dcimCameraDir.absolutePath))
                AlbumPathDecoder.initDecoder(mapOf(currentAlbum!! to dcimCameraDir))
                val job = launch {
                    withContext(Dispatchers.IO) {
                        scanImages(
                            albums = listOf(currentAlbum!!),
                            albumToShow = currentAlbum,
                            cacheEnabled = settingRepository.imageCacheEnabledFlow.first()
                        )
                    }
                }
                // allow changing default album shown in the first window
                viewModelScope.launch {
                    settingRepository.defaultAlbumPathFlow.collectLatest { albumPath ->
                        if (albumPath != AlbumPathDecoder.decode(currentAlbum!!).absolutePath) {
                            val albumId = repository.getAlbumByPath(albumPath)
                            albumId?.let { id ->
                                setImagePagingFlow(id)
                            }
                            currentAlbum = albumId
                        }
                    }
                }
                var prevImageChangeJob: Job? = null
                getImageChangeUseCase().collectLatest { imageChange ->
                    prevImageChangeJob?.cancel()
                    prevImageChangeJob = launch {
                        thumbnailQuality = imageChange.thumbnailQuality
                        if (isFirstLoad == null) {
                            isFirstLoad = true
                        }else{
                            // cancel the first load when necessary
                            while (isFirstLoad == true) {
                                delay(100)
                            }
                            if (!job.isCancelled) job.cancel()
                        }
                        // cancel all the ongoing thumbnail requests,
                        // but it does not stop generating thumbnails immediately because we load images and generate thumbnails asynchronously
                        currentThumbnailHandler?.cancel()
                        currentThumbnailHandler = collectThumbnailRequest()
                        if (!imageChange.thumbnailEnabled || imageChange.isThumbnailQualityChange) {
                            // wait for all thumbnail files generated so that we will not leave out any thumbnail files after deletion
                            while (thumbnailRequestSent != thumbnailRequestHandled) {
                                delay(100)
                            }
                            withContext(Dispatchers.IO) {
                                // remove all cached thumbnails
                                ScopedThumbNailStorage.removeAllThumbnails()
                            }
                            thumbnailRequestSent = 0
                            thumbnailRequestHandled = 0
                        }
                        // Whenever a ContentResolver is changed (like the user takes a photo by another app),
                        // we detect that change and rescan the directory to check the potential updates.
                        // Multiple requests may be sent here in a short time for mediaStore change
                        val albumList = repository.getAllImageDir()
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
                                // get auto-incremented id and set albumId with it
                                albumInfoToAdd[index].album = album
                            }
                        mutableMapOf<Long, File>().apply {
                            previousAlbums.forEach { this[it.album] = File(it.path) }
                            albumInfoToDelete.forEach { this.remove(it.album) }
                            albumInfoToAdd.forEach { this[it.album] = File(it.path) }
                            if (isFirstLoad == true) this[currentAlbum!!] = dcimCameraDir
                            AlbumPathDecoder.initDecoder(this)
                        }

                        if (AlbumPathDecoder.albums.isNotEmpty()) {
                            scanImages(
                                albums = if (isFirstLoad == true) AlbumPathDecoder.albums - currentAlbum!! else AlbumPathDecoder.albums,
                                prevScanJob = job,
                                cacheEnabled = imageChange.thumbnailEnabled,
                                albumToShow = if (isFirstLoad == true) null else currentAlbum
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

    private suspend fun scanImages(
        albums: List<Long>,
        albumToShow: Long? = null,
        cacheEnabled: Boolean,
        prevScanJob: Job? = null,
    ) {
        // First, we compare the file info stored in DB and storage
        // Delete the inconsistent part in DB and add new info of files in storage to DB
        coroutineScope {   // suspend here
            val allImageInfoList = ConcurrentLinkedQueue<List<ImageInfo>>()
            var currentAlbumImageInfoList: List<ImageInfo>? = null
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
                        file.isFile && AlbumPathDecoder.validImageSuffix
                            .contains(file.extension) && !(file.name.startsWith("."))
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
                        addedFilePaths = addedFiles,
                        album = album
                    )
                    Log.d(getCallSiteInfo(), "album: $album, albumToShow: $albumToShow")
                    if (album == albumToShow && (isFirstLoad == true || deletedItems.isNotEmpty() || addedImageInfo.isNotEmpty())) {
                        // update paging flow only when we detect changes to update the screen
                        setImagePagingFlow(albumToShow)   // update image flow
                        isFirstLoad = false
                    }
                    if (album == albumToShow) {
                        // For images created with a more recent timestamp by camera, its file name tends to have a higher natural order.
                        // And we show the more recent photos to the users first, and want it to be cached first here.
                        // So that when a user scrolls the photos, it will have more chance to hit the cache
                        currentAlbumImageInfoList = (imageInfoList + addedImageInfo).sortedByDescending { it.path }
                    } else {
                        allImageInfoList.add(imageInfoList + addedImageInfo)
                    }
                }
            }
            // let the thumbnails from DCIM generated first
            prevScanJob?.join()
            if (cacheEnabled) {
                // generate cache for currentAlbum first
                thumbnailRequestFlow.emit(
                    ThumbnailRequest(
                        // let the images inside the "currentAlbum" get handled first, so put it at the head of the list
                        listOf(currentAlbumImageInfoList ?: emptyList()) + allImageInfoList.toList()
                    )
                )
            }
        }
    }

    private fun collectThumbnailRequest(): Job {
        return viewModelScope.launch {
            thumbnailRequestFlow.collect { request ->
                request?.let { thumbnailRequest ->
                    for (imageInfoList in thumbnailRequest.imageInfoList) {
                        if (!isActive) return@collect
                        for (imageInfo in imageInfoList) {
                            if (!isActive) return@collect
                            if (!imageInfo.isThumbnailAvailable) {
                                requestThumbnail(imageInfo.fullImageFile, imageInfo) {
                                    ++thumbnailRequestHandled
                                }
                                ++thumbnailRequestSent
                                // avoid getting ImageLoader overwhelmed so that it cannot handle the request from UI right away
                                // (The onSuccess of "genImageRequest" runs in the UI main thread)
                                delay(150)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestThumbnail(file: File, imageInfo: ImageInfo, onFinish: () -> Unit) {
        repository.genImageRequest(file, imageInfo, thumbnailQuality, onFinish)
    }

    private fun setImagePagingFlow(album: Long) {
        _pagingFlow.value = repository.getImagePagingFlow(album)
            .convertImageInfoPagingFlow(ImagePagingFlowSupport.PagingSourceType.IMAGE)
            .cachedIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        backgroundThreadPool.shutdownNow()
    }

    data class ThumbnailRequest(
        val imageInfoList: List<List<ImageInfo>>,
    )
}