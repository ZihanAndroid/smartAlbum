package com.example.image_multi_recognition.compose.statelessElements

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.db.LabelInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputSearch(
    modifier: Modifier = Modifier,
    onStartFocused: Boolean = true,
    dropDownItemHeight: Int = 48,
    onSearchClick: (String) -> Unit,
    onSearchTextChange: (String) -> List<LabelInfo>
) {
    var inputSelected by rememberSaveable { mutableStateOf(true) }
    var inputText by rememberSaveable { mutableStateOf("") }
    var dropDownItems: List<LabelInfo> by rememberSaveable { mutableStateOf(emptyList()) }
    val textFieldFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(true) {
        if (onStartFocused) {
            textFieldFocusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    ExposedDropdownMenuBox(
        expanded = inputSelected,
        onExpandedChange = {},
        modifier = modifier
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = {
                inputText = it
                dropDownItems = onSearchTextChange(it)
            },
            label = {
                Text(
                    text = stringResource(R.string.input_label)
                )
            },
            modifier = Modifier.fillMaxWidth().onFocusChanged {
                if(it.isFocused){
                    dropDownItems = onSearchTextChange(inputText.trim())
                }else if(!it.isFocused){
                    // clear DropdownMenu to avoid the following strange exception (or bug of Jetpack Compose?)
                    // "java.lang.IllegalStateException: LayoutCoordinate operations are only valid when isAttached is true"
                    dropDownItems = emptyList()
                }
                inputSelected = it.isFocused
            }.menuAnchor().focusRequester(textFieldFocusRequester),
            trailingIcon = {
                IconButton(
                    onClick = {
                        onSearchClick(inputText)
                        focusManager.clearFocus()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "search"
                    )
                }
            },
            singleLine = true,
            keyboardActions = KeyboardActions(onDone = {
                onSearchClick(inputText)
                focusManager.clearFocus()
            }),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )
        if (dropDownItems.isNotEmpty()) {
            DropdownMenu(
                expanded = inputSelected,
                onDismissRequest = {},
                modifier = Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.3).toInt().dp)
                    .exposedDropdownSize(),
                properties = PopupProperties(focusable = false)
            ) {
                repeat(dropDownItems.size) { index ->
                    DropdownMenuItem(
                        text = {
                            Row {
                                Text(
                                    text = dropDownItems[index].label.substring(0, inputText.length),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorResource(R.color.IndianRed)
                                )
                                Text(
                                    text = dropDownItems[index].label.substring(inputText.length),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        },
                        onClick = {
                            onSearchClick(dropDownItems[index].label)
                            inputText = dropDownItems[index].label
                            focusManager.clearFocus()
                        },
                        modifier = Modifier.height(dropDownItemHeight.dp)
                    )
                }
            }
        }
    }
}