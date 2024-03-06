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
    suspend fun getAllImageOfAlbum(album: Long): List<ImageIdPath>

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
    fun getImageShowPagingSourceForAlbum(album: Long): PagingSource<Int, ImageInfo>

    @Query("""
        SELECT * 
        FROM image_info i1 join image_labels i2 on i1.id=i2.id
        WHERE i2.label=:label
        ORDER BY time_created DESC
    """)
    fun getImagePagingSourceByLabel(label: String): PagingSource<Int, ImageInfo>

    @Query("""
        SELECT i1.album as album_, i1.path as path_, i2.count as count_
        FROM image_info as i1 join (
            SELECT album, MAX(time_created) as latest_time, COUNT(album) as count
            FROM image_info
            GROUP BY album
        ) as i2 on i1.album=i2.album and i1.time_created=i2.latest_time
        GROUP BY album_, count_
        HAVING path=MIN(path_)
    """)
    fun getAlbumWithLatestImagePagingSource(): PagingSource<Int, AlbumWithLatestImage>


    @Query("""
        WITH joined_table AS (
            SELECT *
            FROM image_labels join image_info on image_labels.id=image_info.id
        )
        SELECT j1.label as label_, j1.album as album_, j1.path as path_
        FROM joined_table as j1 join (
            SELECT t1.label, time_max, MAX(id) as id_max
            FROM joined_table as t1 join (
                SELECT label, MAX(time_created) as time_max
                FROM joined_table
                WHERE label LIKE :label
                GROUP BY label
            ) as t2 on t1.label=t2.label and t1.time_created=t2.time_max
            GROUP by t1.label, t1.time_created
        ) as j2 on j1.label=j2.label and j1.time_created=j2.time_max and j1.id=j2.id_max
    """)
    suspend fun getImagesByLabel(label: String): List<LabelWithLatestImage>
}

data class AlbumWithLatestImage(
    @ColumnInfo("album_") val album: Long,
    @ColumnInfo("path_") val path: String,
    @ColumnInfo("count_") val count: Int
)

data class LabelWithLatestImage(
    @ColumnInfo("label_") val label: String,
    @ColumnInfo("album_") val album: Long,
    @ColumnInfo("path_") val path: String,
)

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



