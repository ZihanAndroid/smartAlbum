package com.example.image_multi_recognition.util

import android.os.Environment
import java.io.File

object AlbumPathDecoder {
    val validImageSuffix = setOf("jpeg", "jpg", "png", "gif", "tiff")

    fun decode(album: String): File = when (album) {
        Environment.DIRECTORY_PICTURES -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        //... other items
        else -> File(album)
    }

    // When you add a new album, check whether that album is already a subdirectory of other albums,
    // if so, just create a view to the existing album and do not insert duplicate data into the DB.
    // Otherwise, insert the image data into DB and create a new album
    fun checkAlbumContained(albums: List<String>, newAlbumPath: File) =
        albums.map { decode(it) }.any { newAlbumPath.startsWith(it) }

}