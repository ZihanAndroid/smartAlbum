package com.example.image_multi_recognition.viewmodel.basic

import android.util.Log
import com.example.image_multi_recognition.db.LabelInfo
import com.example.image_multi_recognition.util.binarySearchLowerBoundIndex
import com.example.image_multi_recognition.util.binarySearchUpperBoundIndex
import com.example.image_multi_recognition.util.getCallSiteInfoFunc

interface LabelSearchSupport{
    var orderedLabelList: List<LabelInfo>
    // Assume that "prefix" is lowercase
    private fun getRangeByPrefix(prefix: String): Pair<Int, Int> {
        val comparator = Comparator<LabelInfo> { prefixElement, labelInfo ->
            val lowercaseElement = prefixElement.label
            val lowercaseLabel = labelInfo.label.lowercase()
            // prefix match, ignore case
            if (lowercaseElement.length <= lowercaseLabel.length
                && lowercaseElement == lowercaseLabel.substring(0, lowercaseElement.length)
            ) {
                0
            } else {
                lowercaseElement.compareTo(lowercaseLabel)
            }
        }
        return orderedLabelList.binarySearchLowerBoundIndex(
            element = LabelInfo(prefix, 0), comparator = comparator
        ) to orderedLabelList.binarySearchUpperBoundIndex(
            element = LabelInfo(prefix, 0), comparator = comparator
        )
    }

    fun getLabelListByPrefix(prefix: String): List<LabelInfo> {
        if (prefix.isEmpty()) return emptyList()
        return with(getRangeByPrefix(prefix.lowercase())) {
            if (first != -1 && second != -1 && first <= second) {
                Log.d(getCallSiteInfoFunc(), "found: ($first, $second)")
                orderedLabelList.subList(first, second + 1)
            } else {
                Log.w(getCallSiteInfoFunc(), "no reasonable index found: ($first, $second)")
                emptyList()
            }
        }.apply {
            Log.d(getCallSiteInfoFunc(), "obtained popup labels: $this")
        }
    }
}