package com.example.image_multi_recognition.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.viewmodel.basic.ImagePagingFlowSupport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LabelPhotoViewModel @Inject constructor(
    val repository: ImageRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel(), ImagePagingFlowSupport {
    val label = savedStateHandle.get<String>("label") ?: ""
    val pagingFlow = repository.getImagePagingFlowByLabel(label).convertImageInfoPagingFlow().cachedIn(viewModelScope)

    private var _labelRemoving: MutableStateFlow<Boolean?> = MutableStateFlow(null)
    val labelRemoving: StateFlow<Boolean?>
        get() = _labelRemoving

    fun requestThumbnail(file: File, imageInfo: ImageInfo) {
        repository.genImageRequest(file, imageInfo)
    }

    // after deletion, you can do some cleanup to make sure the UI shows the right info
    fun removeLabels(imageIds: List<Long>, onComplete: ()->Unit){
        viewModelScope.launch {
            _labelRemoving.value = true
            repository.removeImageLabelsByLabel(label, imageIds)
            onComplete()
            _labelRemoving.value = false
        }
    }
}