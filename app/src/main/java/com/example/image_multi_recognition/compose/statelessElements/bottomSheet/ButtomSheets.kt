package com.example.image_multi_recognition.compose.statelessElements.bottomSheet

import android.util.Log
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.ImageItemRow
import com.example.image_multi_recognition.compose.statelessElements.SimpleInputView
import com.example.image_multi_recognition.db.AlbumInfo
import com.example.image_multi_recognition.db.AlbumInfoWithLatestImage
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.util.StorageHelper
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.util.showSnackBar
import com.example.image_multi_recognition.viewmodel.basic.ImageFileOperationSupport
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetView(
    fileOperationSupport: ImageFileOperationSupport,
    snackbarHostState: SnackbarHostState,
    albumSelectState: AlbumSelectState,
    selectedImageIdList: List<Long>,    // copy and move are based on selected images
    onDismissRequest: () -> Unit,
    onAlbumAddClick: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun submitSelection(selectedAlbum: AlbumInfo, isAlbumNew: Boolean){
        when (albumSelectState.purpose) {
            AlbumSelectState.Purpose.MOVE_IMAGES -> {
                onDismissRequest()
                fileOperationSupport.requestImagesMove(
                    imageIdList = selectedImageIdList,
                    newAlbum = selectedAlbum,
                    isAlbumNew = isAlbumNew,
                ) {
                    showSnackBar(
                        snackbarHostState,
                        "${context.getString(R.string.move_fail)}!"
                    )
                }
            }

            AlbumSelectState.Purpose.COPY_IMAGES -> {
                onDismissRequest()
                fileOperationSupport.copyImagesTo(
                    newAlbum = selectedAlbum,
                    isAlbumNew = isAlbumNew,
                    imageIdList = selectedImageIdList
                ) { failedPath ->
                    showSnackBar(
                        snackbarHostState = snackbarHostState,
                        message = if (failedPath.isEmpty()) context.getString(R.string.copy_success)
                        else {
                            if (failedPath.find { it.second == StorageHelper.ImageCopyError.IO_ERROR } != null) {
                                "${StorageHelper.ImageCopyError.IO_ERROR}"
                            } else {
                                context.getString(R.string.same_name_exist)
                            }
                        },
                        delayTimeMillis = if (failedPath.isNotEmpty()) 3000 else 1000  // set a longer time period for an error message
                    )
                }
            }
            // should never be here!
            else -> Log.e(getCallSiteInfoFunc(), "Error: ${albumSelectState.purpose}")
        }
    }

    val albumList by fileOperationSupport.albumListStateFlow.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { sheetValue ->
            if (sheetValue == SheetValue.Hidden) false else true
        }  // avoid ModalBottomSheet is closed by scrolling
    )
    if (!albumSelectState.newAlbumInput) {
        // bottom sheet: https://developer.android.com/jetpack/compose/components/bottom-sheets
        // https://stackoverflow.com/questions/68809132/jetpack-compose-disable-bottomsheet-outside-touch
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            modifier = Modifier.height((LocalConfiguration.current.screenHeightDp * 0.95).dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            coroutineScope.launch {
                                sheetState.hide() // generate animation for hiding
                                onDismissRequest()
                            }
                        }
                    )
                }
        ) {
            BottomSheetContent(
                albumInfoWithLatestImageList = albumList,
                onItemClick = { selectedAlbum -> submitSelection(selectedAlbum, false) },
                onAlbumAddClick = onAlbumAddClick
            )
        }
    } else {
        val excludedAlbumSet = remember { AlbumPathDecoder.albumNamePathMap.map { it.value.name.lowercase() }.toSet() }
        SimpleInputView(
            title = stringResource(R.string.new_album),
            checkExcluded = { it.trim().lowercase() in excludedAlbumSet },
            onDismiss = onDismissRequest,
            onConfirm = { newAlbum ->
                focusManager.clearFocus()
                keyboardController?.hide()
                fileOperationSupport.createAlbumSharedDir(newAlbum).let { albumFile ->
                    if (albumFile == null){
                        coroutineScope.launch {
                            showSnackBar(
                                snackbarHostState = snackbarHostState,
                                message = context.getString(R.string.album_create_fail),
                                delayTimeMillis = 3000
                            )
                        }
                    }else{
                        AlbumPathDecoder.albums
                        submitSelection(AlbumInfo(path = albumFile.absolutePath), true)
                    }
                }
            }
        )
    }
}

@Composable
fun BottomSheetContent(
    albumInfoWithLatestImageList: List<AlbumInfoWithLatestImage>,
    onItemClick: (AlbumInfo) -> Unit, // the chosen album
    onAlbumAddClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp, start = 16.dp, end = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.choose_album),
            style = MaterialTheme.typography.titleMedium
        )
        ElevatedButton(onClick = onAlbumAddClick) {
            Icon(Icons.Filled.Add, "add")
        }
    }
    LazyColumn {
        items(count = albumInfoWithLatestImageList.size) { index ->
            albumInfoWithLatestImageList[index].let { album ->
                ImageItemRow(
                    albumName = File(album.albumPath).name,
                    imageCount = album.count,
                    albumAbsolutePath = album.albumPath,
                    imageFilePath = File(album.albumPath, album.path).absolutePath,
                    onClick = { onItemClick(AlbumInfo(album.album, album.albumPath)) }
                )
            }
        }
    }
}

data class AlbumSelectState(
    val selecting: Boolean,
    val newAlbumInput: Boolean,
    val purpose: Purpose,
) {
    enum class Purpose {
        MOVE_IMAGES, COPY_IMAGES, NO_PURPOSE
    }

    companion object {
        val saver = listSaver<MutableState<AlbumSelectState>, String>(
            save = { state ->
                listOf(
                    state.value.selecting.toString(),
                    state.value.selecting.toString(),
                    state.value.purpose.toString()
                )
            },
            restore = { savedList ->
                mutableStateOf(
                    AlbumSelectState(
                        savedList[0].toBoolean(),
                        savedList[1].toBoolean(),
                        Purpose.valueOf(savedList[2])
                    )
                )
            })
        val noSelection = AlbumSelectState(selecting = false, newAlbumInput = false, purpose = Purpose.NO_PURPOSE)
    }
}