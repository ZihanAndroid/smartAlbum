package com.example.image_multi_recognition.util

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

// app-specific external storage
object ScopedThumbNailStorage {
    lateinit var imageStorage: File
    const val dirName = ".thumbnailsOfApp"

    //https://developer.android.com/training/data-storage/app-specific#external-verify-availability
    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    // Checks if a volume containing external storage is available to at least read.
    private fun isExternalStorageReadable(): Boolean {
        return Environment.getExternalStorageState() in
                setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)
    }

    // initialize external imageStorage and check availability
    private fun Context.isExternalStorageAvailable(): Boolean {
        imageStorage = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), dirName)
        return if (isExternalStorageWritable()) {
            if (!imageStorage.exists()) {
                imageStorage.mkdir()
            } else true
        } else false
    }

    // initialize imageStorage and check availability, call this method in MainActivity
    fun Context.setupScopedStorage(): Boolean {


        return if (!isExternalStorageAvailable()) {
            // use internal storage instead
            imageStorage = File(filesDir, dirName)
            if (!imageStorage.exists()) {
                imageStorage.mkdir()
            } else true
        } else true
    }
}

// From: https://www.youtube.com/watch?v=jcO6p5TlcGs
object StorageHelper {
    // https://stackoverflow.com/questions/66859325/how-to-get-directorys-uuid-using-storagemanager-on-android-api-below-26
    //    private fun Context.getAllocatableBytesForDir(directory: File): Long {
    //        return getSystemService<StorageManager>()?.let { storageManager ->
    //            storageManager.getAllocatableBytes(storageManager.getUuidForPath(directory))
    //        } ?: 0L
    //    }

