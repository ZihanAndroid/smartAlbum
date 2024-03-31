package com.example.image_multi_recognition.db

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.util.Size
import androidx.room.*
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.util.ExifHelper
import com.example.image_multi_recognition.util.ScopedThumbNailStorage
import com.example.image_multi_recognition.util.getCallSiteInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@Entity(
    tableName = "IMAGE_INFO",
    foreignKeys = [ForeignKey(
        entity = AlbumInfo::class,
        parentColumns = ["album"],
        childColumns = ["album"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["album"])]
)
data class ImageInfo(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "path") val path: String, // image file name
    @ColumnInfo(name = "album") val album: Long,  // directory, also used as album
    // @ColumnInfo(name = "labeled") var labeled: Boolean,
    // @ColumnInfo(name = "albumId") val albumId: Long,
    @ColumnInfo(name = "time_created") val timestamp: Long,
    @ColumnInfo(name = "favorite") val favorite: Boolean = false,
    // cache thumbnail's path instead of thumbnail itself
    // https://developer.android.com/topic/performance/sqlite-performance-best-practices#store-small
    // @ColumnInfo(name = "cached_image", typeAffinity = ColumnInfo.BLOB) var cachedImage: ByteArray = ByteArray(0)

) {
    @Ignore
    val fullImageFile: File = File(AlbumPathDecoder.decode(album), path)

    @Ignore
    val thumbnailFile: File =
        File(ScopedThumbNailStorage.imageStorage, "${album}_${path.replace("/", "_")}")

//    @Ignore
//    val thumbnailSize: Size = ExifHelper.getImageSize(fullImageFile)

    val rotationDegree: Int
        get() = ExifHelper.getImageRotationDegree(fullImageFile)

    val isThumbnailAvailable: Boolean
        get() = thumbnailFile.exists() && thumbnailFile.length() > 0

    // under each directory, we put the thumbnails into app's scoped external storage (not shared storage)
    // Not main-thread safe
    fun setImageCache(bitmap: Bitmap) {
        try {
            if (!thumbnailFile.exists() || thumbnailFile.length() == 0L) {
                FileOutputStream(thumbnailFile).use { outputStream ->
                    // it seems that compressing to WEBP_LOSSY is much slower than JPEG
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 15, outputStream)) {
                        throw IOException("Failed to compress bitmap!")
                    }
                    Log.d(
                        getCallSiteInfo(),
                        "Thumbnail size: ${String.format("%d", thumbnailFile.length() / 1024)}KB"
                    )
                }
            }
        } catch (e: IOException) {
            Log.e(
                getCallSiteInfo(),
                "Failed to write bitmap to thumbnail file: $thumbnailFile\n${e.stackTraceToString()}"
            )
        }
    }
}

// ImageInfo:ImageLabel, one to many
@Entity(
    tableName = "IMAGE_LABELS",
    foreignKeys = [ForeignKey(
        entity = ImageInfo::class,
        parentColumns = ["id"],
        childColumns = ["id"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE
    )],
    primaryKeys = ["id", "label"]
)
data class ImageLabel(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "label") val label: String,
    // the "rect" value is based on original_image_width and original_image_height of the image file
    // If rect is null, it means that the rect is for whole image labels or user-added labels
    @ColumnInfo(name = "rect") val rect: Rect?,
)

@Entity(
    tableName = "ALBUM_INFO",
)
data class AlbumInfo(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "album") var album: Long = 0,
    // @PrimaryKey @ColumnInfo(name = "album") val album: String,  // album name, it may equal to the directory name or not
    @ColumnInfo(name = "path") val path: String, // absolute path
)