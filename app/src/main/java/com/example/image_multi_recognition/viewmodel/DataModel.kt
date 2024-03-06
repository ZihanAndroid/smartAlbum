package com.example.image_multi_recognition.viewmodel

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
        val dayOfWeek: DayOfWeek
    ) : UiModel()

    data class Item(
        val imageInfo: ImageInfo,
        val originalIndex: Int,
    ) : UiModel()
}

sealed class LabelUiModel {
    data class Label(
        val label: String
    ) : LabelUiModel()

    data class Item(
        val imageInfo: ImageInfo
    ) : LabelUiModel()
}

// Before user labeling
data class ObjectDetectedImageData(
    val imageInfo: ImageInfo,
    val generatedLabels: Map<Rect, String>
)

// After user labeling
data class UserLabeledImageData(
    val imageInfo: ImageInfo,
    val labels: List<String>
)