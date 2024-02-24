package com.example.image_multi_recognition.compose.view

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.ElevatedSmallIconButton
import com.example.image_multi_recognition.compose.statelessElements.LabelSelectionElement
import com.example.image_multi_recognition.compose.statelessElements.SingleImageView
import com.example.image_multi_recognition.compose.statelessElements.crop
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.util.LabelPlacingStrategy
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.viewmodel.ImageLabelResult
import com.example.image_multi_recognition.viewmodel.SingleImageViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SingleImageComposable(
    viewModel: SingleImageViewModel,
    modifier: Modifier = Modifier,
) {
    Log.d(getCallSiteInfoFunc(), "currentThread: ${Thread.currentThread().id}: ${Thread.currentThread().name}")
    val pagingDataFlow by viewModel.pagingFlow.collectAsStateWithLifecycle()
    val pagingItems = pagingDataFlow.collectAsLazyPagingItems()
    val pagerState = rememberPagerState(
        // For HorizontalPager, the index starts from 0; for PagingSource, the index starts from 1
        initialPage = viewModel.initialKey - 1,
        pageCount = { pagingItems.itemCount }
    )
    // if no label is detected, then we receive an empty list here,
    val imageLabelResult by viewModel.imageLabelFlow.collectAsStateWithLifecycle()

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
    Log.d(getCallSiteInfoFunc(), "currentPage: ${pagingItems.itemCount}")

    var addLabelClicked by rememberSaveable{ mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize()
    ){
        SingleImageView(
            //title = "${pagerState.currentPage}/${pagerState.pageCount}",
            title = "${pagerState.currentPage + 1}/${pagingItems.itemCount}",
            onBack = {},
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
                    SingleImagePage(
                        imageInfo = imageInfo,
                        imageLabelResult = imageLabelResult,
                        viewModel.imageSize,
                        onLabelClick = {},
                        onLabelDone = {}
                    )
                }
            }
        }
        if(addLabelClicked){

        }
    }
}

@Composable
fun SingleImagePage(
    imageInfo: ImageInfo,
    imageLabelResult: List<ImageLabelResult>,
    originalImageSize: Pair<Int, Int>,
    modifier: Modifier = Modifier,
    onLabelClick: (Boolean) -> Unit,
    onLabelDone: () -> Unit,
) {
    //(if (++addedLabel > maximumCount) false else true)
    // add it.label.isNotEmpty() to filter out ImageLabelResult.EmptyResultList
    val partImageLabelResult = imageLabelResult.filter { it.rect != null && it.label.isNotEmpty() }
    Log.d(getCallSiteInfoFunc(), "partImageLabelResult: $partImageLabelResult")
    // remove duplication: "it !in partImageLabelResult"
    val partLabels = setOf(*partImageLabelResult.map { it.label }.toTypedArray())
    val wholeImageLabelResult =
        imageLabelResult.filter { it.rect == null && it.label.isNotEmpty() && it.label !in partLabels }
    Log.d(getCallSiteInfoFunc(), "wholeImageLabelResult: $wholeImageLabelResult")
    ConstraintLayout(
        modifier = modifier.fillMaxSize()
    ) {
        val (imageRef, labelRowRef, editRowRef, noLabelRef) = createRefs()
        CustomImageLayout(
            imageInfo = imageInfo,
            originalWidth = originalImageSize.first,
            originalHeight = originalImageSize.second,
            imageLabelList = partImageLabelResult,
            onLabelClick = onLabelClick,
            modifier = Modifier.constrainAs(imageRef) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
        )
        // imageLabelResult.isNotEmpty() means that the user has clicked the "label" button
        if (imageLabelResult.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.constrainAs(labelRowRef) {
                    bottom.linkTo(imageRef.top)
                }
            ) {
                wholeImageLabelResult.forEach {
                    LabelSelectionElement(
                        imageLabelResult = it,
                        onClick = onLabelClick
                    )
                }
            }
            Row(
                modifier = Modifier.constrainAs(editRowRef) {
                    end.linkTo(parent.end)
                    top.linkTo(imageRef.bottom)
                },
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ElevatedSmallIconButton(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add labels",
                    onClick = {},
                )
                ElevatedSmallIconButton(
                    imageVector = Icons.Filled.Done,
                    contentDescription = "Submit",
                    onClick = onLabelDone
                )
            }
            if (ImageLabelResult.isEmptyResult(imageLabelResult)) {
                Text(
                    text = "No label found!",
                    modifier = Modifier.constrainAs(noLabelRef) {
                        top.linkTo(editRowRef.top)
                        bottom.linkTo(editRowRef.bottom)
                        start.linkTo(parent.start)
                    }.padding(horizontal = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colorResource(R.color.Crimson)
                )
            }
        }
    }
}

