package com.example.image_multi_recognition.compose.view

import android.app.Activity.RESULT_OK
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.*
import com.example.image_multi_recognition.db.AlbumInfo
import com.example.image_multi_recognition.db.AlbumInfoWithLatestImage
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.util.MutableSetWithState
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.util.showSnackBar
import com.example.image_multi_recognition.viewmodel.PhotoViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PhotoComposable(
    modifier: Modifier = Modifier,
    viewModel: PhotoViewModel,
    onImageClick: (album: Long, originalIndex: Int) -> Unit,   //Int: index of selected image in LazyPagingItems
    customTopBar: Boolean = false,
    rootSnackBarHostState: SnackbarHostState? = null,
    onTopBottomBarHidden: (Boolean) -> Unit = {},
    onBack: () -> Unit = {}
) {
    //val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val pagingDataFlow by viewModel.pagingFlow.collectAsStateWithLifecycle()
    val pagingItems = pagingDataFlow.collectAsLazyPagingItems()

    var selectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedImageIdSet = remember { MutableSetWithState<Long>() }
    val deletedImageIds = rememberSaveable { mutableSetOf<Long>() }
    // track execution state
    val onImagesDeleting by viewModel.deleteImageStateFlow.collectAsStateWithLifecycle()
    val onImagesMoving by viewModel.moveImageStateFlow.collectAsStateWithLifecycle()
    val onImagesRenaming by viewModel.renameImageStateFlow.collectAsStateWithLifecycle()
    val onImagesCopying by viewModel.copyImageStateFlow.collectAsStateWithLifecycle()
    val onImagesFavoriteAdding by viewModel.addFavoriteImageStateFlow.collectAsStateWithLifecycle()

    fun noImageOperationOngoing() =
        !onImagesMoving && !onImagesDeleting && !onImagesRenaming && !onImagesCopying && !onImagesFavoriteAdding

//    fun noImageOperationStarted() =
//        onImagesMoving == null && onImagesDeleting == null && onImagesRenaming == null && onImagesCopying == null && onImagesFavoriteAdding == null

    val operationDoneString = stringResource(R.string.operation_done)
    val deletionSuccessString = stringResource(R.string.deletion_success)
    val deletionFailString = stringResource(R.string.deletion_fail)
    val moveSuccessString = stringResource(R.string.move_success)
    val moveFailString = stringResource(R.string.move_fail)

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var albumSelectState by rememberSaveable(saver = AlbumSelectState.saver) {
        mutableStateOf(AlbumSelectState.noSelection)
    }

    val deletionPendingIntent by viewModel.deletionPendingIntentFlow.collectAsStateWithLifecycle()
    val movePendingIntent by viewModel.movePendingIntentFlow.collectAsStateWithLifecycle()

    // https://stackoverflow.com/questions/64721218/jetpack-compose-launch-activityresultcontract-request-from-composable-function
    val imageRequestLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (onImagesDeleting) {  // only monitor the ActivityResult generated from deleting images
                if (it.resultCode == RESULT_OK) {
                    viewModel.deleteImages(selectedImageIdSet.toList()) {
                        showSnackBar(rootSnackBarHostState ?: snackbarHostState, "$deletionSuccessString!")
                    }
                } else {
                    // failed to delete images, user may deny our deletion request
                    coroutineScope.launch {
                        showSnackBar(rootSnackBarHostState ?: snackbarHostState, "$deletionFailString!")
                    }
                    viewModel.resetDeleteImagesState()
                }
            } else if (onImagesMoving) {
                if (it.resultCode == RESULT_OK) {
                    viewModel.moveImagesTo { failedImagePaths ->
                        showSnackBar(rootSnackBarHostState ?: snackbarHostState, "Error, some images failed to move!")
                    }
                } else {
                    coroutineScope.launch {
                        showSnackBar(rootSnackBarHostState ?: snackbarHostState, "$moveFailString!")
                    }
                    viewModel.resetMoveImagesState()
                }
            }
        }

    LaunchedEffect(deletionPendingIntent) {
        deletionPendingIntent?.let { pendingIntent ->
            imageRequestLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        }
    }

    LaunchedEffect(movePendingIntent) {
        movePendingIntent?.let { intent ->
            imageRequestLauncher.launch(IntentSenderRequest.Builder(intent.pendingIntent!!.intentSender).build())
        }
    }


