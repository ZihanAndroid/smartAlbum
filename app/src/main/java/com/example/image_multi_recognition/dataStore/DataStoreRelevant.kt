package com.example.image_multi_recognition.dataStore

import android.os.Environment
import androidx.datastore.core.Serializer
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.copy
import com.example.image_multi_recognition.util.capitalizeFirstChar
import com.example.image_multi_recognition.viewmodel.basic.LabelingState
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

// this serializer is used by Proto DataStore
@Singleton
class AppDataSerializer @Inject constructor() : Serializer<AppData> {
    // default setting
    override val defaultValue: AppData = defaultAppData

    override suspend fun readFrom(input: InputStream): AppData {
        return AppData.parseFrom(input)
    }

    override suspend fun writeTo(t: AppData, output: OutputStream) {
        t.writeTo(output)
    }

    companion object {
        val defaultAppData: AppData = AppData.getDefaultInstance().copy {
            themeSetting = AppData.Theme.SYSTEM_DEFAULT
            // default album: DCIM/Camera
            defaultAlbumPath =
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera").absolutePath
            imagesPerRow = 4
            imageCacheEnabled = false
            thumbNailQuality = 0.1f
            imageLabelingConfidence = 0.7f
            labelingStatus = AppData.LabelingStatus.NOT_STARTED
            workerResultFileName = ""
        }

        // naming convention: the enum names are capital letters and underscores
        fun convertEnumToString(theme: AppData.Theme): String {
            return theme.toString().lowercase().capitalizeFirstChar().replace("_", " ")
        }

        fun convertStringToEnum(str: String): AppData.Theme {
            return AppData.Theme.valueOf(str.replace(" ", "_").uppercase())
        }
    }
}