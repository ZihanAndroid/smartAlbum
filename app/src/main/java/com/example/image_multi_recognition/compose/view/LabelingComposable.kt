package com.example.image_multi_recognition.compose.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.PagingItemImage
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.viewmodel.LabelUiModel
import com.example.image_multi_recognition.viewmodel.LabelingViewModel
import java.io.File

@Composable
fun LabelingComposable(
    modifier: Modifier = Modifier,
    viewModel: LabelingViewModel,
    onAlbumClick: (Long) -> Unit
) {
    var labelingClicked by rememberSaveable { mutableStateOf(false) }
    val labelingState by viewModel.labelingStateFlow.collectAsStateWithLifecycle()

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
                    // let the coroutine continue running to recognize the "scanCancelled" flag
                    viewModel.resumeScanPaused()
                    // change window content
                    labelingClicked = false
                },
                scanPaused = scanPaused
            )
        }
    } else {
        val unlabeledImageList by viewModel.unlabeledImageListFlow.collectAsStateWithLifecycle()
        val albumPagingItems = viewModel.unlabeledImageAlbumFlow.collectAsLazyPagingItems()
        val gridState = rememberLazyGridState()

        Column {
            UnlabeledInfo(unlabeledImageList.size) {
                viewModel.scanImages(unlabeledImageList)
                labelingClicked = true
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(DefaultConfiguration.ALBUM_PER_ROW),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(DefaultConfiguration.ALBUM_INTERVAL.dp),
                verticalArrangement = Arrangement.spacedBy(DefaultConfiguration.ALBUM_INTERVAL.dp),
                modifier = modifier.padding(horizontal = DefaultConfiguration.ALBUM_INTERVAL.dp)
            ) {
                items(
                    count = albumPagingItems.itemCount
                ) { index ->
                    albumPagingItems[index]?.let { albumWithLatestImage ->
                        key(albumWithLatestImage.album) {
                            AlbumPagingItem(
                                // albumImage = albumWithLatestImage,
                                imagePath = File(
                                    AlbumPathDecoder.decode(albumWithLatestImage.album),
                                    albumWithLatestImage.path
                                ),
                                title = "${AlbumPathDecoder.decodeAlbumName(albumWithLatestImage.album)} (${albumWithLatestImage.count})",
                                contentDescription = albumWithLatestImage.album.toString(),
                                sizeDp = ((LocalConfiguration.current.screenWidthDp - DefaultConfiguration.ALBUM_INTERVAL * 3) / 2).dp,
                                onAlbumClick = { onAlbumClick(albumWithLatestImage.album) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageLabelingResultShow(
    pagingItems: LazyPagingItems<LabelUiModel>,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(DefaultConfiguration.IMAGE_PER_ROW),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(DefaultConfiguration.IMAGE_INTERVAL.dp),
        verticalArrangement = Arrangement.spacedBy(DefaultConfiguration.IMAGE_INTERVAL.dp),
        modifier = modifier
    ) {
        items(
            count = pagingItems.itemCount,
            span = { index ->
                pagingItems[index]?.let { item ->
                    when (item) {
                        is LabelUiModel.Label -> {
                            GridItemSpan(DefaultConfiguration.IMAGE_PER_ROW)
                        }

                        is LabelUiModel.Item -> {
                            GridItemSpan(1)
                        }
                    }
                } ?: GridItemSpan(1)
            }
        ) { index ->
            pagingItems[index]?.let { item ->
                when (item) {
                    is LabelUiModel.Label -> {
                        Text(
                            text = item.label,
                            modifier = modifier.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    is LabelUiModel.Item -> {
                        PagingItemImage(
                            imageInfo = item.imageInfo,
                            onImageClick = { },
                            availableScreenWidth = LocalConfiguration.current.screenWidthDp,
                            onSendThumbnailRequest = { _, _ -> }
                        )
                    }
                }
            }
        }
    }
    if(pagingItems.itemCount == 0){
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ){
            Text(
                text = stringResource(R.string.no_recognized_image),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun LabelingOnProgress(
    progress: Float,
    text: String,
    scanPaused: Boolean,
    onResumePauseClicked: () -> Unit,
    onCancelClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LinearProgressIndicator(
                progress = progress
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
            Row {
                ElevatedButton(
                    onClick = onResumePauseClicked
                ) {
                    Text(stringResource(if (scanPaused) R.string.resume else R.string.pause))
                }
                ElevatedButton(
                    onClick = onCancelClicked
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
fun UnlabeledInfo(
    unlabeledImageCount: Int,
    modifier: Modifier = Modifier,
    onLabelingClick: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.unlabeled_image_count, unlabeledImageCount)
        )
        IconButton(
            onClick = onLabelingClick
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.baseline_new_label_24),
                contentDescription = "autoLabeling",
            )
        }
    }
}