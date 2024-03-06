package com.example.image_multi_recognition.compose.statelessElements

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.util.AlbumPathDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelSelectionElement(
    label: String,
    modifier: Modifier = Modifier,
    initialSelected: Boolean = false,
    onClick: ((String, Boolean) -> Unit)? = null,
) {
    var selected by rememberSaveable { mutableStateOf(initialSelected) }

    ElevatedFilterChip(
        // crop the padding set by minHeight(32.dp) inside SelectableChip() call of ElevatedFilterChip
        modifier = modifier.crop(vertical = 4.dp),
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
                // color = colorResource(R.color.SeaGreen)
            )
        },
        selected = selected,
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = "Selected $label",
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                    tint = colorResource(R.color.LimeGreen)
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelElement(
    label: String,
    modifier: Modifier = Modifier,
) {
    ElevatedSuggestionChip(
        // crop the padding set by minHeight(32.dp) inside SelectableChip() call of ElevatedFilterChip
        //modifier = modifier.crop(vertical = 4.dp),
        onClick = {},
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                // color = colorResource(R.color.SeaGreen)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElevatedSmallIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit, // return false if reaching the maximum number of allowed labels
    modifier: Modifier = Modifier,
    maximumCount: Int = 2,
) {
    //var addedLabel = rememberSaveable { 0 }
    // (if (++addedLabel > maximumCount) false else true)

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
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
//                text = AlbumPathDecoder.decodeAlbumName(viewModel.currentAlbum).let { albumName ->
//                    if(albumName.length > 30){
//                        albumName.substring(0, 30) + "..."
//                    }else{
//                        albumName
//                    }
//                },
                text = if (title.length > 30) {
                    title.substring(0, 30) + "..."
                } else {
                    title
                },
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
        actions = actions
    )
}