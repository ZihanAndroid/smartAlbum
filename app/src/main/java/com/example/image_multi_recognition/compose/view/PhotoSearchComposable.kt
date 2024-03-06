package com.example.image_multi_recognition.compose.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.compose.statelessElements.InputSearch
import com.example.image_multi_recognition.db.LabelInfo
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.viewmodel.PhotoSearchViewModel
import java.io.File


@Composable
fun PhotoSearchComposable(
    viewModel: PhotoSearchViewModel,
    modifier: Modifier = Modifier,
    onLabelClick: (String) -> Unit
) {
    val labelImages by viewModel.labelImagesFlow.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    Column(
        modifier = modifier.padding(horizontal = DefaultConfiguration.ALBUM_INTERVAL.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        InputSearch(
            onStartFocused = false,
            onSearchClick = { viewModel.searchImagesByLabel(it) },
            onSearchTextChange = { viewModel.getLabelListByPrefix(it) }
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
                        imagePath = File(AlbumPathDecoder.decode(labelImages[index].album), labelImages[index].path),
                        title = labelImages[index].label,
                        sizeDp = ((LocalConfiguration.current.screenWidthDp - DefaultConfiguration.ALBUM_INTERVAL * 3) / 2).dp,
                        onAlbumClick = { onLabelClick(labelImages[index].label) }
                    )
                }
            }
        }
    }
}

