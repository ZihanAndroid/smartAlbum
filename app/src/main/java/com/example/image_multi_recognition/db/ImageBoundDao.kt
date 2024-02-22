package com.example.image_multi_recognition.db

import android.graphics.Rect
import androidx.room.*

@Dao
interface ImageBoundDao : BaseDao<ImageBound> {
    @Query("DELETE FROM image_bounds")
    suspend fun deleteAll()

    @Query("""
        DELETE FROM image_bounds
        where id = :id
    """)
    suspend fun deleteById(id: Long): Int

    @Query("""
        SELECT rect FROM image_bounds
        WHERE id = :id
    """)
    suspend fun selectById(id: Long): List<Rect>
}