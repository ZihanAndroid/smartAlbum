package com.example.image_multi_recognition.db

import androidx.room.*

@Dao
interface ImageLabelDao : BaseDao<ImageLabel>{
    @Query("DELETE FROM image_labels")
    suspend fun deleteAll()
}