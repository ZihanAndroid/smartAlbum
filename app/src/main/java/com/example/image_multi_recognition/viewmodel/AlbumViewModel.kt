package com.example.image_multi_recognition.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import com.example.image_multi_recognition.repository.ImageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel @Inject constructor(
    repository: ImageRepository
): ViewModel(){
    val albumPagingFlow = repository.getAlbumPagingFlow().cachedIn(viewModelScope)
}