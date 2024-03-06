package com.example.image_multi_recognition.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.viewmodel.basic.ImagePagingFlowSupport
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LabelPhotoViewModel @Inject constructor(
    val repository: ImageRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel(), ImagePagingFlowSupport {
    val label = savedStateHandle.get<String>("label") ?: ""
    val pagingFlow = repository.getImagePagingFlowByLabel(label).convertImageInfoPagingFlow().cachedIn(viewModelScope)

    fun requestThumbnail(file: File, imageInfo: ImageInfo) {
        repository.genImageRequest(file, imageInfo)
    }
}