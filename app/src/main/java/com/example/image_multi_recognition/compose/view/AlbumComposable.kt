package com.example.image_multi_recognition.compose.view

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.db.AlbumWithLatestImage
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.util.AlbumPathDecoder
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.viewmodel.AlbumViewModel
import com.example.image_multi_recognition.viewmodel.PhotoViewModel
import java.io.File
import java.util.regex.Pattern

@Composable
fun AlbumComposable(
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel,
    onAlbumClick: (Long) -> Unit  // album String
) {
    val albumPagingItems = viewModel.albumPagingFlow.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()
    LazyVerticalGrid(
        modifier = modifier,
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
                        albumImage = albumWithLatestImage,
                        sizeDp = ((LocalConfiguration.current.screenWidthDp - DefaultConfiguration.ALBUM_INTERVAL) / 2).dp,
                        onAlbumClick = { onAlbumClick(albumWithLatestImage.album) }
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumPagingItem(
    albumImage: AlbumWithLatestImage,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
    onAlbumClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(sizeDp)
    ) {
        AsyncImage(
            model = File(AlbumPathDecoder.decode(albumImage.album), albumImage.path),
            contentDescription = albumImage.album.toString(),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp).clickable { onAlbumClick() }
        )
        Box(modifier = Modifier.fillMaxSize().background(colorResource(R.color.greyAlpha).copy(alpha = 0.7f)))
        Text(
            text = "${AlbumPathDecoder.decodeAlbumName(albumImage.album)} (${albumImage.count})",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
            textAlign = TextAlign.Center
        )
    }
}

