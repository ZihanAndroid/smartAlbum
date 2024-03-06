package com.example.image_multi_recognition.compose.statelessElements

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import coil.compose.AsyncImage
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.viewmodel.UiModel
import java.io.File

@Composable
fun ImagePagerView(
    pagingItems: LazyPagingItems<UiModel>,
    onImageClick: (Int) -> Unit,  // user wants to view a selected image
    modifier: Modifier = Modifier,
    onSendThumbnailRequest: (File, ImageInfo) -> Unit
) {
    Log.d(getCallSiteInfoFunc(), "Recomposition")
    val lazyGridState = rememberLazyGridState()
    val availableScreenWidth = LocalConfiguration.current.screenWidthDp

    LazyVerticalGrid(
        columns = GridCells.Fixed(DefaultConfiguration.IMAGE_PER_ROW),
        horizontalArrangement = Arrangement.spacedBy(DefaultConfiguration.IMAGE_INTERVAL.dp),
        verticalArrangement = Arrangement.spacedBy(DefaultConfiguration.IMAGE_INTERVAL.dp),
        modifier = modifier,
        state = lazyGridState
    ) {
        items(
            count = pagingItems.itemCount,
            span = { index ->
                pagingItems[index]?.let { pageItem ->
                    when (pageItem) {
                        is UiModel.ItemHeaderYearMonth -> {
                            GridItemSpan(DefaultConfiguration.IMAGE_PER_ROW)
                        }

                        is UiModel.ItemHeaderDay -> {
                            GridItemSpan(DefaultConfiguration.IMAGE_PER_ROW)
                        }

                        else -> GridItemSpan(1)
                    }
                } ?: GridItemSpan(1)
            }
        ) { index ->
            pagingItems[index]?.let { pageItem ->
                when (pageItem) {
                    is UiModel.ItemHeaderYearMonth -> {
                        // show both YearMonth and Day title
                        Column {
                            PageTitleYearMonth(pageItem.year, pageItem.month.toString())
                            PageTitleDay(
                                pageItem.month.toString(),
                                pageItem.dayOfMonth,
                                pageItem.dayOfWeek.toString()
                            )
                        }
                    }

                    is UiModel.ItemHeaderDay -> {
                        PageTitleDay(
                            pageItem.month.toString(),
                            pageItem.dayOfMonth,
                            pageItem.dayOfWeek.toString()
                        )
                    }

                    is UiModel.Item ->
                        key(pageItem.imageInfo.id) {
                            PagingItemImage(
                                imageInfo = pageItem.imageInfo,
                                onImageClick = { onImageClick(pageItem.originalIndex) },
                                availableScreenWidth = availableScreenWidth,
                                onSendThumbnailRequest = onSendThumbnailRequest
                            )
                        }
                }
            }

        }
        // Bad implementation! Do not use LazyColumn + FlowRow with a "prevItemList",
        // It is slower and difficult to set "prevItemList" correctly due to recomposition
        //    LazyColumn(
        //        modifier = modifier,
        //        state = lazyListState
        //    ) {
        //        val itemCount = pagingItems.itemCount
        //        Log.d(getCallSiteInfo(), "itemCount: $itemCount")
        //        Log.d(getCallSiteInfoFunc(), "2, recomposition here")
        //        // val prevItemList = mutableListOf<ImageInfo>()
        //        item {
        //            prevItemList.clear()
        //        }
        //
        //        items(count = itemCount) { index ->
        //            pagingItems[index]?.let { pageItem ->
        //                when (pageItem) {
        //                    is UiModel.ItemHeaderYearMonth -> {
        //                        // show both YearMonth and Day title
        //                        if (prevItemList.isNotEmpty()) PageItemImageShow(
        //                            prevItemList,
        //                            onImageSelected,
        //                            availableScreenWidth
        //                        )
        //                        PageTitleYearMonth(pageItem.year, pageItem.month.toString())
        //                        PageTitleDay(
        //                            pageItem.year,
        //                            pageItem.month.toString(),
        //                            pageItem.dayOfMonth,
        //                            pageItem.dayOfWeek.toString()
        //                        )
        //                    }
        //
        //                    is UiModel.ItemHeaderDay -> {
        //                        if (prevItemList.isNotEmpty()) PageItemImageShow(
        //                            prevItemList,
        //                            onImageSelected,
        //                            availableScreenWidth
        //                        )
        //                        PageTitleDay(
        //                            pageItem.year,
        //                            pageItem.month.toString(),
        //                            pageItem.dayOfMonth,
        //                            pageItem.dayOfWeek.toString()
        //                        )
        //                    }
        //
        //                    is UiModel.Item -> prevItemList.add(pageItem.imageInfo)
        //                }
        //            }
        //        }
        //        item {
        //            PageItemImageShow(prevItemList, onImageSelected, availableScreenWidth)
        //        }
        //    }
    }
}

@Composable
fun PagingItemImage(
    imageInfo: ImageInfo,
    onImageClick: () -> Unit,
    availableScreenWidth: Int,
    onSendThumbnailRequest: (File, ImageInfo) -> Unit
) {
    val imageSize = remember(availableScreenWidth) {
        (availableScreenWidth - (DefaultConfiguration.IMAGE_PER_ROW - 1) * DefaultConfiguration.IMAGE_INTERVAL) / DefaultConfiguration.IMAGE_PER_ROW
    }

    val imagePath = if (imageInfo.isThumbnailAvailable) {
        Log.d(getCallSiteInfoFunc(), "Loading image: ${imageInfo.path} from cache")
        imageInfo.thumbnailFile
    } else {
        Log.d(getCallSiteInfoFunc(), "Loading image: ${imageInfo.path} from disk")
        // send cache request
        onSendThumbnailRequest(imageInfo.fullImageFile, imageInfo)
        imageInfo.fullImageFile
    }
    AsyncImage(
        model = imagePath,
        contentDescription = imageInfo.id.toString(),
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(imageSize.dp).clickable { onImageClick() }
    )
}

@Composable
private fun PageTitleYearMonth(
    year: Int,
    month: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = "$month $year",
        modifier = modifier.padding(top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleLarge
    )
}

@Composable
private fun PageTitleDay(
    month: String,
    dayOfMonth: Int,
    dayOfWeek: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = "$dayOfWeek, $month $dayOfMonth",
        modifier = modifier.padding(vertical = 8.dp),
        style = MaterialTheme.typography.titleMedium
    )
}