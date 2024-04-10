package com.example.image_multi_recognition.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.repository.UserSettingRepository
import com.example.image_multi_recognition.viewmodel.basic.ImagePagingFlowSupport
import com.example.image_multi_recognition.viewmodel.basic.ImagePagingFlowSupportImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class LabelPhotoViewModel @Inject constructor(
    val repository: ImageRepository,
    val settingRepository: UserSettingRepository,
    savedStateHandle: SavedStateHandle,
    imagePagingFlowSupportImpl: ImagePagingFlowSupportImpl,
) : ViewModel(), ImagePagingFlowSupport by imagePagingFlowSupportImpl {
    val label = savedStateHandle.get<String>("label") ?: ""
    val pagingFlow = repository.getImagePagingFlowByLabel(label)
        .convertImageInfoPagingFlow(ImagePagingFlowSupport.PagingSourceType.LABEL_IMAGE).cachedIn(viewModelScope)
    private var _labelRemoving: MutableStateFlow<Boolean?> = MutableStateFlow(null)
    val labelRemoving: StateFlow<Boolean?>
        get() = _labelRemoving

    private val thumbnailQualityFlow = settingRepository.thumbNailQualityFlow
    private var thumbnailQuality: Float = 0.1f

    init {
        viewModelScope.launch { thumbnailQualityFlow.collectLatest { thumbnailQuality = it } }
    }

    val imagesPerRowFlow = settingRepository.imagesPerRowFlow

    // after deletion, you can do some cleanup to make sure the UI shows the right info
    fun removeLabels(imageIds: List<Long>, onComplete: suspend () -> Unit) {
        viewModelScope.launch {
            _labelRemoving.value = true
            repository.removeImageLabelsByLabel(label, imageIds)
            onComplete()
            _labelRemoving.value = false
        }
    }
}