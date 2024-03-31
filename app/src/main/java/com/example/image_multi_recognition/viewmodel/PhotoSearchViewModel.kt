package com.example.image_multi_recognition.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.db.ImageLabel
import com.example.image_multi_recognition.db.LabelInfo
import com.example.image_multi_recognition.db.LabelWithLatestImage
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.viewmodel.basic.LabelSearchSupport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotoSearchViewModel @Inject constructor(
    val repository: ImageRepository
) : ViewModel(), LabelSearchSupport {
    private var _labelImagesFlow = MutableStateFlow<List<LabelWithLatestImage>>(emptyList())
    val labelImagesFlow: StateFlow<List<LabelWithLatestImage>>
        get() = _labelImagesFlow

    override var orderedLabelList: List<LabelInfo> = emptyList()

    init {
        viewModelScope.launch {
            _labelImagesFlow.value = repository.getImagesByLabel("")
            orderedLabelList = repository.getAllOrderedLabelList()
        }
    }

    fun searchImagesByLabel(label: String) {
        viewModelScope.launch {
            _labelImagesFlow.value = repository.getImagesByLabel(label)
        }
    }
}