package com.example.image_multi_recognition.viewmodel.basic

import android.app.Activity
import android.app.PendingIntent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.db.AlbumInfo
import com.example.image_multi_recognition.db.AlbumInfoWithLatestImage
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.util.StorageHelper
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.util.showSnackBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Stable
interface ImageFileOperationSupport {
    val deleteImageStateFlow: StateFlow<Boolean>
    val copyImageStateFlow: StateFlow<Boolean>
    val moveImageStateFlow: StateFlow<Boolean>
    val renameImageStateFlow: StateFlow<Boolean>
    val addFavoriteImageStateFlow: StateFlow<Boolean>
    val pendingIntentFlow: StateFlow<PendingIntent?>
    // val movePendingIntentFlow: StateFlow<StorageHelper.MediaModifyRequest?>
    val albumListStateFlow: StateFlow<List<AlbumInfoWithLatestImage>>

    fun resetDeleteImagesState()
    fun resetMoveImagesState()
    fun resetRenameImagesState()
    fun requestImagesDeletion(imageIdList: List<Long>, onRequestFail: suspend () -> Unit)
    fun deleteImages(imageIdList: List<Long>, onComplete: suspend () -> Unit)
    fun requestImagesMove(
        imageIdList: List<Long>,
        newAlbum: AlbumInfo,
        isAlbumNew: Boolean,
        onRequestFail: suspend () -> Unit
    )
    fun moveImagesTo(onComplete: suspend (List<Pair<ImageInfo, StorageHelper.ImageCopyError>>) -> Unit)
    fun copyImagesTo(
        newAlbum: AlbumInfo,
        isAlbumNew: Boolean,
        imageIdList: List<Long>,
        onComplete: suspend (List<Pair<String, StorageHelper.ImageCopyError>>) -> Unit
    )
    fun changeFavoriteImages(imageIdList: List<Long>)
    fun shareImages(imageIdList: List<Long>)
    fun setAlbumListStateFlow(excludedAlbum: Long)
    fun noImageOperationOngoing(): Boolean
    suspend fun getAllImageIdsByCurrentAlbum(currentAlbum: Long): List<Long>
    fun requestFileNameUpdate(
        imageId: Long,
        absolutePath: String,
        newFileName: String,
        onRequestFail: suspend () -> Unit
    )
    fun updateFileName(onComplete: suspend () -> Unit, onFailure: suspend () -> Unit)
}

