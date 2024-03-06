package com.example.image_multi_recognition.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.image_multi_recognition.repository.ImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

//@HiltViewModel
//class AlbumPhotoViewModel @Inject constructor(
//    repository: ImageRepository,
//    savedStateHandle: SavedStateHandle,
//): PhotoViewModel(repository, savedStateHandle.get<Long>("album")){
//
//    init {
//        setImagePagingFlow(currentAlbum!!)
//    }
//}