package com.example.image_multi_recognition.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ExifHelper {
    private val zoneId = ZoneId.systemDefault()
    private val androidTimestampFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd kk:mm:ss")
    private val dbTimestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd kk:mm:ss")

    fun timestampToLocalDataTime(timestamp: Long, timeZone: ZoneId = zoneId): LocalDateTime =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), timeZone)

    // the format for is "yyyy:MM:dd hh:mm:ss", but Java's Timestamp for sql requires "yyyy-mm-dd hh:mm:ss"
    fun getImageCreatedTime(file: File): String? = getImageCreatedTime(ExifInterface(file))
    fun getImageCreatedTime(exif: ExifInterface): String? {
        return exif.getAttribute(ExifInterface.TAG_DATETIME)?.let {
            dbTimestampFormatter.format(androidTimestampFormatter.parse(it))
        }
    }

    fun getImageSize(file: File): Pair<Int, Int> = getImageSize(ExifInterface(file))
    fun getImageSize(exif: ExifInterface): Pair<Int, Int> {
        return try {
            val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val length = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            width to length
        } catch (e: Throwable) {
            Log.e(getCallSiteInfoFunc(), e.stackTraceToString())
            0 to 0
        }
    }

    fun getImageLocation(file: File): Pair<Double, Double> = getImageLocation(ExifInterface(file))
    fun getImageLocation(exif: ExifInterface): Pair<Double, Double> {
        return try {
            val longitude = exif.getAttributeDouble(ExifInterface.LONGITUDE_EAST, 0.0)
            val latitude = exif.getAttributeDouble(ExifInterface.LATITUDE_NORTH, 0.0)
            longitude to latitude
        } catch (e: Throwable) {
            Log.e(getCallSiteInfoFunc(), e.stackTraceToString())
            invalidLocation
        }
    }

    private val invalidLocation = -1.0 to -1.0
    suspend fun Context.getImageInformation(imageFile: File): List<String> {
        return try {
            withContext(Dispatchers.IO) {
                val exif = ExifInterface(imageFile) // may throw IOException
                val createdTime = getImageCreatedTime(exif)
                val (width, height) = getImageSize(exif)
                val fileSize = (imageFile.length() / 1024).let { kb ->
                    if (kb < 1024) "$kb KB"
                    else "${kb / 1024} MB"
                }
                // val (longitude, latitude) = getImageLocation(exif)
                // Log.d(getCallSiteInfo(), "location: ($longitude, $latitude)")
                // val location = try {
                //     if (Geocoder.isPresent() && ((longitude to latitude) != invalidLocation)) {
                //         Geocoder(this@getImageInformation, Locale.getDefault()).getFromLocation(latitude, longitude, 1)
                //             ?.firstOrNull()
                //     } else null
                // } catch (e: Throwable) {
                //     Log.e(getCallSiteInfo(), e.stackTraceToString())
                //     null
                // }

                listOf(
                    imageFile.name,
                    fileSize,
                    imageFile.path,
                    "$height * $width",
                    createdTime ?: "",
                    // location?.let { getStringFromAddress(it) } ?: ""
                )
            }
        }catch (e: Throwable){
            Log.e(getCallSiteInfo(), e.stackTraceToString())
            emptyList()
        }
    }

    // private fun getStringFromAddress(address: Address): String {
    //     var resString = ""
    //     if(!address.subLocality.isNullOrEmpty()){
    //         resString += address.subLocality
    //     }
    //     if(!address.subAdminArea.isNullOrEmpty()){
    //         resString += if(resString.isNotEmpty()) ", " else "" + address.subAdminArea
    //     }
    //     if(!address.countryName.isNullOrEmpty()){
    //         resString += if(resString.isNotEmpty()) ", " else "" + address.subAdminArea
    //     }
    //     return resString
    // }

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

    fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        return Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
    }
}

// For logging
fun Any?.getCallSiteInfo(): String {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val methodName = StackWalker.getInstance().walk { frames ->
            // we do not need frames.skip(1).findFirst() here because "inline" is used
            frames.skip(1).findFirst().map { it.methodName }.orElse("MethodNameNotFound")
        }
        "${if (this != null) this::class.simpleName + "." else ""}$methodName()"
    } else "Require API 34+"
}

// call from composable function
fun getCallSiteInfoFunc(): String {
    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val methodName = StackWalker.getInstance().walk { frames ->
            // we do not need frames.skip(1).findFirst() here because "inline" is used
            frames.skip(1).findFirst().map { it.methodName }.orElse("MethodNameNotFound")
        }
        "$methodName()"
    } else "Require API 34+"
}