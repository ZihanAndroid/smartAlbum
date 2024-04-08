package com.example.image_multi_recognition.compose.view

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.ImagePagerView
import com.example.image_multi_recognition.compose.statelessElements.TopAppBarForNotRootDestination
import com.example.image_multi_recognition.util.MutableSetWithState
import com.example.image_multi_recognition.util.showSnackBar
import com.example.image_multi_recognition.viewmodel.LabelPhotoViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelPhotoComposable(
    viewModel: LabelPhotoViewModel,
    modifier: Modifier = Modifier,
    provideInitialSetting: () -> AppData,
    onImageClick: (Int, String) -> Unit,
    onBack: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val pagingItems = viewModel.pagingFlow.collectAsLazyPagingItems()

    val selectedImageInfoSet = remember { MutableSetWithState<Long>() }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    val labelRemoving by viewModel.labelRemoving.collectAsStateWithLifecycle()
    val imagesPerRow by viewModel.imagesPerRowFlow.collectAsStateWithLifecycle(provideInitialSetting().imagesPerRow)

    val waitForPreviousOperationString = stringResource(R.string.waiting_for_previous)
    val labelRemovedString = stringResource(R.string.label_removed)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBarForNotRootDestination(
                title = if (!selectionMode) {
                    viewModel.label
                } else {
                    selectedImageInfoSet.version.value    // read State for recomposition when selectedImageIdSet is changed
                    stringResource(R.string.removing, selectedImageInfoSet.size)
                },
                onBack = onBack,
                actions = {
                    // check "labelRemoving" here to avoid calling "viewModel.removeLabels()" multiple times when user clicks button too fast
                    if (selectionMode) {
                        IconButton(
                            onClick = {
                                if (selectedImageInfoSet.isNotEmpty()) {
                                    selectedImageInfoSet.map { it }.apply {
                                        viewModel.removeLabels(this) {
                                            // clear the selectedImageIdSet immediately after the deletion is completed
                                            selectedImageInfoSet.clear()
                                            showSnackBar(snackbarHostState, labelRemovedString)
                                        }
                                    }
                                }
                                selectionMode = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "confirm"
                            )
                        }
                        IconButton(
                            onClick = {
                                selectedImageInfoSet.clear()
                                selectionMode = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "cancel selection"
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (labelRemoving == null || labelRemoving == false) {
                                    selectedImageInfoSet.clear()
                                    selectionMode = true
                                } else if (labelRemoving == true) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(waitForPreviousOperationString)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.baseline_playlist_remove_24),
                                contentDescription = "remove image"
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { paddingValue ->
        ImagePagerView(
            modifier = modifier.padding(paddingValue),
            pagingItems = pagingItems,
            onImageClick = { imageInfoId ->
                onImageClick(
                    viewModel.getValidOriginalIndex(imageInfoId),
                    viewModel.label
                )
            },
            onSendThumbnailRequest = viewModel::requestThumbnail,
            selectionMode = selectionMode,
            onLongPress = { imageId ->
                if (!selectionMode && labelRemoving != true) selectionMode = true
                if (labelRemoving != true) {
                    if (imageId !in selectedImageInfoSet) {
                        selectedImageInfoSet.add(imageId)
                    } else {
                        selectedImageInfoSet.remove(imageId)
                    }
                }
            },
            enableLongPressAndDrag = true,
            selectedImageIdSet = selectedImageInfoSet,
            provideImagePerRow = { imagesPerRow }
        )
    }
}