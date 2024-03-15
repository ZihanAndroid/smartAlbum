package com.example.image_multi_recognition.db

import androidx.room.*
import com.example.image_multi_recognition.DefaultConfiguration

@Dao
interface ImageLabelDao : BaseDao<ImageLabel> {
    @Query("DELETE FROM image_labels")
    suspend fun deleteAll()

    // COLLATE NOCASE: case-insensitive
    @Query(
        """
        SELECT label, COUNT(id) as count
        FROM image_labels
        GROUP BY label
        ORDER BY label COLLATE NOCASE ASC, count DESC
    """
    )
    suspend fun getAllOrderedLabels(): List<LabelInfo>

    @Query(
        """
        DELETE FROM image_labels
        WHERE id = :id
    """
    )
    suspend fun deleteById(id: Long)

    @Query(
        """
        DELETE FROM image_labels
        WHERE label=:label and id in (:idList)
    """
    )
    suspend fun _deleteByLabelAndId(label: String, idList: List<Long>)

    suspend fun deleteByLabelAndIdList(label: String, vararg ids: Long) {
        val idList = ids.toList()
        var index = 0
        while (index < idList.size) {
            val nextIndex =
                if (index + DefaultConfiguration.DB_BATCH_SIZE > idList.size) idList.size else index + DefaultConfiguration.DB_BATCH_SIZE
            _deleteByLabelAndId(label, idList.subList(index, nextIndex))
            index = nextIndex
        }
    }

//    @Query(
//        """
//        DELETE FROM image_info
//        WHERE id in (:id)"""
//    )
//    suspend fun _deleteById(id: List<Long>)
//
//    suspend fun deleteById(vararg id: Long) {
//        val idList = id.toList()
//        var index = 0
//        while (index < id.size) {
//            val nextIndex =
//                if (index + DefaultConfiguration.DB_BATCH_SIZE > id.size) id.size else index + DefaultConfiguration.DB_BATCH_SIZE
//            _deleteById(idList.subList(index, nextIndex))
//            index = nextIndex
//        }
//    }
}

data class LabelInfo(
    @ColumnInfo("label") val label: String,
    @ColumnInfo("count") val count: Int
)