package com.example.image_multi_recognition.compose.view

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.image_multi_recognition.compose.statelessElements.ImagePagerView
import com.example.image_multi_recognition.compose.navigation.Destination
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.viewmodel.PhotoViewModel

@Composable
fun PhotoComposable(
    modifier: Modifier = Modifier,
    viewModel: PhotoViewModel,
    navController: NavController
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val pagingDataFlow by viewModel.pagingFlow.collectAsStateWithLifecycle()
    val pagingItems = pagingDataFlow.collectAsLazyPagingItems()

    LaunchedEffect(true) {
        Log.d(getCallSiteInfo(), "viewModel.scanImages() is called")
        viewModel.scanImages()
    }

    Log.i("PhotoComposable", "Here. PhotoComposable")
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.fillMaxSize()
        )
    } else {
        ImagePagerView(
            modifier = modifier,
            pagingItems = pagingItems,
            onImageClick = { originalIndex ->
                Log.d(getCallSiteInfoFunc(), "Navigating to: ${Destination.SINGLE_IMAGE.navRoute}")
                "${Destination.SINGLE_IMAGE.route}/${viewModel.currentAlbum}/$originalIndex".let { route ->
                    Log.d(getCallSiteInfoFunc(), "Navigation route: $route")
                    navController.navigate(route){
                        launchSingleTop = true
                    }
                }
            },   // Not implemented yet
            availableScreenWidth = LocalConfiguration.current.screenWidthDp,
            onSendThumbnailRequest = viewModel::sendImageRequestForThumbnail
        )
    }
}