//    LaunchedEffect(onImagesDeleting, onImagesMoving, onImagesRenaming, onImagesCopying, onImagesFavoriteAdding) {
////        if (noImageOperationOngoing() && !noImageOperationStarted()) {
////            launch {
////
////                rootSnackBarHostState?.showSnackbar(operationDoneString, duration = SnackbarDuration.Indefinite)
////                if (rootSnackBarHostState == null) {
////                    snackbarHostState.showSnackbar(operationDoneString, duration = SnackbarDuration.Indefinite)
////                }
////                viewModel.resetAddFavoriteImagesState()
////            }.apply {
////                delay(1000)
////                cancel()
////            }
////        }
//    }

    Scaffold(
        snackbarHost = if (customTopBar) {
            { SnackbarHost(snackbarHostState) }
        } else {
            {}
        },
        topBar = {
            if (!selectionMode && customTopBar) {
                TopAppBarForNotRootDestination(
                    title = AlbumPathDecoder.decodeAlbumName(viewModel.currentAlbum), onBack = onBack
                )
            } else if (selectionMode) {
                TopAppBarForNotRootDestination(title = stringResource(
                    R.string.selected_count,
                    (selectedImageIdSet.size)
                ),
                    onBack = {
                        selectedImageIdSet.clear()
                        onTopBottomBarHidden(false)
                        selectionMode = false
                        onBack()
                    },
                    actions = {
                        TopBarIcons(
                            imageVectorList = listOf(
                                Icons.Filled.Favorite,
                                Icons.Filled.Share,
                                Icons.Filled.Delete
                            ),
                            contentDescriptionList = listOf("favorite", "share", "delete"),
                            popUpItems = listOf(
                                stringResource(R.string.move_to), stringResource(R.string.copy_to)
                            ),
                            onClickList = listOf({
                                viewModel.changeFavoriteImages(selectedImageIdSet.toList())
                                onTopBottomBarHidden(false)
                                selectionMode = false
                            }, {
                                viewModel.shareImages(selectedImageIdSet.toList())
                                onTopBottomBarHidden(false)
                                selectionMode = false
                            }, {
                                viewModel.requestImagesDeletion(selectedImageIdSet.toList()) {
                                    showSnackBar(rootSnackBarHostState ?: snackbarHostState, "$deletionFailString!")
                                }
                                onTopBottomBarHidden(false)
                                selectionMode = false
                            }, {
                                // choose an album
                                albumSelectState = AlbumSelectState(
                                    selecting = true,
                                    newAlbumInput = false,
                                    AlbumSelectState.Purpose.MOVE_IMAGES
                                )
                                viewModel.setAlbumListStateFlow(excludedAlbum = viewModel.currentAlbum!!)
                                onTopBottomBarHidden(false)
                                selectionMode = false
                            }, {
                                albumSelectState = AlbumSelectState(
                                    selecting = true,
                                    newAlbumInput = false,
                                    AlbumSelectState.Purpose.COPY_IMAGES
                                )
                                viewModel.setAlbumListStateFlow(excludedAlbum = viewModel.currentAlbum!!)
                                onTopBottomBarHidden(false)
                                selectionMode = false
                            })
                        )
                    })
            }
        }, modifier = modifier
    ) { paddingValues ->
        if (pagingItems.itemCount == 0) {
            Box(
                modifier = Modifier.padding(paddingValues).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
            }
        } else {
            ImagePagerView(
                modifier = modifier.padding(paddingValues),
                pagingItems = pagingItems,
                onImageClick = { originalIndex ->
                    onImageClick(viewModel.currentAlbum!!, originalIndex)
                },
                onSendThumbnailRequest = viewModel::requestThumbnail,
                selectionMode = selectionMode,
                onClickSelect = { imageId ->
                    if (imageId !in selectedImageIdSet) {
                        selectedImageIdSet += imageId
                    } else {
                        selectedImageIdSet -= imageId
                    }
                },
                onLongPress = { imageId ->
                    if (!selectionMode && noImageOperationOngoing()) {
                        selectedImageIdSet.clear()
                        onTopBottomBarHidden(true)
                        selectionMode = true
                    }
                    if (noImageOperationOngoing()) {
                        if (imageId !in selectedImageIdSet) {
                            selectedImageIdSet += imageId
                        } else {
                            selectedImageIdSet -= imageId
                        }
                    }
                },
                enableLongPressAndDrag = true,
                selectedImageIdSet = selectedImageIdSet,
                deletedImageIds = deletedImageIds,
            )

            if (albumSelectState.selecting) {
                val albumList by viewModel.albumListStateFlow.collectAsStateWithLifecycle()
                val sheetState = rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                    confirmValueChange = { sheetValue ->
                        if (sheetValue == SheetValue.Hidden) false else true
                    }  // avoid ModalBottomSheet is closed by scrolling
                )
                if (!albumSelectState.newAlbumInput) {
                    // bottom sheet: https://developer.android.com/jetpack/compose/components/bottom-sheets
                    // https://stackoverflow.com/questions/68809132/jetpack-compose-disable-bottomsheet-outside-touch
                    ModalBottomSheet(
                        onDismissRequest = { albumSelectState = AlbumSelectState.noSelection },
                        sheetState = sheetState,
                        // dragHandle = null,
                        modifier = Modifier.height((LocalConfiguration.current.screenHeightDp * 0.95).dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        coroutineScope.launch {
                                            sheetState.hide() // generate animation for hiding
                                            albumSelectState = AlbumSelectState.noSelection
                                        }
                                    }
                                )
                            }
                    ) {
                        BottomSheetContent(
                            albumInfoWithLatestImageList = albumList,
                            onItemClick = { selectedAlbum ->
                                when (albumSelectState.purpose) {
                                    AlbumSelectState.Purpose.MOVE_IMAGES -> viewModel.requestImagesMove(
                                        imageIdList = selectedImageIdSet.toList(),
                                        newAlbum = selectedAlbum,
                                        isAlbumNew = false,
                                    ) {
                                        showSnackBar(rootSnackBarHostState ?: snackbarHostState, "$moveFailString!")
                                    }

                                    AlbumSelectState.Purpose.COPY_IMAGES -> viewModel.copyImagesTo(
                                        selectedAlbum,
                                        selectedImageIdSet.toList()
                                    )

                                    else -> Log.e(
                                        getCallSiteInfo(),
                                        "Error: should never be here with ${albumSelectState.purpose}"
                                    )
                                }
                            },
                            onAlbumAddClick = { albumSelectState = albumSelectState.copy(newAlbumInput = true) }
                        )
                    }
                } else {
                    SimpleInputView(
                        excludedNames = setOf("Harry"),
                        onDismiss = { albumSelectState = AlbumSelectState.noSelection },
                        onConfirm = { albumName ->

                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ColumnScope.BottomSheetContent(
    albumInfoWithLatestImageList: List<AlbumInfoWithLatestImage>,
    onItemClick: (AlbumInfo) -> Unit, // the chosen album
    onAlbumAddClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.choose_album),
            style = MaterialTheme.typography.titleMedium
        )
        ElevatedButton(onClick = onAlbumAddClick) {
            Icon(Icons.Filled.Add, "add")
        }
    }
    LazyColumn {
        items(count = albumInfoWithLatestImageList.size) { index ->
            albumInfoWithLatestImageList[index].let { album ->
                ImageItemRow(
                    albumName = File(album.albumPath).name,
                    imageCount = album.count,
                    albumAbsolutePath = album.albumPath,
                    imageFilePath = File(album.albumPath, album.path).absolutePath,
                    onClick = { onItemClick(AlbumInfo(album.album, album.albumPath)) }
                )
            }
        }
    }
}

data class AlbumSelectState(
    val selecting: Boolean,
    val newAlbumInput: Boolean,
    val purpose: Purpose,
) {
    enum class Purpose {
        MOVE_IMAGES, COPY_IMAGES, NO_PURPOSE
    }

    companion object {
        val saver = listSaver<MutableState<AlbumSelectState>, String>(
            save = { state ->
                listOf(
                    state.value.selecting.toString(),
                    state.value.selecting.toString(),
                    state.value.purpose.toString()
                )
            },
            restore = { savedList ->
                mutableStateOf(
                    AlbumSelectState(
                        savedList[0].toBoolean(),
                        savedList[1].toBoolean(),
                        AlbumSelectState.Purpose.valueOf(savedList[2])
                    )
                )
            })
        val noSelection = AlbumSelectState(selecting = false, newAlbumInput = false, purpose = Purpose.NO_PURPOSE)
    }
}