package com.example.image_multi_recognition.compose.statelessElements

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
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
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.viewmodel.ImageLabelResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelSelectionElement(
    imageLabelResult: ImageLabelResult,
    onClick: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by rememberSaveable { mutableStateOf(false) }

    ElevatedFilterChip(
        // crop the padding set by minHeight(32.dp) inside SelectableChip() call of ElevatedFilterChip
        modifier = modifier.crop(vertical = 4.dp),
        onClick = {
            selected = !selected
            onClick(selected)
        },
        label = {
            Text(
                text = imageLabelResult.label,
                style = MaterialTheme.typography.labelMedium,
                // color = colorResource(R.color.SeaGreen)
            )
        },
        selected = selected,
        leadingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = "Selected ${imageLabelResult.imageId}",
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                    tint = colorResource(R.color.LimeGreen)
                )
            }
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