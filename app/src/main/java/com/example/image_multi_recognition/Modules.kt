package com.example.image_multi_recognition

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import androidx.room.Room
import com.example.image_multi_recognition.dataStore.AppDataSerializer
import com.example.image_multi_recognition.db.AlbumInfoDao
import com.example.image_multi_recognition.db.ImageInfoDao
import com.example.image_multi_recognition.db.ImageInfoDatabase
import com.example.image_multi_recognition.db.ImageLabelDao
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Singleton

object DefaultConfiguration {
    const val PAGE_SIZE = 80

    // SQLITE_MAX_VARIABLE_NUMBER in SQLite is 999 by default, set "DB_BATCH_SIZE" to a value smaller than that
    const val DB_BATCH_SIZE = 950
    const val IMAGE_MAX_HEIGHT_PROPORTION = 0.62
    const val ALBUM_PER_ROW = 2
    const val IMAGE_INTERVAL = 2
    const val ALBUM_INTERVAL = 6
    const val APP_DATASTORE = "APP_DATASTORE"

    const val ACCEPTED_CONFIDENCE = 0.5f

    const val DRAG_SCROLL_THRESHOLD = 100
}

@Module
@InstallIn(SingletonComponent::class)
object ImageModules {
    @Provides
    @Singleton
    fun provideThreadPool(): Executor {
        return Executors.newCachedThreadPool()
    }
    // ml-kit
    @Provides
    @Singleton
    fun provideMlKitObjectDetector(
        executor: Executor
    ): com.google.mlkit.vision.objects.ObjectDetector {
        // https://developers.google.com/ml-kit/vision/object-detection/android
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .setExecutor(executor)
            //.enableClassification()  // Optional
            .build()
        ObjectDetection.getClient(options)
        return ObjectDetection.getClient(options)
    }

    // MediaPipe
    // @Provides
    // @Singleton
    // fun provideMediaPipeObjectDetector(
    //     @ApplicationContext context: Context,
    // ): ObjectDetector {
    //     // https://developers.google.com/mediapipe/solutions/vision/object_detector/android#image
    //     val options = ObjectDetector.ObjectDetectorOptions.builder()
    //         // .setBaseOptions(BaseOptions.builder().setModelAssetPath("efficientdet_lite0.tflite").build())
    //         .setBaseOptions(BaseOptions.builder().setModelAssetPath("ssd_mobilenet_v2.tflite").build())
    //         .setRunningMode(RunningMode.IMAGE)
    //         .setMaxResults(5)
    //         .setScoreThreshold(0.5f)
    //         .build()
    //     return ObjectDetector.createFromOptions(context, options)
    // }

    @Provides
    @Singleton
    fun provideImageLabeler(
        executor: Executor,
    ): ImageLabeler {
        // https://developers.google.com/ml-kit/vision/image-labeling/android
        val options = ImageLabelerOptions.Builder()
            // set the confidence to the lowest possible value that the user can select
            .setConfidenceThreshold(DefaultConfiguration.ACCEPTED_CONFIDENCE)
            .setExecutor(executor)
            .build()
        return ImageLabeling.getClient(options)
    }
    // @Provides
    // @Singleton
    // fun provideGlide(
    //     @ApplicationContext context: Context
    // ): RequestBuilder<Bitmap> = Glide.with(context).asBitmap()
}

@Module
@InstallIn(SingletonComponent::class)
object DataBaseModule {
    @Provides
    @Singleton
    fun provideRepoDatabase(
        @ApplicationContext context: Context,
    ): ImageInfoDatabase = Room.databaseBuilder(
        context = context,
        klass = ImageInfoDatabase::class.java,
        name = "image_info_db"
    ).fallbackToDestructiveMigration()
        .build()

    @Provides
    @Singleton
    fun provideImageInfoDao(
        database: ImageInfoDatabase,
    ): ImageInfoDao = database.getImageInfoDao()

    @Provides
    @Singleton
    fun provideAlbumInfoDao(
        database: ImageInfoDatabase,
    ): AlbumInfoDao = database.getAlbumInfoDao()

    @Provides
    @Singleton
    fun provideImageLabelDao(
        database: ImageInfoDatabase,
    ): ImageLabelDao = database.getImageLabelDao()
}

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun provideProtoDataStore(
        @ApplicationContext context: Context,
        serializer: AppDataSerializer,
    ): DataStore<AppData> =
        DataStoreFactory.create(
            serializer = serializer,
            produceFile = { context.dataStoreFile(DefaultConfiguration.APP_DATASTORE) },
            corruptionHandler = ReplaceFileCorruptionHandler { corruptionException ->
                Log.e(getCallSiteInfoFunc(), corruptionException.stackTraceToString())
                AppData.getDefaultInstance()
            }
        )
}

// @Module
// @InstallIn(SingletonComponent::class)
// abstract class BindsModule{
//     // @Binds
//     // internal abstract fun bindsImagePagingFlowSupport(
//     //     imagePagingFlowSupportImpl: ImagePagingFlowSupportImpl
//     // ): ImagePagingFlowSupport
//     @Binds
//     internal abstract fun bindsImageFileOperationSupport(
//         imageFileOperationSupportViewModel: ImageFileOperationSupportViewModel
//     ): ImageFileOperationSupport
// }