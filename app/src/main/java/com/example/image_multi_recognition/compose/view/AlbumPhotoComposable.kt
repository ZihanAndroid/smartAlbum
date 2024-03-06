package com.example.image_multi_recognition.compose.view

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.example.image_multi_recognition.compose.navigation.Destination
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.viewmodel.PhotoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumPhotoComposable(
    viewModel: PhotoViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onImageClick: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = AlbumPathDecoder.decodeAlbumName(viewModel.currentAlbum).let { albumName ->
                            if(albumName.length > 30){
                                albumName.substring(0, 30) + "..."
                            }else{
                                albumName
                            }
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "back")
                    }
                }
            )
        },
        modifier = modifier
    ) {
        PhotoComposable(
            viewModel = viewModel,
            onImageClick = { originalIndex ->
                onImageClick("${Destination.SINGLE_IMAGE.route}/${viewModel.currentAlbum}/$originalIndex")
            },
            modifier = Modifier.padding(it)
        )
    }
}