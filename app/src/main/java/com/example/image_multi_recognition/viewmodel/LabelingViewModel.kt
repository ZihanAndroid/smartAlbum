package com.example.image_multi_recognition.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.domain.GetAllUnlabeledImagesUseCase
import com.example.image_multi_recognition.domain.GetUnlabeledAlbumsUseCase
import com.example.image_multi_recognition.permission.PermissionAccessor
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.repository.UserSettingRepository
import com.example.image_multi_recognition.repository.WorkManagerRepository
import com.example.image_multi_recognition.viewmodel.basic.LabelingSupportViewModel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.objects.ObjectDetector
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LabelingViewModel @Inject constructor(
    repository: ImageRepository,
    settingRepository: UserSettingRepository,
    workManagerRepository: WorkManagerRepository,
    getAllUnlabeledImagesUseCase: GetAllUnlabeledImagesUseCase,
    getUnlabeledAlbumsUseCase: GetUnlabeledAlbumsUseCase,
    objectDetector: ObjectDetector,
    imageLabeler: ImageLabeler,
    val permissionAccessor: PermissionAccessor,
    moshi: Moshi
) : LabelingSupportViewModel(repository, settingRepository, workManagerRepository, objectDetector, imageLabeler, moshi) {

    val unlabeledImageListFlow: StateFlow<List<ImageInfo>> =
        getAllUnlabeledImagesUseCase().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val unlabeledImageAlbumFlow = with(getUnlabeledAlbumsUseCase) { viewModelScope.invoke() }
}

