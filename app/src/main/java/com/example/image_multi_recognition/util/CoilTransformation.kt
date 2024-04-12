package com.example.image_multi_recognition.util

import android.graphics.Bitmap
import android.graphics.Matrix
import coil.size.Size
import coil.transform.Transformation

data class RotateTransformation(val rotationDegree: Float) : Transformation{
    override val cacheKey: String = "${javaClass.name}-$rotationDegree"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        // rotation: 0 -> 90 -> 180 -> 270 -> 360 (360 > 0, do not go back to 0)
        return if(rotationDegree > 0f) Matrix().let {
            it.postRotate(rotationDegree)
            if(rotationDegree == 90f || rotationDegree == 270f){
                it.postScale(input.width.toFloat() / input.height, input.width.toFloat() / input.height)
            }
            Bitmap.createBitmap(input, 0, 0, input.width, input.height, it, true)
        } else input
    }
}