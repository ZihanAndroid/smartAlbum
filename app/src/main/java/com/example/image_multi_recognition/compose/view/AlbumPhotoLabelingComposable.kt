package com.example.image_multi_recognition.compose.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.ImagePagerView
import com.example.image_multi_recognition.compose.statelessElements.TopAppBarForNotRootDestination
import com.example.image_multi_recognition.viewmodel.AlbumPhotoLabelingViewModel

@Composable
fun AlbumPhotoLabelingComposable(
    viewModel: AlbumPhotoLabelingViewModel,
    modifier: Modifier = Modifier,
    onImageClick: (Long, Int) -> Unit,
    onBack: () -> Unit
) {
    val imageInfoList by viewModel.unlabeledImageInAlbumStateFlow.collectAsStateWithLifecycle()
    var labelingClicked by rememberSaveable { mutableStateOf(false) }
    val labelingState by viewModel.labelingStateFlow.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBarForNotRootDestination(
                title = if (labelingClicked) {
                    if (labelingState.labelingDone) {
                        stringResource(R.string.labeling_result)
                    } else {
                        "${stringResource(R.string.loading)}..."
                    }
                } else {
                    stringResource(R.string.unlabeled_image_count, imageInfoList.size)
                },
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.scanImages(imageInfoList)
                            labelingClicked = true
                        }
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.baseline_new_label_24),
                            contentDescription = "autoLabeling",
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = modifier.padding(paddingValues)) {
            if (labelingClicked) {
                if (labelingState.labelingDone) {
                    val pagingItems = viewModel.labelImagesFlow.collectAsLazyPagingItems()
                    ImageLabelingResultShow(pagingItems, modifier)
                } else {
                    val scanPaused by viewModel.scanPaused.collectAsStateWithLifecycle()
                    LabelingOnProgress(
                        progress = labelingState.labeledImageCount.toFloat() / viewModel.imageObjectsMap.size,
                        text = "${stringResource(R.string.loading)}...\t${labelingState.labeledImageCount}/${viewModel.imageObjectsMap.size}",
                        onResumePauseClicked = { viewModel.reverseScanPaused() },
                        onCancelClicked = {
                            viewModel.scanCancelled = true
                            viewModel.resumeScanPaused()
                            labelingClicked = false
                        },
                        scanPaused = scanPaused
                    )
                }
            } else {
                val imageListPagingItemsFlow by viewModel.unlabeledImagePagingFlow.collectAsStateWithLifecycle()
                val pagingItems = imageListPagingItemsFlow.collectAsLazyPagingItems()

                ImagePagerView(
                    modifier = modifier,
                    pagingItems = pagingItems,
                    onImageClick = { originalIndex ->
                        onImageClick(viewModel.album, originalIndex)
                    },
                    onSendThumbnailRequest = viewModel::requestThumbnail
                )
            }
        }
    }
}