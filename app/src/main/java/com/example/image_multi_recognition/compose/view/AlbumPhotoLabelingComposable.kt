package com.example.image_multi_recognition.compose.view

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.ImagePagerView
import com.example.image_multi_recognition.compose.statelessElements.TopAppBarForNotRootDestination
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.viewmodel.AlbumPhotoLabelingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

    // imageSelectedStateHolder and labelSelectedStateHolder are updated for "labelingState" change
    val imageSelectedStateHolder: Map<String, Map<Long, MutableState<Boolean>>> = remember(labelingState) {
        // viewModel.labelImagesMap is the backing data of pagingItem from viewModel.labelImagesFlow
        mapOf(*(viewModel.labelImagesMap.map { labelImages ->
            labelImages.key to mapOf(*labelImages.value.map { imageInfo ->
                imageInfo.id to mutableStateOf(true)
            }.toTypedArray())
        }.toTypedArray()))
    }
    val labelSelectedStateHolder: Map<String, MutableState<Boolean>> = remember(labelingState) {
        mapOf(*(viewModel.labelImagesMap.map { it.key to mutableStateOf(true) }).toTypedArray())
    }

    val labelAdding by viewModel.labelAddingStateFlow.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val labelAddedString = stringResource(R.string.label_added)

    // LaunchedEffect(labelAdding) {
    //     if (labelAdding == false) {
    //         // https://stackoverflow.com/questions/71471679/jetpack-compose-scaffold-possible-to-override-the-standard-durations-of-snackbar
    //         val job = launch {
    //             snackbarHostState.showSnackbar(labelAddedString, duration = SnackbarDuration.Indefinite)
    //         }
    //         delay(1000)
    //         job.cancel()
    //         labelingClicked = false
    //     }
    // }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    if (!labelingClicked) {
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
                    } else {
                        if (labelingState.labelingDone) {
                            IconButton(
                                onClick = {
                                    if(!labelAdding) {
                                        coroutineScope.launch {
                                            viewModel.onLabelingConfirm(imageSelectedStateHolder) {
                                                val job = launch {
                                                    snackbarHostState.showSnackbar(
                                                        labelAddedString,
                                                        duration = SnackbarDuration.Indefinite
                                                    )
                                                }
                                                delay(1000)
                                                job.cancel()
                                                labelingClicked = false
                                            }
                                        }
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "done",
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = modifier.padding(paddingValues)) {
            if (labelingClicked) {
                if (labelingState.labelingDone) {
                    val pagingItems = viewModel.labelImagesFlow.collectAsLazyPagingItems()
                    Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        ImageLabelingResultShow(
                            pagingItemsEmpty = viewModel.labelImagesMap.isEmpty(),
                            pagingItems = pagingItems,
                            imageSelectedStateHolderParam = imageSelectedStateHolder,
                            labelSelectedStateHolderParam = labelSelectedStateHolder
                        )
                    }

                } else {
                    val scanPaused by viewModel.scanPaused.collectAsStateWithLifecycle()
                    LabelingOnProgress(
                        progress = labelingState.labeledImageCount.toFloat() / viewModel.imageObjectsMap.size,
                        text = "${stringResource(R.string.labeling)}...    ${labelingState.labeledImageCount}/${viewModel.imageObjectsMap.size}",
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
                    onImageClick = { imageInfoId ->
                        onImageClick(
                            viewModel.album,
                            viewModel.getValidOriginalIndex(imageInfoId)
                        )
                    },
                    onSendThumbnailRequest = viewModel::requestThumbnail
                )
            }
        }
    }
}