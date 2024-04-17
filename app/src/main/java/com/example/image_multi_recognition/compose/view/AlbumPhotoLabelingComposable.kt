package com.example.image_multi_recognition.compose.view

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.CustomSnackBar
import com.example.image_multi_recognition.compose.statelessElements.ImagePagerView
import com.example.image_multi_recognition.compose.statelessElements.TopAppBarForNotRootDestination
import com.example.image_multi_recognition.util.showSnackBar
import com.example.image_multi_recognition.viewmodel.AlbumPhotoLabelingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AlbumPhotoLabelingComposable(
    viewModel: AlbumPhotoLabelingViewModel,
    modifier: Modifier = Modifier,
    provideInitialSetting: () -> AppData,
    onImageClick: (Long, Int) -> Unit,
    onBack: () -> Unit,
) {
    val imageInfoList by viewModel.unlabeledImageInAlbumStateFlow.collectAsStateWithLifecycle()
    var labelingClicked by rememberSaveable { mutableStateOf(false) }
    val labelingState by viewModel.labelingStateFlow.collectAsStateWithLifecycle()
    val unlabeledEmpty by viewModel.unlabeledEmptyFlow.collectAsStateWithLifecycle()

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
    val imagesPerRow by viewModel.imagesPerRowFlow.collectAsStateWithLifecycle(provideInitialSetting().imagesPerRow)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val labelAddedString = stringResource(R.string.label_added)
    val activity = LocalContext.current as ComponentActivity

    LaunchedEffect(Unit) {
        // Since WorkManager lives longer than Activity's lifecycle, when the previous Worker finished its task,
        // so the result file may not be handled yet, check it first
        coroutineScope.launch {
            viewModel.getPreviousResultFileName().let { fileName ->
                if (fileName.isNotEmpty()) {
                    with(viewModel) {
                        activity.setWorkManagerLabelingResult {
                            labelingClicked = false
                            coroutineScope.launch {
                                showSnackBar(
                                    snackbarHostState,
                                    activity.getString(R.string.labeling_failed),
                                    3000
                                )
                            }
                        }
                        labelingClicked = true
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) { CustomSnackBar(it, 0.dp) } },
        topBar = {
            TopAppBarForNotRootDestination(
                title = if (labelingClicked) {
                    if (labelingState.labelingDone) {
                        stringResource(R.string.labeling_result)
                    } else {
                        "${stringResource(R.string.loading)}..."
                    }
                } else {
                    if (imageInfoList.isEmpty()) ""
                    else stringResource(R.string.unlabeled_image_count, imageInfoList.size)
                },
                onBack = onBack,
                actions = {
                    if (!labelingClicked) {
                        var showPermissionRational by remember { mutableStateOf(false) }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            with(viewModel.permissionAccessor) {
                                activity.setupPermissionRequest(
                                    permission = android.Manifest.permission.POST_NOTIFICATIONS,
                                    provideShowPermissionRational = { showPermissionRational },
                                    setShowPermissionRational = { showPermissionRational = it },
                                    onPermissionGranted = {
                                        viewModel.scanImagesByWorkManager(
                                            album = viewModel.album,
                                            onStateChange = {
                                                if (!labelingClicked) labelingClicked = true
                                            },
                                            onWorkCanceled = { labelingClicked = false },
                                            onWorkFinished = {
                                                with(viewModel) {
                                                    activity.setWorkManagerLabelingResult {
                                                        labelingClicked = false
                                                        coroutineScope.launch {
                                                            showSnackBar(
                                                                snackbarHostState,
                                                                activity.getString(R.string.labeling_failed),
                                                                3000
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    },
                                    onPermissionDenied = {
                                        viewModel.scanImages(imageInfoList)
                                        labelingClicked = true
                                    }
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                if (imageInfoList.size > DefaultConfiguration.WORK_MANAGER_THRESHOLD) {
                                    // check POST_NOTIFICATION permission first, if no permission, do not use workManager which requires notification
                                    with(viewModel.permissionAccessor) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                            && !activity.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                                        ) {
                                            // setup permission requester in permissionAccessor
                                            if (activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                                                showPermissionRational = true
                                            } else {
                                                // shouldShowRequestPermissionRationale returns false if the permission has not been asked before
                                                permissionRequester.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        } else {
                                            // we do not need POST_NOTIFICATIONS, just run WorkManager with notification
                                            viewModel.scanImagesByWorkManager(
                                                album = viewModel.album,
                                                onStateChange = {
                                                    if (!labelingClicked) labelingClicked = true
                                                },
                                                onWorkCanceled = {
                                                    labelingClicked = false
                                                },
                                                onWorkFinished = {
                                                    with(viewModel) {
                                                        activity.setWorkManagerLabelingResult {
                                                            labelingClicked = false
                                                            coroutineScope.launch {
                                                                showSnackBar(
                                                                    snackbarHostState,
                                                                    activity.getString(R.string.labeling_failed),
                                                                    3000
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    viewModel.scanImages(imageInfoList)
                                    labelingClicked = true
                                }
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
                                    if (!labelAdding) {
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
                                                // when unlabeledEmpty is empty, we will exit the current window, so there is no need to change the state
                                                // After labeling, the itemCount may become 0, and we exit the current window.
                                                // Note that we should not check pagingItems.itemCount because that value is lazily updated
                                                // and its initial value is always zero when the page first loaded
                                                if (!unlabeledEmpty) labelingClicked = false
                                                else onBack()
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
        val activity = LocalContext.current as ComponentActivity
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
                            labelSelectedStateHolderParam = labelSelectedStateHolder,
                            provideImagePerRow = { imagesPerRow }
                        )
                    }
                } else {
                    val scanPaused by viewModel.scanPaused.collectAsStateWithLifecycle()
                    LabelingOnProgress(
                        progress = labelingState.labeledImageCount.toFloat() / viewModel.unlabeledSize,
                        text = "${stringResource(R.string.labeling)}...    ${labelingState.labeledImageCount}/${viewModel.unlabeledSize}",
                        onResumePauseClicked = { viewModel.reverseScanPaused() },
                        onCancelClicked = {
                            viewModel.scanCancelled = true
                            viewModel.resumeScanPaused()
                            labelingClicked = false
                        },
                        scanPaused = scanPaused,
                        showPauseCancel = with(viewModel) { activity.isRunWorker() }
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
                    provideImagePerRow = { imagesPerRow }
                )
            }
        }
    }
}