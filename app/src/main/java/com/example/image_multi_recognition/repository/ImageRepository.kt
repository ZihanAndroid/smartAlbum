package com.example.image_multi_recognition.repository

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.net.toUri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import coil.ImageLoader
import coil.imageLoader
import coil.request.ImageRequest
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.db.*
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.util.ExifHelper
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject.Label
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.IOException
import java.sql.Timestamp
import javax.inject.Inject

class ImageRepository @Inject constructor(
    private val database: ImageInfoDatabase,
    private val imageLabelDao: ImageLabelDao,
    private val imageInfoDao: ImageInfoDao,
    private val imageBoundDao: ImageBoundDao,
    @ApplicationContext private val context: Context
) {
    suspend fun registerUnLabeledImage(
        album: String,
        path: String,
        imageBounds: List<ImageBound>,
        timestamp: Long
    ): Boolean {
        if (imageBounds.isEmpty()) {
            Log.e(getCallSiteInfo(), "An empty imageBounds list is passed")
            return false
        }
        return database.withTransaction {
            imageInfoDao.insert(
                ImageInfo(
                    id = imageBounds[0].id,
                    album = album,
                    path = path,
                    labeled = false,
                    timestamp = timestamp
                )
            )
            imageBoundDao.insert(*imageBounds.toTypedArray())
            true
        }
    }

    suspend fun registerLabeledImage(imageLabels: List<ImageLabel>): Boolean {
        if (imageLabels.isEmpty()) {
            Log.e(getCallSiteInfo(), "An empty imageLabels list is passed")
            return false
        }
        return database.withTransaction {
            imageLabels[0].id.let { id ->
                imageInfoDao.setLabeled(id, true)
                imageLabelDao.insert(*imageLabels.toTypedArray())
                imageBoundDao.deleteById(id) > 0
            }
        }
    }

    suspend fun getBoundsFromId(id: Long): List<Rect> = imageBoundDao.selectById(id)

    suspend fun getAllImageOfAlbum(album: String): List<ImageIdPath> = imageInfoDao.getAllImageOfAlbum(album)

    suspend fun deleteAndAddImages(
        deletedIds: List<Long>,
        addedFilePaths: List<File>,
        //cachedImages: List<ByteArray>,
        album: String
    ): List<ImageInfo> {
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
        return addedImageInfo
    }

    suspend fun insertImageInfo(vararg imageInfo: ImageInfo) {
        imageInfoDao.insert(*imageInfo)
    }

    suspend fun updateImageInfo(vararg imageInfo: ImageInfo) {
        imageInfoDao.update(*imageInfo)
    }

    suspend fun getAllOrderedLabelList(): List<LabelInfo> = imageLabelDao.getAllOrderedLabels()

    suspend fun insertImageLabel(imageLabelList: List<ImageLabel>){
        imageLabelDao.insert(*imageLabelList.toTypedArray())
    }

    // Proto DataStore
    fun getImagePagingFlow(album: String, initialKey: Int? = null): Flow<PagingData<ImageInfo>> = Pager(
        initialKey = initialKey,
        config = PagingConfig(
            pageSize = DefaultConfiguration.PAGE_SIZE,
            enablePlaceholders = true
        ),
        pagingSourceFactory = { imageInfoDao.getImageShowPagingSourceForAlbum(album) }
    ).flow

    fun genImageRequest(file: File, onSuccess: (Drawable) -> Unit = {}): Pair<ImageLoader, ImageRequest> {
        val request = ImageRequest.Builder(context)
            .data(file)
            .crossfade(true)
            .target(
                onSuccess = onSuccess,  // onSuccess is not a suspend function, it is assumed that Coil calls this callback from only one thread
                onError = {
                    Log.e(getCallSiteInfo(), "Failed to load image from: ${file.absolutePath}")
                }
            ).build()
        //context.imageLoader.enqueue(request)
        return Pair(context.imageLoader, request)
    }

    fun getInputImage(file: File): InputImage?{
        return try{
            InputImage.fromFilePath(context, file.toUri())
        } catch(e: IOException){
            Log.e(getCallSiteInfo(), e.stackTraceToString())
            null
        }
    }
}