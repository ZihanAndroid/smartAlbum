package com.example.image_multi_recognition.db

import androidx.room.*

@Dao
interface ImageLabelDao : BaseDao<ImageLabel>{
    @Query("DELETE FROM image_labels")
    suspend fun deleteAll()

    // COLLATE NOCASE: case-insensitive
    @Query("""
        SELECT label, COUNT(id) as count
        FROM image_labels
        GROUP BY label
        ORDER BY label COLLATE NOCASE ASC, count DESC
    """)
    suspend fun getAllOrderedLabels(): List<LabelInfo>
}

data class LabelInfo(
    @ColumnInfo("label") val label: String,
    @ColumnInfo("count") val count: Int
)