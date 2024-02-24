package com.example.image_multi_recognition.compose.statelessElements

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// From https://stackoverflow.com/questions/65921799/how-to-convert-dp-to-pixels-in-android-jetpack-compose
@Composable
fun Dp.dpToPx(): Float = with(LocalDensity.current) {
    this@dpToPx.toPx()
}

@Composable
fun Dp.dpToPxInt(): Int = with(LocalDensity.current) {
    this@dpToPxInt.roundToPx()
}

@Composable
fun Int.toDp(): Dp = with(LocalDensity.current) {
    this@toDp.toDp()
}

// https://github.com/JetBrains/compose-multiplatform/issues/1831
// https://stackoverflow.com/questions/74238933/how-to-remove-dropdownmenus-default-vertical-padding-when-clicking-the-first-it
fun Modifier.crop(
    horizontal: Dp = 0.dp,
    vertical: Dp = 0.dp
): Modifier = this.layout { measurable, constraints ->
    // Note the MeasureScope of layout() implements Density interface, so you can directly use Dp.toPx() here
    val placeable = measurable.measure(constraints)
    // reduce the placeable size for cropping
    layout(
        width = placeable.width - (horizontal * 2).roundToPx(),
        height = placeable.height - (vertical * 2).roundToPx()
    ) {
        placeable.placeRelative(-horizontal.roundToPx(), -vertical.roundToPx())
    }
}

