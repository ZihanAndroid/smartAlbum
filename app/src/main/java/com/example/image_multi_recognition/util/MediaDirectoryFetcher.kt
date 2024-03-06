package com.example.image_multi_recognition.util

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.image_multi_recognition.DefaultConfiguration
import java.io.File

object MediaDirectoryFetcher {

    // get all the directories that contain at least one image file in external storage, sorted by directory name
    fun Context.getAllImageDir(): List<File> {
        // https://developer.android.com/about/versions/14/changes/partial-photo-video-access
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Query all the device storage volumes instead of the primary only
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        // https://developer.android.com/training/data-storage/shared/media#data-column
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val selection =
            "${MediaStore.Images.Media.MIME_TYPE} LIKE 'image/%'"
        val dirSet = mutableSetOf<String>()
        return try {
            contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                val absolutePathColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                while (cursor.moveToNext()) {
                    val absolutePath = File(cursor.getString(absolutePathColumn))
                    // Log.d(getCallSiteInfoFunc(), "absolute path: $absolutePath")
                    absolutePath.parent?.let { newDir ->
                        if (newDir !in dirSet) {
                            dirSet.add(newDir)
                        }
                    }
                }
            }
            dirSet.map { File(it) }.sortedBy { it.name }.apply {
                Log.d(getCallSiteInfoFunc(), "Album directories:\n${joinToString("\n")}")
            }
        } catch (e: Throwable) {
            Log.e(getCallSiteInfoFunc(), e.stackTraceToString())
            emptyList()
        }
    }

    fun Context.getAllVideoDir(): List<File> {
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        // https://developer.android.com/training/data-storage/shared/media#data-column
        val projection = arrayOf(MediaStore.Video.Media.DATA)
        val selection =
            "${MediaStore.Video.Media.MIME_TYPE} LIKE 'video/%'"

        val dirSet = mutableSetOf<String>()

        return try {
            contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                val absolutePathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                while (cursor.moveToNext()) {
                    val absolutePath = File(cursor.getString(absolutePathColumn))
                    absolutePath.parent?.let {
                        if (it !in dirSet) {
                            dirSet.add(it)
                        }
                    }
                }
            }
            dirSet.map { File(it) }.sortedBy { it.name }
        } catch (e: Throwable) {
            Log.e(getCallSiteInfoFunc(), e.stackTraceToString())
            emptyList()
        }
    }
}