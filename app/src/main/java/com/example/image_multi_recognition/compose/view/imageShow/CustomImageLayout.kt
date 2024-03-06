package com.example.image_multi_recognition.compose.view.imageShow

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
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
    placingStrategyWithCache: Boolean = false,
    image: @Composable () -> Unit,
    labels: @Composable () -> Unit
) {
    Log.d(getCallSiteInfoFunc(), "Recomposition")
    Log.d(getCallSiteInfoFunc(), "ImageLabel list: $imageLabelList")
    val rectList = imageLabelList.map { it.rect!! }
    val rects = @Composable {
        rectList.forEach { _ ->
            Box(
                modifier = Modifier.background(Color.Transparent)
                    .border(width = 2.dp, color = Color.Red),
            )
        }
    }

    // custom layout handle pixel instead of dp
    Layout(
        contents = listOf(image, rects, labels),
        modifier = modifier
    ) { (imageMeasurables, rectMeasurables, labelMesurables), constrains ->
        val imagePlaceable = imageMeasurables.first().measure(constrains)
        val labelPlaceables = labelMesurables.map { it.measure(constrains) }
        val widthProportion: Double = imagePlaceable.width.toDouble() / originalWidth
        val heightProportion: Double = imagePlaceable.height.toDouble() / originalHeight

        val rectPlaceables = rectMeasurables.mapIndexed { index, rectMeasurable ->
            val width = ((rectList[index].width()) * widthProportion).toInt()
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
        Log.d(
            getCallSiteInfoFunc(),
            "proportion: (x, y) = (${imagePlaceable.width.toDouble() / originalWidth}, ${imagePlaceable.height.toDouble() / originalHeight})"
        )
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
                    (rectList[index].left * widthProportion).toInt(),
                    (rectList[index].top * heightProportion).toInt()
                )
            }
            if (labelPlaceables.isNotEmpty()) {
                val places = if(!placingStrategyWithCache) {
                    CachedLabelPlacingStrategy.placeLabels(
                        imageLabelList.map { it.label },
                        imageLabelList.map { it.rect!! }
                    )
                }else{
                    CachedLabelPlacingStrategy.placeLabels(imageLabelList.map { it.label })
                }
                val convertedPlaces = LabelPlacingStrategy.convertPlacingResult(
                    placingResultList = places,
                    labelWidth = labelPlaceables.map { it.width },
                    labelHeight = labelPlaceables.map { it.height },
                    widthProportion = widthProportion,
                    heightProportion = heightProportion,
                    boundaryWidth = imagePlaceable.width,
                    boundaryHeight = imagePlaceable.height
                )
                Log.d(getCallSiteInfoFunc(), "placing strategy: $places.toString()")
                labelPlaceables.forEachIndexed { index, labelPlaceable ->
                    labelPlaceable.place(convertedPlaces[index].first, convertedPlaces[index].second)
                }
            }
        }
    }
}
