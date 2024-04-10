package com.example.image_multi_recognition.compose.statelessElements

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A stateless SingleImageView
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleImageView(
    title: String,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    showAppBar: Boolean = true,
    topRightItems: List<SingleImageViewItem> = emptyList(),
    topRightOnClicks: List<() -> Unit> = emptyList(),
    bottomItems: List<SingleImageViewItem> = emptyList(),
    bottomOnClicks: List<() -> Unit> = emptyList(),
    moreVertItems: List<String> = emptyList(),
    moreVertItemOnClicks: List<() -> Unit> = emptyList(),
    content: @Composable (PaddingValues) -> Unit = {},
) {
    assert(topRightItems.size == topRightOnClicks.size && bottomItems.size == bottomOnClicks.size && moreVertItems.size == moreVertItemOnClicks.size)

    var showMoreVertItems by rememberSaveable { mutableStateOf(false) }
    val handledSnackbarHostState by rememberUpdatedState(snackbarHostState)

    Scaffold(
        snackbarHost = { SnackbarHost(handledSnackbarHostState) },
        topBar = {
            if (showAppBar) {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp)
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                        }
                    },
                    actions = {
                        Row {
                            topRightItems.forEachIndexed { index, item ->
                                IconButton(
                                    onClick = topRightOnClicks[index]
                                ) {
                                    Icon(
                                        imageVector = item.imageVector,
                                        contentDescription = item.contentDescription,
                                        modifier = item.modifier.size(22.dp),
                                        tint = item.tint ?: MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (moreVertItems.isNotEmpty()) {
                                // Put the remaining icons into MoreVert icon
                                Box {
                                    DropdownMenu(
                                        expanded = showMoreVertItems,
                                        onDismissRequest = { showMoreVertItems = false },
                                        modifier = Modifier.crop(vertical = 8.dp)
                                            .widthIn(min = (LocalConfiguration.current.screenWidthDp / 3).dp)
                                    ) {
                                        moreVertItems.forEachIndexed { index, text ->
                                            if (index > 0) Divider()
                                            DropdownMenuItem(
                                                onClick = {
                                                    showMoreVertItems = false
                                                    moreVertItemOnClicks[index]()
                                                },
                                                text = {
                                                    Text(
                                                        text = text,
                                                        style = MaterialTheme.typography.labelLarge
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { showMoreVertItems = true }
                                    ) {
                                        Icon(Icons.Default.MoreVert, "moreVert")
                                    }
                                }
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (showAppBar) {
                BottomAppBar(
                    modifier = Modifier.crop(vertical = 24.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxSize().align(Alignment.CenterVertically)
                    ) {
                        bottomItems.forEachIndexed { index, item ->
                            IconButton(
                                onClick = bottomOnClicks[index]
                            ) {
                                Icon(
                                    imageVector = item.imageVector,
                                    contentDescription = item.contentDescription,
                                    modifier = item.modifier,
                                    tint = item.tint ?: MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { originalPaddingValues ->
        val paddingValues = PaddingValues(
            start = originalPaddingValues.calculateStartPadding(LayoutDirection.Ltr),
            end = originalPaddingValues.calculateEndPadding(LayoutDirection.Ltr),
            top = if (showAppBar) originalPaddingValues.calculateTopPadding() else 0.dp,
            // we crop the BottomAppBar, so calculate the crop manually here
            bottom = if (showAppBar) originalPaddingValues.calculateTopPadding() - 12.dp else 0.dp,
        )
        content(paddingValues)
    }
}

data class SingleImageViewItem(
    val imageVector: ImageVector,
    val contentDescription: String,
    // you can add some changing effect to the icon by providing a modifier
    val modifier: Modifier = Modifier,
    val tint: Color? = null,
)