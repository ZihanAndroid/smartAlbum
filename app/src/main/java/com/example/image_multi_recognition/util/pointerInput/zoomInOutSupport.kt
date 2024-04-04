package com.example.image_multi_recognition.util.pointerInput

import android.util.Log
import androidx.compose.animation.core.AnimationVector3D
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import kotlin.math.PI
import kotlin.math.abs

// double click and pinch to zoom in/out
fun Modifier.doubleClickZoomSupport(
    currentParentSize: IntSize,
    currentImageSize: IntSize,
    zoomState: State<Float>,
    offsetState: State<Offset>,
    minimalZoom: Float = 2f,
    shouldRun: () -> Boolean = { true },
    onSingleTap: ((Offset) -> Unit)? = null,
    provideNewValues: (newZoom: Float, newOffset: Offset, newTransformOrigin: TransformOrigin?, offsetRemained: Offset) -> Unit,
) = this.pointerInput(currentParentSize, currentImageSize) {
    // the reason to use zoomState and offsetState here is to get the latest value of zoom,
    // and we do not put them into keys of  pointerInput(keys)
    // because these value can change very frequently when user move and zoom the image,
    // and each of the zoom change can cause a rebuild to this pointerInput(..., zoom, offset) modifier which is unnecessary
    // passing a State directly solves this problem
    detectTapGestures(
        // double tap to zoom in or zoom out
        // tapOffset is related to the Composable that the pointerInput is applied (like parent container)
        onDoubleTap = { tapOffset ->
            if (!shouldRun()) {
                return@detectTapGestures
            }
            val zoom by zoomState
            val offset by offsetState
            val proportion = currentParentSize.height.toFloat() / currentImageSize.height
            val thresholdZoom = if (proportion < minimalZoom) minimalZoom else proportion
            val ty = (currentParentSize.height - currentImageSize.height) / 2f
            var newTransformOrigin: TransformOrigin? = null
            var offsetRemained: Offset? = null
            var newZoom = 1f
            if (zoom == 1f || offset == Offset.Zero) {
                // offset == Offset.Zero: no transformation after previous double click, we can just reproduce previous animation
                // if (!transformByAnimation) transformByAnimation = true
                // note the transformOrigin and offset is related to the coordinate of the composable that the modifier "graphicsLayer" is applied to, like CustomLayout)
                // while tapOffset is related to the composable that the coordinate of the composable
                // that the current modifier "doubleClickZoomSupport" is applied to (like ConstrainLayout)
                if (zoom == 1f) {
                    newTransformOrigin = TransformOrigin(
                        pivotFractionX = tapOffset.x / currentParentSize.width,
                        pivotFractionY = if (proportion < 2f) {
                            (tapOffset.y.coerceIn(
                                minimumValue = (currentParentSize.height - currentParentSize.height / thresholdZoom) / 2f,
                                maximumValue = (currentParentSize.height + currentParentSize.height / thresholdZoom) / 2f
                            ) - ty).let { margin ->
                                if(margin < 0) 0.5f
                                else margin / currentImageSize.height
                            }
                        } else 0.5f
                    )
                }
                newZoom = if (zoom == 1f) thresholdZoom else 1f
            } else if (offset != Offset.Zero) {
                // recover original image size

                val equivalentXInImage = (tapOffset.x - offset.x) / zoom
                val equivalentYInImage = (tapOffset.y - offset.y - ty) / zoom
                newTransformOrigin = TransformOrigin(
                    pivotFractionX = equivalentXInImage / currentImageSize.width,
                    pivotFractionY = equivalentYInImage / currentImageSize.height
                )
                offsetRemained = Offset(
                    x = tapOffset.x - equivalentXInImage,
                    y = -(ty - tapOffset.y + equivalentYInImage)
                )
                Log.d("", "calculated value: ${offsetRemained.x}, ${offsetRemained.y}")
                Log.d(getCallSiteInfoFunc(), "offsetRemained: $offsetRemained")
                // Failed to make an appropriate animation to recover its size, skip the animation!
                newZoom = 1f
            }
            provideNewValues(newZoom, Offset.Zero, newTransformOrigin, offsetRemained ?: Offset.Zero)
        },
        onTap = onSingleTap
    )
}


