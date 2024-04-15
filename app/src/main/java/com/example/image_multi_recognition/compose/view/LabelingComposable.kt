package com.example.image_multi_recognition.compose.view

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.CustomSnackBar
import com.example.image_multi_recognition.compose.statelessElements.PagingItemImage
import com.example.image_multi_recognition.compose.statelessElements.ToggleIcon
import com.example.image_multi_recognition.compose.statelessElements.TopAppBarForNotRootDestination
import com.example.image_multi_recognition.compose.view.imageShow.OffsetAnimationData
import com.example.image_multi_recognition.compose.view.imageShow.ZoomAnimationData
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.model.LabelUiModel
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.util.pointerInput.*
import com.example.image_multi_recognition.viewmodel.LabelingViewModel
import com.example.image_multi_recognition.viewmodel.basic.LabelingSupportViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.absoluteValue
import kotlin.reflect.KProperty

@Composable
fun LabelingComposable(
    modifier: Modifier = Modifier,
    viewModel: LabelingViewModel,
    navBottomAppBar: @Composable () -> Unit,
    provideInitialSetting: () -> AppData,
    onSettingClick: () -> Unit,
    onAlbumClick: (Long) -> Unit,
) {
    var labelingClicked by rememberSaveable { mutableStateOf(false) }
    val labelingState by viewModel.labelingStateFlow.collectAsStateWithLifecycle()
    val labelAdding by viewModel.labelAddingStateFlow.collectAsStateWithLifecycle()
    val snackBarState = remember { SnackbarHostState() }
    val unlabeledImageList by viewModel.unlabeledImageListFlow.collectAsStateWithLifecycle()
    val imagesPerRow by viewModel.imagesPerRowFlow.collectAsStateWithLifecycle(provideInitialSetting().imagesPerRow)
    val labelingStatus by viewModel.labelingStatusFlow.collectAsStateWithLifecycle(provideInitialSetting().labelingStatus)

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
    val activity = LocalContext.current as ComponentActivity

    Scaffold(
        snackbarHost = { SnackbarHost(snackBarState) { CustomSnackBar(it) } },
        topBar = {
            TopAppBarForNotRootDestination(
                title = if (labelingClicked) {
                    if (labelingState.labelingDone) {
                        stringResource(R.string.labeling_result)
                    } else {
                        "${stringResource(R.string.loading)}..."
                    }
                } else {
                    stringResource(R.string.app_name)
                },
                onBack = if (labelingClicked) {
                    {
                        if (labelingClicked && !labelingState.labelingDone) {
                            viewModel.scanCancelled = true
                            viewModel.resumeScanPaused()
                        }
                        labelingClicked = false
                    }
                } else null
            ) {
                if (labelingClicked && labelingState.labelingDone) {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = {
                                if (!labelAdding) {
                                    coroutineScope.launch {
                                        viewModel.onLabelingConfirm(imageSelectedStateHolder) {
                                            // https://stackoverflow.com/questions/71471679/jetpack-compose-scaffold-possible-to-override-the-standard-durations-of-snackbar
                                            val job = launch {
                                                snackBarState.showSnackbar(
                                                    labelAddedString,
                                                    duration = SnackbarDuration.Indefinite
                                                )
                                            }
                                            delay(1000)
                                            job.cancel()
                                            labelingClicked = false
                                        }
                                    }
                                }
                            }) {
                            Icon(Icons.Filled.Check, "confirm_labels")
                        }
                    }
                } else if (!labelingClicked) {
                    var showPermissionRational by remember { mutableStateOf(false) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        with(viewModel.permissionAccessor) {
                            activity.setupPermissionRequest(
                                permission = Manifest.permission.POST_NOTIFICATIONS,
                                provideShowPermissionRational = { showPermissionRational },
                                setShowPermissionRational = { showPermissionRational = it },
                                onPermissionGranted = {
                                    viewModel.scanImagesByWorkManager(
                                        album = 0L,
                                        onProgressChange = {
                                            if (!labelingClicked) labelingClicked = true
                                        },
                                        onWorkCanceled = { labelingClicked = false },
                                        onWorkFinished = {}
                                    )
                                },
                                onPermissionDenied = {
                                    viewModel.scanImages(unlabeledImageList)
                                    labelingClicked = true
                                }
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (unlabeledImageList.size > DefaultConfiguration.WORK_MANAGER_THRESHOLD) {
                                // check POST_NOTIFICATION permission first, if no permission, do not use workManager which requires notification
                                with(viewModel.permissionAccessor) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                        && !activity.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                                    ) {
                                        // setup permission requester in permissionAccessor
                                        if (activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                                            showPermissionRational = true
                                        } else {
                                            // shouldShowRequestPermissionRationale returns false if the permission has not been asked before
                                            permissionRequester.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    } else {
                                        // we do not need POST_NOTIFICATIONS, just run WorkManager with notification
                                        viewModel.scanImagesByWorkManager(
                                            album = 0L,
                                            onProgressChange = {
                                                if (!labelingClicked) labelingClicked = true
                                            },
                                            onWorkCanceled = { labelingClicked = false },
                                            onWorkFinished = {}
                                        )
                                    }
                                }
                            } else {
                                viewModel.scanImages(unlabeledImageList)
                                labelingClicked = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.baseline_new_label_24),
                            contentDescription = "autoLabeling"
                        )
                    }
                    IconButton(
                        onClick = onSettingClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "setting"
                        )
                    }
                }
            }
        },
        bottomBar = navBottomAppBar
    ) { originalPaddingValues ->
        val paddingValues = PaddingValues(
            start = originalPaddingValues.calculateStartPadding(LayoutDirection.Ltr),
            end = originalPaddingValues.calculateEndPadding(LayoutDirection.Ltr),
            top = originalPaddingValues.calculateTopPadding(),
            bottom = originalPaddingValues.calculateBottomPadding() + DefaultConfiguration.NAV_BOTTOM_APP_BAR_CROPPED.dp
        )
        Column(
            modifier = Modifier.padding(paddingValues),
            verticalArrangement = Arrangement.Center
        ) {
            if (labelingClicked) {
                if (labelingState.labelingDone) {
                    val pagingItems = viewModel.labelImagesFlow.collectAsLazyPagingItems()
                    ImageLabelingResultShow(
                        pagingItemsEmpty = viewModel.labelImagesMap.isEmpty(),
                        pagingItems = pagingItems,
                        imageSelectedStateHolderParam = imageSelectedStateHolder,
                        labelSelectedStateHolderParam = labelSelectedStateHolder,
                        provideImagePerRow = { imagesPerRow }
                    )
                } else {
                    val scanPaused by viewModel.scanPaused.collectAsStateWithLifecycle()
                    LabelingOnProgress(
                        progress = labelingState.labeledImageCount.toFloat() / viewModel.unlabeledSize,
                        text = "${stringResource(R.string.labeling)}...    ${labelingState.labeledImageCount}/${viewModel.unlabeledSize}",
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
                val albumPagingItems = viewModel.unlabeledImageAlbumFlow.collectAsLazyPagingItems()
                val gridState = rememberLazyGridState()

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(DefaultConfiguration.ALBUM_PER_ROW),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(DefaultConfiguration.ALBUM_INTERVAL.dp),
                        verticalArrangement = Arrangement.spacedBy(DefaultConfiguration.ALBUM_INTERVAL.dp),
                        modifier = modifier.padding(horizontal = DefaultConfiguration.ALBUM_INTERVAL.dp),
                    ) {
                        items(
                            count = albumPagingItems.itemCount + 1,
                            span = { index ->
                                if (index == 0) GridItemSpan(DefaultConfiguration.ALBUM_PER_ROW)
                                else GridItemSpan(1)
                            }
                        ) { index ->
                            if (index == 0) {
                                // It seems that applying an align(Alignment.CenterHorizontally) modifier to Text does not work.
                                // So we use a Row to do that
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.unlabeled_image_count, unlabeledImageList.size),
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            } else {
                                albumPagingItems[index - 1]?.let { albumWithLatestImage ->
                                    key(albumWithLatestImage.album) {
                                        AlbumPagingItem(
                                            imagePath = File(
                                                AlbumPathDecoder.decode(albumWithLatestImage.album),
                                                albumWithLatestImage.path
                                            ),
                                            title = "${AlbumPathDecoder.decodeAlbumName(albumWithLatestImage.album)} (${albumWithLatestImage.count})",
                                            contentDescription = albumWithLatestImage.album.toString(),
                                            sizeDp = ((LocalConfiguration.current.screenWidthDp - DefaultConfiguration.ALBUM_INTERVAL * 3) / 2).dp,
                                            onAlbumClick = {
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
        }
    }
}

@Composable
fun ImageLabelingResultShow(
    pagingItemsEmpty: Boolean,
    pagingItems: LazyPagingItems<LabelUiModel>,
    modifier: Modifier = Modifier,
    imageSelectedStateHolderParam: Map<String, Map<Long, MutableState<Boolean>>>,
    labelSelectedStateHolderParam: Map<String, MutableState<Boolean>>,
    provideImagePerRow: () -> Int,
) {
    var doubleClickedImageInfo: ImageInfo? by remember { mutableStateOf(null) }
    val gridState = rememberLazyGridState()

    var scrollingAmount by remember { mutableFloatStateOf(0f) }
    val scrolledFlow = remember { MutableStateFlow(false) }

    LaunchedEffect(scrollingAmount) {
        if (scrollingAmount != 0f) {
            while (isActive) {
                // Note although you have scrolled the screen, if your finger stays still during the scrolling,
                // no onDrag is triggered and longPressAndDragSelection() does not get notified
                // As a result, during scrolling, no items are further selected if your finger stays still
                gridState.scrollBy(scrollingAmount)
                // To solve the problem above, we use a scrolledFlow and let the longPressAndDragSelection to access that flow
                scrolledFlow.value = !scrolledFlow.value
                delay(20)
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
            columns = GridCells.Fixed(provideImagePerRow()),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(DefaultConfiguration.IMAGE_INTERVAL.dp),
            verticalArrangement = Arrangement.spacedBy(DefaultConfiguration.IMAGE_INTERVAL.dp),
            modifier = Modifier.longPressAndDragSelection(
                lazyGridState = gridState,
                scrolledFlow = scrolledFlow,
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
                            addedKeys.forEach { modifySelectedState(keyImageIdLabelMap[it], true) }
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
            ).fillMaxSize()
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
                                GridItemSpan(provideImagePerRow())
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
                            // val selected by remember { derivedStateOf { !excludedImageSet.set.contains(item.label to item.imageInfo.id) } }
                            // key(item.imageInfo.id) {
                            Log.d(getCallSiteInfo(), "Recomposition in LabelUiModel.Item here!!!!!")
                            Log.d(
                                getCallSiteInfo(),
                                "value: ${imageSelectedStateHolder[item.label]?.get(item.imageInfo.id)?.value}"
                            )
                            PagingItemImage(
                                imageInfo = item.imageInfo,
                                onImageClick = { modifySelectedState(item.toImageIdAndLabel(), null) },
                                onImageDoubleClick = { doubleClickedImageInfo = item.imageInfo },
                                availableScreenWidth = LocalConfiguration.current.screenWidthDp,
                                selectionMode = true,
                                selected = imageSelectedStateHolder[item.label]?.get(item.imageInfo.id)?.value
                                    ?: false,
                                provideImagePerRow = provideImagePerRow
                            )
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
        if (doubleClickedImageInfo != null) {
            FullScreenImage(
                imageFile = doubleClickedImageInfo!!.fullImageFile,
                contentDescription = doubleClickedImageInfo!!.id.toString(),
                onDismiss = { doubleClickedImageInfo = null }
            )
        }
    }
}

@Composable
fun FullScreenImage(
    imageFile: File,
    modifier: Modifier = Modifier,
    contentDescription: String = "",
    onDismiss: () -> Unit,
) {
    val zoomState = remember { mutableFloatStateOf(1f) }
    var zoom by zoomState   // you need to pass the state to some modifier
    val offsetState = remember { mutableStateOf(Offset.Zero) }
    var offset by offsetState
    val rememberedTransformOriginState = remember { mutableStateOf(TransformOrigin.Center) }
    var rememberedTransformOrigin by rememberedTransformOriginState
    val coroutineScope = rememberCoroutineScope()

    // animation
    var zoomAnimationData by remember { mutableStateOf(ZoomAnimationData()) }
    var offsetAnimationData by remember { mutableStateOf(OffsetAnimationData()) }
    var transformByAnimation by remember { mutableStateOf(false) }
    val animatedZoomOffset = remember { Animatable(ZoomOffsetData(), ZoomOffsetData.VectorConverter) }
    var animationTriggered by remember { mutableStateOf<Boolean?>(null) }
    var animationOngoing by remember { mutableStateOf(false) }
    var currentImageSize by remember { mutableStateOf(IntSize.Zero) }
    var currentParentSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(animationTriggered) {
        if (animationTriggered != null) {
            animationOngoing = true
            animatedZoomOffset.animateTo(
                targetValue = ZoomOffsetData(
                    zoomAnimationData.right,
                    Offset(offsetAnimationData.right.x, offsetAnimationData.right.y)
                ),
                animationSpec = spring(stiffness = Spring.StiffnessMedium)
            )
            offset = offsetAnimationData.right
            zoom = zoomAnimationData.right
            animationOngoing = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(colorResource(R.color.black).copy(alpha = 0.6f))
            .doubleClickZoomSupport(
                currentImageSize = currentImageSize,
                currentParentSize = currentParentSize,
                zoomState = zoomState,
                offsetState = offsetState,
                onSingleTap = { onDismiss() }
            ) { newZoom, newOffset, newTransformOrigin, offsetRemained ->
                // for animation
                zoomAnimationData = ZoomAnimationData(left = zoom, right = newZoom)
                offsetAnimationData = if (zoom == 1f || offset == Offset.Zero) {
                    OffsetAnimationData(Offset.Zero, Offset.Zero)
                } else {
                    OffsetAnimationData(offsetRemained, Offset.Zero)
                }
                coroutineScope.launch {
                    newTransformOrigin?.let {
                        rememberedTransformOrigin = it
                    }
                    // set animation zoom offset when transformByAnimation is set to true
                    // Note you cannot use "updateTransition()" here because it does not support reset the initial state,
                    // You can only change the target state in transition.animateXxx()
                    animatedZoomOffset.snapTo(
                        ZoomOffsetData(
                            zoomAnimationData.left,
                            Offset(offsetRemained.x, offsetRemained.y)
                        )
                    )
                    if (!transformByAnimation) transformByAnimation = true
                    animationTriggered = !(animationTriggered ?: false)
                }
            }.pinchZoomAndPanMoveSupport(
                currentImageSize = currentImageSize,
                currentParentSize = currentParentSize,
                zoomState = zoomState,
                offsetState = offsetState,
                transformOriginState = rememberedTransformOriginState,
                shouldRun = { !animationOngoing },
            ) { newZoom, newOffset, newTransformOrigin ->
                if (transformByAnimation) transformByAnimation = false
                zoom = newZoom
                offset = newOffset
                newTransformOrigin?.let { rememberedTransformOrigin = it }
            }.onGloballyPositioned { currentParentSize = it.size },
        verticalArrangement = Arrangement.Center
    ) {
        Log.d("", "AsyncImage is called")
        AsyncImage(
            model = imageFile,
            contentDescription = contentDescription,
            modifier = modifier.fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.8).toInt().dp)
                //.clipToBounds()
                .onGloballyPositioned { currentImageSize = it.size }
                .graphicsLayer {
                    if (transformByAnimation) {
                        scaleX = animatedZoomOffset.value.zoom
                        scaleY = animatedZoomOffset.value.zoom
                        translationX = animatedZoomOffset.value.offset.x
                        translationY = animatedZoomOffset.value.offset.y
                        // Note the range of a TransformOrigin is [0.0, 1.0]
                        transformOrigin = rememberedTransformOrigin
                    } else {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = offset.x
                        translationY = offset.y
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
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
    modifier: Modifier = Modifier,
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
                progress = { progress },
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

internal suspend fun LabelingSupportViewModel.onLabelingConfirm(
    imageSelectedStateHolder: Map<String, Map<Long, MutableState<Boolean>>>,
    onComplete: suspend () -> Unit = {},
) {
    startLabelAdding()
    addSelectedImageLabel(getSelectionResult(imageSelectedStateHolder)) { onComplete() }
}

internal fun getSelectionResult(imageSelectedStateHolder: Map<String, Map<Long, MutableState<Boolean>>>) =
    imageSelectedStateHolder.map { labelImages ->
        labelImages.key to labelImages.value
            .filter { it.value.value }    // selected
            .map { it.key }
    }.filter {
        it.second.isNotEmpty()
    }.toMap()

private data class ImageIdAndLabel(
    val imageId: Long,
    val label: String,
)

private fun LabelUiModel.Item.toImageIdAndLabel() = ImageIdAndLabel(imageInfo.id, label)

data class Wrapper<T>(var value: T)

@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> Wrapper<T>.getValue(thisObj: Any?, property: KProperty<*>): T = value

@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> Wrapper<T>.setValue(thisObj: Any?, property: KProperty<*>, value: T) {
    this.value = value
}