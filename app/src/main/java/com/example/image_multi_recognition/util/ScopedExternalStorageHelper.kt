package com.example.image_multi_recognition.util

import android.content.Context
import android.os.Environment
import java.io.File

// app-specific external storage
object ScopedThumbNailStorage {
    lateinit var imageStorage: File
    const val dirName = ".thumbnailsOfApp"

    //https://developer.android.com/training/data-storage/app-specific#external-verify-availability
    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    // Checks if a volume containing external storage is available to at least read.
    private fun isExternalStorageReadable(): Boolean {
        return Environment.getExternalStorageState() in
                setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
    }

    // initialize external imageStorage and check availability
    private fun Context.isExternalStorageAvailable(): Boolean {
        imageStorage = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), dirName)
        return if (isExternalStorageWritable()) {
            if (!imageStorage.exists()) {
                imageStorage.mkdir()
            } else true
        } else false
    }

    // initialize imageStorage and check availability, call this method in MainActivity
    fun Context.setupScopedStorage(): Boolean {
        return if (!isExternalStorageAvailable()) {
            // use internal storage instead
            imageStorage = File(filesDir, dirName)
            if (!imageStorage.exists()) {
                imageStorage.mkdir()
            } else true
        } else true
    }
}