fun Modifier.pinchZoomAndPanMoveSupport(
    currentParentSize: IntSize,
    currentImageSize: IntSize,
    zoomState: State<Float>,
    offsetState: State<Offset>,
    transformOriginState: State<TransformOrigin>,
    shouldRun: () -> Boolean = { true },
    shouldEventConsumedForShouldRunFalse: () -> Boolean = { false },
    provideNewValues: (newZoom: Float, newOffset: Offset, newTransformOrigin: TransformOrigin?) -> Unit,
) = this.pointerInput(currentParentSize, currentImageSize) {
    detectTransformGesturesWithConsumeChoice { centroid, pan, gestureZoom, _, onEventConsume ->
        val transformOrigin by transformOriginState
        val zoom by zoomState
        var offset = offsetState.value
        var newTransformOrigin: TransformOrigin? = null
        if (!shouldRun()) {
            // another task (like animation) is ongoing,
            // we consume the event to prevent the user trigger other pointer events during that task running
            if (shouldEventConsumedForShouldRunFalse()) onEventConsume()
            false
        }
        // Offset may be Unspecified, and when you call offset.x to an Unspecified you get an exception
        else if (centroid == Offset.Unspecified || pan == Offset.Unspecified) true// return@detectTransformGesturesWithoutConsume
        else if (!(currentParentSize.height > 0 && currentImageSize.height > 0)) true // return@detectTransformGesturesWithoutConsume
        else {
            if (transformOrigin.pivotFractionX != 0f || transformOrigin.pivotFractionY != 0f) {
                // convert offset by the preview TransformOrigin
                offset = Offset(
                    x = currentImageSize.width * transformOrigin.pivotFractionX * (1 - zoom),
                    y = currentImageSize.height * transformOrigin.pivotFractionY * (1 - zoom)
                )
                newTransformOrigin = TransformOrigin(0f, 0f)
            }
            var xBoundaryReached = false
            val thresholdZoom = currentParentSize.height.toFloat() / currentImageSize.height
            val newZoom = (zoom * gestureZoom).coerceAtLeast(1.0f)
            // Log.d(
            //     getCallSiteInfoFunc(),
            //     "size: $size, centroid: [$centroid], pan: [$pan], gestureZoom: [$gestureZoom], threshold reached: [${zoom > thresholdZoom}]"
            // )
            // Log.d(
            //     getCallSiteInfoFunc(),
            //     "currentParentSize.height & currentImageSize.height: ${currentParentSize.height} &  ${currentImageSize.height}"
            // )
            // my own algorithm for zoom in/out in a UI container which contains an image
            val newOffset = Offset(
                x = if (gestureZoom == 1f) {
                    pan.x + offset.x
                } else {
                    (1 - gestureZoom) * (centroid.x - offset.x) + offset.x
                }.let { value ->
                    val leftBoundary = (1 - newZoom) * currentImageSize.width
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
                provideNewValues(newZoom, newOffset, newTransformOrigin)
                // consume the event so that the pager will not handle this pointerInput
                onEventConsume()
                true
            } else {
                provideNewValues(newZoom, newOffset, newTransformOrigin)
                // keep on tracking the following events,
                // in case the user leave boundary so that the modifier can consume the events again during the same gesture
                true
            }
        }
    }
}

// add some customization to detectTransformGestures()
suspend fun PointerInputScope.detectTransformGesturesWithConsumeChoice(
    panZoomLock: Boolean = false,
    // onGesture() returns a Boolean to indicate whether we need to call onGesture for further pointer event
    onGestureFinished: () -> Unit = {},
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float, onEventConsume: () -> Unit) -> Boolean,
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        var lockedToPanZoom = false
        var cancelFurtherOnGesture = false

        awaitFirstDown(requireUnconsumed = false)

        do {
            val event = awaitPointerEvent()
            // just calling awaitPointerEvent() repeatedly to wait for this gesture finished
            val canceled = event.changes.fastAny { it.isConsumed }
            if (!canceled) {
                if (cancelFurtherOnGesture) continue
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation * PI.toFloat() * centroidSize / 180f)
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop ||
                        rotationMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                        lockedToPanZoom = panZoomLock && rotationMotion < touchSlop
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val effectiveRotation = if (lockedToPanZoom) 0f else rotationChange
                    if (effectiveRotation != 0f ||
                        zoomChange != 1f ||
                        panChange != Offset.Zero
                    ) {
                        if (!onGesture(centroid, panChange, zoomChange, effectiveRotation) {
                                // let the caller decides whether to consume the event or not
                                event.changes.fastForEach {
                                    if (it.positionChanged()) {
                                        it.consume()
                                    }
                                }
                            }) {
                            cancelFurtherOnGesture = true
                        }
                    }
                }
            }
        } while (!canceled && event.changes.fastAny { it.pressed })
        onGestureFinished()
    }
}

data class ZoomOffsetData(
    val zoom: Float = 1f,
    val offset: Offset = Offset.Zero,
    val rotation: Float = 0f
) {
    companion object {
        val VectorConverter = TwoWayConverter<ZoomOffsetData, AnimationVector3D>(
            convertFromVector = {
                ZoomOffsetData(it.v1, Offset(it.v2, it.v3))
            },
            convertToVector = {
                AnimationVector3D(it.zoom, it.offset.x, it.offset.y)
            }
        )
    }
}
