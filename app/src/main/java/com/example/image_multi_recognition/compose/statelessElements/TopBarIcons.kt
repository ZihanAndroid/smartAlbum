package com.example.image_multi_recognition.compose.statelessElements

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
                modifier = Modifier.size(22.dp)
            )
        }
    }
    if (popUpItems.isNotEmpty()) {
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
            onDismissRequest = { menuOpened = false }
        ) {
            popUpItems.forEachIndexed { index, item ->
                // if (index > 0) Divider()
                DropdownMenuItem(
                    onClick = {
                        menuOpened = false
                        onClickList[imageVectorList.size + index]()
                    },
                    text = {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.titleSmall
                        )
                    },
                    modifier = Modifier.wrapContentHeight()
                )
            }
        }
    }
}