package com.example.image_multi_recognition.db

import android.graphics.Rect
import androidx.room.*

@Database(
    entities = [ImageInfo::class, ImageLabel::class],
    version = 2
)
@TypeConverters(value = [RectConverter::class])
abstract class ImageInfoDatabase : RoomDatabase(){
    abstract fun getImageInfoDao(): ImageInfoDao
    abstract fun getImageLabelDao(): ImageLabelDao
    // abstract fun getImageBoundDao(): ImageBoundDao
}

class RectConverter {
    @TypeConverter
    fun from(str: String): Rect = Rect.unflattenFromString(str)!!

    @TypeConverter
    fun to(rect: Rect): String = rect.flattenToString()
}
