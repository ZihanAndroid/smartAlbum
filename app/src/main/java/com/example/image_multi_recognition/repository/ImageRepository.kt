package com.example.image_multi_recognition.repository

import android.app.PendingIntent
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.room.withTransaction
import coil.imageLoader
import coil.request.ImageRequest
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.db.*
import com.example.image_multi_recognition.util.*
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageRepository @Inject constructor(
    private val database: ImageInfoDatabase,
    private val imageLabelDao: ImageLabelDao,
    private val imageInfoDao: ImageInfoDao,
    private val albumInfoDao: AlbumInfoDao,
    @ApplicationContext private val context: Context,
) {
    init {
        context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    // it seems that taking a new photo may trigger this onChange callback multiple times
                    _mediaStoreChangeFlow.value = !_mediaStoreChangeFlow.value
                }
            })
    }

    private val _mediaStoreChangeFlow = MutableStateFlow(false)
    val mediaStoreChangeFlow: StateFlow<Boolean>
        get() = _mediaStoreChangeFlow

    // val dataReadyFlow =
    val backgroundThreadPool = Executors.newFixedThreadPool(1)

    fun resetAllImages() {
        _mediaStoreChangeFlow.value = !_mediaStoreChangeFlow.value
    }

    fun getAllImageDir(): List<File> = with(MediaDirectoryFetcher) {
        context.getAllImageDir()
    }

    suspend fun getAllAlbumInfo(): List<AlbumInfo> = albumInfoDao.getAllAlbums()

    suspend fun getAllImageInfoByAlbum(album: Long): List<ImageInfo> = imageInfoDao.getAllImageInfoByAlbum(album)

    suspend fun deleteAndAddAlbums(deletedAlbums: List<AlbumInfo>, addedAlbums: List<AlbumInfo>): List<Long> {
        return database.withTransaction {
            albumInfoDao.delete(*deletedAlbums.toTypedArray())
            albumInfoDao.insert(*addedAlbums.toTypedArray())
        }
    }

    // return added or existing album id
    suspend fun addAlbumInfoIfNotExist(albumInfo: AlbumInfo): Long {
        return database.withTransaction {
            albumInfoDao.getAlbumByPath(albumInfo.path) ?: albumInfoDao.insert(albumInfo)[0]
        }
    }

    suspend fun deleteAndAddImages(
        deletedIds: List<Long>,
        addedFilePaths: List<File>,
        album: Long,
    ): List<ImageInfo> {
        val createdDate = with(StorageHelper) {
            context.getDateCreatedForImageFile(addedFilePaths.map { it.absolutePath }).toMap()
        }
        return database.withTransaction {
            imageInfoDao.deleteById(deletedIds)
            val addedImageInfo = addedFilePaths.map { file ->
                ImageInfo(
                    album = album,
                    path = file.absolutePath.removePrefix(AlbumPathDecoder.decode(album).absolutePath + "/"),
                    // cachedImage = cachedImages[index],
                    // if failed to get createdTime of the image file, set timestamp to null
                    timestamp = try {
                        // getting meta-data from MediaStore is much than reading files
                        createdDate[file.absolutePath] ?: 0
                        // ExifHelper.getImageCreatedTime(file)?.let { Timestamp.valueOf(it).time } ?: 0
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
    }

    suspend fun insertImageInfo(vararg imageInfo: ImageInfo) {
        imageInfoDao.insert(*imageInfo)
    }

    // suspend fun updateImageAlbum(newAlbum: AlbumInfo? = null, imageInfoList: List<ImageInfo>) {
    //     database.withTransaction {
    //         if (newAlbum != null) {
    //             insertAlbumInfo(newAlbum)
    //         }
    //         imageInfoDao.update(*imageInfoList.toTypedArray())
    //     }
    // }

    suspend fun updateImageAlbum(newAlbum: AlbumInfo, imageInfoList: List<ImageInfo>, isImageMove: Boolean) {
        var realNewAlbum = newAlbum
        database.withTransaction {
            if (newAlbum.album == 0L) {
                val id = insertAlbumInfo(newAlbum)
                realNewAlbum = newAlbum.copy(album = id[0])
            }
            AlbumPathDecoder.addAlbum(realNewAlbum)
            // Note: you need to set the id to 0 to tell Room that the ImageInfo does not exist in the database (id not set yet)
            // set it to any other value causes a conflict when inserting (resolved by something like: @Insert(onConflict = OnConflictStrategy.IGNORE))
            if (!isImageMove) { // copy
                imageInfoDao.insert(*(imageInfoList.map { it.copy(album = realNewAlbum.album, id = 0) }).toTypedArray())
            } else {    // move
                imageInfoDao.update(*(imageInfoList.map { it.copy(album = realNewAlbum.album, id = 0) }).toTypedArray())
            }
        }
    }

    suspend fun changeImageInfoFavorite(idList: List<Long>) {
        imageInfoDao.changeImageInfoFavorite(idList)
    }

    suspend fun insertAlbumInfo(vararg albumInfo: AlbumInfo): List<Long> {
        return albumInfoDao.insert(*albumInfo)
    }

    suspend fun getAlbumByPath(path: String): Long? = albumInfoDao.getAlbumByPath(path)

    suspend fun getAlbumById(album: Long): AlbumInfo? = albumInfoDao.getAlbumById(album)

    fun getAllOrderedLabelListFlow(): Flow<List<LabelInfo>> = imageLabelDao.getAllOrderedLabels()

    suspend fun updateImageLabel(imageId: Long, imageLabelList: List<ImageLabel>) {
        database.withTransaction {
            imageLabelDao.deleteById(imageId)
            if (imageLabelList.isNotEmpty()) {
                imageLabelDao.insert(*imageLabelList.toTypedArray())
            }
        }
    }

    var prevImagePagingSource: PagingSource<Int, ImageInfo>? = null

    // Proto DataStore
    fun getImagePagingFlow(album: Long, initialKey: Int? = null): Flow<PagingData<ImageInfo>> = Pager(
        initialKey = initialKey,
        config = PagingConfig(
            pageSize = DefaultConfiguration.PAGE_SIZE,
            enablePlaceholders = true
        ),
        pagingSourceFactory = {
            if (album != DefaultConfiguration.FAVORITES_ALBUM_ID) {
                imageInfoDao.getImageShowPagingSourceForAlbum(album).apply { prevImagePagingSource = this }
            } else {
                imageInfoDao.getImageShowPagingSourceForFavorite().apply { prevImagePagingSource = this }
            }
        }
    ).flow

    var prevLabelImagePagingSource: PagingSource<Int, ImageInfo>? = null
    fun getImagePagingFlowByLabel(label: String, initialKey: Int? = null): Flow<PagingData<ImageInfo>> = Pager(
        initialKey = initialKey,
        config = PagingConfig(
            pageSize = DefaultConfiguration.PAGE_SIZE,
            enablePlaceholders = true
        ),
        pagingSourceFactory = {
            imageInfoDao.getImagePagingSourceByLabel(label).apply { prevLabelImagePagingSource = this }
        }
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

    fun genImageRequest(file: File, imageInfo: ImageInfo, thumbnailQuality: Float, onFinish: () -> Unit) {
        val request = ImageRequest.Builder(context)
            .data(file)
            .crossfade(true)
            .target(
                onSuccess = { drawable ->
                    // Log.d(getCallSiteInfo(), "Current Thread: ${Thread.currentThread().name}")
                    // Log.d(getCallSiteInfo(), "load image success: ${imageInfo.fullImageFile.absolutePath}")
                    backgroundThreadPool.submit {
                        imageInfo.setImageCache(drawable.toBitmap(), thumbnailQuality, onFinish)
                    }
                },  // onSuccess is not a suspend function, it is assumed that Coil calls this callback from only one thread
                onError = {
                    onFinish()
                    Log.e(getCallSiteInfo(), "Failed to load image from: ${file.absolutePath}")
                }
            ).build()
        context.imageLoader.enqueue(request)
    }

    fun getInputImage(file: File): InputImage? {
        return try {
            // InputImage.fromBitmap(BitmapFactory.decodeFile(file.absolutePath), 0)
            InputImage.fromFilePath(context, file.toUri())
            // val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            // InputImage.fromByteArray(file.readBytes(), bitmap.width, bitmap.height, 0,  ImageFormat.YUV_420_888)
        } catch (e: IOException) {
            Log.e(getCallSiteInfo(), e.stackTraceToString())
            null
        }
    }

    // add "%" for fuzzy search
    suspend fun getImagesByLabel(label: String) = imageInfoDao.getImagesByLabel("$label%")

    suspend fun getImageInfoById(ids: List<Long>) = imageInfoDao.getImageInfoByIds(*ids.toLongArray())
    suspend fun getAllImagesByAlbum(album: Long): List<Long>{
        return if(album != DefaultConfiguration.FAVORITES_ALBUM_ID){
            imageInfoDao.getAllImagesByAlbum(album)
        }else{
            imageInfoDao.getAllFavoriteImages()
        }
    }
    suspend fun getAllFileNamesByCurrentAlbum(album: Long) = imageInfoDao.getAllFileNamesByCurrentAlbum(album)

    fun getUnlabeledAlbumPagerFlow(): Flow<PagingData<AlbumWithLatestImage>> = Pager(
        config = PagingConfig(
            pageSize = DefaultConfiguration.PAGE_SIZE,
            enablePlaceholders = true
        ),
        pagingSourceFactory = { imageInfoDao.getUnlabeledAlbumWithLatestImage() }
    ).flow

    fun getAllUnlabeledImages(): Flow<List<ImageInfo>> = imageInfoDao.getAllUnlabeledImages().distinctUntilChanged()

    fun getAllUnlabeledImagesList(): List<ImageInfo> = imageInfoDao.getAllUnlabeledImagesList()

    val unlabeledAlbumImagesFlow: Flow<List<ImageInfo>>? = null
    fun getUnlabeledImagesByAlbum(album: Long): Flow<List<ImageInfo>> =
        imageInfoDao.getUnlabeledImagesByAlbum(album).distinctUntilChanged()

    fun getUnlabeledImagesListByAlbum(album: Long): List<ImageInfo> =
        imageInfoDao.getUnlabeledImagesListByAlbum(album)

    suspend fun insertImageLabels(imageLabels: List<ImageLabel>) {
        imageLabelDao.insert(*imageLabels.toTypedArray())
    }

    suspend fun getLabelsByImageId(imageId: Long): List<ImageLabel> {
        return imageLabelDao.getLabelsByImageId(imageId)
    }

    suspend fun removeImageLabelsByLabel(label: String, imageIds: List<Long>) {
        imageLabelDao.deleteByLabelAndIdList(label, *imageIds.toLongArray())
    }

    suspend fun deleteImagesById(imageIds: List<Long>) {
        imageInfoDao.deleteById(imageIds)
    }

    suspend fun deleteAlbumById(album: Long) {
        albumInfoDao.deleteById(album)
    }

    suspend fun getAlbumInfoWithLatestImage(excludedAlbum: Long = -1): List<AlbumInfoWithLatestImage> {
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

    suspend fun getContentUris(absolutePaths: List<String>): List<StorageHelper.MediaStoreItem>{
        return with(StorageHelper){
            context.getContentUriForImageFile(absolutePaths)
        }
    }

    // return a map for (absolute paths : error code) for failed items
    suspend fun copyImageTo(
        newAlbum: AlbumInfo,
        items: List<ImageCopyItem>,
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

    // private suspend fun updateImageName(imageId: Long, newFileName: String) {
    //     imageInfoDao.updateImageName(imageId, newFileName)
    // }

    suspend fun updateFileName(imageId: Long, absolutePath: String, newFileName: String): Boolean {
        return with(StorageHelper) {
            if (context.updateImageFileName(absolutePath, newFileName)) {
                // updateImageName(imageId, newFileName)
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
        val mimeType: String,
    )
}