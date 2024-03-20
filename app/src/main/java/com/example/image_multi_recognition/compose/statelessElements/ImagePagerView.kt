package com.example.image_multi_recognition.compose.statelessElements

import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import coil.compose.AsyncImage
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.util.*
import com.example.image_multi_recognition.viewmodel.UiModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

@Composable
fun ImagePagerView(
    pagingItems: LazyPagingItems<UiModel>,
    onImageClick: (Int) -> Unit,  // user wants to view a selected image
    modifier: Modifier = Modifier,
    onSendThumbnailRequest: (File, ImageInfo) -> Unit,
    selectionMode: Boolean = false,
    onClickSelect: (Long) -> Unit = {},
    onLongPress: (Long) -> Unit = {},
    enableLongPressAndDrag: Boolean = false, // enable drag selection
    // we use MutableSetWithState<Long> instead of MutableSetWithState<ImageInfo>
    // because ImageInfo may change like creating a new ImageInfo to change the "favorite" property
    // but the id will not change
    selectedImageIdSet: MutableSetWithState<Long> = MutableSetWithState(),
    deletedImageIds: Set<Long> = emptySet()
) {
    Log.d(getCallSiteInfoFunc(), "Recomposition")
    val lazyGridState = rememberLazyGridState()
    val availableScreenWidth = LocalConfiguration.current.screenWidthDp
    var scrollingAmountOnDragging by rememberSaveable { mutableStateOf(0f) }

    // scrolling LaunchedEffect
    LaunchedEffect(scrollingAmountOnDragging) {
        if (scrollingAmountOnDragging != 0f) {
            while (isActive) {
                lazyGridState.scrollBy(scrollingAmountOnDragging)
                delay(10)
            }
        }
    }
    val keyImageIdMap = rememberSaveable { mutableMapOf<Int, Long>() }
    // avoid duplication for imageId
    val addedImageIdKeyMap = rememberSaveable { mutableMapOf<Long, Int>() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(DefaultConfiguration.IMAGE_PER_ROW),
        horizontalArrangement = Arrangement.spacedBy(DefaultConfiguration.IMAGE_INTERVAL.dp),
        verticalArrangement = Arrangement.spacedBy(DefaultConfiguration.IMAGE_INTERVAL.dp),
        modifier = modifier.let { currentModifier ->
            if (enableLongPressAndDrag) {
                currentModifier.longPressAndDragSelection<Int>(
                    lazyGridState = lazyGridState,
                    autoScrollThreshold = with(LocalDensity.current) { DefaultConfiguration.DRAG_SCROLL_THRESHOLD.dp.toPx() },
                    scrollingAmountSetter = { scrollingAmountOnDragging = it },
                    provideDraggedKeys = { initialKey, prevKey, currentKey ->
                        onKeyProvided(
                            initialKey = initialKey,
                            prevKey = prevKey,
                            currentKey = currentKey,
                            onKeyRemove = { removedKeys ->
                                selectedImageIdSet.removeAll(removedKeys.map { keyImageIdMap[it] }.toSet())
                            },
                            onKeyAdd = { addedKeys ->
                                selectedImageIdSet.addAll(addedKeys.mapNotNull { keyImageIdMap[it] })
                            },
                            keyExists = {
                                selectedImageIdSet.contains(keyImageIdMap[it])
                            },
                            keyTracked = { it > 0 },
                            keyDeletedPreviously = {
                                deletedImageIds.contains(keyImageIdMap[it])
                            }
                        )
                    },
                    onSelectionStart = { key ->
                        onLongPress(keyImageIdMap[key]!!)
                    },
                    keyTracked = { it > 0 } // if key < 0, then it calculates from hashcode of non-image item
                )
            } else currentModifier
        }.tapClick<Int>(
            lazyGridState = lazyGridState,
            onTap = { key ->
                if (keyImageIdMap[key] !in selectedImageIdSet) {
                    selectedImageIdSet.add(keyImageIdMap[key]!!)
                } else {
                    selectedImageIdSet.remove(keyImageIdMap[key])
                }
            },
            keyTracked = { it > 0 }
        ),
        state = lazyGridState
    ) {
        items(
            key = { index ->
                pagingItems[index]?.let { uiModel ->
                    if (uiModel is UiModel.Item) {
                        if (uiModel.imageInfo.id !in addedImageIdKeyMap) {
                            keyImageIdMap[keyImageIdMap.size + 1] = uiModel.imageInfo.id
                            addedImageIdKeyMap[uiModel.imageInfo.id] = keyImageIdMap.size
                            keyImageIdMap.size
                        } else {
                            // do not generate a new key for the item has been shown previously
                            addedImageIdKeyMap[uiModel.imageInfo.id]
                        }
                    } else {
                        uiModel.hashCode().let { if (it > 0) -it else it }
                    }
                } ?: 0
            },
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
                            val selected by remember {
                                derivedStateOf {
                                    // read the State version.value to get "selected" automatically when state is change
                                    selectedImageIdSet.version.value
                                    selectedImageIdSet.contains(pageItem.imageInfo.id)
                                }
                            }

                            PagingItemImage(
                                imageInfo = pageItem.imageInfo,
                                onImageClick = if (selectionMode) {
                                    // do not set click to image to avoid conflict(long press and click) between pointerInput in lazy grid and image
                                    // onClickSelect(pageItem.imageInfo.id)
                                    null
                                } else {
                                    { onImageClick(pageItem.originalIndex) }
                                },
                                onImageLongClick = if (enableLongPressAndDrag) {
                                    null
                                } else {
                                    { onLongPress(pageItem.imageInfo.id) }
                                },
                                availableScreenWidth = availableScreenWidth,
                                onSendThumbnailRequest = onSendThumbnailRequest,
                                selectionMode = selectionMode,
                                selected = if (selectionMode) selected else false
                            )
                        }
                }
            }

        }
        // Bad implementation! Do not use LazyColumn + FlowRow with a "prevItemList",
        // It is slower and more difficult to set "prevItemList" correctly due to recomposition
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagingItemImage(
    imageInfo: ImageInfo,
    onImageClick: (() -> Unit)? = null,
    onImageLongClick: (() -> Unit)? = null,
    availableScreenWidth: Int,
    onSendThumbnailRequest: (File, ImageInfo) -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false
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
    Box {
        val transition = updateTransition(selected, label = "image_${imageInfo.id}_selected")
        val paddingValue by transition.animateDp(
            label = "image_padding",
            transitionSpec = { spring(stiffness = Spring.StiffnessHigh) }
        ) { selected ->
            if (selected) 6.dp else 0.dp
        }
        val cornerValue by transition.animateDp(
            label = "image_corner",
            transitionSpec = { spring(stiffness = Spring.StiffnessHigh) }
        ) { selected ->
            if (selected) 6.dp else 0.dp
        }

        AsyncImage(
            model = imagePath,
            contentDescription = imageInfo.id.toString(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(imageSize.dp).let {
                if (onImageClick != null) {
                    it.combinedClickable(
                        onClick = onImageClick,
                        // when onImageLongClick is null, onLongClick is set to null, as its default value in combinedClickable()
                        onLongClick = onImageLongClick
                    )
                } else it
            }.padding(paddingValue).clip(RoundedCornerShape(cornerValue))
        )
        if (selectionMode) {
            ToggleIcon(selected)
        }
        if (imageInfo.favorite) {
            // favoraite icon show be drawn after AsyncImage is shown
            Icon(
                imageVector = Icons.Filled.Favorite,
                // Note the position of padding() and size() modifier, apply padding() first then size(), the size will not change
                // while applying size() first then padding(), the size is changed by padding change
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp + paddingValue).size(16.dp - paddingValue / 4),
                contentDescription = "icon_${imageInfo.id}"

            )
        }
    }
}

@Composable
fun ToggleIcon(selected: Boolean) {
    if (selected) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.baseline_check_circle_outline_24),
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = null,
            modifier = Modifier
                .padding(4.dp)
                .border(1.dp, colorResource(R.color.colorAccent), CircleShape)
                .size(20.dp)
                .clip(CircleShape)
                .background(colorResource(R.color.colorAccent))
        )
    } else {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.baseline_radio_button_unchecked_24),
            contentDescription = null,
            modifier = Modifier.padding(4.dp)
        )
    }
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