package com.example.image_multi_recognition.compose.statelessElements.settingChoices

import android.widget.RadioButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun RadioButtonGroup(
    modifier: Modifier = Modifier,
    provideSelectedItem: ()->String,   // selectedItem is expected to be a state
    items: List<String>,
    onSelect: (String) -> Unit, // onSelect is expected to change the value of "selectedItem" State
) {
    Column(
        modifier = modifier,
    ) {
        items.forEach { item ->
            SingleRadioButton(
                selected = provideSelectedItem() == item,
                onClick = { onSelect(item) },
                label = item
            )
        }
    }
}

@Composable
fun SingleRadioButton(
    modifier: Modifier = Modifier,
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            onClick = { onClick() },
            selected = selected
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}