package com.example.image_multi_recognition.util

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toIntRect

// "key" is set by items(key) in lazy grid, assume key is consecutive
fun <T : Any> keyAtOffset(hitPoint: Offset, lazyGridState: LazyGridState): T? =
    lazyGridState.layoutInfo.visibleItemsInfo.find { itemInfo ->
        itemInfo.size.toIntRect().contains(hitPoint.round() - itemInfo.offset)
    }?.key as? T

// T is the type of items' key
// You need to set "lazyGridState.scrollBy(scrollingAmountOnDragging)" to use the following longPressAndDragSelection modifier
//    var scrollingAmountOnDragging by rememberSaveable { mutableStateOf(0f) }
//
//    LaunchedEffect(scrollingAmountOnDragging){
//        if(scrollingAmountOnDragging != 0f){
//            lazyGridState.scrollBy(scrollingAmountOnDragging)
//            delay(10)
//        }
//    }
fun <T : Any> Modifier.longPressAndDragSelection(
    lazyGridState: LazyGridState,
    scrollingAmountSetter: (Float) -> Unit,
    autoScrollThreshold: Float,
    // "provideDraggedKeys" can be set by using "onKeyProvided" below if the key is Int
    provideDraggedKeys: (initialKey: T?, prevKey: T?, currentKey: T?) -> Unit,
    onSelectionStart: (T) -> Unit,
    // in lazy grid, the UiModel may contain multiple kinds of items while we may only care about one of them,
    // like image item instead of title items, we can set "keyTracked()" for that
    keyTracked: (T) -> Boolean = { true },
    // Note the Long drag and press may conflict to onClick at some cases (after the LongPressAndDrag finished, the onClick event may also be triggered)
) = this.pointerInput(Unit) {
    var initialKey: T? = null
    var prevKey: T? = null

    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            initialKey = keyAtOffset<T>(offset, lazyGridState)?.let { key ->
                if (keyTracked(key)) {
                    prevKey = key
                    provideDraggedKeys(key, key, key)
                    onSelectionStart(key)
                    key
                } else null
            }
        },
        onDrag = { change, _ ->
            if (initialKey != null) {
                keyAtOffset<T>(change.position, lazyGridState)?.let { currentKey ->
                    if (keyTracked(currentKey)) {
                        provideDraggedKeys(initialKey, prevKey, currentKey)
                        prevKey = currentKey
                    }
                }
                // set scrolling when dragging
                val distFromBottom = lazyGridState.layoutInfo.viewportSize.height - change.position.y
                val distFromTop = change.position.y
                scrollingAmountSetter(
                    when {
                        distFromBottom < autoScrollThreshold -> autoScrollThreshold - distFromBottom
                        distFromTop < autoScrollThreshold -> -(autoScrollThreshold - distFromTop)
                        else -> 0f
                    }
                )
            }
        },
        onDragEnd = {
            scrollingAmountSetter(0f)
            initialKey = null
        },
        onDragCancel = {
            scrollingAmountSetter(0f)
            initialKey = null
        }
    )
}

fun <T : Any> Modifier.tapClick(
    lazyGridState: LazyGridState,
    onTap: (T) -> Unit,
    keyTracked: (T) -> Boolean
) = this.pointerInput(Unit) {
    detectTapGestures { offset: Offset ->
        keyAtOffset<T>(offset, lazyGridState)?.let { key ->
            if(keyTracked(key)){
                onTap(key)
            }
        }
    }
}

// key: Int, assume key is consecutive
fun onKeyProvided(
    initialKey: Int?,
    prevKey: Int?,
    currentKey: Int?,
    // we provide key instead of value here
    onKeyRemove: (Collection<Int>) -> Unit,
    onKeyAdd: (Collection<Int>) -> Unit,
    keyDeletedPreviously: (Int) -> Boolean = { false },
    keyExists: (Int) -> Boolean,
    keyTracked: (Int) -> Boolean
) {
    // all in the indexRange are the keys
    fun addOrRemoveImageId(indexRange: IntRange) {
        val targetKeys = indexRange.toSet()
        val removedKeys = targetKeys.filter { keyExists(it) }.toSet()
        onKeyRemove(removedKeys)
        // imageId may already be deleted by previous selection
        onKeyAdd((targetKeys - removedKeys).filterNot { keyDeletedPreviously(it) })
    }

    if (initialKey != null && prevKey != null && currentKey != null
        && keyTracked(initialKey) && keyTracked(prevKey) && keyTracked(currentKey)
    ) {
        if (prevKey != currentKey) {
            if (prevKey > initialKey) {
                if (currentKey > prevKey) {
                    addOrRemoveImageId(prevKey + 1..currentKey) // exclude prevKey
                } else {
                    // currentKey < prevKey
                    addOrRemoveImageId((if (currentKey < initialKey) currentKey else currentKey + 1)..prevKey) // exclude currentKey
                }
            } else if (prevKey < initialKey) {
                if (currentKey < prevKey) {
                    addOrRemoveImageId(currentKey..<prevKey)    // exclude prevKey
                } else {
                    addOrRemoveImageId(prevKey..<(if (currentKey > initialKey) currentKey + 1 else currentKey))    // exclude currentKey
                }
            } else {
                // exclude initialKey because we assume that it has been set properly by "onSelectionStart" parameter of modifier "longPressAndDragSelection()"
                if (currentKey < prevKey) {
                    addOrRemoveImageId(currentKey..<prevKey)    // exclude prevKey
                } else {
                    addOrRemoveImageId(prevKey + 1..currentKey)    // exclude currentKey
                }
            }
        }
    }
}