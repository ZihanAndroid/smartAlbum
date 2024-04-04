package com.example.image_multi_recognition.compose.statelessElements

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.image_multi_recognition.R
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LabelSelectionElement(
    label: String,
    modifier: Modifier = Modifier,
    initialSelected: Boolean = false,
    longPressAndDragSupport: Boolean = false,
    onClick: ((String, Boolean) -> Unit)? = null,
) {
    var selected by rememberSaveable { mutableStateOf(initialSelected) }
    // long press and drag support
    var movable by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    ElevatedFilterChip(
        // crop the padding set by minHeight(32.dp) inside SelectableChip() call of ElevatedFilterChip
        modifier = modifier.crop(vertical = 4.dp).graphicsLayer {
            translationX = offsetX
            translationY = offsetY
        },
        onClick = {
            // do not change selected when onclick is null (keep it as initialSelected)
            onClick?.let {
                selected = !selected
                it.invoke(label, selected)
            }
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.let { prevModifier ->
                    if (longPressAndDragSupport) {
                        // https://stackoverflow.com/questions/74119839/detecting-long-press-events-on-chips-in-jetpack-compose
                        prevModifier.pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { movable = true },
                                onDrag = { change, _ ->
                                    offsetX += change.position.x
                                    offsetY += change.position.y
                                },
                                onDragCancel = { movable = false },
                                onDragEnd = { movable = false }
                            )
                        }
                    } else prevModifier
                }
                // color = colorResource(R.color.SeaGreen)
            )
        },
        selected = selected,
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = "Selected $label",
                    modifier = Modifier.size(FilterChipDefaults.IconSize).let { prevModifier ->
                        if (longPressAndDragSupport) {
                            prevModifier.pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { movable = true },
                                    onDrag = { change, _ ->
                                        offsetX += change.position.x
                                        offsetY += change.position.y
                                    },
                                    onDragCancel = { movable = false },
                                    onDragEnd = { movable = false }
                                )
                            }
                        } else prevModifier
                    },
                    tint = colorResource(R.color.LimeGreen)
                )
            }
        }
    )
}

@Composable
fun ElevatedSmallIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit, // return false if reaching the maximum number of allowed labels
    modifier: Modifier = Modifier,
) {
    ElevatedButton(
        modifier = modifier.crop(vertical = 4.dp).heightIn(24.dp),
        // crop the padding set by minHeight(32.dp) inside SelectableChip() call of ElevatedFilterChip
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = colorResource(R.color.LimeGreen)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarForNotRootDestination(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            if (onBack == null) {
                Text(
                    text = if (title.length > 30) {
                        title.substring(0, 30) + "..."
                    } else {
                        title
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp)
                )
            }
        },
        navigationIcon = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
                    }
                    // It seems that if we do not put the Text here, in some cases the text cannot show due to the change of navigationIcon
                    // So we put the text as part of the navigationIcon
                    Text(
                        text = if (title.length > 30) {
                            title.substring(0, 30) + "..."
                        } else {
                            title
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp)
                    )
                }
            }
        },
        actions = actions
    )
}

@Composable
fun ImageItemRow(
    modifier: Modifier = Modifier,
    albumName: String,
    imageCount: Int,
    albumAbsolutePath: String,
    imageFilePath: String,
    onClick: (() -> Unit)? = null,
) {
    val imageSize = (LocalConfiguration.current.screenWidthDp / 5).dp
    Row(
        modifier = modifier.fillMaxWidth().let {
            if (onClick != null) it.clickable { onClick() }
            else it
        }
    ) {
        AsyncImage(
            model = File(imageFilePath),
            contentDescription = "albumImage_$albumName",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(imageSize + 8.dp).padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Column(
            modifier = Modifier.height(imageSize + 8.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = if (albumName.length > 30) albumName.substring(0, 30) + "..." else albumName,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "$imageCount ${stringResource(R.string.images)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = if (albumAbsolutePath.length > 40) {
                    albumAbsolutePath.substring(0, 40) + "..."
                } else albumAbsolutePath,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}