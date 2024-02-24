package com.example.image_multi_recognition.compose.statelessElements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.image_multi_recognition.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
// @Preview(showSystemUi = true)
fun InputView(
    onDismiss: () -> Unit = {},
    onConfirm: (String) -> Unit = {},
    dropDownItemHeight: Int = 48,
    dropDownItemList: List<String>
) {
    var input by rememberSaveable { mutableStateOf("") }
    var inputSelected by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.fillMaxSize().background(colorResource(R.color.greyAlpha).copy(alpha = 0.5f)))

        Card(
            modifier = Modifier.width((LocalConfiguration.current.screenWidthDp * 0.85).dp).align(Alignment.Center),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(30.dp),
                modifier = Modifier.padding(top = 36.dp, bottom = 12.dp).wrapContentWidth()
            ) {
                val maxDropDownMenuHeight = (LocalConfiguration.current.screenHeightDp * 0.3).toInt()
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
                        onValueChange = { input = it },
                        label = { Text(stringResource(R.string.add_label)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).onFocusChanged {
                            inputSelected = it.isFocused
                        }.menuAnchor().onPlaced { }
                    )
                    DropdownMenu(
                        expanded = inputSelected,
                        onDismissRequest = {},
                        // https://stackoverflow.com/questions/70642330/cannot-make-exposeddropdownmenu-same-width-as-outlinedtextfield/70683378#70683378
                        // set the width of DropdownMenu the same as TextField, use DropdownMenu instead of ExposedDropdownMenu for now
                        modifier = Modifier.heightIn(max = maxDropDownMenuHeight.dp).exposedDropdownSize(),
                        // x = 24.dp: horizontal padding value of OutlinedTextField
                        // y based on the number of drop-down items
                        offset = DpOffset(
                            x = 24.dp,
                            // it seems that there is no other way to get offsetY effectively
                            y = -(offsetY.dp + OutlinedTextFieldDefaults.MinHeight + 16.dp)
                        )
                    ) {
                        repeat(dropDownItemList.size) { index ->
                            DropdownMenuItem(
                                text = {
                                    Text(text = dropDownItemList[index], style = MaterialTheme.typography.bodyMedium)
                                },
                                onClick = {},
                                modifier = Modifier.height(dropDownItemHeight.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.End)
                ) {
                    ElevatedButton(
                        onClick = { onConfirm(input) },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(text = stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
                    }
                    ElevatedButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(text = stringResource(R.string.ok), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}