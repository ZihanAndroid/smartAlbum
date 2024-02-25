package com.example.image_multi_recognition.util

import android.util.Log

// Assume the List<T> is ordered by ascendant, return -1 if no element is found
// "element" is passed as the first parameter of "comparator()"
fun <T> List<T>.binarySearchLowerBoundIndex(
    element: T,
    start: Int = 0,
    end: Int = size,
    comparator: Comparator<T>
): Int {
    if(!(start < end && this.size >= end && start >= 0)) return -1
    // set (startIndex, endIndex], the passed parameter is [start, end)
    var startIndex = start-1
    var endIndex = end-1
    var midIndex = (startIndex + endIndex) / 2
    // Log.d(getCallSiteInfoFunc(), "element: $element, mid: ${this[midIndex]}")
    while(startIndex + 1 < endIndex){
        if(comparator.compare(element, this[midIndex]) <= 0){
            endIndex = midIndex
            // Log.d(getCallSiteInfoFunc(), "Left: ($startIndex, $endIndex)")
        }else{
            startIndex = midIndex
            // Log.d(getCallSiteInfoFunc(), "Right: ($startIndex, $endIndex)")
        }
        midIndex = (startIndex + endIndex) / 2
    }
    return if(comparator.compare(element, this[endIndex]) == 0) endIndex else -1
}

fun String.capitalizeFirstChar(): String =
    if(this.isEmpty()) {
        ""
    } else{
        this.first().uppercaseChar() + if (this.length > 1) this.substring(1) else ""
    }

fun <T> List<T>.binarySearchUpperBoundIndex(
    element: T,
    start: Int = 0,
    end: Int = size,
    comparator: Comparator<T>
): Int {
    if(!(start < end && this.size >= end && start >= 0)) return -1
    // set [startIndex, endIndex)
    var startIndex = start
    var endIndex = end
    var midIndex = (startIndex + endIndex) / 2

    while(startIndex + 1 < endIndex){
        if(comparator.compare(element, this[midIndex]) >= 0){
            startIndex = midIndex
        }else{
            endIndex = midIndex
        }
        midIndex = (startIndex + endIndex) / 2
    }
    return if(comparator.compare(element, this[startIndex]) == 0) startIndex else -1
}