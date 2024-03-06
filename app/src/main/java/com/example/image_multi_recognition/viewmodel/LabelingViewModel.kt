package com.example.image_multi_recognition.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.viewmodel.basic.LabelingSupportViewModel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.objects.ObjectDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LabelingViewModel @Inject constructor(
    repository: ImageRepository,
    objectDetector: ObjectDetector,
    imageLabeler: ImageLabeler,
) : LabelingSupportViewModel(repository, objectDetector, imageLabeler) {

    val unlabeledImageListFlow: StateFlow<List<ImageInfo>> =
        repository.getAllUnlabeledImages().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val unlabeledImageAlbumFlow = repository.getUnlabeledAlbumPagerFlow().cachedIn(viewModelScope)
}