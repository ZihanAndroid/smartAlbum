package com.example.image_multi_recognition.repository

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import coil.imageLoader
import coil.request.ImageRequest
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.db.*
import com.example.image_multi_recognition.util.*
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            } else {
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
        // cachedImages: List<ByteArray>,
        album: Long
    ): List<ImageInfo> = database.withTransaction {
        imageInfoDao.deleteById(deletedIds)
        val addedImageInfo = addedFilePaths.map { file ->
            ImageInfo(
                album = album,
                path = file.absolutePath.removePrefix(AlbumPathDecoder.decode(album).absolutePath),
                // cachedImage = cachedImages[index],
                // if failed to get createdTime of the image file, set timestamp to null
                timestamp = try {
                    ExifHelper.getImageCreatedTime(file)?.let { Timestamp.valueOf(it).time } ?: 0
                } catch (e: IOException) {
                    0
                }
            )
        }
        // set auto-increment id
        val ids = imageInfoDao.insert(*addedImageInfo.toTypedArray())
        if (ids.size == addedImageInfo.size) {
            addedImageInfo.mapIndexed { index, oldImageInfo ->
                oldImageInfo.copy(id = ids[index])
            }
        } else {
            Log.e(
                getCallSiteInfo(),
                "Image insertion failed!\nreturned ids: ${ids.joinToString()}\nImageInfo to add: ${addedImageInfo.joinToString()}"
            )
            emptyList()
        }
    }

    suspend fun insertImageInfo(vararg imageInfo: ImageInfo) {
        imageInfoDao.insert(*imageInfo)
    }

    suspend fun updateImageAlbum(newAlbum: AlbumInfo? = null, imageInfoList: List<ImageInfo>) {
        database.withTransaction {
            if (newAlbum != null) {
                insertAlbumInfo(newAlbum)
            }
            imageInfoDao.update(*imageInfoList.toTypedArray())
        }
    }

    suspend fun createImageAlbum(newAlbum: AlbumInfo? = null, imageInfoList: List<ImageInfo>) {
        database.withTransaction {
            if (newAlbum != null) {
                insertAlbumInfo(newAlbum)
            }
            imageInfoDao.insert(*imageInfoList.toTypedArray())
        }
    }

    suspend fun changeImageInfoFavorite(idList: List<Long>) {
        imageInfoDao.changeImageInfoFavorite(idList)
    }

    suspend fun insertAlbumInfo(vararg albumInfo: AlbumInfo) {
        albumInfoDao.insert(*albumInfo)
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
        pagingSourceFactory = { imageInfoDao.getImagePagingSourceByLabel(label) }
    ).flow

    fun getAlbumUnlabeledPagingFlow(album: Long, initialKey: Int? = null): Flow<PagingData<ImageInfo>> = Pager(
        initialKey = initialKey,
        config = PagingConfig(
            pageSize = DefaultConfiguration.PAGE_SIZE,
            enablePlaceholders = true
        ),
        pagingSourceFactory = { imageInfoDao.getAlbumUnlabeledPagingSource(album) }
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

    suspend fun getImageInfoById(ids: List<Long>) = imageInfoDao.getImageInfoByIds(*ids.toLongArray())
    suspend fun getAllImagesByAlbum(album: Long) = imageInfoDao.getAllImagesByAlbum(album)

    fun getUnlabeledAlbumPagerFlow(): Flow<PagingData<AlbumWithLatestImage>> = Pager(
        config = PagingConfig(
            pageSize = DefaultConfiguration.PAGE_SIZE,
            enablePlaceholders = true
        ),
        pagingSourceFactory = { imageInfoDao.getUnlabeledAlbumWithLatestImage() }
    ).flow

    fun getAllUnlabeledImages(): Flow<List<ImageInfo>> = imageInfoDao.getAllUnlabeledImages().distinctUntilChanged()

    fun getUnlabeledImagesByAlbum(album: Long): Flow<List<ImageInfo>> =
        imageInfoDao.getUnlabeledImagesByAlbum(album).distinctUntilChanged()

    suspend fun insertImageLabels(imageLabels: List<ImageLabel>) {
        imageLabelDao.insert(*imageLabels.toTypedArray())
    }

    suspend fun removeImageLabelsByLabel(label: String, imageIds: List<Long>) {
        imageLabelDao.deleteByLabelAndIdList(label, *imageIds.toLongArray())
    }

    suspend fun deleteImagesById(imageIds: List<Long>) {
        imageInfoDao.deleteById(imageIds)
    }

    suspend fun getAlbumInfoWithLatestImage(excludedAlbum: Long): List<AlbumInfoWithLatestImage> {
        return imageInfoDao.getAlbumInfoWithLatestImage(excludedAlbum).sortedBy { File(it.albumPath).name }
    }

    suspend fun getDeleteImagesRequest(imageInfoList: List<ImageInfo>): PendingIntent? {
        return with(StorageHelper) {
            context.requestImageFileDeletion(imageInfoList.map { it.fullImageFile.absolutePath })
        }
    }

    suspend fun getMimeTypes(fileAbsolutePathList: List<String>): Map<String, String> {
        return with(StorageHelper) {
            context.getMimeTypeForImageFile(fileAbsolutePathList)
        }
    }

    suspend fun getMoveImageRequest(imageInfoList: List<ImageInfo>): StorageHelper.MediaModifyRequest {
        return with(StorageHelper) {
            context.requestImageFileModification(imageInfoList.map { it.fullImageFile.absolutePath })
        }
    }

    // return a map for (absolute paths : error code) for failed items
    suspend fun copyImageTo(
        newAlbum: AlbumInfo,
        items: List<ImageCopyItem>
    ): Map<String, StorageHelper.ImageCopyError> {
        val failedItems = mutableMapOf<String, StorageHelper.ImageCopyError>()
        with(StorageHelper) {
            items.forEach { item ->
                context.copyImageToSharedStorage(
                    newImage = File(newAlbum.path, File(item.absolutePath).name),
                    imageMimeType = item.mimeType,
                    fileFrom = File(item.absolutePath),
                ).let { code ->
                    if (code != StorageHelper.ImageCopyError.NO_ERROR) {
                        failedItems[item.absolutePath] = code
                    }
                }
            }
        }
        return failedItems
    }

    suspend fun deleteImageFiles(contentUris: List<Uri>) {
        withContext(Dispatchers.IO) {
            contentUris.forEach { uri ->
                context.contentResolver.delete(uri, null)
            }
        }
    }

    suspend fun requestFileNameUpdate(absolutePath: String): StorageHelper.MediaModifyRequest {
        return with(StorageHelper) {
            context.requestImageFileModification(listOf(absolutePath))
        }
    }

    private suspend fun updateImageName(imageId: Long, newFileName: String) {
        imageInfoDao.updateImageName(imageId, newFileName)
    }

    suspend fun updateFileName(imageId: Long, absolutePath: String, newFileName: String): Boolean {
        return with(StorageHelper) {
            if (context.updateImageFileName(absolutePath, newFileName)) {
                updateImageName(imageId, newFileName)
                true
            } else false
        }
    }

    suspend fun getImageInformation(imageFile: File): List<String> {
        return with(ExifHelper) {
            context.getImageInformation(imageFile)
        }
    }

    data class ImageCopyItem(
        val absolutePath: String,
        val mimeType: String
    )
}