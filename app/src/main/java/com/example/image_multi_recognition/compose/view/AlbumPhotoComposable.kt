package com.example.image_multi_recognition.compose.view

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.image_multi_recognition.compose.navigation.Destination
import com.example.image_multi_recognition.compose.statelessElements.TopAppBarForNotRootDestination
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.viewmodel.PhotoViewModel

@Composable
fun AlbumPhotoComposable(
    viewModel: PhotoViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onImageClick: (album: Long, originalIndex: Int) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBarForNotRootDestination(
                title = AlbumPathDecoder.decodeAlbumName(viewModel.currentAlbum),
                onBack = onBack
            )
        },
        modifier = modifier
    ) {
        PhotoComposable(
            viewModel = viewModel,
            onImageClick = { album, originalIndex ->
                onImageClick(album, originalIndex)
            },
            modifier = Modifier.padding(it)
        )
    }
}