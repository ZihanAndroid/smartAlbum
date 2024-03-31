package com.example.image_multi_recognition.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.util.toPagingSource
import com.example.image_multi_recognition.viewmodel.basic.ImagePagingFlowSupport
import com.example.image_multi_recognition.viewmodel.basic.ImagePagingFlowSupportImpl
import com.example.image_multi_recognition.viewmodel.basic.LabelingSupportViewModel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.objects.ObjectDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class AlbumPhotoLabelingViewModel @Inject constructor(
    private val repository: ImageRepository,
    objectDetector: ObjectDetector,
    imageLabeler: ImageLabeler,
    savedStateHandle: SavedStateHandle,
    imagePagingFlowSupportImpl: ImagePagingFlowSupportImpl,
) : LabelingSupportViewModel(repository, objectDetector, imageLabeler),
    ImagePagingFlowSupport by imagePagingFlowSupportImpl {
    val album = savedStateHandle.get<Long>("album")!!

    private val unlabeledImageInAlbumFlow = repository.getUnlabeledImagesByAlbum(album)
    val unlabeledImageInAlbumStateFlow: StateFlow<List<ImageInfo>> =
        unlabeledImageInAlbumFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())
    private val _unlabeledImagePagingFlow: MutableStateFlow<Flow<PagingData<UiModel>>> = MutableStateFlow(flowOf())
    val unlabeledImagePagingFlow: StateFlow<Flow<PagingData<UiModel>>>
        get() = _unlabeledImagePagingFlow

    init {
        viewModelScope.launch {
            // convert the list to pagingSource
            unlabeledImageInAlbumStateFlow.collect { imageInfoList ->
                _unlabeledImagePagingFlow.value = Pager(
                    config = PagingConfig(
                        pageSize = DefaultConfiguration.PAGE_SIZE,
                        enablePlaceholders = true
                    ),
                    pagingSourceFactory = { imageInfoList.toPagingSource(DefaultConfiguration.PAGE_SIZE) }
                ).flow.convertImageInfoPagingFlow(ImagePagingFlowSupport.PagingSourceType.UNLABELED_IMAGE)
                    .cachedIn(viewModelScope)
            }
        }
    }

    fun requestThumbnail(file: File, imageInfo: ImageInfo) {
        repository.genImageRequest(file, imageInfo)
    }
}