package com.example.image_multi_recognition.db

import android.media.Image
import androidx.paging.PagingSource
import androidx.room.*
import com.example.image_multi_recognition.DefaultConfiguration
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageInfoDao : BaseDao<ImageInfo> {
    @Query("DELETE FROM image_info")
    suspend fun deleteAll()

//    @Query("""
//        UPDATE image_info
//        SET labeled=:labeled
//        WHERE id=:id
//    """)
//    suspend fun setLabeled(id: Long, labeled: Boolean)

    // @Query("""
    //     SELECT id, path
    //     FROM image_info
    //     WHERE album=:album
    // """)
    // suspend fun getAllImageOfAlbum(album: Long): List<ImageIdPath>

    @Query("""
        DELETE FROM image_info
        WHERE id in (:idList)
    """)
    suspend fun _deleteById(idList: List<Long>)

    suspend fun deleteById(idList: List<Long>) {
        var index = 0
        while (index < idList.size) {
            val nextIndex =
                if (index + DefaultConfiguration.DB_BATCH_SIZE > idList.size) idList.size else index + DefaultConfiguration.DB_BATCH_SIZE
            _deleteById(idList.subList(index, nextIndex))
            index = nextIndex
        }
    }

    @Query("""
        UPDATE image_info
        SET path=:newFileName
        WHERE id=:imageId
    """)
    suspend fun updateImageName(imageId: Long, newFileName: String)

    @Query("""
        SELECT * FROM image_info
        WHERE id in (:id)
    """)
    suspend fun getImageInfoByIds(vararg id: Long): List<ImageInfo>

    @Query("""
        SELECT id FROM image_info
        where album=:album
    """)
    suspend fun getAllImagesByAlbum(album: Long): List<Long>

    @Query("""
        SELECT * FROM image_info
        where album=:album
    """)
    suspend fun getAllImageInfoByAlbum(album: Long): List<ImageInfo>

    @Query("""
        SELECT path FROM image_info
        where album=:album
    """)
    suspend fun getAllFileNamesByCurrentAlbum(album: Long): List<String>

    @Query("""
        SELECT * FROM image_info
        WHERE album=:album
        ORDER BY time_created DESC
    """)
    fun getImageShowPagingSourceForAlbum(album: Long): PagingSource<Int, ImageInfo>

    @Query("""
        SELECT i1.id, path, album, time_created, i1.favorite 
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
        HAVING path=MAX(path_)
    """)
    fun getAlbumWithLatestImagePagingSource(): PagingSource<Int, AlbumWithLatestImage>

    @Query("""
        WITH album_with_latest_image as(
            SELECT i1.album as album_, i1.path as path_, i2.count as count_
            FROM image_info as i1 join (
                SELECT album, MAX(time_created) as latest_time, COUNT(album) as count
                FROM image_info
                GROUP BY album
            ) as i2 on i1.album=i2.album and i1.time_created=i2.latest_time
            GROUP BY album_, count_
            HAVING path=MAX(path_)
        )
        SELECT a1.album_, a1.count_, a1.path_, a2.path as album_path
        FROM album_with_latest_image as a1 join album_info as a2
            on a1.album_ = a2.album
        WHERE a1.album_!=:excludedAlbum
    """)
    suspend fun getAlbumInfoWithLatestImage(excludedAlbum: Long): List<AlbumInfoWithLatestImage>

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

    @Query("""
        WITH unlabeled AS(
            SELECT * FROM image_info
            WHERE NOT EXISTS (
                SELECT * FROM image_labels 
                WHERE image_info.id = image_labels.id
            ) 
        )
        SELECT i1.album as album_, i1.path as path_, i2.count as count_
        FROM unlabeled as i1 join (
            SELECT album, MAX(time_created) as latest_time, COUNT(album) as count
            FROM unlabeled
            GROUP BY album
        ) as i2 on i1.album=i2.album and i1.time_created=i2.latest_time
        GROUP BY album_, count_
        HAVING path=MAX(path_)
    """)
    fun getUnlabeledAlbumWithLatestImage(): PagingSource<Int, AlbumWithLatestImage>

    @Query("""
        SELECT * FROM image_info
        WHERE NOT EXISTS (
            SELECT * FROM image_labels 
            WHERE image_info.id = image_labels.id
        ) 
    """)
    fun getAllUnlabeledImages(): Flow<List<ImageInfo>>

    @Query("""
        SELECT * FROM image_info
        WHERE NOT EXISTS (
            SELECT * FROM image_labels 
            WHERE image_info.id = image_labels.id
        ) and album=:album
        ORDER BY time_created DESC
    """)
    fun getAlbumUnlabeledPagingSource(album: Long): PagingSource<Int, ImageInfo>

    @Query("""
        SELECT * FROM image_info
        WHERE NOT EXISTS (
            SELECT * FROM image_labels 
            WHERE image_info.id = image_labels.id
        ) and album=:album
        ORDER BY time_created DESC
    """)
    fun getUnlabeledImagesByAlbum(album: Long): Flow<List<ImageInfo>>

    @Query("""
        UPDATE image_info
        SET favorite= NOT favorite
        WHERE id in (:idList)
    """)
    suspend fun _changeImageInfoFavorite(idList: List<Long>)

    suspend fun changeImageInfoFavorite(idList: List<Long>){
        var index = 0
        while (index < idList.size) {
            val nextIndex =
                if (index + DefaultConfiguration.DB_BATCH_SIZE > idList.size) idList.size else index + DefaultConfiguration.DB_BATCH_SIZE
            _changeImageInfoFavorite(idList.subList(index, nextIndex))
            index = nextIndex
        }
    }
}

data class AlbumWithLatestImage(
    @ColumnInfo("album_") val album: Long,
    @ColumnInfo("path_") val path: String,  // path for image
    @ColumnInfo("count_") val count: Int
)

data class AlbumInfoWithLatestImage(
    @ColumnInfo("album_") val album: Long,
    @ColumnInfo("album_path") val albumPath: String,
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