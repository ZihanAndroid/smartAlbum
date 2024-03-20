package com.example.image_multi_recognition.db

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import com.example.image_multi_recognition.DefaultConfiguration

@Dao
interface AlbumInfoDao : BaseDao<AlbumInfo> {
    @Query("""SELECT * FROM album_info""")
    suspend fun getAllAlbums(): List<AlbumInfo>

    @Query("""
        SELECT album FROM album_info
        WHERE path=:path
    """)
    suspend fun getAlbumByPath(path: String): Long?


//    @Query("""
//        SELECT * FROM album_info
//        WHERE album in (:albums)
//        ORDER BY album
//    """)
//    suspend fun _getAlbumsByIds(albums: List<Long>): List<AlbumInfo>  // assume albums is sorted asc
//
//    suspend fun getAlbumsByIds(albums: List<Long>): List<AlbumInfo> {
//        val resList: MutableList<List<AlbumInfo>> = mutableListOf()
//        var index = 0
//        while (index < albums.size) {
//            val nextIndex =
//                if (index + DefaultConfiguration.DB_BATCH_SIZE < albums.size) index + DefaultConfiguration.DB_BATCH_SIZE
//                else albums.size
//            resList.add(_getAlbumsByIds(albums.subList(index, nextIndex)))
//            index = nextIndex
//        }
//        return resList.flatten()
//    }
}