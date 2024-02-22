package com.example.image_multi_recognition.compose.view

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.SingleImageView
import com.example.image_multi_recognition.db.ImageInfo
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
    //val rectList by viewModel.rectListFlow.collectAsStateWithLifecycle()
    val imageLabelResult by viewModel.imageLabelFlow.collectAsStateWithLifecycle()

    // Note if you use "LaunchedEffect{...}" it will run after the current recomposition
    // As a result, when switching to the next page, the previous rectangle first shows then disappears.
    // However, by using "remember(pagerState.currentPage){...}", we can clear imageLabelResult first.
    remember(pagerState.currentPage) {
        // the info in imageLabelResult has been shown in the previous screen,
        // we clear it so that when moving to the next page, the same imageLabelResult is not shown
        viewModel.clearPage(pagerState.currentPage)
    }

    // a strange behavior, once I access "pagerState.pageCount" instead of "pagingItems.itemCount" here, it causes infinite recomposition
    // Log.d(getCallSiteInfoFunc(), "currentPage: ${pagerState.pageCount}")
    Log.d(getCallSiteInfoFunc(), "currentPage: ${pagingItems.itemCount}")

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
                viewModel.setObjectDetectingRects(imageInfo)
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
        modifier = modifier
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(paddingValues)
        ) { itemIndex ->
            val partImageLabelResult = imageLabelResult.filter { it.rect != null }
            val wholeImageLabelResult = imageLabelResult.filter { it.rect == null }
            pagingItems[itemIndex]?.let { imageInfo ->
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        wholeImageLabelResult.forEach {
                            LabelSelectButton(
                                imageLabelResult = it,
                                onLabelClick = {}
                            )
                        }
                    }
                    CustomImageLayout(
                        imageInfo = imageInfo,
                        originalWidth = viewModel.imageSize.first,
                        originalHeight = viewModel.imageSize.second,
                        imageLabelList = partImageLabelResult,
                        onLabelClick = {},
                        onLabelDone = {}
                    )
                }
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
    onLabelClick: (ImageLabelResult) -> Unit,
    onLabelDone: () -> Unit,
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
            LabelSelectButton(imageLabelResult, onLabelClick)
        }
    }
    // custom layout handle pixel instead of dp
    Layout(
        contents = listOf(image, rects, labels),
        modifier = modifier
    ) { (imageMeasurables, rectMeasurables, labelMesurables), constrains ->
        val imagePlaceable = imageMeasurables.first().measure(constrains)
        val labelPlaceables = labelMesurables.map { it.measure(constrains) }
        // when rect is null, the label is for the whole image instead of a part of the image
//        val wholeImageLabelPlaceable = labelPlaceables.filterIndexed { index, placeable ->
//            imageLabelList[index].rect == null
//        }
//        val partImageLabelPlaceable = labelPlaceables.filterIndexed { index, placeable ->
//            imageLabelList[index].rect != null
//        }

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
                labelPlaceables.forEachIndexed { index, labelPlaceable ->
                    // place the label in the middle of the rect
                    val x =
                        (imageLabelList[index].rect!!.left + (imageLabelList[index].rect!!.width() - labelPlaceable.width) / 2) * widthProportion
                    val y =
                        (imageLabelList[index].rect!!.top + (imageLabelList[index].rect!!.height() - labelPlaceable.height) / 2) * heightProportion
                    labelPlaceable.place(x.toInt(), y.toInt())
                }
            }
//            if (wholeImageLabelPlaceable.isNotEmpty()) {
//                val intervalWidth = (imagePlaceable.width -
//                        wholeImageLabelPlaceable.map { it.width }
//                            .reduce { acc, width -> acc + width }) / (wholeImageLabelPlaceable.size + 1)
//                var accX = intervalWidth
//                wholeImageLabelPlaceable.forEach { labelPlaceable ->
//                    // put the label below the image with SpaceEvenly effect
//                    labelPlaceable.place(accX, imagePlaceable.height)
//                    accX += labelPlaceable.width + intervalWidth
//                }
//            }
        }
    }
}

@Composable
fun LabelSelectButton(
    imageLabelResult: ImageLabelResult,
    onLabelClick: (ImageLabelResult) -> Unit,
    modifier: Modifier = Modifier
) {
    var isSelected by rememberSaveable { mutableStateOf(false) }
    val onClick = {
        isSelected = !isSelected
        onLabelClick(imageLabelResult)
    }
    if (!isSelected) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(
                text = imageLabelResult.label,
                color = Color.Blue,
                style = MaterialTheme.typography.labelMedium
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier
        ) {
            Text(
                text = imageLabelResult.label,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}