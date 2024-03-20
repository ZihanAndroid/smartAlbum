package com.example.image_multi_recognition.viewmodel

import android.app.PendingIntent
import android.os.Environment
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.image_multi_recognition.db.AlbumInfo
import com.example.image_multi_recognition.db.AlbumInfoWithLatestImage
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.util.*
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
    savedStateHandle: SavedStateHandle,
) : ViewModel(), ImagePagingFlowSupport {
    private val backgroundThreadPool: ExecutorService = Executors.newCachedThreadPool()

    // you should set pagingSourceChanged to "true" before you modify the DB tables that may affect the PagingFlow
    override val imageIdOriginalIndexMap: MutableMap<Long, Int> = mutableMapOf()

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
        repository.genImageRequest(file, imageInfo)
    }

//    fun sendImageRequestForThumbnail(file: File, imageInfo: ImageInfo) {
//        viewModelScope.launch {
//            withContext(Dispatchers.Default) {
//                repository.genThumbnail(file, imageInfo)
//            }
//        }
//    }

    fun setImagePagingFlow(album: Long) {
        _pagingFlow.value = repository.getImagePagingFlow(album)
            .convertImageInfoPagingFlow()
            .cachedIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        backgroundThreadPool.shutdownNow()
    }

    // icons' operations
    private val _deleteImagesStateFlow = MutableStateFlow<Boolean>(false)
    val deleteImageStateFlow: StateFlow<Boolean>
        get() = _deleteImagesStateFlow

    private val _copyImagesStateFlow = MutableStateFlow<Boolean>(false)
    val copyImageStateFlow: StateFlow<Boolean>
        get() = _copyImagesStateFlow

    private val _moveImagesStateFlow = MutableStateFlow<Boolean>(false)
    val moveImageStateFlow: StateFlow<Boolean>
        get() = _moveImagesStateFlow

    private val _renameImagesStateFlow = MutableStateFlow<Boolean>(false)
    val renameImageStateFlow: StateFlow<Boolean>
        get() = _renameImagesStateFlow

    private val _addFavoriteImagesStateFlow = MutableStateFlow<Boolean>(false)
    val addFavoriteImageStateFlow: StateFlow<Boolean>
        get() = _addFavoriteImagesStateFlow

    fun resetDeleteImagesState() {
        _deleteImagesStateFlow.value = false
    }

    fun resetMoveImagesState() {
        _moveImagesStateFlow.value = false
    }

    private val _deletionPendingIntentFlow = MutableStateFlow<PendingIntent?>(null)
    val deletionPendingIntentFlow
        get() = _deletionPendingIntentFlow

    private val _movePendingIntentFlow = MutableStateFlow<StorageHelper.MediaModifyRequest?>(null)
    val movePendingIntentFlow
        get() = _movePendingIntentFlow

    lateinit var imageMoveRequest: ImageMoveRequest

    // step1 for deleting images, ask user permission
    fun requestImagesDeletion(imageIdList: List<Long>, onRequestFail: suspend () -> Unit) {
        _deleteImagesStateFlow.value = true
        viewModelScope.launch {
            val imageInfoList = repository.getImageInfoById(imageIdList)
            repository.getDeleteImagesRequest(imageInfoList).let { pendingIntent ->
                if (pendingIntent == null) {
                    Log.e(getCallSiteInfoFunc(), "PendingIntent is null!")
                    _deleteImagesStateFlow.value = false
                    onRequestFail()
                } else {
                    _deletionPendingIntentFlow.value = pendingIntent
                }
            }
        }
    }

    // step2 for deleting images
    fun deleteImages(imageIdList: List<Long>, onComplete: suspend () -> Unit) {
        viewModelScope.launch {
            repository.deleteImagesById(imageIdList)
            _deleteImagesStateFlow.value = false
            onComplete()
        }
    }

    // step1 for moving images, ask user permission
    fun requestImagesMove(imageIdList: List<Long>, newAlbum: AlbumInfo, isAlbumNew: Boolean, onRequestFail: suspend () -> Unit) {
        _moveImagesStateFlow.value = true
        viewModelScope.launch {
            val imageInfoList = repository.getImageInfoById(imageIdList)
            repository.getMoveImageRequest(imageInfoList).let { mediaModifyRequest ->
                if (mediaModifyRequest.pendingIntent == null) {
                    Log.e(getCallSiteInfoFunc(), "PendingIntent is null!")
                    _deleteImagesStateFlow.value = false
                    onRequestFail()
                } else {
                    // cache parameters for the following moveImagesTo() call
                    imageMoveRequest = ImageMoveRequest(newAlbum, isAlbumNew, mediaModifyRequest, imageInfoList)
                    _movePendingIntentFlow.value = mediaModifyRequest
                }
            }
        }
    }

    // step2 for moving images
    // onComplete: accept a list of absolute paths for failed copy
    fun moveImagesTo(onComplete: suspend (List<String>) -> Unit) {
        viewModelScope.launch {
            // copy images
            val failedItems = repository.copyImageTo(
                newAlbum = imageMoveRequest.newAlbum,
                items = imageMoveRequest.request.mediaStoreItems,
            )
            // delete original images, since we have called "MediaStore.createWriteRequest()",
            // we do not need to request permission here
            if(failedItems.size != imageMoveRequest.request.mediaStoreItems.size) {
                repository.deleteImageFiles(imageMoveRequest.request.mediaStoreItems.map { it.contentUri }
                        - failedItems.map { it.contentUri }.toSet())
                // modify album info in the database
                if(imageMoveRequest.isAlbumNew){
                    repository.insertAlbumInfo(imageMoveRequest.newAlbum)
                }
                repository.updateImageInfo(
                    *imageMoveRequest.imageInfoList.map { it.copy(album = imageMoveRequest.newAlbum.album) }
                        .toTypedArray()
                )
            }
            _moveImagesStateFlow.value = false
            onComplete(failedItems.map { it.absolutePath })
        }
    }

    fun copyImagesTo(newAlbum: AlbumInfo, imageIdList: List<Long>) {
        viewModelScope.launch {
            _copyImagesStateFlow.value = true
            delay(5000)
            _copyImagesStateFlow.value = false
        }
    }

    fun renameImage(imageId: Long) {
        // change file name and modify path
        viewModelScope.launch {
            _renameImagesStateFlow.value = true
            delay(5000)
            _renameImagesStateFlow.value = false
        }
    }

    fun changeFavoriteImages(imageIdList: List<Long>) {
        viewModelScope.launch {
            _addFavoriteImagesStateFlow.value = true
            repository.changeImageInfoFavorite(imageIdList)
            _addFavoriteImagesStateFlow.value = false
        }
    }

    fun shareImages(imageIdList: List<Long>) {
        Log.d(getCallSiteInfo(), "shareImages() is called")
    }

    val _albumListStateFlow: MutableStateFlow<List<AlbumInfoWithLatestImage>> = MutableStateFlow(emptyList())
    val albumListStateFlow: StateFlow<List<AlbumInfoWithLatestImage>>
        get() = _albumListStateFlow

    fun setAlbumListStateFlow(excludedAlbum: Long) {
        viewModelScope.launch {
            _albumListStateFlow.value = repository.getAlbumInfoWithLatestImage(excludedAlbum)
        }
    }

    data class ImageMoveRequest(
        val newAlbum: AlbumInfo,
        val isAlbumNew: Boolean,
        val request: StorageHelper.MediaModifyRequest,
        val imageInfoList: List<ImageInfo>
    )
}