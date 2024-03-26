package com.example.image_multi_recognition.compose.view.imageShow

import android.util.Log
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.constraintlayout.compose.ConstraintLayout
import coil.compose.AsyncImage
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.ElevatedSmallIconButton
import com.example.image_multi_recognition.compose.statelessElements.LabelSelectionElement
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.util.detectTransformGesturesWithoutConsume
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.viewmodel.ImageLabelResult

// Support zoom in and zoom out for an image
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun SingleImagePage(
    imageInfo: ImageInfo,
    partImageLabelResult: List<ImageLabelResult>?,
    wholeImageLabelResult: List<ImageLabelResult>?,
    addedLabelList: List<String>?,
    originalImageSize: Pair<Int, Int>,
    modifier: Modifier = Modifier,
    onLabelClick: (String, Boolean) -> Unit,
    onLabelDone: () -> Unit,
    onLabelAddingClick: () -> Unit,
    onAddedLabelClick: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    pageScrolling: Boolean
) {
    var toggled by remember { mutableStateOf(false) }

    var zoom by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // animation
    var transformByAnimation by remember { mutableStateOf(false) }
    val derivedTransformByAnimation by remember { derivedStateOf { transformByAnimation } }
    var rememberedTransformOrigin by remember { mutableStateOf(TransformOrigin.Center) }
    var imagePositionYInParent = remember { 0f }
    // val transformByAnimationDerived by remember { derivedStateOf { transformByAnimation } }
    val doubleClickTransition = updateTransition(
        targetState = toggled,
        label = "doubleClickAnimation"
    )
    var zoomLeft by remember { mutableFloatStateOf(1f) }
    var zoomRight by remember { mutableFloatStateOf(1f) }
    // var offsetLeft by remember { mutableStateOf(Offset.Zero) }
    // var offsetRight by remember { mutableStateOf(Offset.Zero) }
    val zoomAnimated by doubleClickTransition.animateFloat(
        label = "animateZoom",
        transitionSpec = { spring(stiffness = Spring.StiffnessLow) }
    ) {
        if (it) zoomLeft else zoomRight
    }

    var currentImageSize_ by remember { mutableStateOf(IntSize.Zero) }
    var currentParentSize_ by remember { mutableStateOf(IntSize.Zero) }
    val currentImageSize by rememberUpdatedState(currentImageSize_)
    val currentParentSize by rememberUpdatedState(currentParentSize_)
    val derivedPageSrcolling by rememberUpdatedState(pageScrolling)

    Log.d(getCallSiteInfoFunc(), "Recomposition")
    ConstraintLayout(
        modifier = modifier.fillMaxSize().clipToBounds()
            //     .pointerInput(Unit) {
            //     detectTapGestures { onDismiss() }
            // }
            // recover it !!!!!!!!!!!!!!!!!!
            .pointerInput(Unit) {
                detectTapGestures(
                    // double tap to zoom in or zoom out
                    // equation:
                    // (offset.x - newOffsetX) / (newZoom * sizeX) = offset.x / sizeX   => newOffsetX
                    onDoubleTap = { tapOffset ->
                        if (derivedPageSrcolling) return@detectTapGestures
                        if (!transformByAnimation) {
                            transformByAnimation = true
                        }
                        val prevZoom = zoom
                        val thresholdZoom = currentParentSize.height.toFloat() / currentImageSize.height
                        zoom = if (zoom > 1f) 1f else thresholdZoom
                        // offset = if (tapOffset.y in imagePositionYInParent..imageBottomYInParent) {
                        //     Offset(x = tapOffset.x * (1 - zoom), y = (tapOffset.y - imagePositionYInParent) * (1 - zoom))
                        // } else {
                        //     // as if you tap at the x = x, y = middle y of the image
                        //     Offset(
                        //         x = tapOffset.x * (1 - zoom),
                        //         y = (imageBottomYInParent - imagePositionYInParent) / 2f * (1 - zoom)
                        //     )
                        // }
                        // if (!transformByAnimation) transformByAnimation = true
                        // if (!toggled) {
                        // note we do not change the transform origin if the image is already zoomed out
                        // because we want the same transform origin to zoom in back
                        if (prevZoom == 1f) {
                            rememberedTransformOrigin = TransformOrigin(tapOffset.x / currentImageSize.width, 0.5f)
                        } else if (offset != Offset.Zero) {
                            // convert from the other pointerInput modifier
                            rememberedTransformOrigin = TransformOrigin(
                                tapOffset.x / currentImageSize.width,
                                tapOffset.y / currentImageSize.height
                            )
                        }
                        // set different endian based on toggled value
                        if (toggled) {
                            // left value is the previews value
                            zoomRight = zoom
                            // previous zoom may have been changed because of user's pinch gesture
                            zoomLeft = prevZoom
                            toggled = false
                        } else {
                            zoomLeft = zoom
                            zoomRight = prevZoom
                            toggled = true  // trigger recomposition
                        }
                    },
                )
            }
            .onGloballyPositioned { currentParentSize_ = it.size }
            // for all the local variables (not State) captured by pointerInput lambda,
            // you either use rememberUpdatedState for each of these variables or add them as parameters of "pointerInput".
            // if you do not do that, when these variables change, your lambda will still use the old values of these variables
            .pointerInput(Unit) {
                detectTransformGesturesWithoutConsume { centroid, pan, gestureZoom, _, onEventConsume ->
                    // Offset may be Unspecified, and when you call offset.x to an Unspecified you get an exception
                    if (derivedPageSrcolling) false
                    else if (centroid == Offset.Unspecified || pan == Offset.Unspecified) true// return@detectTransformGesturesWithoutConsume
                    // Log.d(
                    //     getCallSiteInfoFunc(),
                    //     "currentImageSize: $currentImageSize, currentParentSize: $currentParentSize"
                    // )
                    else if (!(currentParentSize.height > 0 && currentImageSize.height > 0 && currentParentSize != currentImageSize)) true // return@detectTransformGesturesWithoutConsume
                    else {
                        if (transformByAnimation) {
                            transformByAnimation = false
                            // convert offset by the preview TransformOrigin
                            offset = Offset(
                                x = currentImageSize.width * rememberedTransformOrigin.pivotFractionX * (1 - zoom),
                                y = currentImageSize.height * rememberedTransformOrigin.pivotFractionY * (1 - zoom)
                            )
                        }
                        Log.d(getCallSiteInfoFunc(), "trans here")
                        var xBoundaryReached = false
                        // val thresholdZoom = currentParentSize.height.toFloat() / currentImageSize.height
                        val thresholdZoom = currentParentSize.height.toFloat() / currentImageSize.height
                        val newZoom = (zoom * gestureZoom).coerceAtLeast(1.0f)

                        Log.d(
                            getCallSiteInfo(),
                            "size: $size, centroid: [$centroid], pan: [$pan], gestureZoom: [$gestureZoom], threshold reached: [${zoom > thresholdZoom}]"
                        )
                        Log.d(
                            getCallSiteInfoFunc(),
                            "currentParentSize.height & currentImageSize.height: ${currentParentSize.height} &  ${currentImageSize.height}"
                        )
                        // my own algorithm for zoom in/out in a UI container which contains an image
                        val newOffset = Offset(
                            x = if (gestureZoom == 1f) {
                                pan.x + offset.x
                            } else {
                                (1 - gestureZoom) * (centroid.x - offset.x) + offset.x
                            }.let { value ->
                                val leftBoundary = (1 - newZoom) * currentImageSize.width
                                Log.d(
                                    getCallSiteInfoFunc(),
                                    "x value before coerce: $value, leftBoundary: $leftBoundary"
                                )
                                value.coerceIn(leftBoundary, 0f).apply {
                                    xBoundaryReached = this == leftBoundary || this == 0f
                                }
                            },
                            y = if (newZoom <= thresholdZoom) {
                                (1 - newZoom) * currentImageSize.height / 2f
                            } else {
                                if (gestureZoom == 1f) {
                                    (pan.y + offset.y)
                                } else {
                                    ((1 - gestureZoom) * (centroid.y - offset.y - (thresholdZoom - 1f) * currentImageSize.height / 2f)) + offset.y
                                }.coerceIn(
                                    -currentImageSize.height * (newZoom - thresholdZoom + (thresholdZoom - 1f) / 2f),
                                    -currentImageSize.height * ((thresholdZoom - 1f) / 2f)
                                )
                            }
                        )
                        // whenever xBoundaryReached, we start allowing to move to the next page.
                        // and we do not provide the newOffset to "offset" so that the offset for sliding into the next page
                        // can be calculated by Pager independently without the interference of the newOffset here
                        if (!xBoundaryReached) {
                            zoom = newZoom
                            offset = newOffset
                            onEventConsume()
                            // consume the event so that the pager will not handle this pointerInput
                            // event.changes.forEach { it.consume() }
                            // firstEvent.consume()
                            true
                        } else {
                            // onScrollToNext(xBoundaryReached > 0){
                            //     scrollToNextStarted = false
                            // }
                            zoom = newZoom
                            offset = newOffset
                            // onPageScrolling(true)
                            // skip the remaining pointer event in "detectTransformGesturesWithoutConsume"
                            // to avoid the interference with pager's scrolling
                            true
                        }
                    }
                }
            }
    ) {
        val (imageRef, labelRowRef, addedLabelRowRef, editRowRef, noLabelRef) = createRefs()
        CustomImageLayout(
            originalWidth = originalImageSize.first,
            originalHeight = originalImageSize.second,
            imageLabelList = partImageLabelResult ?: emptyList(),
            image = @Composable {
                Log.d(getCallSiteInfoFunc(), "AsyncImage() is called")
                AsyncImage(
                    model = imageInfo.fullImageFile,
                    contentDescription = imageInfo.id.toString(),
                    modifier = Modifier.fillMaxWidth().let {
                        if (partImageLabelResult != null || wholeImageLabelResult != null) {
                            it.heightIn(max = (LocalConfiguration.current.screenHeightDp * DefaultConfiguration.IMAGE_MAX_HEIGHT_PROPORTION).dp)
                        } else it
                    }.graphicsLayer {
                        if (partImageLabelResult == null && wholeImageLabelResult == null) {
                            if (derivedTransformByAnimation) {
                                scaleX = zoomAnimated
                                scaleY = zoomAnimated
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
            },
            labelDrawing = @Composable { label ->
                LabelSelectionElement(
                    label = label,
                    onClick = onLabelClick,
                    modifier = Modifier,
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
                // if (isFirstImageShow) {
                //     initialImagePositionYInParent = it.positionInParent().y
                //     initialImagePositionYInParent = it.positionInParent().x
                //     isFirstImageShow = false
                // }
                // Note the position here does not contain zoom in/out and transition X/Y
                // it seems that LayoutCoordinate.size may not get the right image size at times, we use boundsInParent instead
                // currentImageSize = it.size
                currentImageSize_ = it.size
                imagePositionYInParent = it.positionInParent().y
                Log.d(getCallSiteInfoFunc(), "position to parent: ${it.positionInParent()}")
                // Log.d(getCallSiteInfoFunc(), "currentImageSize: ${currentImageSize_}")
                Log.d(getCallSiteInfoFunc(), "photo offset: ${it.boundsInParent()}")
            }
        )
        // null means that the user has not clicked the "label" button
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
                    onClick = onLabelAddingClick,
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

// No support for Zoom in and Zoom out of an image
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SingleImagePageLabelingDone(
    imageInfo: ImageInfo,
    originalImageSize: Pair<Int, Int>,
    partImageLabelResult: List<ImageLabelResult>?,
    otherImageLabelResult: List<ImageLabelResult>?,
    modifier: Modifier = Modifier,
    isPreview: Boolean,
    onDismiss: () -> Unit
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
            imageLabelList = partImageLabelResult ?: emptyList(),
            image = @Composable {
                Log.d(getCallSiteInfoFunc(), "AsyncImage() is called")
                AsyncImage(
                    model = imageInfo.fullImageFile,
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
            placingStrategyWithCache = !isPreview
        )
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
