package com.example.image_multi_recognition.viewmodel.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.db.AlbumInfoWithLatestImage
import com.example.image_multi_recognition.db.LabelInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.repository.UserSettingRepository
import com.example.image_multi_recognition.viewmodel.basic.LabelSearchSupport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingScreenViewModel @Inject constructor(
    private val settingRepository: UserSettingRepository,
    private val imageRepository: ImageRepository,
) : ViewModel() {
    val themeSettingFlow: Flow<AppData.Theme> = settingRepository.themeSettingFlow
    val defaultAlbumPathFlow: Flow<String> = settingRepository.defaultAlbumPathFlow
    val imagesPerRowFlow: Flow<Int> = settingRepository.imagesPerRowFlow
    val imageCacheEnabledFlow: Flow<Boolean> = settingRepository.imageCacheEnabledFlow
    val thumbNailQualityFlow: Flow<Float> = settingRepository.thumbNailQualityFlow
    val imageLabelingConfidenceFlow: Flow<Float> = settingRepository.imageLabelingConfidenceFlow
    val excludedLabelsListFlow: Flow<List<String>> = settingRepository.excludedLabelsListFlow
    val excludedAlbumPathsListFlow: Flow<List<String>> = settingRepository.excludedAlbumPathsListFlow
    var allAlbums: List<AlbumInfoWithLatestImage> = emptyList()

    init {
        viewModelScope.launch {
            // setting page never modify albums, so we just get the list once instead of monitoring a flow
            allAlbums = imageRepository.getAlbumInfoWithLatestImage()
        }
    }

    fun updateThemeSetting(newTheme: AppData.Theme) {
        viewModelScope.launch { settingRepository.updateThemeSetting(newTheme) }
    }

    fun updateDefaultAlbumPath(absolutePath: String) {
        viewModelScope.launch { settingRepository.updateDefaultAlbumPath(absolutePath) }
    }

    fun updateImagesPerRow(imagesPerRow: Int) {
        viewModelScope.launch { settingRepository.updateImagesPerRow(imagesPerRow) }
    }

    fun updateImageCacheEnabled(cacheEnabled: Boolean) {
        viewModelScope.launch { settingRepository.updateImageCacheEnabled(cacheEnabled) }
    }

    fun updateThumbnailQuality(quality: Float) {
        viewModelScope.launch { settingRepository.updateThumbnailQuality(quality) }
    }

    fun updateImageLabelingConfidence(confidence: Float) {
        viewModelScope.launch { settingRepository.updateImageLabelingConfidence(confidence) }
    }

    fun updateExcludedAlbumPaths(excludedAlbumPaths: List<String>) {
        viewModelScope.launch { settingRepository.updateExcludedAlbumPaths(excludedAlbumPaths) }
    }

    fun updateExcludedLabels(excludedLabels: List<String>) {
        viewModelScope.launch { settingRepository.updateExcludedLabels(excludedLabels) }
    }
}
