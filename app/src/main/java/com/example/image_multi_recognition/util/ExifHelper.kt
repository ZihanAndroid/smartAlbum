package com.example.image_multi_recognition.util

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.compose.navigation.Destination
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ExifHelper {
    private val zoneId = ZoneId.of("UTC")
    private val androidTimestampFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd kk:mm:ss")
    private val dbTimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss")

    fun timestampToLocalDataTime(timestamp: Long): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zoneId)

    // the format for is "yyyy:MM:dd hh:mm:ss", but Java's Timestamp for sql requires "yyyy-mm-dd hh:mm:ss"
    fun getImageCreatedTime(file: File): String? {
        return ExifInterface(file).getAttribute(ExifInterface.TAG_DATETIME)?.let {
            //Log.d(getCallSiteInfo(), "converted image created time: $this")
            dbTimestampFormatter.format(androidTimestampFormatter.parse(it))
        }
    }

//    fun getImageSize(file: File): Size {
//        return try {
//            val width = ExifInterface(file).getAttributeInt(
//                ExifInterface.TAG_IMAGE_WIDTH,
//                DefaultConfiguration.DEFAULT_THUMBNAIL_SIZE
//            )
//            val length = ExifInterface(file).getAttributeInt(
//                ExifInterface.TAG_IMAGE_WIDTH,
//                DefaultConfiguration.DEFAULT_THUMBNAIL_SIZE
//            )
//            return Size(
//                DefaultConfiguration.DEFAULT_THUMBNAIL_SIZE,
//                (DefaultConfiguration.DEFAULT_THUMBNAIL_SIZE.toFloat() * length / width).toInt()
//            )
//        } catch (e: Throwable) {
//            Log.e(getCallSiteInfoFunc(), e.stackTraceToString())
//            Size(DefaultConfiguration.DEFAULT_THUMBNAIL_SIZE, DefaultConfiguration.DEFAULT_THUMBNAIL_SIZE)
//        }
//    }

    fun getImageRotationDegree(file: File): Int =
        try {
            when (ExifInterface(file).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Throwable) {
            Log.e(getCallSiteInfoFunc(), e.stackTraceToString())
            0
        }

    fun buildDestinationFromRoute(navRouteWithArgument: String?): Destination? =
        navRouteWithArgument?.let {
            Destination.valueOf(navRouteWithArgument.split("/")[0])
        }

    fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        return Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
    }
}

// For logging
fun Any?.getCallSiteInfo(): String {
    val methodName = StackWalker.getInstance().walk { frames ->
        // we do not need frames.skip(1).findFirst() here because "inline" is used
        frames.skip(1).findFirst().map { it.methodName }.orElse("MethodNameNotFound")
    }
    return "${if (this != null) this::class.simpleName + "." else ""}$methodName()"
}

// call from composable function
fun getCallSiteInfoFunc(): String {
    val methodName = StackWalker.getInstance().walk { frames ->
        // we do not need frames.skip(1).findFirst() here because "inline" is used
        frames.skip(1).findFirst().map { it.methodName }.orElse("MethodNameNotFound")
    }
    return "$methodName()"
}