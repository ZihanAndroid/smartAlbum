package com.example.image_multi_recognition.repository

import android.content.Context
import android.graphics.drawable.Drawable
import android.provider.MediaStore
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.db.*
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.util.ExifHelper
import com.example.image_multi_recognition.util.MediaDirectoryFetcher
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException
import java.sql.Timestamp
import java.util.concurrent.Executors
import javax.inject.Inject

class ImageRepository @Inject constructor(
    private val appDataStore: DataStore<AppData>,
    private val database: ImageInfoDatabase,
    private val imageLabelDao: ImageLabelDao,
    private val imageInfoDao: ImageInfoDao,
    private val albumInfoDao: AlbumInfoDao,
    // private val imageBoundDao: ImageBoundDao,
    @ApplicationContext private val context: Context
) {
    // val dataReadyFlow =
    val backgroundThreadPool = Executors.newCachedThreadPool()

    val mediaStoreVersionFlow: Flow<String> = appDataStore.data.catch { e ->
        if (e is IOException) {
            Log.e("DataStoreProtoRepository", "Error in fetching AppData:\n${e.stackTraceToString()}")
            emit(AppData.getDefaultInstance())
        } else {
            throw e
        }
    }.map { it.mediaStoreVersion }.distinctUntilChanged()

    // return newVersion if media stored is updated or empty string if not
    fun checkMediaStoreUpdate(previousVersion: String): String {
        return MediaStore.getVersion(context).let { newVersion ->
            if (newVersion != previousVersion) {
                newVersion
            }else{
                ""
            }
        }
    }

    suspend fun updateMediaStoreVersion(newVersion: String) {
        appDataStore.updateData { currentData ->
            currentData.toBuilder().setMediaStoreVersion(newVersion).build()
        }
    }

    fun getAllImageDir(): List<File> = with(MediaDirectoryFetcher) {
        context.getAllImageDir()
    }

    suspend fun getAllAlbumInfo(): List<AlbumInfo> = albumInfoDao.getAllAlbums()
//    suspend fun registerUnLabeledImage(
//        album: String,
//        path: String,
//        imageBounds: List<ImageBound>,
//        timestamp: Long
//    ): Boolean {
//        if (imageBounds.isEmpty()) {
//            Log.e(getCallSiteInfo(), "An empty imageBounds list is passed")
//            return false
//        }
//        return database.withTransaction {
//            imageInfoDao.insert(
//                ImageInfo(
//                    id = imageBounds[0].id,
//                    album = album,
//                    path = path,
//                    labeled = false,
//                    timestamp = timestamp
//                )
//            )
//            imageBoundDao.insert(*imageBounds.toTypedArray())
//            true
//        }
//    }

//    suspend fun registerLabeledImage(imageLabels: List<ImageLabel>): Boolean {
//        if (imageLabels.isEmpty()) {
//            Log.e(getCallSiteInfo(), "An empty imageLabels list is passed")
//            return false
//        }
//        return database.withTransaction {
//            imageLabels[0].id.let { id ->
//                imageInfoDao.setLabeled(id, true)
//                imageLabelDao.insert(*imageLabels.toTypedArray())
//                imageBoundDao.deleteById(id) > 0
//            }
//        }
//    }

    // suspend fun getBoundsFromId(id: Long): List<Rect> = imageBoundDao.selectById(id)
    suspend fun getAllImageOfAlbum(album: Long): List<ImageIdPath> = imageInfoDao.getAllImageOfAlbum(album)

    suspend fun deleteAndAddAlbums(deletedAlbums: List<AlbumInfo>, addedAlbums: List<AlbumInfo>): List<Long> {
        return database.withTransaction {
            albumInfoDao.delete(*deletedAlbums.toTypedArray())
            albumInfoDao.insert(*addedAlbums.toTypedArray())
        }
    }

    suspend fun deleteAndAddImages(
        deletedIds: List<Long>,
        addedFilePaths: List<File>,
        //cachedImages: List<ByteArray>,
        album: Long
    ): List<ImageInfo> = database.withTransaction {
        imageInfoDao.deleteById(*deletedIds.toLongArray())
        val addedImageInfo = addedFilePaths.map { file ->
            ImageInfo(
                labeled = false,
                album = album,
                path = file.absolutePath.removePrefix(AlbumPathDecoder.decode(album).absolutePath),
                //cachedImage = cachedImages[index],
                // if failed to get createdTime of the image file, set timestamp to null
                timestamp = try {
                    ExifHelper.getImageCreatedTime(file)?.let { Timestamp.valueOf(it).time } ?: 0
                } catch (e: IOException) {
                    0
                }
            )
        }
        // set auto-increment id
        imageInfoDao.insert(*addedImageInfo.toTypedArray()).forEachIndexed { index, id ->
            addedImageInfo[index].id = id
        }
        addedImageInfo
    }

    suspend fun insertImageInfo(vararg imageInfo: ImageInfo) {
        imageInfoDao.insert(*imageInfo)
    }

    suspend fun updateImageInfo(vararg imageInfo: ImageInfo) {
        imageInfoDao.update(*imageInfo)
    }

    suspend fun getAlbumByPath(path: String): Long? = albumInfoDao.getAlbumByPath(path)

    suspend fun getAllOrderedLabelList(): List<LabelInfo> = imageLabelDao.getAllOrderedLabels()

    suspend fun updateImageLabelAndGetAllOrderedLabelList(imageLabelList: List<ImageLabel>) =
        database.withTransaction {
            imageLabelDao.deleteById(imageLabelList.first().id)
            imageLabelDao.insert(*imageLabelList.toTypedArray())
            getAllOrderedLabelList()
        }


    // Proto DataStore
    fun getImagePagingFlow(album: Long, initialKey: Int? = null): Flow<PagingData<ImageInfo>> = Pager(
        initialKey = initialKey,
        config = PagingConfig(
            pageSize = DefaultConfiguration.PAGE_SIZE,
            enablePlaceholders = true
        ),
        pagingSourceFactory = { imageInfoDao.getImageShowPagingSourceForAlbum(album) }
    ).flow

    fun getImagePagingFlowByLabel(label: String, initialKey: Int? = null): Flow<PagingData<ImageInfo>> = Pager(
        initialKey = initialKey,
        config = PagingConfig(
            pageSize = DefaultConfiguration.PAGE_SIZE,
            enablePlaceholders = true
        ),
        pagingSourceFactory = {imageInfoDao.getImagePagingSourceByLabel(label)}
    ).flow

    fun getAlbumPagingFlow(): Flow<PagingData<AlbumWithLatestImage>> = Pager(
        config = PagingConfig(
            pageSize = DefaultConfiguration.PAGE_SIZE,
            enablePlaceholders = true
        ),
        pagingSourceFactory = { imageInfoDao.getAlbumWithLatestImagePagingSource() }
    ).flow

    fun genImageRequest(file: File, imageInfo: ImageInfo) {
        val request = ImageRequest.Builder(context)
            .data(file)
            .crossfade(true)
            .target(
                onSuccess = { drawable ->
                    // Log.d(getCallSiteInfo(), "Current Thread: ${Thread.currentThread().name}")
                    Log.d(getCallSiteInfo(), "load image success: ${imageInfo.fullImageFile.absolutePath}")
                    backgroundThreadPool.submit {
                        imageInfo.setImageCache(drawable.toBitmap())
                    }
                },  // onSuccess is not a suspend function, it is assumed that Coil calls this callback from only one thread
                onError = {
                    Log.e(getCallSiteInfo(), "Failed to load image from: ${file.absolutePath}")
                }
            ).build()
        if (!imageInfo.isThumbnailAvailable) context.imageLoader.enqueue(request)
    }

//    fun genThumbnail(file: File, imageInfo: ImageInfo) {
//        try {
//            // val bitmap = context.contentResolver.loadThumbnail(Uri.fromFile(file), imageInfo.thumbnailSize, null)
//            // imageInfo.setImageCache(bitmap)
//        } catch (e: IOException) {
//            Log.e(getCallSiteInfo(), e.stackTraceToString())
//        }
//    }

    fun getInputImage(file: File): InputImage? {
        return try {
            InputImage.fromFilePath(context, file.toUri())
        } catch (e: IOException) {
            Log.e(getCallSiteInfo(), e.stackTraceToString())
            null
        }
    }

    // add "%" for fuzzy search
    suspend fun getImagesByLabel(label: String) = imageInfoDao.getImagesByLabel("$label%")
}