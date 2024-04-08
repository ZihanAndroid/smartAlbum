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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.image_multi_recognition.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
//@Preview(showSystemUi = true)
fun SimpleInputView(
    modifier: Modifier = Modifier,
    initialText: String = "",
    suffixText: String = "",
    title: String,
    checkExcluded: (String) -> Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // disallow change file extension name
    var input by remember { mutableStateOf(TextFieldValue(initialText)) }
    var firstLoad = rememberSaveable { true }
    // 0: no error, 1: empty string, 2: name exists
    var errorCode by rememberSaveable { mutableIntStateOf(0) }
    val onConfirmClick: (String) -> Unit = { inputString ->
        if (inputString.trim().isEmpty()) {
            errorCode = 1
        } else if (checkExcluded(inputString)) {
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
                Row {
                    OutlinedTextField(
                        value = input.let {
                            if (firstLoad) {
                                it.copy(selection = TextRange(0, it.text.length))
                            } else it
                        },
                        onValueChange = {
                            // remove error string when user starts input
                            if (firstLoad) {
                                firstLoad = false
                            } else { // skip the on-the-fly check for the first load
                                if (checkExcluded(it.text)) {
                                    if (errorCode != 2) errorCode = 2
                                } else if (errorCode != 0) errorCode = 0
                            }
                            input = it
                        },
                        label = {
                            Text(
                                text = when (errorCode) {
                                    0 -> {
                                        stringResource(R.string.input_name)
                                        buildAnnotatedString {
                                            append(stringResource(R.string.input_name))
                                            if(suffixText.isNotEmpty()) {
                                                append(" (")
                                                withStyle(
                                                    SpanStyle(fontStyle = FontStyle.Italic)
                                                ) {
                                                    append(suffixText)
                                                }
                                                append(")")
                                            }
                                        }
                                    }

                                    1 -> buildAnnotatedString { append(stringResource(R.string.name_empty)) }
                                    else -> buildAnnotatedString { append(stringResource(R.string.name_exist)) }
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (errorCode == 0) Color.Unspecified else colorResource(R.color.colorAccent),
                            )

                        },
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = { onConfirmClick(input.text) }),
                        // textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End),
                        modifier = Modifier.focusRequester(textFieldFocusRequester).padding(8.dp)
                    )
                }

                InputViewButtons(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    onDismiss = onDismiss,
                    onConfirm = { onConfirmClick(input.text) },
                    confirmDisabled = input.text.trim().let { checkExcluded(it) || it.isEmpty() }
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
    confirmDisabled: Boolean = false,
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