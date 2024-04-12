package com.example.image_multi_recognition.viewmodel.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.image_multi_recognition.db.AlbumInfoWithLatestImage
import com.example.image_multi_recognition.db.LabelInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.repository.UserSettingRepository
import com.example.image_multi_recognition.viewmodel.basic.LabelSearchSupport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FullScreenSelectionViewModel @Inject constructor(
    private val settingRepository: UserSettingRepository,
    private val imageRepository: ImageRepository,
) : ViewModel(), LabelSearchSupport {
    var allAlbums: List<AlbumInfoWithLatestImage> = emptyList()
    override var orderedLabelList: List<LabelInfo> = emptyList()
    val orderedLabelListFlow: StateFlow<List<LabelInfo>> = imageRepository.getAllOrderedLabelListFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    val excludedLabelsListFlow: Flow<List<String>> = settingRepository.excludedLabelsListFlow

    init {
        viewModelScope.launch {
            // setting page never modify albums, so we just get the list once instead of monitoring a flow
            allAlbums = imageRepository.getAlbumInfoWithLatestImage()
            orderedLabelListFlow.collectLatest { labelList ->
                orderedLabelList = labelList
            }
        }
    }

    fun updateExcludedLabels(excludedLabels: List<String>) {
        viewModelScope.launch { settingRepository.updateExcludedLabels(excludedLabels) }
    }
}