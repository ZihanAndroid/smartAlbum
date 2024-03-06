package com.example.image_multi_recognition.compose.view

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.InputView
import com.example.image_multi_recognition.compose.statelessElements.SingleImageView
import com.example.image_multi_recognition.compose.view.imageShow.SingleImagePage
import com.example.image_multi_recognition.compose.view.imageShow.SingleImagePageLabelingDone
import com.example.image_multi_recognition.db.ImageLabel
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.viewmodel.ImageLabelResult
import com.example.image_multi_recognition.viewmodel.SingleImageViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SingleImageComposable(
    viewModel: SingleImageViewModel,
    modifier: Modifier = Modifier,
    onBackClick: ()->Unit
) {
    Log.d(getCallSiteInfoFunc(), "Recomposition")
    val pagingDataFlow by viewModel.pagingFlow.collectAsStateWithLifecycle()
    val pagingItems = pagingDataFlow.collectAsLazyPagingItems()
    val pagerState = rememberPagerState(
        // For HorizontalPager, the index starts from 0; for PagingSource, the index starts from 1
        initialPage = viewModel.initialKey - 1,
        pageCount = { pagingItems.itemCount }
    )
    // if no label is detected, then we receive empty lists here,
    val imageLabelLists by viewModel.imageLabelStateFlow.collectAsStateWithLifecycle()
    var addLabelClicked by rememberSaveable { mutableStateOf(false) }

    // Note if you use "LaunchedEffect{...}" it will run after the current recomposition
    // As a result, when switching to the next page, the previous rectangle shows in the new page then disappears.
    // However, by using "remember(pagerState.currentPage){...}", we can clear imageLabelResult first.
    remember(pagerState.currentPage) {
        // the info in imageLabelResult has been shown in the previous screen,
        // we clear it so that when moving to the next page, the same imageLabelResult is not shown
        viewModel.clearPage(pagerState.currentPage)
    }

    // a strange behavior, once I access "pagerState.pageCount" instead of "pagingItems.itemCount" here, it causes infinite recomposition
    // Log.d(getCallSiteInfoFunc(), "currentPage: ${pagerState.pageCount}")
    // Log.d(getCallSiteInfoFunc(), "currentPage: ${pagingItems.itemCount}")

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        SingleImageView(
            title = if(pagingItems.itemCount == 0) "" else "${pagerState.currentPage + 1}/${pagingItems.itemCount}",
            onBack = onBackClick,
            topRightIcons = listOf(
                ImageVector.vectorResource(R.drawable.baseline_new_label_24) to "label",
                ImageVector.vectorResource(R.drawable.baseline_rotate_right_24) to "rotate",
            ),
            topRightOnClicks = listOf({
                pagingItems[pagerState.currentPage]?.let { imageInfo ->
                    viewModel.detectAndLabelImage(imageInfo)
                }
            }, {

            }),
            bottomIcons = listOf(
                Icons.Default.Favorite to "favorite",
                Icons.Default.Info to "info",
                Icons.Default.Delete to "delete",
                Icons.Default.Share to "share"
            ),
            bottomOnClicks = listOf(
                {},
                {},
                {},
                {}
            ),
        ) { paddingValues ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.padding(paddingValues),
            ) { itemIndex ->
                pagingItems[itemIndex]?.let { imageInfo ->
                    if (!imageLabelLists.labelingDone) {
                        SingleImagePage(
                            imageInfo = imageInfo,
                            partImageLabelResult = imageLabelLists.partImageLabelList,
                            wholeImageLabelResult = imageLabelLists.wholeImageLabelList,
                            addedLabelList = imageLabelLists.addedLabelList,
                            originalImageSize = viewModel.imageSize,
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
                                    labelingDone = true
                                )
                                // allow labeling again
                                viewModel.labelingStart = false
                            },
                            onLabelAddingClick = { addLabelClicked = true },
                            onAddedLabelClick = { label, selected ->
                                // deselecting an added label makes it disappear
                                if (!selected) {
                                    viewModel.setAddedLabelList(imageLabelLists.addedLabelList?.minus(label))
                                }
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
    }
}