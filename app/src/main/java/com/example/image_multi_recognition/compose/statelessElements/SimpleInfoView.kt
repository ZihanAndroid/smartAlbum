package com.example.image_multi_recognition.compose.statelessElements

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.ui.theme.md_theme_dark_onPrimaryContainer
import com.example.image_multi_recognition.ui.theme.md_theme_dark_onSurfaceVariant

@Composable
fun SimpleInfoView(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize().background(colorResource(R.color.Black).copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Text(
                        text = "${item.first}:",
                        style = MaterialTheme.typography.titleMedium,
                        // the reason we set fixed color here is that the color of images does not change by the theme change
                        color = md_theme_dark_onPrimaryContainer
                    )
                    Text(
                        text = item.second,
                        style = MaterialTheme.typography.bodyMedium,
                        color = md_theme_dark_onSurfaceVariant
                    )
                }
            }
        }
    }
}