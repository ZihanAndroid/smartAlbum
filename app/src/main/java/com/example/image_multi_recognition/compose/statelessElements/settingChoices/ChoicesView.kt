package com.example.image_multi_recognition.compose.statelessElements.settingChoices

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.image_multi_recognition.R

@Composable
@Preview(showSystemUi = true)
fun MultiItemChoiceView(
    modifier: Modifier = Modifier,
    title: String = "Thumbnail quality",
    items: List<String> = listOf("Low(faster)", "Medium", "High(slower)"),
    provideSelectedItem: () -> String = { "Medium" },
    onChoiceSubmit: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    ElevatedCard(
        modifier = modifier.width((LocalConfiguration.current.screenWidthDp * 0.9).toInt().dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )

    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
            RadioButtonGroup(
                provideSelectedItem = provideSelectedItem,
                items = items,
                onSelect = {
                    onChoiceSubmit(it)
                    onDismiss()
                }
            )
            ElevatedButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}