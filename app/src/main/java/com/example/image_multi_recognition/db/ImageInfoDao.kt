package com.example.image_multi_recognition.db

import android.media.Image
import androidx.paging.PagingSource
import androidx.room.*
import com.example.image_multi_recognition.DefaultConfiguration

@Dao
interface ImageInfoDao: BaseDao<ImageInfo> {
    @Query("DELETE FROM image_info")
    suspend fun deleteAll()

    @Query("""
        UPDATE image_info
        SET labeled=:labeled
        WHERE id=:id 
    """)
    suspend fun setLabeled(id: Long, labeled: Boolean)

    @Query("""
        SELECT id, path
        FROM image_info
        WHERE album=:album
    """)
    suspend fun getAllImageOfAlbum(album: String): List<ImageIdPath>

    @Query("""
        DELETE FROM image_info
        WHERE id in (:id)
    """)
    suspend fun _deleteById(id: List<Long>)

    suspend fun deleteById(vararg id: Long){
        val idList = id.toList()
        var index = 0
        while(index < id.size){
            val nextIndex = if(index + DefaultConfiguration.DB_BATCH_SIZE > id.size) id.size else index + DefaultConfiguration.DB_BATCH_SIZE
            _deleteById(idList.subList(index, nextIndex))
            index = nextIndex
        }
    }

    @Query("""
        SELECT * FROM image_info
        WHERE album=:album
        ORDER BY time_created DESC
    """)
    fun getImageShowPagingSourceForAlbum(album: String): PagingSource<Int, ImageInfo>
}

data class ImageIdPath(
    @ColumnInfo("id") val id: Long,
    @ColumnInfo("path") val path: String
)

//data class ImageShow(
//    @ColumnInfo("id") val id: Long,
//    @ColumnInfo(name = "labeled") val labeled: Boolean,
//    @ColumnInfo(name = "time_created") val timestamp: Long,
//    @ColumnInfo("cached_image") val cachedImage: ByteArray
//)

//data class CachedImage(
//    @ColumnInfo("id") val id: Long,
//    @ColumnInfo(name = "cached_image", typeAffinity = ColumnInfo.BLOB) val cachedImage: ByteArray = ByteArray(0)
//)



