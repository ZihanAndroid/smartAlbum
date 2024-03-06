package com.example.image_multi_recognition.db

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query

@Dao
interface AlbumInfoDao : BaseDao<AlbumInfo>{
    @Query("""SELECT * FROM album_info""")
    suspend fun getAllAlbums(): List<AlbumInfo>

    @Query("""
        SELECT album FROM album_info
        WHERE path=:path
    """)
    suspend fun getAlbumByPath(path: String): Long?
}