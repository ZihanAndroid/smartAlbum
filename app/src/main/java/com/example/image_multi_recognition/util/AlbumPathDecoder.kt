package com.example.image_multi_recognition.util

import android.os.Environment
import android.util.Log
import com.example.image_multi_recognition.db.AlbumInfo
import java.io.File
import java.util.concurrent.atomic.AtomicReference

object AlbumPathDecoder {
    val validImageSuffix = setOf("jpeg", "jpg", "png", "gif", "tiff")
    private var albumNamePathMapRef: AtomicReference<MutableMap<Long, File>> = AtomicReference<MutableMap<Long, File>>()
    val albumNamePathMap: Map<Long, File>
        get() = albumNamePathMapRef.get()
    val albums: List<Long>
        get() = albumNamePathMap.map { it.key }

    fun initDecoder(namePathMap: Map<Long, File>) {
        albumNamePathMapRef.set(namePathMap.toMutableMap())
    }

    fun decode(album: Long): File = if (album in albumNamePathMap.keys) {
        albumNamePathMap[album]!!
    } else {
        Log.e(getCallSiteInfoFunc(), "Not recognized album: $album")
        // throw RuntimeException("Not recognized album: [$album]")
        File("")
    }

    // directory name is the album name shown to users
    fun decodeAlbumName(album: Long?): String =
        albumNamePathMap[album]?.name ?: "".apply {
            Log.e(getCallSiteInfoFunc(), "Not recognized album: $album")
        }

    fun addAlbum(albumInfo: AlbumInfo){
        albumNamePathMapRef.get()[albumInfo.album] = File(albumInfo.path)
    }

    fun removeAlbum(album: Long){
        albumNamePathMapRef.get().remove(album)
    }
}