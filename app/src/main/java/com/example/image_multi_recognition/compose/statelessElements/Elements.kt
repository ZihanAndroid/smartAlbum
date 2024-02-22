package com.example.image_multi_recognition.compose.statelessElements

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

// From https://stackoverflow.com/questions/65921799/how-to-convert-dp-to-pixels-in-android-jetpack-compose
@Composable
fun Dp.dpToPx(): Float = with(LocalDensity.current){
    this@dpToPx.toPx()
}

@Composable
fun Int.toDp(): Dp = with(LocalDensity.current){
    this@toDp.toDp()
}