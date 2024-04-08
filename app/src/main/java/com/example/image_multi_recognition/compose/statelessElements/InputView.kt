package com.example.image_multi_recognition.compose.statelessElements

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.db.LabelInfo
import com.example.image_multi_recognition.util.capitalizeFirstChar
import com.example.image_multi_recognition.util.getCallSiteInfoFunc

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
//@Preview(showSystemUi = true)
fun InputView(
    userAddedLabelList: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    dropDownItemHeight: Int = 48,
    onTextChange: (String) -> List<LabelInfo>,
    maxAllowedLabelCount: Int = 3
) {
    Log.d(getCallSiteInfoFunc(), "Recomposition")
    var input by rememberSaveable { mutableStateOf("") }
    var inputSelected by rememberSaveable { mutableStateOf(false) }

    var userAddedLabelSet by rememberSaveable { mutableStateOf(setOf(*userAddedLabelList.toTypedArray())) }
    var dropDownItemList by rememberSaveable { mutableStateOf(listOf<LabelInfo>()) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val textFieldFocusRequester = remember { FocusRequester() }

    val onLabelAdded: (String) -> Unit = remember {
        { inputString ->
            with(inputString.trim()) {
                if (userAddedLabelSet.size < maxAllowedLabelCount && isNotEmpty()) {
                    if (this !in userAddedLabelSet) {
                        userAddedLabelSet = userAddedLabelSet + this.capitalizeFirstChar()
                    }
                    // Note: set "input" programmatically does not call "onValueChange" of OutlinedTextField
                    input = ""
                    // Therefore, update dropDownItemList manually here
                    dropDownItemList = onTextChange("")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        textFieldFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 36.dp, bottom = 12.dp, start = 24.dp, end = 24.dp).fillMaxWidth()
            ) {
                val maxDropDownMenuHeight = (LocalConfiguration.current.screenHeightDp * 0.32).toInt()
                val offsetY = remember(dropDownItemList.size) {
                    // 16: there is vertical padding (16.dp) in Material 3 DropdownMenu, take it into consideration
                    if (dropDownItemList.size * dropDownItemHeight > maxDropDownMenuHeight) maxDropDownMenuHeight else dropDownItemList.size * dropDownItemHeight + 16
                }

                ExposedDropdownMenuBox(
                    expanded = inputSelected,
                    onExpandedChange = {}
                ) {
                    // https://stackoverflow.com/questions/67111020/exposed-drop-down-menu-for-jetpack-compose
                    // You need to set menuAnchor() to use ExposedDropdownMenuBox
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            dropDownItemList = onTextChange(it)
                        },
                        // enabled = userAddedLabelSet.size < maxAllowedLabelCount,
                        label = {
                            Text(
                                text = if (maxAllowedLabelCount > userAddedLabelSet.size) {
                                    stringResource(R.string.add_label, maxAllowedLabelCount - userAddedLabelSet.size)
                                } else {
                                    stringResource(R.string.no_more_label)
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().onFocusChanged {
                            inputSelected = it.isFocused
                        }.menuAnchor().focusRequester(textFieldFocusRequester),
                        trailingIcon = {
                            IconButton(
                                onClick = { onLabelAdded(input) }
                            ) {
                                Icon(imageVector = Icons.Filled.Add, contentDescription = "addLabel")
                            }
                        },
                        singleLine = true,
                        keyboardActions = KeyboardActions(onDone = { onLabelAdded(input) }),
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    if (dropDownItemList.isNotEmpty()) {
                        DropdownMenu(
                            expanded = inputSelected,
                            onDismissRequest = {},
                            // https://stackoverflow.com/questions/70642330/cannot-make-exposeddropdownmenu-same-width-as-outlinedtextfield/70683378#70683378
                            // set the width of DropdownMenu the same as TextField, use DropdownMenu instead of ExposedDropdownMenu for now
                            modifier = Modifier.heightIn(max = maxDropDownMenuHeight.dp).exposedDropdownSize(),
                            // y based on the number of drop-down items
                            offset = DpOffset(
                                x = 0.dp,
                                // it seems that there is no other way to get offsetY effectively
                                y = -(offsetY.dp + OutlinedTextFieldDefaults.MinHeight + 16.dp)
                            ),
                            // do not grab the focus of OutlinedTextField when DropdownMenu is shown
                            properties = PopupProperties(focusable = false)
                        ) {
                            repeat(dropDownItemList.size) { index ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.padding(12.dp).fillMaxWidth()
                                        ) {
                                            Row {
                                                Text(
                                                    text = dropDownItemList[index].label.substring(0, input.length),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = colorResource(R.color.colorAccent)
                                                )
                                                Text(
                                                    text = dropDownItemList[index].label.substring(input.length),
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            Text(
                                                text = "${dropDownItemList[index].count}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    },
                                    onClick = {
                                        onLabelAdded(dropDownItemList[index].label)
                                    },
                                    modifier = Modifier.height(dropDownItemHeight.dp)
                                )
                            }
                        }
                    }
                }
                if (userAddedLabelSet.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 6.dp)
                    ) {
                        userAddedLabelSet.forEach { label ->
                            key(label) {
                                ElevatedAssistChip(
                                    onClick = {},
                                    label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = label,
                                            modifier = Modifier.size(AssistChipDefaults.IconSize).clickable {
                                                userAddedLabelSet -= label
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.fillMaxWidth().height(48.dp))
                }

                InputViewButtons(
                    onDismiss = onDismiss,
                    onConfirm = {
                        onConfirm(with(userAddedLabelSet.toList()) {
                            if (userAddedLabelSet.size < maxAllowedLabelCount && input.trim()
                                    .isNotEmpty() && input !in userAddedLabelSet
                            ) {
                                this + input.trim().capitalizeFirstChar()
                            } else {
                                this
                            }
                        })
                    }
                )
            }
        }
    }
}