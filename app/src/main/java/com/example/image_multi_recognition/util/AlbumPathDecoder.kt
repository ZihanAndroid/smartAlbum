package com.example.image_multi_recognition.util

import android.os.Environment
import android.util.Log
import java.io.File

object AlbumPathDecoder {
    val validImageSuffix = setOf("jpeg", "jpg", "png", "gif", "tiff")
    lateinit var albumNamePathMap: Map<Long, File>
    val albums: List<Long>
        get() = albumNamePathMap.map { it.key }


    fun initDecoder(namePathMap: Map<Long, File>){
        albumNamePathMap = namePathMap
    }

    fun decode(album: Long): File = if(album in albumNamePathMap.keys) {
        albumNamePathMap[album]!!
    }else{
        Log.e(getCallSiteInfoFunc(), "Not recognized album: $album")
        throw RuntimeException("Not recognized album: [$album]")
    }
    // directory name is the album name shown to users
    fun decodeAlbumName(album: Long?): String =
        albumNamePathMap[album]?.name ?: "".apply {
            Log.e(getCallSiteInfoFunc(), "Not recognized album: $album")
        }

//    fun decode(album: String): File = when (album) {
//        Environment.DIRECTORY_PICTURES -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
//        //... other items
//        else -> File(album)
//    }

    // When you add a new album, check whether that album is already a subdirectory of other albums,
    // if so, just create a view to the existing album and do not insert duplicate data into the DB.
    // Otherwise, insert the image data into DB and create a new album
//    fun checkAlbumContained(albums: List<Long>, newAlbumPath: File) =
//        albums.map { decode(it) }.any { newAlbumPath.startsWith(it) }

}