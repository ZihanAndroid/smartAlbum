package com.example.image_multi_recognition.compose.view

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.CustomSnackBar
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PhotoComposable(
    modifier: Modifier = Modifier,
    viewModel: PhotoViewModel,
    customAppBar: Boolean = false,
    navTopAppBar: @Composable () -> Unit,
    navBottomAppBar: @Composable () -> Unit,
    onBack: () -> Unit = {},
    provideInitialSetting: () -> AppData,
    onImageClick: (album: Long, originalIndex: Int) -> Unit,   // Int: index of selected image in LazyPagingItems
) {
    val pagingDataFlow by viewModel.pagingFlow.collectAsStateWithLifecycle()
    val pagingItems = pagingDataFlow.collectAsLazyPagingItems()
    val imagesPerRowFlow by viewModel.imagePerRowFlow.collectAsStateWithLifecycle(provideInitialSetting().imagesPerRow)

    var selectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedImageIdSet = remember { MutableSetWithState<Long>() }
    var confirmedNoImage by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as Activity
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var albumSelectState by rememberSaveable(saver = AlbumSelectState.saver) {
        mutableStateOf(AlbumSelectState.noSelection)
    }

    ImageFileOperationComposableSupport(
        support = viewModel,
        snackbarHostState = snackbarHostState,
        imageIdList = selectedImageIdSet.toList(),
        onDeleteSuccessExtra = {
            // remove the album if there is no image left
            if (pagingItems.itemCount == 0) {
                viewModel.removeEmptyAlbum()
                // Since there is no image left, we exit the current window
                onBack()
            }
        }
    )

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { CustomSnackBar(it, if (customAppBar) 0.dp else 20.dp) }
        },
        topBar = {
            if (!selectionMode && customAppBar) {
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
                        selectionMode = false
                        onBack()
                    },
                    actions = {
                        TopBarIcons(
                            imageVectorList = listOf(
                                Icons.Filled.Favorite,
                                Icons.Filled.Share,
                                // Icons.Filled.Delete
                            ),
                            contentDescriptionList = listOf("favorite", "share"),
                            popUpItems = mutableListOf<String>().apply {
                                // disallow file operation when showing favorite
                                if (viewModel.currentAlbum != DefaultConfiguration.FAVORITES_ALBUM_ID) {
                                    this += stringResource(R.string.move_to)
                                    this += stringResource(R.string.copy_to)
                                    this += stringResource(R.string.delete)
                                }
                            }.apply {
                                this += stringResource(R.string.select_all)
                                this += stringResource(R.string.deselect_all)
                            },
                            onClickList = mutableListOf<() -> Unit>().apply {
                                this += {
                                    viewModel.changeFavoriteImages(selectedImageIdSet.toList())
                                    selectionMode = false
                                }
                                this += {
                                    with(viewModel) { activity.shareImages(selectedImageIdSet.toList()) }
                                    selectionMode = false
                                }
                                if (viewModel.currentAlbum != DefaultConfiguration.FAVORITES_ALBUM_ID) {
                                    this += {
                                        viewModel.setAlbumListStateFlow(excludedAlbum = viewModel.currentAlbum!!)
                                        albumSelectState = AlbumSelectState(
                                            selecting = true,
                                            newAlbumInput = false,
                                            AlbumSelectState.Purpose.MOVE_IMAGES
                                        )
                                        selectionMode = false
                                    }
                                    this += {
                                        viewModel.setAlbumListStateFlow(excludedAlbum = viewModel.currentAlbum!!)
                                        albumSelectState = AlbumSelectState(
                                            selecting = true,
                                            newAlbumInput = false,
                                            AlbumSelectState.Purpose.COPY_IMAGES
                                        )
                                        selectionMode = false
                                    }
                                    this += {
                                        viewModel.requestImagesDeletion(
                                            selectedImageIdSet.toList(),
                                            viewModel.currentAlbum!!
                                        ) {
                                            showSnackBar(
                                                snackbarHostState,
                                                "${context.getString(R.string.deletion_fail)}!"
                                            )
                                        }
                                        selectionMode = false
                                        confirmedNoImage = true
                                    }
                                }
                                this += {
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
                                }
                                this += {
                                    // deselect all
                                    selectedImageIdSet.clear()
                                }
                            }
                        )
                    }
                )
            } else {
                // not in selectionMode plus not custom bar (default navigation bar)
                navTopAppBar()
            }
        },
        bottomBar = {
            if (!customAppBar) navBottomAppBar()
        },
        modifier = modifier
    ) { originalPaddingValues ->
        // handle the padding problem for applying enableEdgeToEdge() when multiple Scaffolds exist
        // (the best way is to use only one Scaffold in one window, each window has its own scaffold and do not share Scaffold among multiple windows)
        // https://slack-chats.kotlinlang.org/t/16057136/with-enableedgetoedge-the-height-of-the-system-navbar-gestur
        val paddingValues = PaddingValues(
            start = originalPaddingValues.calculateStartPadding(LayoutDirection.Ltr),
            end = originalPaddingValues.calculateEndPadding(LayoutDirection.Ltr),
            top = originalPaddingValues.calculateTopPadding(),
            // we cropped the Navigation BottomAppBar, see "ScaffoldBottomBar()" as a result, we need to compensate the cropped size here
            bottom = originalPaddingValues.calculateBottomPadding() + DefaultConfiguration.NAV_BOTTOM_APP_BAR_CROPPED.dp
        )
        if (pagingItems.itemCount == 0) {
            Box(
                modifier = Modifier.padding(paddingValues).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (confirmedNoImage) {
                    Text(
                        text = stringResource(R.string.no_image),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    LaunchedEffect(Unit) {
                        coroutineScope.launch {
                            delay(4000)
                            // show "no image" after 5 seconds
                            confirmedNoImage = true
                        }
                    }
                }
            }
        } else {
            ImagePagerView(
                modifier = modifier.padding(paddingValues),
                pagingItems = pagingItems,
                provideImagePerRow = { imagesPerRowFlow },
                onImageClick = { imageInfoId ->
                    onImageClick(
                        viewModel.currentAlbum!!,
                        viewModel.getValidOriginalIndex(imageInfoId)
                    )
                },
                selectionMode = selectionMode,
                onLongPress = { imageId ->
                    if (!selectionMode && viewModel.noImageOperationOngoing()) {
                        selectedImageIdSet.clear()
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
                    snackbarHostState = snackbarHostState,
                    albumSelectState = albumSelectState,
                    selectedImageIdList = selectedImageIdSet.toList(),
                    onDismissRequest = { albumSelectState = AlbumSelectState.noSelection },
                    onAlbumAddClick = { albumSelectState = albumSelectState.copy(newAlbumInput = true) }
                )
            }
        }
    }
}