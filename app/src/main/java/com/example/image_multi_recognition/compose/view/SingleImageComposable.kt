package com.example.image_multi_recognition.compose.view

import android.annotation.SuppressLint
import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.*
import com.example.image_multi_recognition.compose.statelessElements.bottomSheet.AlbumSelectState
import com.example.image_multi_recognition.compose.statelessElements.bottomSheet.BottomSheetView
import com.example.image_multi_recognition.compose.view.imageShow.SingleImagePage
import com.example.image_multi_recognition.compose.view.imageShow.SingleImagePageLabelingDone
import com.example.image_multi_recognition.db.ImageLabel
import com.example.image_multi_recognition.model.RotationDegree
import com.example.image_multi_recognition.ui.theme.md_theme_dark_primary
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.util.showSnackBar
import com.example.image_multi_recognition.util.splitLastBy
import com.example.image_multi_recognition.viewmodel.ImageLabelResult
import com.example.image_multi_recognition.viewmodel.SingleImageViewModel
import com.example.image_multi_recognition.viewmodel.basic.ImageFileOperationComposableSupport
import kotlinx.coroutines.launch

@SuppressLint("RememberReturnType")
@Composable
fun SingleImageComposable(
    viewModel: SingleImageViewModel,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
    onAlbumEmptyBack: () -> Unit,
) {
    Log.d(getCallSiteInfoFunc(), "Recomposition")
    val pagingDataFlow by viewModel.pagingFlow.collectAsStateWithLifecycle()
    val pagingItems = pagingDataFlow.collectAsLazyPagingItems()
    val pagerState = rememberPagerState(
        // For HorizontalPager, the index starts from 0; for PagingSource, the index starts from 1
        initialPage = viewModel.initialKey - 1,
        pageCount = { pagingItems.itemCount }
    )

    val context = LocalContext.current
    val imageExif by viewModel.imageExifFlow.collectAsStateWithLifecycle()
    val itemNames = rememberSaveable {
        listOf(
            context.getString(R.string.name),
            context.getString(R.string.size),
            context.getString(R.string.path),
            context.getString(R.string.resolution),
            context.getString(R.string.date_taken),
            context.getString(R.string.mime_type),
            context.getString(R.string.location),
        )
    }
    var promptWindowShow by rememberSaveable { mutableStateOf(false) }
    // if no label is detected, then we receive empty lists here,
    val imageLabelLists by viewModel.imageLabelStateFlow.collectAsStateWithLifecycle()
    var addLabelClicked by rememberSaveable { mutableStateOf(false) }
    val labelAddedCacheAvailable by viewModel.labelAddedCacheAvailable.collectAsStateWithLifecycle()
    var renamingImageOngoing by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var imageIdList = rememberSaveable { mutableListOf<Long>() }
    var albumSelectState by rememberSaveable(saver = AlbumSelectState.saver) {
        mutableStateOf(AlbumSelectState.noSelection)
    }
    val currentImageInfo by remember {
        derivedStateOf {
            if (pagingItems.itemSnapshotList.size > pagerState.currentPage) {
                pagingItems.itemSnapshotList[pagerState.currentPage]
            } else null
        }
    }
    var fileNamesInAlbum by remember { mutableStateOf(emptyList<String>()) }

    ImageFileOperationComposableSupport(
        support = viewModel,
        snackbarHostState = snackbarHostState,
        imageIdList = imageIdList,
        onDeleteSuccessExtra = {
            // remove the album if there is no image left
            if (pagingItems.itemCount == 0) {
                viewModel.removeEmptyAlbum()
                // Since there is no image left, we exit the current window
                onAlbumEmptyBack()
            }
        }
    )
    var rotationDegree by remember { mutableStateOf(RotationDegree.D0) }
    // Note if you use "LaunchedEffect{...}" it will run after the current recomposition
    // As a result, when switching to the next page, the previous rectangle shows in the new page then disappears.
    // However, by using "remember(pagerState.currentPage){...}", we can clear imageLabelResult first.
    remember(pagerState.currentPage, pagingItems.itemCount) {
        // pagingItems.itemCount may change because the user can move or delete an image shown by the pager.
        // the info in imageLabelResult has been shown in the previous screen,
        // we clear it so that when moving to the next page, the same imageLabelResult is not shown
        viewModel.clearPage(pagerState.currentPage)
    }
    val pageScrolling by remember { derivedStateOf { pagerState.isScrollInProgress } }
    // https://stackoverflow.com/questions/73873622/how-to-start-new-activity-in-jetpack-compose
    val activity = LocalContext.current as Activity

    // a strange behavior, once I access "pagerState.pageCount" instead of "pagingItems.itemCount" here, it causes infinite recomposition
    // Log.d(getCallSiteInfoFunc(), "currentPage: ${pagerState.pageCount}")
    // Log.d(getCallSiteInfoFunc(), "currentPage: ${pagingItems.itemCount}")

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        SingleImageView(
            title = if (pagingItems.itemCount == 0) "" else "${pagerState.currentPage + 1}/${pagingItems.itemCount}",
            snackbarHostState = snackbarHostState,
            onBack = onBackClick,
            topRightItems = listOf(
                SingleImageViewItem({ ImageVector.vectorResource(R.drawable.baseline_new_label_24) }, "label"),
                SingleImageViewItem({ ImageVector.vectorResource(R.drawable.baseline_preview_24) }, "preview_label")
            ),
            topRightOnClicks = listOf({
                // labeling start
                pagingItems[pagerState.currentPage]?.let { imageInfo ->
                    rotationDegree = RotationDegree.D0
                    viewModel.detectAndLabelImage(imageInfo)
                }
            }, {
                // preview labels
                currentImageInfo?.let {
                    viewModel.setLabelPreview(it)
                }
            }),
            bottomItems = listOf(
                SingleImageViewItem({ ImageVector.vectorResource(R.drawable.baseline_info_outline_24) }, "info"),
                SingleImageViewItem({ ImageVector.vectorResource(R.drawable.baseline_rotate_left_24) }, "rotateLeft"),
                SingleImageViewItem({ ImageVector.vectorResource(R.drawable.baseline_rotate_right_24) }, "rotateRight"),
                SingleImageViewItem({ Icons.Default.Share }, "share"),
                SingleImageViewItem(
                    provideImageVector = {
                        if (pagingItems.itemSnapshotList.size > pagerState.currentPage && pagingItems.itemSnapshotList[pagerState.currentPage]?.favorite == true) {
                            Icons.Default.Favorite
                        } else Icons.Default.FavoriteBorder
                    },
                    contentDescription = "favorite",
                    provideTint = {
                        if (pagingItems.itemSnapshotList.size > pagerState.currentPage && pagingItems.itemSnapshotList[pagerState.currentPage]?.favorite == true) {
                            md_theme_dark_primary
                        } else {
                            null
                        }
                    }
                )
            ),
            bottomOnClicks = listOf({
                currentImageInfo?.fullImageFile?.let { imageFile ->
                    viewModel.getImageInformation(imageFile)
                    promptWindowShow = true
                }
            }, {
                if (!pageScrolling && imageLabelLists.partImageLabelList == null && imageLabelLists.wholeImageLabelList == null) {
                    rotationDegree = when (rotationDegree) {
                        RotationDegree.D0 -> RotationDegree.D270
                        RotationDegree.D270 -> RotationDegree.D180
                        RotationDegree.D180 -> RotationDegree.D90
                        RotationDegree.D90 -> RotationDegree.D0
                    }
                }
            }, {
                if (!pageScrolling && imageLabelLists.partImageLabelList == null && imageLabelLists.wholeImageLabelList == null) {
                    rotationDegree = when (rotationDegree) {
                        RotationDegree.D0 -> RotationDegree.D90
                        RotationDegree.D90 -> RotationDegree.D180
                        RotationDegree.D180 -> RotationDegree.D270
                        RotationDegree.D270 -> RotationDegree.D0
                    }
                }
            }, {
                // share an image
                if (pagingItems.itemSnapshotList.size > pagerState.currentPage) {
                    pagingItems.itemSnapshotList[pagerState.currentPage]?.let { imageInfo ->
                        with(viewModel) { activity.shareImageInfo(listOf(imageInfo)) }
                    }
                }
            }, {
                if (pagingItems.itemSnapshotList.size > pagerState.currentPage && viewModel.noImageOperationOngoing()) {
                    // Data has been retrieved from PagingSource
                    pagingItems.itemSnapshotList[pagerState.currentPage]?.let { imageInfo ->
                        if (pagingItems.itemCount == 1 && imageInfo.favorite && viewModel.currentAlbum == DefaultConfiguration.FAVORITES_ALBUM_ID) {
                            // after removing a favorite in the "favorite" window, the number may be zero
                            onBackClick()
                        }
                        viewModel.changeFavoriteImages(listOf(imageInfo.id))
                    }
                }
            }),
            moreVertItems = if (viewModel.argumentType == 1 && viewModel.currentAlbum != DefaultConfiguration.FAVORITES_ALBUM_ID) {
                listOf(
                    stringResource(R.string.move_to),
                    stringResource(R.string.copy_to),
                    stringResource(R.string.delete),
                    stringResource(R.string.rename)
                )
            } else emptyList(),
            moreVertItemOnClicks = if (viewModel.argumentType == 1 && viewModel.currentAlbum != DefaultConfiguration.FAVORITES_ALBUM_ID) {
                listOf({
                    currentImageInfo?.id?.let { imageId ->
                        // set albums shown to the user
                        viewModel.setAlbumListStateFlow(excludedAlbum = viewModel.currentAlbum!!)
                        imageIdList.clear()
                        imageIdList.add(imageId)
                        albumSelectState = AlbumSelectState(
                            selecting = true,
                            newAlbumInput = false,
                            AlbumSelectState.Purpose.MOVE_IMAGES
                        )
                    }
                }, {
                    currentImageInfo?.id?.let { imageId ->
                        viewModel.setAlbumListStateFlow(excludedAlbum = viewModel.currentAlbum!!)
                        imageIdList.clear()
                        imageIdList.add(imageId)
                        albumSelectState = AlbumSelectState(
                            selecting = true,
                            newAlbumInput = false,
                            AlbumSelectState.Purpose.COPY_IMAGES
                        )
                    }
                }, {
                    // request deleting an image
                    currentImageInfo?.id?.let { imageId ->
                        imageIdList.clear()
                        imageIdList.add(imageId)
                        viewModel.requestImagesDeletion(listOf(imageId), viewModel.currentAlbum!!) {
                            showSnackBar(snackbarHostState, "${context.getString(R.string.deletion_fail)}!")
                        }
                    }
                }, {
                    // rename an image file
                    coroutineScope.launch {
                        fileNamesInAlbum = viewModel.getAllFileNamesByCurrentAlbum(viewModel.currentAlbum!!)
                        renamingImageOngoing = true
                    }
                })
            } else emptyList()
        ) { paddingValues ->
            Log.d(getCallSiteInfoFunc(), "paddingValues: $paddingValues")
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.padding(paddingValues),
                // Default is 400ms, which is a little longer than expected
                flingBehavior = PagerDefaults.flingBehavior(
                    pagerState,
                    snapAnimationSpec = tween(Spring.StiffnessLow.toInt(), 0)
                )
                // key = { pagingItems[it]?.id ?: (-1) }
            ) { itemIndex ->
                pagingItems[itemIndex]?.let { imageInfo ->
                    if (!imageLabelLists.labelingDone) {
                        Log.d(getCallSiteInfoFunc(), "Recomposition")
                        SingleImagePage(
                            imageInfo = imageInfo,
                            partImageLabelResult = imageLabelLists.partImageLabelList,
                            wholeImageLabelResult = imageLabelLists.wholeImageLabelList,
                            addedLabelList = imageLabelLists.addedLabelList,
                            originalImageSize = viewModel.imageSize,
                            pageScrolling = pageScrolling,
                            // when pageScrolling is canceled, we need to recover the user's previous selection
                            labelSelected = { it in viewModel.selectedLabelSet },
                            onLabelClick = { label, selected ->
                                if (selected) viewModel.selectedLabelSet.add(label)
                                else viewModel.selectedLabelSet.remove(label)
                            },
                            onLabelDone = {
                                with(mutableListOf<ImageLabel>()) {
                                    viewModel.selectedLabelSet.forEach { label ->
                                        add(
                                            ImageLabel(
                                                id = imageInfo.id,
                                                label = label,
                                                rect = imageLabelLists.partImageLabelList?.find { it.label == label }?.rect
                                            )
                                        )
                                    }
                                    imageLabelLists.addedLabelList?.forEach { label ->
                                        // deduplication
                                        if (label !in viewModel.selectedLabelSet) {
                                            add(
                                                ImageLabel(
                                                    id = imageInfo.id,
                                                    label = label,
                                                    rect = null,
                                                )
                                            )
                                        }
                                    }
                                    viewModel.resetImageLabelFlow()
                                    viewModel.updateLabel(imageInfo.id, this)
                                }
                                // show selected labels
                                Log.d(getCallSiteInfoFunc(), "selected labels: ${viewModel.selectedLabelSet}")
                                val partImageResult =
                                    imageLabelLists.partImageLabelList?.filter { it.label in viewModel.selectedLabelSet }
                                val wholeImageResult = imageLabelLists.wholeImageLabelList?.filter {
                                    it.label in viewModel.selectedLabelSet
                                }?.toMutableList()?.apply {
                                    imageLabelLists.addedLabelList?.filter { it !in viewModel.selectedLabelSet }
                                        ?.map { label ->
                                            ImageLabelResult(imageInfo.id, null, label)
                                        }.let { addedLabels ->
                                            if (!addedLabels.isNullOrEmpty()) {
                                                addAll(addedLabels)
                                            }
                                        }
                                }
                                val newLabels =
                                    ((partImageResult ?: emptyList()) + (wholeImageResult ?: emptyList()))
                                        .map { it.label }.toSet()
                                // the image may have a label, and change its label can cause changes in PagingSource from DB, we need to check that by this "originalLabel"
                                if (viewModel.currentLabel == null || viewModel.currentLabel in newLabels) {
                                    // when currentLabel is set to "", it will always fail the conditions and no result is shown except a snackBar
                                    viewModel.resetImageLabelFlow(
                                        partImageLabelResult = partImageResult,
                                        wholeImageLabelResult = wholeImageResult,
                                        addedLabelList = imageLabelLists.addedLabelList,
                                        labelingDone = true,
                                        preview = false
                                    )
                                    viewModel.setLabelAddedCacheAvailable()
                                } else {
                                    // show a snack instead of the labeling result because the page has gone after labeling
                                    coroutineScope.launch {
                                        showSnackBar(
                                            snackbarHostState,
                                            context.getString(R.string.labeling_done) + "!"
                                        )
                                        // For labeling page, after labeling, the page may gone because of the change from unlabeled image to labeled image
                                        // Note that if you use "pagingItem.itemCount" instead of "pagerState.pageCount", it will not work
                                        if (pagerState.pageCount == 0) onAlbumEmptyBack()
                                    }
                                }
                            },
                            onLabelAddingClick = { addLabelClicked = true },
                            onAddedLabelClick = { label, selected ->
                                // deselecting an added label makes it disappear
                                if (!selected) {
                                    viewModel.setAddedLabelList(imageLabelLists.addedLabelList?.minus(label))
                                }
                            },
                            // defer the read of the state to defer recomposition
                            provideRotationDegree = { rotationDegree.toFloat() },
                            onDismiss = {
                                viewModel.resetImageLabelFlow()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        SingleImagePageLabelingDone(
                            imageInfo = imageInfo,
                            originalImageSize = viewModel.imageSize,
                            partImageLabelResult = imageLabelLists.partImageLabelList,
                            // the added label has been put into wholeImageLabelList when labeling done button is pressed
                            otherImageLabelResult = imageLabelLists.wholeImageLabelList,
                            pageScrolling = pageScrolling,
                            onDismiss = {
                                viewModel.resetImageLabelFlow()
                            },
                            isPreview = imageLabelLists.preview,
                            labelAddedCacheAvailable = labelAddedCacheAvailable,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        // Add custom labels
        if (addLabelClicked) {
            InputView(
                userAddedLabelList = imageLabelLists.addedLabelList ?: emptyList(),
                onDismiss = { addLabelClicked = false },
                onConfirm = { userSelectedLabelList ->
                    addLabelClicked = false
                    viewModel.setAddedLabelList(userSelectedLabelList)
                },
                onTextChange = { prefix ->
                    viewModel.getLabelListByPrefix(prefix)
                }
            )
        }
        if (albumSelectState.selecting) {
            BottomSheetView(
                fileOperationSupport = viewModel,
                snackbarHostState = snackbarHostState,
                albumSelectState = albumSelectState,
                selectedImageIdList = imageIdList,
                onDismissRequest = { albumSelectState = AlbumSelectState.noSelection },
                onAlbumAddClick = { albumSelectState = albumSelectState.copy(newAlbumInput = true) }
            )
        }
        if (renamingImageOngoing) {
            currentImageInfo?.fullImageFile?.name?.let { fileName ->
                val (prefix, suffix) = remember { fileName.splitLastBy() }
                val caseInsensitiveSet = remember { fileNamesInAlbum.toSet().map { it.lowercase().trim() }.toSet() }
                SimpleInputView(
                    title = stringResource(R.string.file_rename),
                    initialText = prefix,
                    checkExcluded = { (it.lowercase().trim() + suffix) in caseInsensitiveSet },
                    onDismiss = { renamingImageOngoing = false },
                    onConfirm = { newFileName ->
                        renamingImageOngoing = false
                        currentImageInfo?.let { imageInfo ->
                            viewModel.requestFileNameUpdate(
                                imageId = imageInfo.id,
                                absolutePath = imageInfo.fullImageFile.absolutePath,
                                newFileName = newFileName + suffix
                            ) {
                                showSnackBar(snackbarHostState, context.getString(R.string.rename_fail))
                            }
                        }
                    }
                )
            }
        }
        // show photo's exif information
        if (promptWindowShow) {
            SimpleInfoView(
                items = imageExif.mapIndexed { index, s ->
                    itemNames[index] to s
                },
                onDismiss = {
                    promptWindowShow = false
                    viewModel.resetImageInformation()
                }
            )
        }
    }
}