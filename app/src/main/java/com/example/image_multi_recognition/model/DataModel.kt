package com.example.image_multi_recognition.model

import android.graphics.Rect
import com.example.image_multi_recognition.db.ImageInfo
import java.time.DayOfWeek
import java.time.Month

sealed class UiModel {
    // when come across ItemHeaderYearMonth, show both YearMonth and Day titles
    data class ItemHeaderYearMonth(
        val year: Int,
        val month: Month,
        val dayOfMonth: Int,
        val dayOfWeek: DayOfWeek,
    ) : UiModel()

    data class ItemHeaderDay(
        val year: Int,
        val month: Month,
        val dayOfMonth: Int,
        val dayOfWeek: DayOfWeek,
    ) : UiModel()

    data class Item(
        val imageInfo: ImageInfo,
        // val originalIndex: Int,
    ) : UiModel()
}

sealed class LabelUiModel {
    data class Label(
        val label: String,
    ) : LabelUiModel()

    data class Item(
        val imageInfo: ImageInfo,
        val label: String,
    ) : LabelUiModel()
}

// Before user labeling
data class ObjectDetectedImageData(
    val imageInfo: ImageInfo,
    val generatedLabels: Map<Rect, String>,
)

// After user labeling
data class UserLabeledImageData(
    val imageInfo: ImageInfo,
    val labels: List<String>,
)

enum class RotationDegree(
    private val degree: Float,
) {
    D0(0f), D90(90f), D180(180f), D270(270f);

    fun toFloat(): Float = degree
}

data class SettingGroup(
    val title: String,
    val items: List<ChoiceSettingItem<*>>,
)

class ChoiceSettingItem<T : Any>(
    val provideInitialChoice: () -> T,
    // String to show the current choice in the right Text()
    val provideInitialChoiceString: () -> String = { "" },
    // provide the value that selected by a user
    val onValueChange: (T) -> Unit,
    val title: String,
    val explain: String, // explain or the value of the item
    val type: Type,
    // provide choices for Type: MULTI_CHOICE; endian values for SLIDER_CHOICE
    val choices: List<String> = emptyList(),
) {
    enum class Type {
        TWO_CHOICE,     // Boolean
        MULTI_CHOICE,   // String
        SLIDER_CHOICE,  // Float
        VIEW_CHOICE     // List<String>
    }
}

sealed class ContextualFlowItem<T> {
    data class Title<T>(
        val title: String,
    ) : ContextualFlowItem<T>()

    data class Item<T>(
        val item: T,
    ) : ContextualFlowItem<T>()

    companion object {
        fun <T> mapToContextualFlowItemWithTitle(
            items: Iterable<T>,
            emptyItem: T,
            // return an empty String if you do not want a title between prev and current
            onNewTitle: (prev: T, current: T) -> String,
        ): List<ContextualFlowItem<T>> {
            var prev = emptyItem
            return mutableListOf<ContextualFlowItem<T>>().apply {
                items.forEach { curItem ->
                    onNewTitle(prev, curItem).let { title ->
                        if (title.isNotEmpty()) {
                            add(Title(title))
                        }
                    }
                    add(Item(curItem))
                    prev = curItem
                }
            }
        }

        // No title mapped
        fun <T> mapToContextualFlowItem(items: Iterable<T>): List<ContextualFlowItem<T>> {
            return mutableListOf<ContextualFlowItem<T>>().apply {
                items.forEach { add(Item(it)) }
            }
        }
    }
}