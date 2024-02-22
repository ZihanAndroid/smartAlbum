package com.example.image_multi_recognition

import android.content.Context
import androidx.room.Room
import com.example.image_multi_recognition.db.ImageBoundDao
import com.example.image_multi_recognition.db.ImageInfoDao
import com.example.image_multi_recognition.db.ImageInfoDatabase
import com.example.image_multi_recognition.db.ImageLabelDao
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

object DefaultConfiguration{
    const val PAGE_SIZE = 80
    const val IMAGE_INFO_BATCH_SIZE = 100
    // SQLITE_MAX_VARIABLE_NUMBER in SQLite is 999 by default, set "DB_BATCH_SIZE" to a value smaller than that
    const val DB_BATCH_SIZE = 950

    const val IMAGE_PER_ROW = 4
    const val IMAGE_INTERVAL = 4
}

@Module
@InstallIn(SingletonComponent::class)
object ImageModules {
    @Provides
    @Singleton
    fun provideObjectDetector(): ObjectDetector {
        // https://developers.google.com/ml-kit/vision/object-detection/android
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            //.enableClassification()  // Optional
            .build()
        return ObjectDetection.getClient(options)
    }

    @Provides
    @Singleton
    fun provideImageLabeler(): ImageLabeler {
        // https://developers.google.com/ml-kit/vision/image-labeling/android
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.6f)
            .build()
        return ImageLabeling.getClient(options)
    }
    // avoid passing a context as a parameter of viewModel
//    @Provides
//    @Singleton
//    fun provideGlide(
//        @ApplicationContext context: Context
//    ): RequestBuilder<Bitmap> = Glide.with(context).asBitmap()
}

@Module
@InstallIn(SingletonComponent::class)
object DataBaseModule{
    @Provides
    @Singleton
    fun provideRepoDatabase(
        @ApplicationContext context: Context
    ): ImageInfoDatabase = Room.databaseBuilder(
        context = context,
        klass = ImageInfoDatabase::class.java,
        name = "image_info_db"
    ).fallbackToDestructiveMigration()
        .build()

    @Provides
    @Singleton
    fun provideImageInfoDao(
        database: ImageInfoDatabase
    ): ImageInfoDao = database.getImageInfoDao()

    @Provides
    @Singleton
    fun provideImageBoundDao(
        database: ImageInfoDatabase
    ): ImageBoundDao = database.getImageBoundDao()

    @Provides
    @Singleton
    fun provideImageLabelDao(
        database: ImageInfoDatabase
    ): ImageLabelDao = database.getImageLabelDao()
}