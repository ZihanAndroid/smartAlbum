package com.example.image_multi_recognition.compose.view

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.PagingItemImage
import com.example.image_multi_recognition.compose.statelessElements.ToggleIcon
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.util.longPressAndDragSelection
import com.example.image_multi_recognition.util.onKeyProvided
import com.example.image_multi_recognition.viewmodel.LabelUiModel
import com.example.image_multi_recognition.viewmodel.LabelingViewModel
import com.example.image_multi_recognition.viewmodel.basic.LabelingSupportViewModel
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.absoluteValue

@Composable
fun LabelingComposable(
    modifier: Modifier = Modifier,
    viewModel: LabelingViewModel,
    rootSnackBarHostState: SnackbarHostState,
    onAlbumClick: (Long) -> Unit
) {
    var labelingClicked by rememberSaveable { mutableStateOf(false) }
    val labelingState by viewModel.labelingStateFlow.collectAsStateWithLifecycle()
    val labelAdding by viewModel.labelAddingStateFlow.collectAsStateWithLifecycle()

    val imageSelectedStateHolder: Map<String, Map<Long, MutableState<Boolean>>> = remember(labelingState) {
        // viewModel.labelImagesMap is the backing data of pagingItem from viewModel.labelImagesFlow
        mapOf(*(viewModel.labelImagesMap.map { labelImages ->
            labelImages.key to mapOf(*labelImages.value.map { imageInfo ->
                imageInfo.id to mutableStateOf(true)
            }.toTypedArray())
        }.toTypedArray()))
    }
    val labelSelectedStateHolder: Map<String, MutableState<Boolean>> = remember(labelingState) {
        mapOf(*(viewModel.labelImagesMap.map { it.key to mutableStateOf(true) }).toTypedArray())
    }
    val coroutineScope = rememberCoroutineScope()
    val labelAddedString = stringResource(R.string.label_added)

    LaunchedEffect(labelAdding) {
        if (labelAdding == false) {
            // https://stackoverflow.com/questions/71471679/jetpack-compose-scaffold-possible-to-override-the-standard-durations-of-snackbar
            val job = launch {
                rootSnackBarHostState.showSnackbar(labelAddedString, duration = SnackbarDuration.Indefinite)
            }
            delay(1000)
            job.cancel()
            labelingClicked = false
        }
    }

    if (labelingClicked) {
        if (labelingState.labelingDone) {
            val pagingItems = viewModel.labelImagesFlow.collectAsLazyPagingItems()
            Column {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                if (!viewModel.onLabelingConfirm(imageSelectedStateHolder)) {
                                    labelingClicked = false
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Check, "confirm_labels")
                    }
                    IconButton(
                        onClick = { labelingClicked = false }
                    ) {
                        Icon(Icons.Filled.Close, "cancel_labels")
                    }
                }
                ImageLabelingResultShow(
                    pagingItemsEmpty = viewModel.labelImagesMap.isEmpty(),
                    pagingItems = pagingItems,
                    imageSelectedStateHolderParam = imageSelectedStateHolder,
                    labelSelectedStateHolderParam = labelSelectedStateHolder
                )
            }
        } else {
            val scanPaused by viewModel.scanPaused.collectAsStateWithLifecycle()
            LabelingOnProgress(
                progress = labelingState.labeledImageCount.toFloat() / viewModel.imageObjectsMap.size,
                text = "${stringResource(R.string.loading)}...\t${labelingState.labeledImageCount}/${viewModel.imageObjectsMap.size}",
                onResumePauseClicked = { viewModel.reverseScanPaused() },
                onCancelClicked = {
                    viewModel.scanCancelled = true
                    // let the coroutine continue running to recognize the "scanCancelled" flag
                    viewModel.resumeScanPaused()
                    // change window content
                    labelingClicked = false
                },
                scanPaused = scanPaused
            )
        }
    } else {
        val unlabeledImageList by viewModel.unlabeledImageListFlow.collectAsStateWithLifecycle()
        val albumPagingItems = viewModel.unlabeledImageAlbumFlow.collectAsLazyPagingItems()
        val gridState = rememberLazyGridState()

        Column {
            UnlabeledInfo(unlabeledImageList.size) {
                viewModel.scanImages(unlabeledImageList)
                labelingClicked = true
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(DefaultConfiguration.ALBUM_PER_ROW),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(DefaultConfiguration.ALBUM_INTERVAL.dp),
                verticalArrangement = Arrangement.spacedBy(DefaultConfiguration.ALBUM_INTERVAL.dp),
                modifier = modifier.padding(horizontal = DefaultConfiguration.ALBUM_INTERVAL.dp)
            ) {
                items(
                    count = albumPagingItems.itemCount
                ) { index ->
                    albumPagingItems[index]?.let { albumWithLatestImage ->
                        key(albumWithLatestImage.album) {
                            AlbumPagingItem(
                                // albumImage = albumWithLatestImage,
                                imagePath = File(
                                    AlbumPathDecoder.decode(albumWithLatestImage.album),
                                    albumWithLatestImage.path
                                ),
                                title = "${AlbumPathDecoder.decodeAlbumName(albumWithLatestImage.album)} (${albumWithLatestImage.count})",
                                contentDescription = albumWithLatestImage.album.toString(),
                                sizeDp = ((LocalConfiguration.current.screenWidthDp - DefaultConfiguration.ALBUM_INTERVAL * 3) / 2).dp,
                                onAlbumClick = {
                                    viewModel.resetLabelAdding()
                                    onAlbumClick(albumWithLatestImage.album)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageLabelingResultShow(
    pagingItemsEmpty: Boolean,
    pagingItems: LazyPagingItems<LabelUiModel>,
    modifier: Modifier = Modifier,
    imageSelectedStateHolderParam: Map<String, Map<Long, MutableState<Boolean>>>,
    labelSelectedStateHolderParam: Map<String, MutableState<Boolean>>
) {
    var selectedImageInfo: ImageInfo? by remember { mutableStateOf(null) }
    val gridState = rememberLazyGridState()

    var scrollingAmount by rememberSaveable { mutableStateOf(0f) }

    LaunchedEffect(scrollingAmount) {
        if (scrollingAmount != 0f) {
            while (isActive) {
                gridState.scrollBy(scrollingAmount)
                delay(10)
            }
        }
    }
    val keyImageIdLabelMap = rememberSaveable { mutableMapOf<Int, ImageIdAndLabel>() }
    val addedImageIdKeyMap = rememberSaveable { mutableMapOf<ImageIdAndLabel, Int>() }

    val imageSelectedStateHolder by rememberUpdatedState(imageSelectedStateHolderParam)
    val labelSelectedStateHolder by rememberUpdatedState(labelSelectedStateHolderParam)

    val modifySelectedState: (ImageIdAndLabel?, Boolean?) -> Unit = remember {
        { item, value ->
            item?.let {
                imageSelectedStateHolder[item.label]?.get(item.imageId)?.let { state ->
                    state.value = value ?: !state.value
                }
                imageSelectedStateHolder[item.label]?.let { stateMap ->
                    if (stateMap.any { it.value.value }) {
                        labelSelectedStateHolder[item.label]?.let { labelState ->
                            labelState.value = true
                        }
                    } else if (stateMap.all { !it.value.value }) {
                        labelSelectedStateHolder[item.label]?.let { labelState ->
                            labelState.value = false
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(DefaultConfiguration.IMAGE_PER_ROW),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(DefaultConfiguration.IMAGE_INTERVAL.dp),
            verticalArrangement = Arrangement.spacedBy(DefaultConfiguration.IMAGE_INTERVAL.dp),
            modifier = Modifier.longPressAndDragSelection(
                lazyGridState = gridState,
                scrollingAmountSetter = { scrollingAmount = it },
                autoScrollThreshold = with(LocalDensity.current) { DefaultConfiguration.DRAG_SCROLL_THRESHOLD.dp.toPx() },
                provideDraggedKeys = { initialKey: Int?, prevKey: Int?, currentKey: Int? ->
                    onKeyProvided(
                        initialKey = initialKey,
                        prevKey = prevKey,
                        currentKey = currentKey,
                        onKeyRemove = { removedKeys ->
                            removedKeys.forEach { modifySelectedState(keyImageIdLabelMap[it], false) }
                        },
                        onKeyAdd = { addedKeys ->
                            addedKeys.forEach { modifySelectedState(keyImageIdLabelMap[it], true)}
                        },
                        keyExists = { key ->
                            keyImageIdLabelMap[key]?.let { item ->
                                imageSelectedStateHolder[item.label]?.get(item.imageId)?.value ?: false
                            } ?: false
                        },
                        keyTracked = { it > 0 }
                    )
                },
                onSelectionStart = { key ->
                    modifySelectedState(keyImageIdLabelMap[key], null)
                },
                keyTracked = { it > 0 }
            )
        ) {
            items(
                key = { index ->
                    pagingItems[index]?.let { labelUiModel ->
                        if (labelUiModel is LabelUiModel.Item) {
                            if (labelUiModel.toImageIdAndLabel() !in addedImageIdKeyMap) {
                                val imageIdAndLabel = labelUiModel.toImageIdAndLabel()
                                keyImageIdLabelMap[keyImageIdLabelMap.size + 1] = imageIdAndLabel
                                addedImageIdKeyMap[imageIdAndLabel] = keyImageIdLabelMap.size
                                keyImageIdLabelMap.size
                            } else {
                                // do not generate a new key for the item has been shown previously
                                addedImageIdKeyMap[labelUiModel.toImageIdAndLabel()]
                            }
                        } else {
                            -labelUiModel.hashCode().absoluteValue
                        }
                    } ?: 0
                },
                count = pagingItems.itemCount,
                span = { index ->
                    pagingItems[index]?.let { item ->
                        when (item) {
                            is LabelUiModel.Label -> {
                                GridItemSpan(DefaultConfiguration.IMAGE_PER_ROW)
                            }

                            is LabelUiModel.Item -> {
                                GridItemSpan(1)
                            }
                        }
                    } ?: GridItemSpan(1)
                }
            ) { index ->
                pagingItems[index]?.let { item ->
                    when (item) {
                        is LabelUiModel.Label -> {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item.label,
                                    modifier = Modifier.padding(8.dp),
                                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
                                )
                                IconButton(
                                    onClick = {
                                        labelSelectedStateHolder[item.label]?.let { state ->
                                            val selected = !state.value
                                            state.value = selected
                                            imageSelectedStateHolder[item.label]?.let { stateMap ->
                                                stateMap.forEach { _, state ->
                                                    state.value = selected
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    ToggleIcon(labelSelectedStateHolder[item.label]?.value ?: false)
                                }
                            }
                        }

                        is LabelUiModel.Item -> {
                            Log.d(getCallSiteInfo(), "Recomposition in LabelUiModel.Item")
                            //val selected by remember { derivedStateOf { !excludedImageSet.set.contains(item.label to item.imageInfo.id) } }
                            //key(item.imageInfo.id) {
                            Log.d(getCallSiteInfo(), "Recomposition in LabelUiModel.Item here!!!!!")
                            Log.d(
                                getCallSiteInfo(),
                                "value: ${imageSelectedStateHolder[item.label]?.get(item.imageInfo.id)?.value}"
                            )
                            PagingItemImage(
                                imageInfo = item.imageInfo,
                                onImageClick = { modifySelectedState(item.toImageIdAndLabel(), null) },
                                availableScreenWidth = LocalConfiguration.current.screenWidthDp,
                                onSendThumbnailRequest = { _, _ -> },
                                selectionMode = true,
                                // selected = !excludedImageSet.contains(ExcludedItem(item.imageInfo.id, item.label))
                                selected = imageSelectedStateHolder[item.label]?.get(item.imageInfo.id)?.value
                                    ?: false
                            )
                            //}
                        }
                    }
                }
            }
        }
        if (pagingItemsEmpty) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = stringResource(R.string.no_recognized_image),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        if (selectedImageInfo != null) {
            FullScreenImage(
                imageFile = selectedImageInfo!!.fullImageFile,
                contentDescription = selectedImageInfo!!.id.toString(),
                onDismiss = { selectedImageInfo = null }
            )
        }
    }
}

@Composable
fun FullScreenImage(
    imageFile: File,
    modifier: Modifier = Modifier,
    contentDescription: String = "",
    onDismiss: () -> Unit
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableStateOf(1f) }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(colorResource(R.color.greyAlpha).copy(alpha = 0.75f))
            //.clickable { onDismiss() }
            .pointerInput(Unit) {
                detectTapGestures(
                    // double tap to zoom in or zoom out
                    onDoubleTap = { tapOffset ->
                        zoom = if (zoom > 1f) 1f else 2f
                        offset = Offset(x = tapOffset.x * (1 - zoom), y = tapOffset.y * (1 - zoom))
                    },
                    onTap = { onDismiss() }
                )
            }.pointerInput(Unit) {
                // Note that you can access "size" inside the PointerInputScope to get the size of AsyncImage
                // you can use that "size" to restrict the boundary of offset.
                // Also note that the value centroid is corresponding to the coordinate of "size",
                // the pointer input region without the offset and zooming
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    Log.d(
                        getCallSiteInfo(),
                        "size: $size, centroid: [${centroid.x}, ${centroid.y}], pan: [${pan.x}, ${pan.y}]"
                    )
                    //val oldZoom = zoom
                    zoom = (zoom * gestureZoom).coerceAtLeast(1f)
                    // equation:
                    // (centroid.x - offset.x) / zoom = (centroid.x - newOffset.x) / newZoom
                    // Then we get "newOffset.x"
                    offset = Offset(
                        x = (gestureZoom * (offset.x - centroid.x + pan.x) + centroid.x)
                            .coerceIn(size.width * (1 - zoom), 0f),
                        y = (gestureZoom * (offset.y - centroid.y + pan.y) + centroid.y)
                            .coerceIn(size.height * (1 - zoom), 0f)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageFile,
            contentDescription = contentDescription,
            modifier = modifier
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.8).toInt().dp)
                //.clipToBounds()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offset.x
                    translationY = offset.y
                    // transformOrigin affects where the scaling starts from
                    transformOrigin = TransformOrigin(0f, 0f)
                }.fillMaxWidth()
        )
    }
}

@Composable
fun LabelingOnProgress(
    progress: Float,
    text: String,
    scanPaused: Boolean,
    onResumePauseClicked: () -> Unit,
    onCancelClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LinearProgressIndicator(
                progress = progress
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ElevatedButton(
                    onClick = onResumePauseClicked
                ) {
                    Text(stringResource(if (scanPaused) R.string.resume else R.string.pause))
                }
                ElevatedButton(
                    onClick = onCancelClicked
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
fun UnlabeledInfo(
    unlabeledImageCount: Int,
    modifier: Modifier = Modifier,
    onLabelingClick: () -> Unit,
) {
    Row(
        // horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(start = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.unlabeled_image_count, unlabeledImageCount)
        )
        IconButton(
            onClick = onLabelingClick
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.baseline_new_label_24),
                contentDescription = "autoLabeling",
            )
        }
    }
}

internal suspend fun LabelingSupportViewModel.onLabelingConfirm(imageSelectedStateHolder: Map<String, Map<Long, MutableState<Boolean>>>): Boolean {
    return withContext(Dispatchers.Default) {
        getSelectionResult(imageSelectedStateHolder).let {
            addSelectedImageLabel(it)
            it.isNotEmpty()
        }
    }
}

internal fun getSelectionResult(imageSelectedStateHolder: Map<String, Map<Long, MutableState<Boolean>>>) =
    mapOf(*imageSelectedStateHolder.map { labelImages ->
        labelImages.key to labelImages.value
            .filter { it.value.value }    // selected
            .map { it.key }
    }.filter {
        it.second.isNotEmpty()
    }.toTypedArray())

private data class ImageIdAndLabel(
    val imageId: Long,
    val label: String
)

private fun LabelUiModel.Item.toImageIdAndLabel() = ImageIdAndLabel(imageInfo.id, label)