@Singleton
class ImageFileOperationSupportViewModel @Inject constructor(
    val repository: ImageRepository
) : ViewModel(), ImageFileOperationSupport {
    // image operations
    private val _deleteImagesStateFlow = MutableStateFlow(false)
    override val deleteImageStateFlow: StateFlow<Boolean>
        get() = _deleteImagesStateFlow

    private val _copyImagesStateFlow = MutableStateFlow(false)
    override val copyImageStateFlow: StateFlow<Boolean>
        get() = _copyImagesStateFlow

    private val _moveImagesStateFlow = MutableStateFlow(false)
    override val moveImageStateFlow: StateFlow<Boolean>
        get() = _moveImagesStateFlow

    private val _renameImagesStateFlow = MutableStateFlow(false)
    override val renameImageStateFlow: StateFlow<Boolean>
        get() = _renameImagesStateFlow

    private val _addFavoriteImagesStateFlow = MutableStateFlow(false)
    override val addFavoriteImageStateFlow: StateFlow<Boolean>
        get() = _addFavoriteImagesStateFlow

    override fun resetDeleteImagesState() {
        _deleteImagesStateFlow.value = false
    }

    override fun resetMoveImagesState() {
        _moveImagesStateFlow.value = false
    }

    override fun resetRenameImagesState() {
        _renameImagesStateFlow.value = false
    }

    private val _pendingIntentFlow = MutableStateFlow<PendingIntent?>(null)
    override val pendingIntentFlow: StateFlow<PendingIntent?>
        get() = _pendingIntentFlow
    // override val deletionPendingIntentFlow
    //     get() = _deletionPendingIntentFlow

    // private val _movePendingIntentFlow = MutableStateFlow<StorageHelper.MediaModifyRequest?>(null)
    // override val movePendingIntentFlow
    //     get() = _movePendingIntentFlow

    lateinit var imageMoveRequest: ImageMoveRequest

    // step1 for deleting images, ask user permission
    override fun requestImagesDeletion(imageIdList: List<Long>, onRequestFail: suspend () -> Unit) {
        _deleteImagesStateFlow.value = true
        viewModelScope.launch {
            val imageInfoList = repository.getImageInfoById(imageIdList)
            repository.getDeleteImagesRequest(imageInfoList).let { pendingIntent ->
                if (pendingIntent == null) {
                    Log.e(getCallSiteInfoFunc(), "PendingIntent is null!")
                    _deleteImagesStateFlow.value = false
                    onRequestFail()
                } else {
                    _pendingIntentFlow.value = pendingIntent
                }
            }
        }
    }

    // step2 for deleting images
    override fun deleteImages(imageIdList: List<Long>, onComplete: suspend () -> Unit) {
        viewModelScope.launch {
            repository.deleteImagesById(imageIdList)
            _deleteImagesStateFlow.value = false
            onComplete()
        }
    }

    // step1 for moving images, ask user permission
    override fun requestImagesMove(
        imageIdList: List<Long>,
        newAlbum: AlbumInfo,
        isAlbumNew: Boolean,
        onRequestFail: suspend () -> Unit
    ) {
        _moveImagesStateFlow.value = true
        viewModelScope.launch {
            val imageInfoList = repository.getImageInfoById(imageIdList)
            repository.getMoveImageRequest(imageInfoList).let { mediaModifyRequest ->
                if (mediaModifyRequest.pendingIntent == null) {
                    Log.e(getCallSiteInfoFunc(), "PendingIntent is null!")
                    _moveImagesStateFlow.value = false
                    onRequestFail()
                } else {
                    // cache parameters for the following moveImagesTo() call
                    imageMoveRequest =
                        ImageMoveRequest(newAlbum, isAlbumNew, mediaModifyRequest, imageInfoList)
                    _pendingIntentFlow.value = mediaModifyRequest.pendingIntent
                }
            }
        }
    }

    // step2 for moving images
    // onComplete: accept a list of absolute paths for failed copy
    override fun moveImagesTo(onComplete: suspend (List<Pair<ImageInfo, StorageHelper.ImageCopyError>>) -> Unit) {
        viewModelScope.launch {
            // copy images
            val failedAbsolutePaths = repository.copyImageTo(
                newAlbum = imageMoveRequest.newAlbum,
                items = imageMoveRequest.request.mediaStoreItems.map {
                    ImageRepository.ImageCopyItem(
                        it.absolutePath,
                        it.mimeType
                    )
                },
            )
            // delete original images, since we have called "MediaStore.createWriteRequest()",
            // we do not need to request permission here
            if (failedAbsolutePaths.size != imageMoveRequest.request.mediaStoreItems.size) {
                repository.deleteImageFiles(imageMoveRequest.request.mediaStoreItems
                    .filter { it.absolutePath !in failedAbsolutePaths }.map { it.contentUri })
                // create new album in the database if necessary
                repository.updateImageAlbum(
                    newAlbum = if (imageMoveRequest.isAlbumNew) imageMoveRequest.newAlbum else null,
                    imageInfoList = imageMoveRequest.imageInfoList
                        .filter { it.fullImageFile.absolutePath !in failedAbsolutePaths }
                        .map { it.copy(album = imageMoveRequest.newAlbum.album) }
                )
            }
            _moveImagesStateFlow.value = false
            onComplete(
                imageMoveRequest.imageInfoList.filter { it.fullImageFile.absolutePath in failedAbsolutePaths }
                    .map { it to failedAbsolutePaths[it.fullImageFile.absolutePath]!! }
            )
        }
    }

    override fun copyImagesTo(
        newAlbum: AlbumInfo,
        isAlbumNew: Boolean,
        imageIdList: List<Long>,
        onComplete: suspend (List<Pair<String, StorageHelper.ImageCopyError>>) -> Unit
    ) {
        viewModelScope.launch {
            _copyImagesStateFlow.value = true
            val imageInfoList = repository.getImageInfoById(imageIdList)
            val mimeTypes = repository.getMimeTypes(imageInfoList.map { it.fullImageFile.absolutePath })
            val failedPaths = repository.copyImageTo(newAlbum, imageInfoList.map { imageInfo ->
                ImageRepository.ImageCopyItem(
                    imageInfo.fullImageFile.absolutePath,
                    mimeTypes[imageInfo.fullImageFile.absolutePath] ?: ""
                )
            })
            // create new ImageInfo in the database
            if (failedPaths.size != imageInfoList.size) {
                repository.createImageAlbum(
                    newAlbum = if (isAlbumNew) newAlbum else null,
                    imageInfoList = imageInfoList.filter { it.fullImageFile.absolutePath !in failedPaths }
                        // Note: you need to set the id to 0 to tell Room that the ImageInfo does not exist in the database (id not set yet)
                        // set it to any other value causes a conflict when inserting (resolved by something like: @Insert(onConflict = OnConflictStrategy.IGNORE))
                        .map { it.copy(album = newAlbum.album, id = 0) }
                )
            }
            _copyImagesStateFlow.value = false
            onComplete(failedPaths.toList())
        }
    }

    override fun changeFavoriteImages(imageIdList: List<Long>) {
        viewModelScope.launch {
            _addFavoriteImagesStateFlow.value = true
            repository.changeImageInfoFavorite(imageIdList)
            _addFavoriteImagesStateFlow.value = false
        }
    }

    override fun shareImages(imageIdList: List<Long>) {
        Log.d(getCallSiteInfo(), "shareImages() is called")
    }

    private val _albumListStateFlow: MutableStateFlow<List<AlbumInfoWithLatestImage>> = MutableStateFlow(emptyList())
    override val albumListStateFlow: StateFlow<List<AlbumInfoWithLatestImage>>
        get() = _albumListStateFlow

    override fun setAlbumListStateFlow(excludedAlbum: Long) {
        viewModelScope.launch {
            _albumListStateFlow.value = repository.getAlbumInfoWithLatestImage(excludedAlbum)
        }
    }

    override suspend fun getAllImageIdsByCurrentAlbum(currentAlbum: Long): List<Long> {
        return repository.getAllImagesByAlbum(currentAlbum)
    }

    override fun noImageOperationOngoing(): Boolean =
        !_moveImagesStateFlow.value && !_deleteImagesStateFlow.value && !_renameImagesStateFlow.value
                && !_copyImagesStateFlow.value && !_addFavoriteImagesStateFlow.value

    private lateinit var nameUpdate: FileNameUpdateRequest
    override fun requestFileNameUpdate(
        imageId: Long,
        absolutePath: String,
        newFileName: String,
        onRequestFail: suspend () -> Unit
    ) {
        _renameImagesStateFlow.value = true
        viewModelScope.launch {
            repository.requestFileNameUpdate(absolutePath).let { mediaModifyRequest ->
                if (mediaModifyRequest.pendingIntent == null) {
                    Log.e(getCallSiteInfoFunc(), "PendingIntent is null!")
                    _renameImagesStateFlow.value = false
                    onRequestFail()
                } else {
                    // cache parameters for the following moveImagesTo() call
                    nameUpdate = FileNameUpdateRequest(imageId, absolutePath, newFileName)
                    _pendingIntentFlow.value = mediaModifyRequest.pendingIntent
                }
            }
        }
    }

    override fun updateFileName(onComplete: suspend () -> Unit, onFailure: suspend () -> Unit) {
        viewModelScope.launch {
            if (repository.updateFileName(nameUpdate.imageId, nameUpdate.absolutePath, nameUpdate.newFileName)) {
                onComplete()
            } else {
                onFailure()
            }
            _renameImagesStateFlow.value = false
        }
    }

    data class ImageMoveRequest(
        val newAlbum: AlbumInfo,
        val isAlbumNew: Boolean,
        val request: StorageHelper.MediaModifyRequest,
        val imageInfoList: List<ImageInfo>
    )

    data class FileNameUpdateRequest(
        val imageId: Long,
        val absolutePath: String,
        val newFileName: String
    )
}

