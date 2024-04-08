package com.example.image_multi_recognition.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.db.ImageLabel
import com.example.image_multi_recognition.db.LabelInfo
import com.example.image_multi_recognition.db.LabelWithLatestImage
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.repository.UserSettingRepository
import com.example.image_multi_recognition.viewmodel.basic.LabelSearchSupport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotoSearchViewModel @Inject constructor(
    val repository: ImageRepository,
    private val settingRepository: UserSettingRepository,
) : ViewModel(), LabelSearchSupport {
    private var _labelImagesFlow = MutableStateFlow<List<LabelWithLatestImage>>(emptyList())
    val labelImagesFlow: StateFlow<List<LabelWithLatestImage>>
        get() = _labelImagesFlow

    private var orderedLabelListFlow: Flow<List<LabelInfo>> = repository.getAllOrderedLabelListFlow()
    override var orderedLabelList: List<LabelInfo> = emptyList()
    val excludedLabelsSetFlow = settingRepository.excludedLabelsSetFlow

    init {
        viewModelScope.launch {
            _labelImagesFlow.value = repository.getImagesByLabel("")
            excludedLabelsSetFlow.collectLatest { excludedLabels ->
                orderedLabelListFlow.collectLatest { labelList ->
                    orderedLabelList = labelList.filter { it.label !in excludedLabels }
                }
            }
        }
    }

    fun searchImagesByLabel(label: String) {
        viewModelScope.launch {
            _labelImagesFlow.value = repository.getImagesByLabel(label)
        }
    }
}