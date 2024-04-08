package com.example.image_multi_recognition.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.domain.GetAllUnlabeledImagesUseCase
import com.example.image_multi_recognition.domain.GetUnlabeledAlbumsUseCase
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.repository.UserSettingRepository
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
    settingRepository: UserSettingRepository,
    getAllUnlabeledImagesUseCase: GetAllUnlabeledImagesUseCase,
    getUnlabeledAlbumsUseCase: GetUnlabeledAlbumsUseCase,
    objectDetector: ObjectDetector,
    imageLabeler: ImageLabeler,
) : LabelingSupportViewModel(repository, settingRepository, objectDetector, imageLabeler) {

    val unlabeledImageListFlow: StateFlow<List<ImageInfo>> =
        getAllUnlabeledImagesUseCase().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val unlabeledImageAlbumFlow = with(getUnlabeledAlbumsUseCase) { viewModelScope.invoke() }
}