@Composable
fun CustomImageLayout(
    imageInfo: ImageInfo,
    originalWidth: Int,
    originalHeight: Int,
    imageLabelList: List<ImageLabelResult>,
    onLabelClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Log.d(getCallSiteInfoFunc(), "Recomposition")
    Log.d(getCallSiteInfoFunc(), "ImageLabel list: $imageLabelList")
    val rectList = imageLabelList.filter { it.rect != null }.map { it.rect!! }
    val image = @Composable {
        Log.d(getCallSiteInfoFunc(), "AsyncImage() is called")
        AsyncImage(
            model = imageInfo.fullImageFile,
            contentDescription = imageInfo.id.toString()
        )
    }
    val rects = @Composable {
        rectList.forEach { _ ->
            Box(
                modifier = Modifier.background(Color.Transparent)
                    .border(width = 2.dp, color = Color.Red),
            )
        }
    }
    val labels = @Composable {
        imageLabelList.forEach { imageLabelResult ->
            LabelSelectionElement(imageLabelResult, onLabelClick)
        }
    }
    // custom layout handle pixel instead of dp
    Layout(
        contents = listOf(image, rects, labels),
        modifier = modifier
    ) { (imageMeasurables, rectMeasurables, labelMesurables), constrains ->
        val imagePlaceable = imageMeasurables.first().measure(constrains)
        val labelPlaceables = labelMesurables.map { it.measure(constrains) }
        val widthProportion: Double = imagePlaceable.width.toDouble() / originalWidth
        val heightProportion: Double = imagePlaceable.height.toDouble() / originalHeight

        val rectPlaceables = rectMeasurables.mapIndexed() { index, rectMeasurable ->
            val width = ((rectList[index].width()) * widthProportion).toInt()
            val height = ((rectList[index].height()) * heightProportion).toInt()
            rectMeasurable.measure(
                constrains.copy(
                    minWidth = width,
                    maxWidth = width,
                    maxHeight = height,
                    minHeight = height
                )
            )
        }
        Log.d(
            getCallSiteInfoFunc(),
            "proportion: (x, y) = (${imagePlaceable.width.toDouble() / originalWidth}, ${imagePlaceable.height.toDouble() / originalHeight})"
        )
        layout(
            height = imagePlaceable.height,
            width = imagePlaceable.width
        ) {
            imagePlaceable.place(0, 0)
            Log.d(
                getCallSiteInfoFunc(),
                "imagePlaceable size: (width: ${imagePlaceable.width}, height: ${imagePlaceable.height})"
            )
            rectPlaceables.forEachIndexed { index, rectPlaceable ->
                rectPlaceable.place(
                    (rectList[index].left * widthProportion).toInt(),
                    (rectList[index].top * heightProportion).toInt()
                )
            }
            if (labelPlaceables.isNotEmpty()) {
                val places = LabelPlacingStrategy.placeLabels(imageLabelList.map { it.rect!! })
                val convertedPlaces = LabelPlacingStrategy.convertPlacingResult(
                    placingResultList = places,
                    labelWidth = labelPlaceables.map { it.width },
                    labelHeight = labelPlaceables.map { it.height },
                    widthProportion = widthProportion,
                    heightProportion = heightProportion,
                    boundaryWidth = imagePlaceable.width,
                    boundaryHeight = imagePlaceable.height
                )
                Log.d(getCallSiteInfoFunc(), "placing strategy: $places.toString()")
                labelPlaceables.forEachIndexed { index, labelPlaceable ->
                    labelPlaceable.place(convertedPlaces[index].first, convertedPlaces[index].second)
                }
            }
        }
    }
}

