package com.example.image_multi_recognition.compose.view.imageShow

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
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
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.viewmodel.ImageLabelResult

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
) {
    Log.d(getCallSiteInfoFunc(), "Recomposition")
    ConstraintLayout(
        modifier = modifier
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
                    contentDescription = imageInfo.id.toString()
                )
            },
            labels = @Composable {
                partImageLabelResult?.forEach { imageLabelResult ->
                    LabelSelectionElement(
                        label = imageLabelResult.label,
                        onClick = onLabelClick,
                    )
                }
            },
            modifier = Modifier.constrainAs(imageRef) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9).dp) // guarantee some space for labeling
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SingleImagePageLabelingDone(
    imageInfo: ImageInfo,
    originalImageSize: Pair<Int, Int>,
    partImageLabelResult: List<ImageLabelResult>?,
    otherImageLabelResult: List<ImageLabelResult>?,
    showHintText: Boolean = true,
    modifier: Modifier = Modifier
) {
    ConstraintLayout(
        modifier = modifier
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
            labels = @Composable {
                partImageLabelResult?.forEach { imageLabelResult ->
                    LabelSelectionElement(label = imageLabelResult.label)
                }
            },
            modifier = Modifier.constrainAs(imageRef) {
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
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
                )
            }
        }
        if(showHintText) {
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