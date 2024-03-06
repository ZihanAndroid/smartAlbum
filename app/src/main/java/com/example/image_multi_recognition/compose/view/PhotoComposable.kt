package com.example.image_multi_recognition.compose.view

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.image_multi_recognition.compose.navigation.Destination
import com.example.image_multi_recognition.compose.statelessElements.ImagePagerView
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.viewmodel.PhotoViewModel

@Composable
fun PhotoComposable(
    modifier: Modifier = Modifier,
    viewModel: PhotoViewModel,
    onImageClick: (album: Long, originalIndex: Int) -> Unit   //Int: index of selected image in LazyPagingItems
) {
    //val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val pagingDataFlow by viewModel.pagingFlow.collectAsStateWithLifecycle()
    val pagingItems = pagingDataFlow.collectAsLazyPagingItems()

    if (pagingItems.itemCount == 0) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
    } else {
        ImagePagerView(
            modifier = modifier,
            pagingItems = pagingItems,
            onImageClick = { originalIndex ->
                onImageClick(viewModel.currentAlbum!!, originalIndex)
            },
            onSendThumbnailRequest = viewModel::requestThumbnail
        )
    }
}