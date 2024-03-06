package com.example.image_multi_recognition.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log

// Assume the List<T> is ordered by ascendant, return -1 if no element is found
// "element" is passed as the first parameter of "comparator()"
fun <T> List<T>.binarySearchLowerBoundIndex(
    element: T,
    start: Int = 0,
    end: Int = size,
    comparator: Comparator<T>
): Int {
    if (!(start < end && this.size >= end && start >= 0)) return -1
    // set (startIndex, endIndex], the passed parameter is [start, end)
    var startIndex = start - 1
    var endIndex = end - 1
    var midIndex = (startIndex + endIndex) / 2
    // Log.d(getCallSiteInfoFunc(), "element: $element, mid: ${this[midIndex]}")
    while (startIndex + 1 < endIndex) {
        if (comparator.compare(element, this[midIndex]) <= 0) {
            endIndex = midIndex
            // Log.d(getCallSiteInfoFunc(), "Left: ($startIndex, $endIndex)")
        } else {
            startIndex = midIndex
            // Log.d(getCallSiteInfoFunc(), "Right: ($startIndex, $endIndex)")
        }
        midIndex = (startIndex + endIndex) / 2
    }
    return if (comparator.compare(element, this[endIndex]) == 0) endIndex else -1
}

fun <T> List<T>.binarySearchUpperBoundIndex(
    element: T,
    start: Int = 0,
    end: Int = size,
    comparator: Comparator<T>
): Int {
    if (!(start < end && this.size >= end && start >= 0)) return -1
    // set [startIndex, endIndex)
    var startIndex = start
    var endIndex = end
    var midIndex = (startIndex + endIndex) / 2

    while (startIndex + 1 < endIndex) {
        if (comparator.compare(element, this[midIndex]) >= 0) {
            startIndex = midIndex
        } else {
            endIndex = midIndex
        }
        midIndex = (startIndex + endIndex) / 2
    }
    return if (comparator.compare(element, this[startIndex]) == 0) startIndex else -1
}

fun String.capitalizeFirstChar(): String =
    if (this.isEmpty()) {
        ""
    } else {
        this.first().uppercaseChar() + if (this.length > 1) this.substring(1) else ""
    }

// return: (List<T>: in "this" list while not in the parameter list,
//          List<R>: in the parameter list while not in "this" list)
// T and R are compared by a common comparable U
inline fun <reified T, reified R, reified U: Comparable<U>> List<T>.getDifference(
    list: List<R>,
    keyExtractorThis: (T) -> U,
    keyExtractorParam: (R) -> U
): Pair<List<T>, List<R>> {
    val notInParamList = mutableListOf<T>()
    val paramMap = mutableMapOf(*(list.map { keyExtractorParam(it) to (it to false)}.toTypedArray()))
    forEach {
        if(keyExtractorThis(it) !in paramMap){
            notInParamList.add(it)
        }else{
            paramMap[keyExtractorThis(it)] = paramMap[keyExtractorThis(it)]!!.copy(second = true)
        }
    }
    return notInParamList to paramMap.filter { !it.value.second }.map { it.value.first}
}

// Note you cannot just use File.toUri or Uri.parse(File) to access the media file in external storage
// (You can do that in your app's internal storage)
// Because you will get a file uri, not an image uri.
// And maybe you do not have the permission to access file uri in external storage because if you just request READ_MEDIA_IMAGES
//fun Context.getImageFileUri(): Uri{
//    contentResolver.query(
//        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//
//
//        )
//}