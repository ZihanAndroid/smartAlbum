package com.example.image_multi_recognition.compose.statelessElements

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@Composable
fun TopBarIcons(
    imageVectorList: List<ImageVector>,
    contentDescriptionList: List<String>,
    popUpItems: List<String> = emptyList(), // for MoreVert
    onClickList: List<() -> Unit>
) {
    assert(imageVectorList.size + popUpItems.size == onClickList.size && imageVectorList.size == contentDescriptionList.size)

    var menuOpened by remember { mutableStateOf(false) }
    imageVectorList.forEachIndexed { index, imageVector ->
        IconButton(
            onClick = onClickList[index]
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescriptionList[index],
                modifier = Modifier.size(21.dp)
            )
        }
    }
    if (popUpItems.isNotEmpty()) {
        Box {
            IconButton(
                onClick = { menuOpened = true }
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "topBarMoreOptions",
                    modifier = Modifier.size(22.dp)
                )
            }
            DropdownMenu(
                expanded = menuOpened,
                onDismissRequest = { menuOpened = false },
                modifier = Modifier.crop(vertical = 8.dp)
                    .widthIn(min = (LocalConfiguration.current.screenWidthDp / 3).dp)
            ) {
                popUpItems.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider()
                    DropdownMenuItem(
                        onClick = {
                            menuOpened = false
                            onClickList[imageVectorList.size + index]()
                        },
                        text = {
                            Text(
                                text = item,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        modifier = Modifier.wrapContentHeight()
                    )
                }
            }
        }
    }
}