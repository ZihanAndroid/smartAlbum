package com.example.image_multi_recognition.repository

import android.util.Log
import androidx.datastore.core.DataStore
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSettingRepository @Inject constructor(
    private val appDataStore: DataStore<AppData>,
) {
    val settingFlow: Flow<AppData> = appDataStore.data.catch { e ->
        if (e is IOException) {
            Log.e(getCallSiteInfoFunc(), "Error in AppData Datastore:\n${e.stackTraceToString()}")
            emit(AppData.getDefaultInstance())
        } else {
            throw e
        }
    }

    suspend fun updateThemeSetting(newTheme: AppData.Theme) {
        appDataStore.updateData { it.toBuilder().setThemeSetting(newTheme).build() }
    }

    suspend fun updateDefaultAlbumPath(absolutePath: String) {
        appDataStore.updateData { it.toBuilder().setDefaultAlbumPath(absolutePath).build() }
    }

    suspend fun updateImagesPerRow(imagesPerRow: Int) {
        appDataStore.updateData { it.toBuilder().setImagesPerRow(imagesPerRow).build() }
    }

    suspend fun updateImageCacheEnabled(cacheEnabled: Boolean) {
        appDataStore.updateData { it.toBuilder().setImageCacheEnabled(cacheEnabled).build() }
    }

    suspend fun updateThumbnailQuality(quality: Float) {
        appDataStore.updateData { it.toBuilder().setThumbNailQuality(quality).build() }
    }

    suspend fun updateImageLabelingConfidence(confidence: Float) {
        appDataStore.updateData { it.toBuilder().setImageLabelingConfidence(confidence).build() }
    }

    suspend fun updateExcludedAlbumPaths(excludedAlbumPaths: List<String>) {
        appDataStore.updateData {
            it.toBuilder().clearExcludedAlbumPaths().addAllExcludedAlbumPaths(excludedAlbumPaths).build()
        }
    }

    suspend fun updateExcludedLabels(excludedLabels: List<String>) {
        appDataStore.updateData { it.toBuilder().clearExcludedLabels().addAllExcludedLabels(excludedLabels).build() }
    }
}