// In composable, you only need to use ImageFileOperationComposableSupport()
// and call `viewModel.requestImagesDeletion()` for deleting images
// and `requestImagesDeletion.requestImagesMove()` for moving images.
// Then all the other work is handled by ImageFileOperationComposableSupport()
// (sharing the same imageRequestLauncher logic in multiple composable functions)
@Composable
fun ImageFileOperationComposableSupport(
    support: ImageFileOperationSupport,
    snackbarHostState: SnackbarHostState,
    imageIdList: List<Long>,    // the images handled by delete or move currently (which requires user permission like MediaStore.createDeleteRequest() )
    onDeleteSuccessExtra: () -> Unit = {},
    onDeleteFailExtra: () -> Unit = {},
    onMoveSuccessExtra: (List<ImageInfo>) -> Unit = {},   // List<ImageInfo>: images failed to move
    onMoveFailExtra: () -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // track image files' operation state
    val onImagesDeleting by support.deleteImageStateFlow.collectAsStateWithLifecycle()
    val onImagesMoving by support.moveImageStateFlow.collectAsStateWithLifecycle()
    val onImageRenaming by support.renameImageStateFlow.collectAsStateWithLifecycle()
    val pendingIntent by support.pendingIntentFlow.collectAsStateWithLifecycle()
    val handledImageIdList by rememberUpdatedState(imageIdList)
    val handledSnackbarHostState by rememberUpdatedState(snackbarHostState)

    // https://stackoverflow.com/questions/64721218/jetpack-compose-launch-activityresultcontract-request-from-composable-function
    val imageRequestLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { activityResult ->
            if (onImagesDeleting) {  // only monitor the ActivityResult generated from deleting images
                if (activityResult.resultCode == Activity.RESULT_OK) {
                    support.deleteImages(handledImageIdList) {
                        showSnackBar(handledSnackbarHostState, "${context.getString(R.string.deletion_success)}!")
                    }
                    onDeleteSuccessExtra()
                } else {
                    // failed to delete images, user may deny our deletion request
                    coroutineScope.launch {
                        showSnackBar(handledSnackbarHostState, "${context.getString(R.string.deletion_fail)}!")
                    }
                    support.resetDeleteImagesState()
                    onDeleteFailExtra()
                }
            } else if (onImagesMoving) {
                if (activityResult.resultCode == Activity.RESULT_OK) {
                    support.moveImagesTo { failedImages ->
                        showSnackBar(
                            snackbarHostState = handledSnackbarHostState,
                            message = if (failedImages.isNotEmpty()) {
                                if (failedImages.find { it.second == StorageHelper.ImageCopyError.IO_ERROR } != null) {
                                    StorageHelper.ImageCopyError.IO_ERROR.toString()
                                } else {
                                    "${context.getString(R.string.same_name_exist)}!"
                                }
                            } else "${context.getString(R.string.move_success)}!",
                            delayTimeMillis = if (failedImages.isNotEmpty()) 3000 else 1000
                        )
                        onMoveSuccessExtra(failedImages.map { it.first })
                    }
                } else {
                    coroutineScope.launch {
                        showSnackBar(handledSnackbarHostState, "${context.getString(R.string.move_fail)}!")
                    }
                    support.resetMoveImagesState()
                    onMoveFailExtra()
                }
            } else if (onImageRenaming) {
                if (activityResult.resultCode == Activity.RESULT_OK) {
                    support.updateFileName(
                        onComplete = {
                            showSnackBar(
                                snackbarHostState = handledSnackbarHostState,
                                message = "${context.getString(R.string.rename_success)}!",
                            )
                        },
                        onFailure = {
                            showSnackBar(
                                snackbarHostState = handledSnackbarHostState,
                                message = "${context.getString(R.string.rename_fail)}!",
                            )
                        }
                    )
                } else {
                    coroutineScope.launch {
                        showSnackBar(handledSnackbarHostState, "${context.getString(R.string.rename_fail)}!")
                    }
                    support.resetRenameImagesState()
                }
            }
        }

    LaunchedEffect(pendingIntent) {
        pendingIntent?.let { pendingIntent ->
            imageRequestLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }
}