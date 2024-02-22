package com.example.image_multi_recognition.db

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.room.*
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.util.ExifHelper
import com.example.image_multi_recognition.util.ScopedThumbNailStorage
import com.example.image_multi_recognition.util.getCallSiteInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

@Entity(
    tableName = "IMAGE_INFO",
    indices = [Index(value = ["album", "path"], unique = true)]
)
data class ImageInfo(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") var id: Long = 0,
    @ColumnInfo(name = "path") val path: String, // relative path
    @ColumnInfo(name = "album") val album: String,  // directory, also used as album
    @ColumnInfo(name = "labeled") var labeled: Boolean,
    @ColumnInfo(name = "time_created") val timestamp: Long,
    // cache thumbnail's path instead of thumbnail itself
    // https://developer.android.com/topic/performance/sqlite-performance-best-practices#store-small
    // @ColumnInfo(name = "cached_image", typeAffinity = ColumnInfo.BLOB) var cachedImage: ByteArray = ByteArray(0)

) {
    @Ignore
    val fullImageFile: File = File(AlbumPathDecoder.decode(album), path)

    @Ignore
    val thumbnailFile: File =
        File(ScopedThumbNailStorage.imageStorage, "${album.replace("/", "-")}${path.replace("/", "_")}")

    val rotationDegree: Int
        get() = ExifHelper.getImageRotationDegree(fullImageFile)

    val isThumbnailAvailable: Boolean
        get() = thumbnailFile.exists() && thumbnailFile.length() > 0

    // under each directory, we put the thumbnails into app's scoped external storage (not shared storage)
    fun setImageCache(bitmap: Bitmap) {
        try {
            if(!thumbnailFile.exists() || thumbnailFile.length() == 0L) {
                FileOutputStream(thumbnailFile).use { outputStream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 10, outputStream)) {
                        throw IOException("Failed to compress bitmap!")
                    }
                    Log.d(getCallSiteInfo(), "Thumbnail size: ${String.format("%.f", thumbnailFile.length() / 1024.0)}KB")
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
)

// ImageInfo:ImageBound, one to many
@Entity(
    tableName = "IMAGE_BOUNDS",
    foreignKeys = [ForeignKey(
        entity = ImageInfo::class,
        parentColumns = ["id"],
        childColumns = ["id"],
        onDelete = ForeignKey.CASCADE,
        onUpdate = ForeignKey.CASCADE
    )],
    primaryKeys = ["id", "rect"]
)
data class ImageBound(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "rect") val rect: Rect,
)