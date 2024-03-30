package com.example.image_multi_recognition.compose.view

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.*
import com.example.image_multi_recognition.compose.statelessElements.bottomSheet.AlbumSelectState
import com.example.image_multi_recognition.compose.statelessElements.bottomSheet.BottomSheetView
import com.example.image_multi_recognition.compose.view.imageShow.SingleImagePage
import com.example.image_multi_recognition.compose.view.imageShow.SingleImagePageLabelingDone
import com.example.image_multi_recognition.db.ImageLabel
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.util.showSnackBar
import com.example.image_multi_recognition.util.splitLastBy
import com.example.image_multi_recognition.viewmodel.ImageLabelResult
import com.example.image_multi_recognition.viewmodel.SingleImageViewModel
import com.example.image_multi_recognition.viewmodel.basic.ImageFileOperationComposableSupport
import kotlinx.coroutines.launch

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SingleImageComposable(
    viewModel: SingleImageViewModel,
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
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
            context.getString(R.string.location),
        )
    }
    var promptWindowShow by rememberSaveable { mutableStateOf(false) }
    // if no label is detected, then we receive empty lists here,
    val imageLabelLists by viewModel.imageLabelStateFlow.collectAsStateWithLifecycle()
    var addLabelClicked by rememberSaveable { mutableStateOf(false) }
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
        imageIdList = imageIdList
    )

    // Note if you use "LaunchedEffect{...}" it will run after the current recomposition
    // As a result, when switching to the next page, the previous rectangle shows in the new page then disappears.
    // However, by using "remember(pagerState.currentPage){...}", we can clear imageLabelResult first.
    remember(pagerState.currentPage) {
        // the info in imageLabelResult has been shown in the previous screen,
        // we clear it so that when moving to the next page, the same imageLabelResult is not shown
        viewModel.clearPage(pagerState.currentPage)
    }
    val pageScrolling by remember { derivedStateOf { pagerState.isScrollInProgress } }
    val showAppBar by remember { mutableStateOf(true) }
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
                SingleImageViewItem(ImageVector.vectorResource(R.drawable.baseline_new_label_24), "label"),
                SingleImageViewItem(ImageVector.vectorResource(R.drawable.baseline_preview_24), "preview_label")
            ),
            topRightOnClicks = listOf({
                // labeling start
                pagingItems[pagerState.currentPage]?.let { imageInfo ->
                    viewModel.detectAndLabelImage(imageInfo)
                }
            }, {
                // preview labels
                currentImageInfo?.let {
                    viewModel.setLabelPreview(it)
                }
            }),
            bottomItems = listOf(
                SingleImageViewItem(Icons.Default.Info, "info"),
                SingleImageViewItem(ImageVector.vectorResource(R.drawable.baseline_rotate_right_24), "rotate"),
                SingleImageViewItem(Icons.Default.Share, "share"),
                SingleImageViewItem(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "favorite",
                    tint = if (pagingItems.itemSnapshotList.size > pagerState.currentPage && pagingItems.itemSnapshotList[pagerState.currentPage]?.favorite == true) {
                        colorResource(R.color.colorAccent)
                    } else {
                        null
                    }
                )
            ),
            bottomOnClicks = listOf({
                currentImageInfo?.fullImageFile?.let { imageFile ->
                    viewModel.getImageInformation(imageFile)
                    promptWindowShow = true
                }
            }, {

            }, {

            }, {
                if (pagingItems.itemSnapshotList.size > pagerState.currentPage && viewModel.noImageOperationOngoing()) {
                    // Data has been retrieved from PagingSource
                    pagingItems.itemSnapshotList[pagerState.currentPage]?.let { imageInfo ->
                        viewModel.changeFavoriteImages(listOf(imageInfo.id))
                    }
                }
            }),
            moreVertItems = listOf(
                stringResource(R.string.move_to),
                stringResource(R.string.copy_to),
                stringResource(R.string.delete),
                stringResource(R.string.rename)
            ),
            moreVertItemOnClicks = listOf({
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
                    viewModel.requestImagesDeletion(listOf(imageId)) {
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
        ) { paddingValues ->
            Log.d(getCallSiteInfoFunc(), "paddingValues: $paddingValues")
            HorizontalPager(
                state = pagerState,
                // userScrollEnabled = false, // control the scrolling behavior by pointer input programmatically
                modifier = Modifier.padding(paddingValues),
                // Default is 400ms, which is a little longer than expected
                flingBehavior = PagerDefaults.flingBehavior(
                    pagerState,
                    snapAnimationSpec = tween(Spring.StiffnessLow.toInt(), 0)
                ),
                key = { pagingItems[it]?.id ?: -1 }
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
                                    if (isNotEmpty()) {
                                        viewModel.updateLabelAndResetOrderedList(this)
                                    }
                                }
                                // show selected labels
                                Log.d(getCallSiteInfoFunc(), "selected labels: ${viewModel.selectedLabelSet}")
                                viewModel.resetImageLabelFlow(
                                    partImageLabelResult = imageLabelLists.partImageLabelList?.filter { it.label in viewModel.selectedLabelSet },
                                    wholeImageLabelResult = imageLabelLists.wholeImageLabelList?.filter { it.label in viewModel.selectedLabelSet }
                                        ?.toMutableList()?.apply {
                                            imageLabelLists.addedLabelList?.filter { it !in viewModel.selectedLabelSet }
                                                ?.map { label -> ImageLabelResult(imageInfo.id, null, label) }
                                                .let { addedLabels ->
                                                    if (!addedLabels.isNullOrEmpty()) {
                                                        addAll(addedLabels)
                                                    }
                                                }
                                        },
                                    addedLabelList = imageLabelLists.addedLabelList,
                                    labelingDone = true,
                                    preview = false
                                )
                            },
                            onLabelAddingClick = { addLabelClicked = true },
                            onAddedLabelClick = { label, selected ->
                                // deselecting an added label makes it disappear
                                if (!selected) {
                                    viewModel.setAddedLabelList(imageLabelLists.addedLabelList?.minus(label))
                                }
                            },
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
                            onDismiss = {
                                viewModel.resetImageLabelFlow()
                                // viewModel.labelingStart = false
                            },
                            // labelingClicked = viewModel.labelingClicked,
                            isPreview = imageLabelLists.preview,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
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