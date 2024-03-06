package com.example.image_multi_recognition.compose.view

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.image_multi_recognition.compose.navigation.Destination
import com.example.image_multi_recognition.compose.statelessElements.ImagePagerView
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.viewmodel.LabelPhotoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelPhotoComposable(
    viewModel: LabelPhotoViewModel,
    modifier: Modifier = Modifier,
    onImageClick: (Int, String) -> Unit,
    onBack: () -> Unit
) {
    val pagingItems = viewModel.pagingFlow.collectAsLazyPagingItems()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = viewModel.label.let { albumName ->
                            if (albumName.length > 30) {
                                albumName.substring(0, 30) + "..."
                            } else {
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
    ) { paddingValue ->
        ImagePagerView(
            modifier = modifier.padding(paddingValue),
            pagingItems = pagingItems,
            onImageClick = { originalIndex ->
                onImageClick(originalIndex, viewModel.label)
            },
            onSendThumbnailRequest = viewModel::requestThumbnail
        )
    }
}