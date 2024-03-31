package com.example.image_multi_recognition.compose.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.ImagePagerView
import com.example.image_multi_recognition.compose.statelessElements.TopAppBarForNotRootDestination
import com.example.image_multi_recognition.compose.statelessElements.TopBarIcons
import com.example.image_multi_recognition.compose.statelessElements.bottomSheet.AlbumSelectState
import com.example.image_multi_recognition.compose.statelessElements.bottomSheet.BottomSheetView
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.util.MutableSetWithState
import com.example.image_multi_recognition.util.showSnackBar
import com.example.image_multi_recognition.viewmodel.PhotoViewModel
import com.example.image_multi_recognition.viewmodel.basic.ImageFileOperationComposableSupport
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PhotoComposable(
    modifier: Modifier = Modifier,
    viewModel: PhotoViewModel,
    onImageClick: (album: Long, originalIndex: Int) -> Unit,   // Int: index of selected image in LazyPagingItems
    customTopBar: Boolean = false,
    rootSnackBarHostState: SnackbarHostState? = null,
    onTopBottomBarHidden: (Boolean) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val pagingDataFlow by viewModel.pagingFlow.collectAsStateWithLifecycle()
    val pagingItems = pagingDataFlow.collectAsLazyPagingItems()

    var selectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedImageIdSet = remember { MutableSetWithState<Long>() }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var albumSelectState by rememberSaveable(saver = AlbumSelectState.saver) {
        mutableStateOf(AlbumSelectState.noSelection)
    }

    ImageFileOperationComposableSupport(
        support = viewModel,
        snackbarHostState = rootSnackBarHostState ?: snackbarHostState,
        imageIdList = selectedImageIdSet.toList(),
    )

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
                TopAppBarForNotRootDestination(
                    title = stringResource(
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
                                stringResource(R.string.move_to),
                                stringResource(R.string.copy_to),
                                stringResource(R.string.select_all),
                                stringResource(R.string.deselect_all)
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
                                    showSnackBar(
                                        rootSnackBarHostState ?: snackbarHostState,
                                        "${context.getString(R.string.deletion_fail)}!"
                                    )
                                }
                                onTopBottomBarHidden(false)
                                selectionMode = false
                            }, {
                                viewModel.setAlbumListStateFlow(excludedAlbum = viewModel.currentAlbum!!)
                                albumSelectState = AlbumSelectState(
                                    selecting = true,
                                    newAlbumInput = false,
                                    AlbumSelectState.Purpose.MOVE_IMAGES
                                )
                                onTopBottomBarHidden(false)
                                selectionMode = false
                            }, {
                                viewModel.setAlbumListStateFlow(excludedAlbum = viewModel.currentAlbum!!)
                                albumSelectState = AlbumSelectState(
                                    selecting = true,
                                    newAlbumInput = false,
                                    AlbumSelectState.Purpose.COPY_IMAGES
                                )
                                onTopBottomBarHidden(false)
                                selectionMode = false
                            }, {
                                // select all, note paging 3 library fetch data lazily and not all data are fetched,
                                // so you cannot use the following method to select all
                                // selectedImageIdSet.addAll(pagingItems.itemSnapshotList.items
                                //     .filterIsInstance<UiModel.Item>()
                                //     .map {
                                //         it.imageInfo.id
                                //     }
                                // )
                                // Instead you fetch all the data from database manually and add them all into selectedImageIdSet
                                // This way even if not all images are loaded in the paging cache,
                                // whenever new images are loaded, they are marked automatically because their ids are in the selectedImageIdSet
                                coroutineScope.launch {
                                    selectedImageIdSet.addAll(viewModel.getAllImageIdsByCurrentAlbum(viewModel.currentAlbum!!))
                                }
                            }, {
                                // deselect all
                                selectedImageIdSet.clear()
                            })
                        )
                    }
                )
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
                onImageClick = { imageInfoId ->
                    onImageClick(
                        viewModel.currentAlbum!!,
                        viewModel.getValidOriginalIndex(imageInfoId)
                    )
                },
                onSendThumbnailRequest = viewModel::requestThumbnail,
                selectionMode = selectionMode,
                onLongPress = { imageId ->
                    if (!selectionMode && viewModel.noImageOperationOngoing()) {
                        selectedImageIdSet.clear()
                        onTopBottomBarHidden(true)
                        selectionMode = true
                    }
                    if (viewModel.noImageOperationOngoing()) {
                        if (imageId !in selectedImageIdSet) {
                            selectedImageIdSet += imageId
                        } else {
                            selectedImageIdSet -= imageId
                        }
                    }
                },
                enableLongPressAndDrag = true,
                selectedImageIdSet = selectedImageIdSet,
            )

            if (albumSelectState.selecting) {
                BottomSheetView(
                    fileOperationSupport = viewModel,
                    snackbarHostState = rootSnackBarHostState ?: snackbarHostState,
                    albumSelectState = albumSelectState,
                    selectedImageIdList = selectedImageIdSet.toList(),
                    onDismissRequest = { albumSelectState = AlbumSelectState.noSelection },
                    onAlbumAddClick = { albumSelectState = albumSelectState.copy(newAlbumInput = true) }
                )
            }
        }
    }
}