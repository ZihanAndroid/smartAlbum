package com.example.image_multi_recognition.compose.statelessElements

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A stateless SingleImageView
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleImageView(
    title: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    topRightIcons: List<Pair<ImageVector, String>> = emptyList(),
    topRightOnClicks: List<() -> Unit> = emptyList(),
    bottomIcons: List<Pair<ImageVector, String>> = emptyList(),
    bottomOnClicks: List<() -> Unit> = emptyList(),
    moreVertItems: List<String> = emptyList(),
    moreVertItemOnClicks: List<() -> Unit> = emptyList(),
    content: @Composable (PaddingValues) -> Unit = {},
) {
    assert(topRightIcons.size == topRightOnClicks.size && bottomIcons.size == bottomOnClicks.size && moreVertItems.size == moreVertItemOnClicks.size)

    var showMoreVertItems by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
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
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "back")
                    }
                },
                actions = {
                    Row {
                        topRightIcons.forEachIndexed { index, pair ->
                            IconButton(
                                onClick = topRightOnClicks[index]
                            ) {
                                Icon(pair.first, pair.second)
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                bottomIcons.forEachIndexed { index, pair ->
                    IconButton(
                        onClick = bottomOnClicks[index]
                    ) {
                        Icon(pair.first, pair.second)
                    }
                }
                if (moreVertItems.size > 0) {
                    // Put the remaining icons into MoreVert icon
                    Box{
                        DropdownMenu(
                            expanded = showMoreVertItems,
                            onDismissRequest = { showMoreVertItems = false },
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
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    modifier = Modifier.height(42.dp)
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
        },
        modifier = modifier
    ) {
        content(it)
    }
}