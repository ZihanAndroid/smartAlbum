package com.example.image_multi_recognition.compose.statelessElements

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.image_multi_recognition.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
//@Preview(showSystemUi = true)
fun SimpleInputView(
    modifier: Modifier = Modifier,
    initialText: String = "",
    excludedNames: Set<String> = emptySet(),    // case insensitive
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf(initialText) }
    val caseInsensitiveSet = setOf(*excludedNames.map { it.lowercase().trim() }.toTypedArray())
    // 0: no error, 1: empty string, 2: name exists
    var errorCode by rememberSaveable { mutableStateOf(0) }
    val onConfirmClick: (String) -> Unit = { inputString ->
        if (inputString.trim().isEmpty()) {
            errorCode = 1
        } else if (inputString.trim().lowercase() in caseInsensitiveSet) {
            errorCode = 2
        } else {
            onConfirm(inputString)
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val textFieldFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        textFieldFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Spacer(
            modifier = Modifier.fillMaxSize().background(colorResource(R.color.greyAlpha).copy(alpha = 0.5f))
                .clickable {
                    // clear keyboard
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
        )

        Card(
            modifier = Modifier.width((LocalConfiguration.current.screenWidthDp * 0.85).dp).align(Alignment.Center),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(24.dp).fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        // remove error string when user starts input
                        if (it.trim().lowercase() in caseInsensitiveSet) {
                            if (errorCode != 2) errorCode = 2
                        } else if (errorCode != 0) errorCode = 0
                        input = it
                    },
                    label = {
                        Text(
                            text = when (errorCode) {
                                0 -> stringResource(R.string.input_name)
                                1 -> stringResource(R.string.name_empty)
                                else -> stringResource(R.string.name_exist)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (errorCode == 0) Color.Unspecified else colorResource(R.color.colorAccent)
                        )
                    },
                    singleLine = true,
                    keyboardActions = KeyboardActions(onDone = { onConfirmClick(input) }),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.focusRequester(textFieldFocusRequester).padding(8.dp).fillMaxWidth()
                )
                InputViewButtons(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    onDismiss = onDismiss,
                    onConfirm = { onConfirmClick(input) },
                    confirmDisabled = errorCode != 0 || input.trim().isEmpty()
                )
            }
        }
    }
}

@Composable
fun InputViewButtons(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmDisabled: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.End)
    ) {
        ElevatedButton(
            onClick = onDismiss
        ) {
            Text(text = stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
        }
        ElevatedButton(
            onClick = onConfirm,
            enabled = !confirmDisabled
        ) {
            Text(text = "  ${stringResource(R.string.ok)}  ", style = MaterialTheme.typography.labelLarge)
        }
    }
}