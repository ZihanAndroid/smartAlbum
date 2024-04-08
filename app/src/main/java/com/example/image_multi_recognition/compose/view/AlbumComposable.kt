package com.example.image_multi_recognition.compose.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.ui.theme.md_theme_dark_onPrimaryContainer
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.viewmodel.AlbumViewModel
import java.io.File

@Composable
fun AlbumComposable(
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel,
    onAlbumClick: (Long) -> Unit  // album String
) {
    val albumPagingItems = viewModel.albumPagingFlow.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        modifier = modifier.padding(horizontal = DefaultConfiguration.ALBUM_INTERVAL.dp),
        state = gridState,
        columns = GridCells.Fixed(DefaultConfiguration.ALBUM_PER_ROW),
        horizontalArrangement = Arrangement.spacedBy(DefaultConfiguration.ALBUM_INTERVAL.dp),
        verticalArrangement = Arrangement.spacedBy(DefaultConfiguration.ALBUM_INTERVAL.dp)
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

@Composable
fun AlbumPagingItem(
    imagePath: File,
    title: String,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String = "",
    onAlbumClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(sizeDp)
    ) {
        AsyncImage(
            model = imagePath,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp).clickable { onAlbumClick() }.clip(RoundedCornerShape(12.dp))
        )
        Box(
            modifier = Modifier.fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(colorResource(R.color.black).copy(alpha = 0.3f))
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
            textAlign = TextAlign.Center,
            color = md_theme_dark_onPrimaryContainer
        )
    }
}

