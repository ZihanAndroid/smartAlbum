package com.example.image_multi_recognition.compose.view.settingScreen

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.compose.statelessElements.ImageItemRow
import com.example.image_multi_recognition.compose.statelessElements.InputSearchWithoutDropdownMenu
import com.example.image_multi_recognition.compose.statelessElements.LabelSelectionElement
import com.example.image_multi_recognition.compose.statelessElements.TopAppBarForNotRootDestination
import com.example.image_multi_recognition.compose.statelessElements.settingChoices.MultiItemChoiceView
import com.example.image_multi_recognition.dataStore.AppDataSerializer
import com.example.image_multi_recognition.db.AlbumInfoWithLatestImage
import com.example.image_multi_recognition.db.LabelInfo
import com.example.image_multi_recognition.model.ChoiceSettingItem
import com.example.image_multi_recognition.model.ContextualFlowItem
import com.example.image_multi_recognition.model.SettingGroup
import com.example.image_multi_recognition.util.MutableSetWithState
import com.example.image_multi_recognition.util.SliderColorsNoTicks
import com.example.image_multi_recognition.viewmodel.SettingScreenViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    viewModel: SettingScreenViewModel,
    provideInitialSetting: () -> AppData,
    onBack: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    // each setting item is backed by a flow, so when any one of them is changed, only one State get changed here
    // and we can postpone the read of the state to postpone recomposition
    val themeSetting by viewModel.themeSettingFlow.collectAsStateWithLifecycle(provideInitialSetting().themeSetting)
    val defaultAlbumPath by viewModel.defaultAlbumPathFlow.collectAsStateWithLifecycle(provideInitialSetting().defaultAlbumPath)
    val imagesPerRow by viewModel.imagesPerRowFlow.collectAsStateWithLifecycle(provideInitialSetting().imagesPerRow)
    val imageCacheEnabled by viewModel.imageCacheEnabledFlow.collectAsStateWithLifecycle(provideInitialSetting().imageCacheEnabled)
    val thumbNailQuality by viewModel.thumbNailQualityFlow.collectAsStateWithLifecycle(provideInitialSetting().thumbNailQuality)
    val imageLabelingConfidence by viewModel.imageLabelingConfidenceFlow.collectAsStateWithLifecycle(
        provideInitialSetting().imageLabelingConfidence
    )
    val excludedLabelsList by viewModel.excludedLabelsListFlow.collectAsStateWithLifecycle(provideInitialSetting().excludedLabelsList)
    val excludedAlbumPathsList by viewModel.excludedAlbumPathsListFlow.collectAsStateWithLifecycle(provideInitialSetting().excludedAlbumPathsList)

    val context = LocalContext.current
    // val coroutineScope = rememberCoroutineScope()
    val lazyColumnState = rememberLazyListState()

    // Type.MULTI_CHOICE
    var multipleChoiceItem by remember { mutableStateOf(MultipleChoiceItem.notOpen) }
    // Type.VIEW_CHOICE
    var viewChoiceItem by remember { mutableStateOf(ViewChoiceItem.notOpen) }
    var fullScreenShow by remember { mutableStateOf(false) }

    // prepare setting data
    val settingGroupList = remember {
        listOf(
            SettingGroup(
                title = context.getString(R.string.general_setting),
                items = listOf(
                    ChoiceSettingItem(
                        title = context.getString(R.string.theme),
                        explain = context.getString(R.string.theme_explain),
                        type = ChoiceSettingItem.Type.MULTI_CHOICE,
                        choices = AppData.Theme.entries.filter { it != AppData.Theme.UNRECOGNIZED }
                            .map { AppDataSerializer.convertEnumToString(it) },
                        provideInitialChoice = { AppDataSerializer.convertEnumToString(themeSetting) },
                        provideInitialChoiceString = { AppDataSerializer.convertEnumToString(themeSetting) },
                        onValueChange = { viewModel.updateThemeSetting(AppDataSerializer.convertStringToEnum(it)) }
                    ),
                    ChoiceSettingItem<List<String>>(
                        title = context.getString(R.string.default_album),
                        explain = context.getString(R.string.default_album_explain),
                        type = ChoiceSettingItem.Type.VIEW_CHOICE,
                        provideInitialChoice = { listOf(defaultAlbumPath) },
                        provideInitialChoiceString = {
                            File(defaultAlbumPath).name.let {
                                if (it.length > 12) it.substring(0, 12) + "..." else it
                            }
                        },
                        onValueChange = { viewModel.updateDefaultAlbumPath(it.first()) }
                    ),
                    ChoiceSettingItem(
                        title = context.getString(R.string.images_per_row),
                        explain = context.getString(R.string.images_per_row_explain),
                        type = ChoiceSettingItem.Type.MULTI_CHOICE,
                        provideInitialChoice = { imagesPerRow.toString() },
                        provideInitialChoiceString = { imagesPerRow.toString() },
                        choices = listOf("3", "4", "5"),
                        onValueChange = { viewModel.updateImagesPerRow(it.toInt()) }
                    )
                )
            ),
            SettingGroup(
                title = context.getString(R.string.cache_setting),
                items = listOf(
                    ChoiceSettingItem(
                        title = context.getString(R.string.enable_image_cache),
                        explain = context.getString(R.string.enable_image_cache_explain),
                        type = ChoiceSettingItem.Type.TWO_CHOICE,
                        provideInitialChoice = { imageCacheEnabled },
                        onValueChange = { viewModel.updateImageCacheEnabled(it) }
                    ),
                    ChoiceSettingItem(
                        title = context.getString(R.string.thumbnail_quality),
                        explain = context.getString(R.string.thumbnail_quality_explain),
                        type = ChoiceSettingItem.Type.SLIDER_CHOICE,
                        provideInitialChoice = { thumbNailQuality },
                        provideInitialChoiceString = { thumbNailQuality.toString() },
                        onValueChange = { viewModel.updateThumbnailQuality(it) },
                        choices = listOf("0.1", "0.3", "19")
                    )
                )
            ),
            SettingGroup(
                title = context.getString(R.string.image_labeling_setting),
                items = listOf(
                    ChoiceSettingItem(
                        title = context.getString(R.string.image_labeling_confidence),
                        explain = context.getString(R.string.image_labeling_confidence_explain),
                        type = ChoiceSettingItem.Type.SLIDER_CHOICE,
                        provideInitialChoice = { imageLabelingConfidence },
                        provideInitialChoiceString = { imageLabelingConfidence.toString() },
                        onValueChange = { viewModel.updateImageLabelingConfidence(it) },
                        choices = listOf("0.5", "0.9", "39")
                    ),
                    ChoiceSettingItem<List<String>>(
                        title = context.getString(R.string.excluded_labeling_albums),
                        explain = context.getString(R.string.excluded_labeling_albums_explains),
                        type = ChoiceSettingItem.Type.VIEW_CHOICE,
                        provideInitialChoice = { excludedAlbumPathsList },
                        provideInitialChoiceString = { "${excludedAlbumPathsList.size}" },
                        onValueChange = { viewModel.updateExcludedAlbumPaths(it) }
                    ),
                    ChoiceSettingItem<List<String>>(
                        title = context.getString(R.string.excluded_labels),
                        explain = context.getString(R.string.excluded_labels_explain),
                        type = ChoiceSettingItem.Type.VIEW_CHOICE,
                        provideInitialChoice = { excludedLabelsList },
                        provideInitialChoiceString = { "${excludedLabelsList.size}" },
                        onValueChange = { viewModel.updateExcludedLabels(it) }
                    ),
                )
            )
        )
    }

    fun dismiss() {
        if (multipleChoiceItem != MultipleChoiceItem.notOpen) {
            multipleChoiceItem = MultipleChoiceItem.notOpen
        }
        if (viewChoiceItem != ViewChoiceItem.notOpen) {
            viewChoiceItem = ViewChoiceItem.notOpen
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().pointerInput(fullScreenShow) {
            if(!fullScreenShow) detectTapGestures { dismiss() }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBarForNotRootDestination(
                    title = stringResource(R.string.setting),
                    onBack = onBack
                )
            },
        ) { paddingValues ->
            LazyColumn(
                state = lazyColumnState,
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(
                    count = settingGroupList.size,
                ) { index ->
                    settingGroupList[index].let { settingGroup ->
                        if (settingGroup.title == context.getString(R.string.cache_setting)) {
                            SettingGroupView(
                                title = settingGroup.title,
                                items = settingGroup.items,
                                onMultipleChoiceClick = { multipleChoiceItem = it },
                                onViewChoiceClick = { viewChoiceItem = it },
                                // control whether enable setting for this group by certain item
                                provideEnabled = {
                                    if (settingGroup.title == context.getString(R.string.cache_setting)) {
                                        settingGroup.items.find { it.title == context.getString(R.string.enable_image_cache) }
                                            ?.provideInitialChoice?.let { it() as Boolean } ?: true
                                    } else true
                                },
                                enabledTrigger = context.getString(R.string.enable_image_cache)
                            )
                        } else {
                            SettingGroupView(
                                title = settingGroup.title,
                                items = settingGroup.items,
                                onMultipleChoiceClick = { multipleChoiceItem = it },
                                onViewChoiceClick = { viewChoiceItem = it }
                            )
                        }
                    }
                }
            }
        }
        if (multipleChoiceItem != MultipleChoiceItem.notOpen) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) { dismiss() }.align(Alignment.Center)
            )
        }
        if (multipleChoiceItem != MultipleChoiceItem.notOpen) {
            MultiItemChoiceView(
                title = multipleChoiceItem.title,
                items = multipleChoiceItem.items,
                provideSelectedItem = multipleChoiceItem.provideInitialSelection,
                onChoiceSubmit = multipleChoiceItem.onChoice,
                onDismiss = { multipleChoiceItem = MultipleChoiceItem.notOpen },
                modifier = Modifier.align(Alignment.Center)
            )
        }
        if (viewChoiceItem != ViewChoiceItem.notOpen) {
            val sheetState = rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { sheetValue ->
                    if (sheetValue == SheetValue.Hidden) false else true
                }  // avoid ModalBottomSheet is closed by scrolling
            )
            when (viewChoiceItem.purpose) {
                ViewChoiceItem.ViewChoicePurpose.DEFAULT_ALBUM, ViewChoiceItem.ViewChoicePurpose.EXCLUDED_ALBUM -> {
                    BottomSheetSelectionView(
                        title = viewChoiceItem.title,
                        multiSelection = (viewChoiceItem.purpose == ViewChoiceItem.ViewChoicePurpose.EXCLUDED_ALBUM),
                        allAlbums = viewModel.allAlbums,
                        sheetState = sheetState,
                        provideInitialSelectedAlbums = {
                            viewChoiceItem.provideInitialSelection()
                        },
                        onSelectDone = { selected ->
                            viewChoiceItem.onChoices(selected)
                        },
                        onDismiss = {
                            coroutineScope.launch {
                                sheetState.hide() // generate animation for hiding
                                viewChoiceItem = ViewChoiceItem.notOpen
                            }
                        }
                    )
                }

                ViewChoiceItem.ViewChoicePurpose.EXCLUDED_LABEL -> {
                    fullScreenShow = true
                }

                ViewChoiceItem.ViewChoicePurpose.NOT_OPEN -> {}
            }
        }
        // fullScreenShow is enabled only for ViewChoicePurpose.EXCLUDED_LABEL
        if (fullScreenShow) {
            FullScreenSelectionView(
                title = viewChoiceItem.title,
                allLabels = viewModel.orderedLabelList,
                provideInitialSelectedLabels = viewChoiceItem.provideInitialSelection,
                onSelectDone = viewChoiceItem.onChoices,
                onDismiss = {
                    fullScreenShow = false
                    viewChoiceItem = ViewChoiceItem.notOpen
                },
                onSearchTextChange = { viewModel.getLabelListByPrefix(it) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullScreenSelectionView(
    title: String,
    allLabels: List<LabelInfo>,
    provideInitialSelectedLabels: () -> List<String>,
    onSelectDone: (List<String>) -> Unit,
    // onSearchTextChange returns emptyList for empty String, we can use allLabels instead
    onSearchTextChange: (String) -> List<LabelInfo>,
    onDismiss: () -> Unit,
) {
    fun titleConverter(prev: String, current: String): String {
        return if (prev.isEmpty() || prev.first() != current.first()) {
            current.first().toString()
        } else {
            ""
        }
    }

    val mappedAllLabels = remember {
        ContextualFlowItem.mapToContextualFlowItemWithTitle(
            items = allLabels.map { it.label },
            emptyItem = "",
            onNewTitle = ::titleConverter
        )
    }
    val selectedAlbumSet = remember { MutableSetWithState(provideInitialSelectedLabels().toMutableSet()) }
    var labelsInSearch by remember { mutableStateOf(mappedAllLabels) }
    var currentInputTextEmpty by remember { mutableStateOf(true) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBarForNotRootDestination(
                title = title,
                onBack = onDismiss
            ) {
                IconButton(
                    onClick = {
                        onSelectDone(selectedAlbumSet.toList())
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
                    onSearchTextChange(searchText).let { labels ->
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
                modifier = Modifier.safeDrawingPadding().padding(18.dp)
                    .verticalScroll(rememberScrollState()),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetSelectionView(
    modifier: Modifier = Modifier,
    title: String,
    // false: use RadioButtons and allow only one selection;
    // true: use CheckBox and allow multiple selections
    multiSelection: Boolean,
    sheetState: SheetState,
    allAlbums: List<AlbumInfoWithLatestImage>,
    // album absolute path, if multiSelection is false, expect only one value in the list
    provideInitialSelectedAlbums: () -> List<String>,
    onSelectDone: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedAlbumList by remember { mutableStateOf(provideInitialSelectedAlbums()) }

    fun onClick(album: AlbumInfoWithLatestImage) {
        if (album.albumPath in selectedAlbumList) {
            if (multiSelection) selectedAlbumList = selectedAlbumList - album.albumPath
        } else {
            selectedAlbumList = if (multiSelection) selectedAlbumList + album.albumPath
            else listOf(album.albumPath)
            // close the window if it is not multiSelection
            if (!multiSelection) {
                onSelectDone(selectedAlbumList)
                onDismiss()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.height((LocalConfiguration.current.screenHeightDp * 0.95).dp)
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp, start = 24.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$title: ${
                    if (multiSelection) {
                        selectedAlbumList.size
                    } else {
                        File(selectedAlbumList[0]).name.let { albumName ->
                            if (albumName.length > 12) albumName.substring(0, 12) + "..."
                            else albumName
                        }
                    }
                }",
                style = MaterialTheme.typography.titleMedium
            )
            if (multiSelection) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = {
                            Log.d("", "selectedAlbumList: $selectedAlbumList")
                            onSelectDone(selectedAlbumList)
                            onDismiss()
                        }
                    ) {
                        Icon(Icons.Filled.Done, "done")
                    }
                }
            }
        }
        LazyColumn {
            items(
                count = allAlbums.size
            ) { index ->
                allAlbums[index].let { album ->
                    Box(
                        modifier = Modifier.fillMaxSize().clickable { onClick(album) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp)
                        ) {
                            ImageItemRow(
                                albumName = File(album.albumPath).name,
                                imageCount = album.count,
                                albumAbsolutePath = album.albumPath,
                                imageFilePath = File(album.albumPath, album.path).absolutePath,
                                modifier = Modifier.weight(9f)
                            )
                            if (multiSelection) {
                                Checkbox(
                                    checked = album.albumPath in selectedAlbumList,
                                    onCheckedChange = { onClick(album) },
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                RadioButton(
                                    selected = album.albumPath in selectedAlbumList,
                                    onClick = { onClick(album) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingGroupView(
    modifier: Modifier = Modifier,
    title: String,
    items: List<ChoiceSettingItem<*>>,
    provideEnabled: () -> Boolean = { true },
    // trigger never gets disabled, the others are disabled if provideEnabled returns false
    enabledTrigger: String = "",
    onMultipleChoiceClick: (MultipleChoiceItem) -> Unit,
    onViewChoiceClick: (ViewChoiceItem) -> Unit,
) {
    val context = LocalContext.current

    ElevatedCard(
        modifier = modifier.width((LocalConfiguration.current.screenWidthDp * 0.95).dp).let {
            // the alpha in parent affects all of its children
            if (!provideEnabled()) it.alpha(0.5f)
            else it
        },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            // title
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            // content
            items.forEach { settingItem ->
                when (settingItem.type) {
                    ChoiceSettingItem.Type.TWO_CHOICE -> {
                        RowWithSwitch(
                            title = settingItem.title,
                            explain = settingItem.explain,
                            // postpone the call to provideChoice() inside RowWithSwitch so that when the State is changed, only RowWithSwitch gets recomposed
                            provideInitialChecked = (settingItem as ChoiceSettingItem<Boolean>).provideInitialChoice,
                            onSwitchClick = settingItem.onValueChange,
                            provideEnabled = { provideEnabled() || settingItem.title == enabledTrigger }
                        )
                    }

                    ChoiceSettingItem.Type.MULTI_CHOICE -> {
                        RowWithInfo(
                            title = settingItem.title,
                            explain = settingItem.explain,
                            onClick = {
                                onMultipleChoiceClick(
                                    MultipleChoiceItem(
                                        title = settingItem.title,
                                        provideInitialSelection = (settingItem as ChoiceSettingItem<String>).provideInitialChoice,
                                        items = settingItem.choices,
                                        onChoice = settingItem.onValueChange
                                    )
                                )
                            },
                            infoView = { mod ->
                                Text(
                                    text = settingItem.provideInitialChoiceString(),
                                    style = if (settingItem.provideInitialChoiceString().length > 6) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                                    modifier = mod
                                )
                            },
                            provideEnabled = { provideEnabled() || settingItem.title == enabledTrigger }
                        )
                    }

                    ChoiceSettingItem.Type.SLIDER_CHOICE -> {
                        RowWithSlideBarBelow(
                            title = settingItem.title,
                            explain = settingItem.explain,
                            provideInitialValue = (settingItem as ChoiceSettingItem<Float>).provideInitialChoice,
                            onSliderValueSubmit = settingItem.onValueChange,
                            sliderLeft = settingItem.choices[0].toFloat(),
                            sliderRight = settingItem.choices[1].toFloat(),
                            steps = settingItem.choices[2].toInt(),
                            provideEnabled = { provideEnabled() || settingItem.title == enabledTrigger },
                        )
                    }

                    ChoiceSettingItem.Type.VIEW_CHOICE -> {
                        RowWithInfo(
                            title = settingItem.title,
                            explain = settingItem.explain,
                            onClick = {
                                val purpose =
                                    when (settingItem.title) {
                                        context.getString(R.string.excluded_labels) -> {
                                            ViewChoiceItem.ViewChoicePurpose.EXCLUDED_LABEL
                                        }

                                        context.getString(R.string.excluded_labeling_albums) -> {
                                            ViewChoiceItem.ViewChoicePurpose.EXCLUDED_ALBUM
                                        }

                                        context.getString(R.string.default_album) -> {
                                            ViewChoiceItem.ViewChoicePurpose.DEFAULT_ALBUM
                                        }

                                        else -> ViewChoiceItem.ViewChoicePurpose.NOT_OPEN
                                    }
                                onViewChoiceClick(
                                    ViewChoiceItem(
                                        title = settingItem.title,
                                        purpose = purpose,
                                        provideInitialSelection = (settingItem as ChoiceSettingItem<List<String>>).provideInitialChoice,
                                        onChoices = settingItem.onValueChange
                                    )
                                )
                            },
                            infoView = { mod ->
                                Text(
                                    text = settingItem.provideInitialChoiceString(),
                                    style = if (settingItem.provideInitialChoiceString().length > 6) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                                    modifier = mod
                                )
                            },
                            provideEnabled = { provideEnabled() || settingItem.title == enabledTrigger }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RowWithSwitch(
    modifier: Modifier = Modifier,
    title: String,
    explain: String,
    provideEnabled: () -> Boolean,
    provideInitialChecked: () -> Boolean,
    onSwitchClick: (Boolean) -> Unit,
) {
    var checked by rememberSaveable { mutableStateOf(provideInitialChecked()) }
    Row(
        modifier = modifier.fillMaxWidth().let {
            if (provideEnabled()) it.clickable {
                checked = !checked
                onSwitchClick(checked)
            } else it
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RowWithInfo(
            title = title,
            explain = explain,
            onClick = {
                if (provideEnabled()) {
                    checked = !checked
                    onSwitchClick(checked)
                }
            },
            infoView = { mod ->
                Switch(
                    enabled = provideEnabled(),
                    checked = checked,
                    onCheckedChange = {
                        if (provideEnabled()) {
                            checked = !checked
                            onSwitchClick(checked)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedThumbColor = MaterialTheme.colorScheme.secondary,
                        uncheckedTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    thumbContent = if (checked) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
                    modifier = mod
                )
            },
            provideEnabled = provideEnabled
        )
    }
}

@Composable
fun RowWithSlideBarBelow(
    modifier: Modifier = Modifier,
    title: String,
    explain: String,
    sliderLeft: Float,
    sliderRight: Float,
    steps: Int,
    provideInitialValue: () -> Float,
    onSliderValueSubmit: (Float) -> Unit,
    provideEnabled: () -> Boolean,
) {
    var sliderHidden by remember { mutableStateOf<Boolean?>(null) }
    var sliderValue by rememberSaveable { mutableFloatStateOf(provideInitialValue()) }

    Column(modifier = modifier) {
        RowWithInfo(
            title = title,
            explain = explain,
            onClick = {
                if (provideEnabled()) {
                    sliderHidden = !(sliderHidden ?: true)
                    // recover the current value in the State when dismissed
                    sliderValue = provideInitialValue()
                }
            },
            infoView = { mod ->
                Text(
                    text = String.format("%.2f", sliderValue),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = mod
                )
            },
            provideEnabled = provideEnabled
        )
        AnimatedVisibility(
            visible = !(sliderHidden ?: true)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    colors = SliderColorsNoTicks().copy(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary,
                        inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    steps = steps,
                    valueRange = sliderLeft..sliderRight,
                    modifier = Modifier.weight(7f).padding(end = 36.dp, top = 8.dp),
                    enabled = provideEnabled()
                )
                IconButton(
                    enabled = provideEnabled(),
                    onClick = {
                        onSliderValueSubmit(sliderValue)
                        sliderHidden = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Done,
                        contentDescription = "done"
                    )
                }
            }
        }
    }
}

@Composable
fun RowWithInfo(
    modifier: Modifier = Modifier,
    title: String,
    explain: String,
    provideEnabled: () -> Boolean,
    onClick: () -> Unit,
    infoView: @Composable (Modifier) -> Unit,
) {
    TextColumn(
        modifier = modifier.let {
            if (provideEnabled()) it.clickable { onClick() }
            else it
        },
        title = title,
        explain = explain,
        infoView = infoView
    )
}

@Composable
fun TextColumn(
    modifier: Modifier = Modifier,
    title: String,
    explain: String,
    infoView: @Composable (Modifier) -> Unit = { mod -> Spacer(modifier = mod) },
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(6f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = explain,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier.weight(1f)
        ) {
            infoView(Modifier.align(Alignment.Center))
        }
    }

}

data class MultipleChoiceItem(
    val title: String = "",
    val provideInitialSelection: () -> String = { "" },
    val items: List<String> = emptyList(),
    val onChoice: (String) -> Unit = {},    // user selects one of the items
) {
    companion object {
        val notOpen = MultipleChoiceItem()
    }
}

data class ViewChoiceItem(
    val title: String = "",
    val purpose: ViewChoicePurpose = ViewChoicePurpose.NOT_OPEN,
    val provideInitialSelection: () -> List<String> = { emptyList() },
    val onChoices: (List<String>) -> Unit = {},
) {
    enum class ViewChoicePurpose {
        NOT_OPEN, EXCLUDED_ALBUM, EXCLUDED_LABEL, DEFAULT_ALBUM
    }

    companion object {
        val notOpen = ViewChoiceItem()
    }
}