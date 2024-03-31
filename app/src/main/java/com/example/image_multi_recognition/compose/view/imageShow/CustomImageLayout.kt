package com.example.image_multi_recognition.compose.view.imageShow

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import com.example.image_multi_recognition.util.CachedLabelPlacingStrategy
import com.example.image_multi_recognition.util.LabelPlacingStrategy
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.viewmodel.ImageLabelResult

@Composable
fun CustomImageLayout(
    originalWidth: Int,
    originalHeight: Int,
    imageLabelList: List<ImageLabelResult>,
    modifier: Modifier = Modifier,
    placingStrategyWithCache: Boolean = false,  // set this value to false if it is your first time to show image label
    image: @Composable () -> Unit,
    labelDrawing: @Composable (String) -> Unit,
    rectDrawing: @Composable () -> Unit,
) {
    Log.d(getCallSiteInfoFunc(), "Recomposition")
    Log.d(getCallSiteInfoFunc(), "ImageLabel list: $imageLabelList")
    val rectList = imageLabelList.map { it.rect!! }
    val rects = @Composable {
        rectList.forEach { _ ->
            rectDrawing()
        }
    }
    val labels = @Composable {
        imageLabelList.forEach { labelResult ->
            labelDrawing(labelResult.label)
        }
    }
    // val rects = @Composable {
    //     rectList.forEach { _ ->
    //         Box(
    //             modifier = Modifier.background(Color.Transparent)
    //                 .border(width = 2.dp, color = Color.Red),
    //         )
    //     }
    // }
    Log.d(getCallSiteInfoFunc(), "original size: ($originalWidth, $originalHeight)")
    Log.d(getCallSiteInfoFunc(), "rect: ${rectList.joinToString()}")
    // custom layout handle pixel instead of dp
    Layout(
        contents = listOf(image, rects, labels),
        modifier = modifier
    ) { (imageMeasurables, rectMeasurables, labelMesurables), constrains ->
        val imagePlaceable = imageMeasurables.first().measure(constrains)
        val labelPlaceables = labelMesurables.map { it.measure(constrains) }
        val heightProportion: Double = imagePlaceable.height.toDouble() / originalHeight
        // intrinsic width
        val intrinsicWidth = heightProportion * originalWidth
        // val widthProportion: Double = imagePlaceable.width.toDouble() / originalWidth
        val widthProportionNew = intrinsicWidth / originalWidth

        val rectPlaceables = rectMeasurables.mapIndexed { index, rectMeasurable ->
            val width = ((rectList[index].width()) * widthProportionNew).toInt()
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
        // Note that when we apply heightIn() modifier, we only restrict the height of the imagePlaceable
        Log.d(
            getCallSiteInfoFunc(),
            "proportion: (x, y) = (${imagePlaceable.width.toDouble() / originalWidth}, ${imagePlaceable.height.toDouble() / originalHeight})"
        )
        // bias caused by applying heightIn() to calculate intrinsic width
        val halfXBias = (imagePlaceable.width - intrinsicWidth) / 2.0
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
                    (rectList[index].left * widthProportionNew + halfXBias).toInt(),
                    (rectList[index].top * heightProportion).toInt()
                )
            }
            if (labelPlaceables.isNotEmpty()) {
                val places = if (!placingStrategyWithCache) {
                    CachedLabelPlacingStrategy.clearLabels()
                    CachedLabelPlacingStrategy.placeLabels(
                        imageLabelList.map { it.label },
                        imageLabelList.map { it.rect!! }
                    )
                } else {
                    CachedLabelPlacingStrategy.placeLabels(imageLabelList.map { it.label })
                }
                val convertedPlaces = LabelPlacingStrategy.convertPlacingResult(
                    placingResultList = places,
                    labelWidth = labelPlaceables.map { it.width },
                    labelHeight = labelPlaceables.map { it.height },
                    widthProportion = widthProportionNew,
                    heightProportion = heightProportion,
                    boundaryWidth = imagePlaceable.width,
                    boundaryHeight = imagePlaceable.height
                )
                Log.d(getCallSiteInfoFunc(), "placing strategy: $places.toString()")
                labelPlaceables.forEachIndexed { index, labelPlaceable ->
                    labelPlaceable.place(
                        // support heightIn() modifier
                        // (imagePlaceable.width * (widthProportionBefore - widthProportion) / 2.0 - convertedPlaces[index].first * (widthProportion / widthProportionBefore)).toInt(),
                        x = (convertedPlaces[index].first + halfXBias).toInt(),
                        // convertedPlaces[index].first
                        y = convertedPlaces[index].second
                    )
                }
            }
        }
    }
}