    // download a file to app's internal storage and name it as `fileName`
    @Suppress("usableSpace")
    fun Context.downloadFileToInternalStorage(remoteUrl: HttpUrl, fileSize: Long, newFileName: String) {
        val client = OkHttpClient()
        val request = Request.Builder().url(remoteUrl).build()
        client.newCall(request).execute().use { response ->
            response.body?.byteStream()?.use { inputStream ->
                // context.filesDir: app's internal storage directory,
                // use "storageManager.getAllocatableBytes(directoryUUID)" instead of "filesDir.usableSpace"
                val target = if (filesDir.usableSpace > fileSize) {
                    filesDir
                } else {
                    // check app-specific external storage
                    getExternalFilesDirs(null).find { externalDir ->
                        externalDir.usableSpace > fileSize
                    }
                } ?: throw IOException("No enough space")

                target.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }

    // imageMimeType: like "image/jpeg“
    suspend fun Context.copyImageToSharedStorage(newImage: File, imageMimeType: String, fileFrom: File): Boolean {
        // val type = Environment.DIRECTORY_PICTURES
        // shared storage root for a certain media type
        // val target = Environment.getExternalStoragePublicDirectory(type)
        // copy content into shared storage
        return withContext(Dispatchers.IO) {
            try {
                newImage.outputStream().use { outputStream ->
                    fileFrom.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                // scan the created file to make it available for MediaStore
                MediaScannerConnection.scanFile(
                    this@copyImageToSharedStorage,
                    arrayOf(newImage.path),
                    arrayOf(imageMimeType)
                ) { path, uri ->
                    Log.d(getCallSiteInfoFunc(), "newImage created in shared storage: $path || $uri")
                }
                true
            }catch(e: Throwable){
                Log.e(getCallSiteInfo(), e.stackTraceToString())
                false
            }
        }
    }

    // usage:
    //  use "getDocumentPicker" to get a "documentPickerLauncher"
    //  and call documentPickerLauncher.launch(mimeType, like "application/pdf") to show a document picker window to the user
    //    @Composable
    //    fun Context.getDocumentPicker(onDocumentSelected: (Uri?) -> Unit) =
    //        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    //            onDocumentSelected(uri)
    //            /*contentResolver.openInputStream(uri)?.use { inputStream ->
    //                onDocumentRead(inputStream)
    //            }*/
    //        }

    // to pick media like images and videos, you can use Photo Picker, eg:
    //      val pickerLauncher = getVisualMediaPicker { ... }
    //      pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    //    @Composable
    //    fun Context.getMultipleVisualMediaPicker(onMediaSelected: (List<Uri>) -> Unit) =
    //        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uriList: List<Uri> ->
    //            onMediaSelected(uriList)
    //        }
    //
    //    @Composable
    //    fun Context.getVisualMediaPicker(onMediaSelected: (Uri?) -> Unit) =
    //        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    //            onMediaSelected(uri)
    //        }

    private fun Context.getImagesDeletionRequest(uriList: List<Uri>): PendingIntent {
        return MediaStore.createDeleteRequest(contentResolver, uriList)
    }

    // Note when you move an image file and want to delete the original file
    // you call "MediaStore.createWriteRequest()" first (which show a window to ask permission for "modify", not "delete"),
    // then you can call contentResolver.delete(originalFile) directly without calling "MediaStore.createDeleteRequest()"
    // Note when the user approves "MediaStore.createDeleteRequest", the files are deleted,
    // when the user approves "MediaStore.createWriteRequest", no file is modified, you just get the permission to modify
    private fun Context.getImagesWriteRequest(uriList: List<Uri>): PendingIntent {
        return MediaStore.createWriteRequest(contentResolver, uriList)
    }

    // Note you cannot just use File.toUri() for MediaStore access, because such uri does not have the corresponding permissions in MediaStore
    // To avoid such problem, use content uri instead of file uri
    private suspend fun Context.getContentUriForImageFile(fileAbsolutePathList: List<String>): List<MediaStoreItem> {
        // search _ID(content uri) by DATA(file uri) by MediaStore
        val mediaStoreItems: MutableList<MediaStoreItem> = mutableListOf()
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.MIME_TYPE
        )
        val selection =
            "${MediaStore.Images.Media.DATA} in (${fileAbsolutePathList.joinToString(separator = ",") { "'$it'" }})"
        //val selectionArgs = arrayOf()
        // val sortOrder = "${MediaStore.Images.Media.DATA} ASC"

        withContext(Dispatchers.IO) {
            contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val data = cursor.getString(dataColumn)
                    // https://developer.android.com/training/data-storage/shared/media#query-collection
                    val contentUri: Uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val mimeType: String = cursor.getString(mimeColumn)
                    mediaStoreItems.add(MediaStoreItem(data, contentUri, mimeType))
                }
            }
        }
        return mediaStoreItems
    }

    // if PendingIntent is null, it means somehow some images cannot be deleted
    suspend fun Context.requestImageFileDeletion(imagePathList: List<String>): PendingIntent? {
        val mediaStoreItems = getContentUriForImageFile(imagePathList)
        return if (mediaStoreItems.size != imagePathList.size) {
            Log.w(
                getCallSiteInfoFunc(),
                "Failed to get content uri for some files: ${mediaStoreItems.filter { it.absolutePath !in imagePathList }}"
            )
            null
        } else {
            getImagesDeletionRequest(mediaStoreItems.map { it.contentUri })
        }
    }

    // we also return contentUri so that we can access them after gain modify permission based on these contentUri
    suspend fun Context.requestImageFileModification(imagePathList: List<String>): MediaModifyRequest {
        val mediaStoreItems = getContentUriForImageFile(imagePathList)
        if (mediaStoreItems.size != imagePathList.size) {
            Log.w(
                getCallSiteInfoFunc(),
                "Failed to get content uri for some files: ${mediaStoreItems.filter { it.absolutePath !in imagePathList }}"
            )
        }
        return MediaModifyRequest(
            pendingIntent = if (mediaStoreItems.size == imagePathList.size) getImagesWriteRequest(mediaStoreItems.map { it.contentUri }) else null,
            mediaStoreItems = mediaStoreItems
        )

    }

    data class MediaModifyRequest(
        val pendingIntent: PendingIntent?,
        val mediaStoreItems: List<MediaStoreItem>
    )

    data class MediaStoreItem(
        val absolutePath: String,
        val contentUri: Uri,
        val mimeType: String
    )
}

