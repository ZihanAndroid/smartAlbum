package com.example.image_multi_recognition.compose.view.settingScreen

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.InputSearchWithoutDropdownMenu
import com.example.image_multi_recognition.compose.statelessElements.LabelSelectionElement
import com.example.image_multi_recognition.compose.statelessElements.TopAppBarForNotRootDestination
import com.example.image_multi_recognition.model.ContextualFlowItem
import com.example.image_multi_recognition.util.MutableSetWithState
import com.example.image_multi_recognition.viewmodel.setting.FullScreenSelectionViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullScreenSelectionView(
    viewModel: FullScreenSelectionViewModel,
    provideInitialSetting: () -> AppData,
    onDismiss: () -> Unit,
) {
    fun titleConverter(prev: String, current: String): String {
        return if (prev.isEmpty() || prev.first() != current.first()) {
            current.first().toString()
        } else {
            ""
        }
    }

    val selectedLabels by viewModel.excludedLabelsListFlow.collectAsStateWithLifecycle(provideInitialSetting().excludedLabelsList)
    val orderedLabelList by viewModel.orderedLabelListFlow.collectAsStateWithLifecycle()
    val mappedAllLabels = remember(orderedLabelList) {
        ContextualFlowItem.mapToContextualFlowItemWithTitle(
            items = orderedLabelList.map { it.label },
            emptyItem = "",
            onNewTitle = ::titleConverter
        )
    }
    var labelsInSearch by remember(mappedAllLabels) { mutableStateOf(mappedAllLabels) }
    val selectedAlbumSet = remember(selectedLabels) { MutableSetWithState(selectedLabels.toMutableSet()) }
    var currentInputTextEmpty by remember { mutableStateOf(true) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBarForNotRootDestination(
                title = stringResource(R.string.excluded_labels),
                onBack = onDismiss
            ) {
                IconButton(
                    onClick = {
                        viewModel.updateExcludedLabels(selectedAlbumSet.toList())
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        onDismiss()
                    }
                ) {
                    Icon(Icons.Filled.Done, "done")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.padding(paddingValues)
        ) {
            InputSearchWithoutDropdownMenu(
                onSearchTextChange = { searchText ->
                    viewModel.getLabelListByPrefix(searchText).let { labels ->
                        if (currentInputTextEmpty != searchText.isEmpty()) {
                            currentInputTextEmpty = !currentInputTextEmpty
                        }

                        labelsInSearch = if (!currentInputTextEmpty) {
                            ContextualFlowItem.mapToContextualFlowItem(labels.map { it.label })
                        } else mappedAllLabels
                    }
                },
                onSearchClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                },
                modifier = Modifier.padding(horizontal = 18.dp)
            )
            // show labelsInSearch in a lazy FlowRow
            // https://developer.android.com/reference/kotlin/androidx/compose/foundation/layout/package-summary#ContextualFlowRow
            // https://slack-chats.kotlinlang.org/t/16754637/hey-there-folks-yesterday-i-built-an-app-with-a-macrobenchma
            // https://developer.android.com/develop/ui/compose/layouts/flow#lazy-flow
            ContextualFlowRow(
                modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                itemCount = labelsInSearch.size,
                maxItemsInEachRow = 4
            ) { index ->
                if (index !in labelsInSearch.indices) return@ContextualFlowRow
                labelsInSearch[index].let { item ->
                    when (item) {
                        is ContextualFlowItem.Title -> {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                // occupy the whole row in the ContextualFlowRow
                                modifier = Modifier.fillMaxWidth(1f)
                            )
                        }

                        is ContextualFlowItem.Item -> {
                            key(item.item, item.item in selectedAlbumSet) {
                                LabelSelectionElement(
                                    label = item.item,
                                    initialSelected = item.item in selectedAlbumSet,
                                    onClick = { label, currentSelected ->
                                        if (currentSelected) {
                                            Log.d("", "added label: $label")
                                            selectedAlbumSet.add(label)
                                        } else {
                                            selectedAlbumSet.remove(label)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}