package com.example.image_multi_recognition.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.db.AlbumInfo
import com.example.image_multi_recognition.db.AlbumInfoWithLatestImage
import com.example.image_multi_recognition.db.LabelInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.repository.UserSettingRepository
import com.example.image_multi_recognition.util.ControlledRunner
import com.example.image_multi_recognition.util.listDistinct
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingScreenViewModel @Inject constructor(
    private val settingRepository: UserSettingRepository,
    private val imageRepository: ImageRepository,
) : ViewModel() {
    val themeSettingFlow: Flow<AppData.Theme> =
        settingRepository.settingFlow.map { it.themeSetting }.distinctUntilChanged()
    val defaultAlbumPathFlow: Flow<String> =
        settingRepository.settingFlow.map { it.defaultAlbumPath }.distinctUntilChanged()
    val imagesPerRowFlow: Flow<Int> = settingRepository.settingFlow.map { it.imagesPerRow }.distinctUntilChanged()
    val imageCacheEnabledFlow: Flow<Boolean> =
        settingRepository.settingFlow.map { it.imageCacheEnabled }.distinctUntilChanged()
    val thumbNailQualityFlow: Flow<Float> =
        settingRepository.settingFlow.map { it.thumbNailQuality }.distinctUntilChanged()
    val imageLabelingConfidenceFlow: Flow<Float> =
        settingRepository.settingFlow.map { it.imageLabelingConfidence }.distinctUntilChanged()
    val excludedLabelsListFlow: Flow<List<String>> =
        settingRepository.settingFlow.map { it.excludedLabelsList }.distinctUntilChanged(::listDistinct)
    val excludedAlbumPathsListFlow: Flow<List<String>> =
        settingRepository.settingFlow.map { it.excludedAlbumPathsList }.distinctUntilChanged(::listDistinct)

    private val _albumListStateFlow: MutableStateFlow<List<AlbumInfoWithLatestImage>> = MutableStateFlow(emptyList())

    var allAlbums: List<AlbumInfoWithLatestImage> = emptyList()
    var allLabels: List<LabelInfo> = emptyList()
    init {
        viewModelScope.launch {
            // setting page never modify albums, so we just get the list once instead of monitoring a flow
            allAlbums = imageRepository.getAlbumInfoWithLatestImage()
            allLabels = imageRepository.getAllOrderedLabelList()
        }
    }

    suspend fun getInitialSetting(): AppData = settingRepository.settingFlow.first()

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
