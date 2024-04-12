package com.example.image_multi_recognition.compose.view

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.compose.statelessElements.InputSearch
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.viewmodel.PhotoSearchViewModel
import java.io.File


@Composable
fun PhotoSearchComposable(
    viewModel: PhotoSearchViewModel,
    modifier: Modifier = Modifier,
    navTopAppBar: @Composable ()->Unit,
    navBottomAppBar: @Composable ()->Unit,
    provideInitialSetting: () -> AppData,
    onLabelClick: (String) -> Unit,
) {
    val originalLabelImages by viewModel.labelImagesFlow.collectAsStateWithLifecycle()
    val excludedLabelsSet by viewModel.excludedLabelsSetFlow.collectAsStateWithLifecycle(provideInitialSetting().excludedLabelsList.toSet())
    val labelImages = remember(originalLabelImages, excludedLabelsSet) {
        originalLabelImages.filter { it.label !in excludedLabelsSet }
    }

    val gridState = rememberLazyGridState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = navTopAppBar,
        bottomBar = navBottomAppBar
    ) { originalPaddingValues ->
        val paddingValues = PaddingValues(
            start = originalPaddingValues.calculateStartPadding(LayoutDirection.Ltr),
            end = originalPaddingValues.calculateEndPadding(LayoutDirection.Ltr),
            top = originalPaddingValues.calculateTopPadding(),
            bottom = originalPaddingValues.calculateBottomPadding() + DefaultConfiguration.NAV_BOTTOM_APP_BAR_CROPPED.dp
        )
        Column(
            modifier = modifier.padding(paddingValues).padding(horizontal = DefaultConfiguration.ALBUM_INTERVAL.dp).fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InputSearch(
                onStartFocused = false,
                onDropDownItemClick = {
                    viewModel.searchImagesByLabel(it)
                    onLabelClick(it)
                },
                onSearchClickNoFurther = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                },
                onSearchTextChange = {
                    viewModel.searchImagesByLabel(it)
                    viewModel.getLabelListByPrefix(it)
                }
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(DefaultConfiguration.ALBUM_PER_ROW),
                state = gridState,
                verticalArrangement = Arrangement.spacedBy(DefaultConfiguration.ALBUM_INTERVAL.dp),
                horizontalArrangement = Arrangement.spacedBy(DefaultConfiguration.ALBUM_INTERVAL.dp)
            ) {
                items(count = labelImages.size) { index ->
                    key(labelImages[index].label) {
                        AlbumPagingItem(
                            imagePath = File(
                                AlbumPathDecoder.decode(labelImages[index].album),
                                labelImages[index].path
                            ),
                            title = labelImages[index].label,
                            sizeDp = ((LocalConfiguration.current.screenWidthDp - DefaultConfiguration.ALBUM_INTERVAL * 3) / 2).dp,
                            onAlbumClick = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                onLabelClick(labelImages[index].label)
                            }
                        )
                    }
                }
            }
        }
    }
}

