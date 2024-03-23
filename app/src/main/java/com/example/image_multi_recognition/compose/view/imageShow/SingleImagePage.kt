package com.example.image_multi_recognition.compose.view.imageShow

import android.util.Log
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import coil.compose.AsyncImage
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.ElevatedSmallIconButton
import com.example.image_multi_recognition.compose.statelessElements.LabelSelectionElement
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.viewmodel.ImageLabelResult

// Support zoom in and zoom out of an image
@OptIn(ExperimentalLayoutApi::class)
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
    onDismiss: ()->Unit
) {
    var zoom by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Log.d(getCallSiteInfoFunc(), "Recomposition")
    ConstraintLayout(
        modifier = modifier.pointerInput(Unit){
            detectTapGestures { onDismiss() }
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
                    modifier = Modifier.fillMaxWidth()
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                // double tap to zoom in or zoom out
                                // equation:
                                // (offset.x - newOffsetX) / (newZoom * sizeX) = offset.x / sizeX   => newOffsetX
                                onDoubleTap = { tapOffset ->
                                    zoom = if (zoom > 1f) 1f else 2f
                                    offset = Offset(x = tapOffset.x * (1 - zoom), y = tapOffset.y * (1 - zoom))
                                }
                            )
                        }.pointerInput(Unit) {
                            // Note that you can access "size" inside the PointerInputScope to get the size of AsyncImage
                            // you can use that "size" to restrict the boundary of offset.
                            // Also note that the value centroid is corresponding to the coordinate of "size",
                            // the pointer input region without the offset and zooming
                            detectTransformGestures { centroid, pan, gestureZoom, _ ->
                                // Log.d(
                                //     getCallSiteInfo(),
                                //     "size: $size, centroid: [${centroid.x}, ${centroid.y}], pan: [${pan.x}, ${pan.y}]"
                                // )
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
                        }.graphicsLayer {
                            if (partImageLabelResult == null || wholeImageLabelResult == null) {
                                // zoom in and out only when the user clicked the labeling button
                                scaleX = zoom
                                scaleY = zoom
                                translationX = offset.x
                                translationY = offset.y
                                transformOrigin = TransformOrigin(0f, 0f)
                            }
                        }
                )
            },
            labelDrawing = @Composable { label ->
                LabelSelectionElement(
                    label = label,
                    onClick = onLabelClick,
                    modifier = Modifier
                    //     .onGloballyPositioned {
                    //     positionInParentX = it.positionInParent().x
                    //     positionInParentY = it.positionInParent().y
                    // }
                    // .graphicsLayer {
                    //     // the reason to set "positionInParentX * (zoom - oldZoom)":
                    //     // The whole transformation is based on "transformOrigin" of AsyncImage,
                    //     // and "positionInParentX * (zoom - oldZoom)" is the shift based on that transformOrigin for current label composable
                    //     // translationX = offset.x + positionInParentX * (zoom - oldZoom)
                    //     // translationY = offset.y + positionInParentY * (zoom - oldZoom)
                    // }
                )
            },
            rectDrawing = @Composable {
                Box(
                    modifier = Modifier.background(Color.Transparent).border(width = 2.dp, color = Color.Red)
                )
            },
            modifier = Modifier.constrainAs(imageRef) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.7).dp) // guarantee some space for labeling
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
                wholeImageLabelResult?.forEach { labelResult ->
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
    showHintText: Boolean = true,
    onDismiss: () -> Unit
) {
    ConstraintLayout(
        modifier = modifier.pointerInput(Unit){
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
                    contentDescription = imageInfo.id.toString()
                )
            },
            labelDrawing = @Composable { label ->
                LabelSelectionElement(
                    label = label,
                    initialSelected = true,
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
                    modifier = Modifier.background(Color.Transparent).border(width = 2.dp, color = Color.Red)
                )
            },
            placingStrategyWithCache = true
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.constrainAs(labelRef) {
                start.linkTo(parent.start)
                bottom.linkTo(imageRef.top)
            }
        ) {
            otherImageLabelResult?.forEach { imageLabelResult ->
                LabelSelectionElement(
                    label = imageLabelResult.label,
                    initialSelected = true,
                )
            }
        }
        if (showHintText) {
            Text(
                text = if (partImageLabelResult.isNullOrEmpty() && otherImageLabelResult.isNullOrEmpty()) {
                    stringResource(R.string.no_selected_label)
                } else {
                    stringResource(R.string.labeling_done)
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