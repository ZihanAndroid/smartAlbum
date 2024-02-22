package com.example.image_multi_recognition.db

import androidx.room.*

interface BaseDao<T>{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg value: T): List<Long>

    @Update
    suspend fun update(vararg value: T)

    @Delete
    suspend fun delete(vararg value: T)
}