package com.example.image_multi_recognition.compose.view.imageShow

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.ElevatedSmallIconButton
import com.example.image_multi_recognition.compose.statelessElements.LabelSelectionElement
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.util.RotateTransformation
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.util.pointerInput.ZoomOffsetData
import com.example.image_multi_recognition.util.pointerInput.doubleClickZoomSupport
import com.example.image_multi_recognition.util.pointerInput.pinchZoomAndPanMoveSupport
import com.example.image_multi_recognition.viewmodel.ImageLabelResult
import kotlinx.coroutines.launch

// Support zoom in and zoom out for an image
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SingleImagePage(
    imageInfo: ImageInfo,
    partImageLabelResult: List<ImageLabelResult>?,
    wholeImageLabelResult: List<ImageLabelResult>?,
    addedLabelList: List<String>?,
    originalImageSize: Pair<Int, Int>,
    modifier: Modifier = Modifier,
    labelSelected: (String) -> Boolean,
    onLabelClick: (String, Boolean) -> Unit,
    onLabelDone: () -> Unit,
    onLabelAddingClick: () -> Unit,
    onAddedLabelClick: (String, Boolean) -> Unit,
    provideRotationDegree: () -> Float,
    onDismiss: () -> Unit,
    pageScrolling: Boolean,
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
    // https://developer.android.com/develop/ui/compose/animation/value-based#animatable
    // -----------------------------------------------------------------------------------
    // Be careful! You cannot use updateTransition here, it does not support reset the initialValue,
    // eg:
    // val doubleClickTransition = updateTransition(
    //     targetState = toggled,
    //     label = "doubleClickAnimation"
    // )
    // val zoomAnimated by doubleClickTransition.animateFloat(
    //     label = "animateZoom",
    //     transitionSpec = { spring(stiffness = Spring.StiffnessMediumLow) }
    // ) {
    //     if (it) zoomAnimationData.left else zoomAnimationData.right
    // }
    // val offsetAnimated by doubleClickTransition.animateOffset(
    //     label = "animateOffset",
    //     transitionSpec = { spring(stiffness = Spring.StiffnessMediumLow) }
    // ) {
    //     if (it) offsetAnimationData.left else offsetAnimationData.right
    // }
    // And if you want to reset the initial value (left) by change offsetAnimationData and zoomAnimationData,
    // it does not work!, only changing to target value (right) works
    // To work around this, you should use "Animatable" below
    // -----------------------------------------------------------------------------------
    val animatedZoomOffset = remember { Animatable(ZoomOffsetData(), ZoomOffsetData.VectorConverter) }
    var animationTriggered by remember { mutableStateOf<Boolean?>(null) }
    var animationOngoing by remember { mutableStateOf(false) }
    // val rotationDegreeInt by rememberUpdatedState(provideRotationDegree())
    var currentImageSize by remember { mutableStateOf(IntSize.Zero) }
    var currentParentSize by remember { mutableStateOf(IntSize.Zero) }
    val derivedPageSrcolling by rememberUpdatedState(pageScrolling)

    LaunchedEffect(animationTriggered) {
        if (animationTriggered != null && !animationOngoing) {
            animationOngoing = true
            animatedZoomOffset.animateTo(
                targetValue = ZoomOffsetData(
                    zoomAnimationData.right,
                    Offset(offsetAnimationData.right.x, offsetAnimationData.right.y)
                ),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            )
            offset = offsetAnimationData.right
            zoom = zoomAnimationData.right
            animationOngoing = false
        }
    }

    Log.d(getCallSiteInfoFunc(), "Recomposition")
    ConstraintLayout(
        modifier = modifier.fillMaxSize().clipToBounds()
            //     .pointerInput(Unit) {
            //     detectTapGestures { onDismiss() }
            // }
            .doubleClickZoomSupport(
                currentImageSize = currentImageSize,
                currentParentSize = currentParentSize,
                zoomState = zoomState,
                offsetState = offsetState,
                // During scrolling, two pages are shown, and both of them contains doubleClickZoomSupport() modifier.
                // As a result, if you do not restrict the modifier for the next page,
                // you can still zoom in/out the next page when the current and next pages are scrolling together
                shouldRun = { !derivedPageSrcolling && !animationOngoing }
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
                    // set animation zoom offset first when transformByAnimation is set to true
                    // otherwise if you just set "transformByAnimation = true", the graphicsLayout will read the previous animation states
                    // which may cause flick in the screen
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
                shouldRun = { !animationOngoing && !derivedPageSrcolling },
                shouldEventConsumedForShouldRunFalse = { animationOngoing }
            ) { newZoom, newOffset, newTransformOrigin ->
                if (transformByAnimation) transformByAnimation = false
                zoom = newZoom
                offset = newOffset
                newTransformOrigin?.let { rememberedTransformOrigin = it }
            }.onGloballyPositioned { currentParentSize = it.size }
        // for all the local variables (not State) captured by pointerInput lambda,
        // you either use rememberUpdatedState for each of these variables or add them as parameters of "pointerInput".
        // if you do not do that, when these variables change, your lambda will still use the old values of these variables
    ) {
        val (imageRef, labelRowRef, addedLabelRowRef, editRowRef, noLabelRef) = createRefs()
        CustomImageLayout(
            originalWidth = originalImageSize.first,
            originalHeight = originalImageSize.second,
            // rect and labels are hidden when the page is scrolling
            imageLabelList = if (!pageScrolling) partImageLabelResult ?: emptyList() else emptyList(),
            image = @Composable {
                Log.d(getCallSiteInfoFunc(), "AsyncImage() is called")
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageInfo.fullImageFile)
                        // Note: to apply rotation transformation, we do not change graphicsLayer modifier,
                        // instead rotate the image by setting Coil's transformations.
                        // The original modifiers for zooming and panning image work seamlessly with rotated images
                        .transformations(
                            if (provideRotationDegree() == 0f || provideRotationDegree() == 360f) emptyList()
                            else listOf(RotateTransformation(provideRotationDegree()))
                        ).build(),
                    contentDescription = imageInfo.id.toString(),
                    modifier = Modifier.fillMaxWidth().let {
                        if (partImageLabelResult != null || wholeImageLabelResult != null) {
                            it.heightIn(max = (LocalConfiguration.current.screenHeightDp * DefaultConfiguration.IMAGE_MAX_HEIGHT_PROPORTION).dp)
                        } else it
                    },
                )
            },
            labelDrawing = @Composable { label ->
                LabelSelectionElement(
                    label = label,
                    onClick = onLabelClick,
                    modifier = Modifier,
                    initialSelected = labelSelected(label),
                    // in case there is an overlap among the labels, we can drag them
                    longPressAndDragSupport = true
                )
            },
            rectDrawing = @Composable {
                Box(
                    modifier = Modifier.background(Color.Transparent)
                        .border(width = 1.dp, color = MaterialTheme.colorScheme.primary)
                )
            },
            modifier = Modifier.constrainAs(imageRef) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }.wrapContentSize().onGloballyPositioned {
                // Note the position here does not contain zoom in/out and transition X/Y
                // it seems that LayoutCoordinate.size may not get the right image size at times, we use boundsInParent instead
                currentImageSize = it.size
            }.graphicsLayer {
                if (partImageLabelResult == null && wholeImageLabelResult == null) {
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
            }
        )
        if (!pageScrolling) {
            // null means that the user has not clicked the "labeling" button
            if (partImageLabelResult != null || wholeImageLabelResult != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.constrainAs(labelRowRef) {
                        start.linkTo(parent.start)
                        bottom.linkTo(imageRef.top)
                    }
                ) {
                    wholeImageLabelResult?.sortedBy { it.label }?.forEach { labelResult ->
                        LabelSelectionElement(
                            label = labelResult.label,
                            onClick = onLabelClick,
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.constrainAs(editRowRef) {
                        end.linkTo(parent.end)
                        bottom.linkTo(imageRef.top)
                    }
                ) {
                    ElevatedSmallIconButton(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add labels",
                        onClick = onLabelAddingClick
                    )
                    ElevatedSmallIconButton(
                        imageVector = Icons.Filled.Done,
                        contentDescription = "Submit",
                        onClick = onLabelDone
                    )
                }
                if (partImageLabelResult.isNullOrEmpty() && wholeImageLabelResult.isNullOrEmpty() && addedLabelList.isNullOrEmpty()) {
                    Text(
                        text = stringResource(R.string.no_label_found),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                        color = colorResource(R.color.colorAccent),
                        modifier = Modifier.constrainAs(noLabelRef) {
                            start.linkTo(parent.start)
                            end.linkTo(parent.end)
                            top.linkTo(imageRef.bottom)
                        }.padding(vertical = 12.dp)
                    )
                }
            }
            if (addedLabelList?.isNotEmpty() == true) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.constrainAs(noLabelRef) {
                        start.linkTo(parent.start)
                        top.linkTo(imageRef.bottom)
                    }
                ) {
                    addedLabelList.forEach { label ->
                        // add a key here to get a smother visual effect when unselecting a label
                        key(label) {
                            LabelSelectionElement(
                                label = label,
                                initialSelected = true,
                                onClick = onAddedLabelClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

// No support for Zoom in and Zoom out of an image
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SingleImagePageLabelingDone(
    imageInfo: ImageInfo,
    originalImageSize: Pair<Int, Int>,
    partImageLabelResult: List<ImageLabelResult>?,
    otherImageLabelResult: List<ImageLabelResult>?,
    modifier: Modifier = Modifier,
    pageScrolling: Boolean,
    isPreview: Boolean,
    labelAddedCacheAvailable: Boolean,
    onDismiss: () -> Unit,
) {
    ConstraintLayout(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { onDismiss() }
        }
    ) {
        val (imageRef, labelRef, resultRef) = createRefs()
        CustomImageLayout(
            originalWidth = originalImageSize.first,
            originalHeight = originalImageSize.second,
            imageLabelList = if (!pageScrolling) partImageLabelResult ?: emptyList() else emptyList(),
            image = @Composable {
                Log.d(getCallSiteInfoFunc(), "AsyncImage() is called")
                AsyncImage(
                    model = imageInfo.fullImageFile,
                    // .transformations(RotateTransformation(0f))
                    contentDescription = imageInfo.id.toString(),
                    modifier = Modifier.heightIn(
                        max = (LocalConfiguration.current.screenHeightDp * DefaultConfiguration.IMAGE_MAX_HEIGHT_PROPORTION).dp
                    )
                )
            },
            labelDrawing = @Composable { label ->
                LabelSelectionElement(
                    label = label,
                    initialSelected = true,
                    longPressAndDragSupport = true
                )
            },
            modifier = Modifier.constrainAs(imageRef) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            },
            rectDrawing = @Composable {
                Box(
                    modifier = Modifier.background(Color.Transparent)
                        .border(width = 1.dp, color = MaterialTheme.colorScheme.primary)
                )
            },
            placingStrategyWithCache = labelAddedCacheAvailable
        )
        if (!pageScrolling) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.constrainAs(labelRef) {
                    start.linkTo(parent.start)
                    bottom.linkTo(imageRef.top)
                }
            ) {
                otherImageLabelResult?.sortedBy { it.label }?.forEach { imageLabelResult ->
                    LabelSelectionElement(
                        label = imageLabelResult.label,
                        initialSelected = true,
                    )
                }
            }
            Text(
                text = if (partImageLabelResult.isNullOrEmpty() && otherImageLabelResult.isNullOrEmpty()) {
                    stringResource(R.string.no_label) + "!"
                } else {
                    if (!isPreview) stringResource(R.string.done) + "!" else ""
                },
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                color = colorResource(R.color.colorAccent),
                modifier = Modifier.constrainAs(resultRef) {
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                    top.linkTo(imageRef.bottom)
                }.padding(vertical = 12.dp)
            )
        }
    }
}

data class ZoomAnimationData(
    val left: Float = 1f,
    val right: Float = 1f,
)

data class OffsetAnimationData(
    val left: Offset = Offset.Zero,
    val right: Offset = Offset.Zero,
)

private fun IntSize.swapSize(): IntSize {
    val oldWidth = width
    val oldHeight = height
    return IntSize(oldHeight, oldWidth)
}