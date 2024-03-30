package com.example.image_multi_recognition.util

import android.util.Log
import androidx.compose.foundation.gestures.*
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs

// Assume the List<T> is ordered by ascendant, return -1 if no element is found
// "element" is passed as the first parameter of "comparator()"
fun <T> List<T>.binarySearchLowerBoundIndex(
    element: T,
    start: Int = 0,
    end: Int = size,
    comparator: Comparator<T>,
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
    comparator: Comparator<T>,
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
inline fun <reified T, reified R, reified U : Comparable<U>> List<T>.getDifference(
    list: List<R>,
    keyExtractorThis: (T) -> U,
    keyExtractorParam: (R) -> U,
): Pair<List<T>, List<R>> {
    val notInParamList = mutableListOf<T>()
    val paramMap = mutableMapOf(*(list.map { keyExtractorParam(it) to (it to false) }.toTypedArray()))
    forEach {
        if (keyExtractorThis(it) !in paramMap) {
            notInParamList.add(it)
        } else {
            paramMap[keyExtractorThis(it)] = paramMap[keyExtractorThis(it)]!!.copy(second = true)
        }
    }
    return notInParamList to paramMap.filter { !it.value.second }.map { it.value.first }
}

// Note you cannot just use File.toUri or Uri.parse(File) to access the media file in external storage
// (You can do that in your app's internal storage)
// Because you will get a file uri, not an image uri.
// And maybe you do not have the permission to access file uri in external storage because if you just request READ_MEDIA_IMAGES
// fun Context.getImageFileUri(): Uri{
//    contentResolver.query(
//        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
//
//
//        )
//}


fun <K : Comparable<K>, V : Any, U : Any> Map<K, Collection<V>>.toPagingSource(
    itemsPerPage: Int,
    keyMapper: (K) -> U,
    valueMapper: (K, V) -> U,
): PagingSource<Int, U> = object : PagingSource<Int, U>() {
    private var keyList = this@toPagingSource.keys.toList().sortedBy { it }
    private var prevKeyIndex: Int = 0
    private var prevValueIndex: Int = -1

    private fun done(): Boolean {
        return prevKeyIndex == keyList.size
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, U> {
        val position = params.key ?: 0
        return try {
            val convertedData = mutableListOf<U>()
            while (convertedData.size < params.loadSize && !done()) {
                val valueList = this@toPagingSource[keyList[prevKeyIndex]]!!.toList()
                if (prevValueIndex == -1 && valueList.isNotEmpty()) {
                    convertedData.add(keyMapper(keyList[prevKeyIndex]))
                } else {
                    convertedData.add(valueMapper(keyList[prevKeyIndex], valueList[prevValueIndex]))
                }
                prevValueIndex++
                if (prevValueIndex == valueList.size) {
                    prevKeyIndex++
                    prevValueIndex = -1
                }
            }

            LoadResult.Page(
                // put a List<Repo> as the data of a page, the PagingData is just a wrapper to the LoadResult.Page
                data = convertedData,
                prevKey = if (position == 0) null else position - 1,
                nextKey = if (done()) {
                    null
                } else {
                    position + (params.loadSize) / itemsPerPage
                }
            )
        } catch (e: Throwable) {
            Log.e(getCallSiteInfoFunc(), e.stackTraceToString())
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, U>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}

fun <T : Any> List<T>.toPagingSource(itemsPerPage: Int): PagingSource<Int, T> = object : PagingSource<Int, T>() {
    private var index: Int = 0

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val position = params.key ?: 0
        return try {
            val indexEnd =
                if (index + params.loadSize > this@toPagingSource.size) this@toPagingSource.size else index + params.loadSize
            LoadResult.Page(
                data = this@toPagingSource.subList(index, indexEnd),
                prevKey = if (position == 0) null else position - 1,
                nextKey = if (indexEnd >= this@toPagingSource.size) {
                    null
                } else {
                    position + (params.loadSize) / itemsPerPage
                }
            ).apply { index = indexEnd }
        } catch (e: Throwable) {
            Log.e(getCallSiteInfoFunc(), e.stackTraceToString())
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}

suspend fun showSnackBar(snackbarHostState: SnackbarHostState, message: String, delayTimeMillis: Long = 1000) {
    coroutineScope {
        launch {
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Indefinite)
        }.apply {
            delay(delayTimeMillis)
            cancel()
        }
    }
}

// abc.jpg -> "abc", "jpg"
fun String.splitLastBy(delimiter: Char = '.'): Pair<String, String> {
    val lastIndex = indexOfLast { char -> char == '.' }.let {
        if (it == -1) length else it
    }
    val prefix = this.substring(0, lastIndex)
    // suffix contains '.'
    val suffix = this.substring(lastIndex, length)
    return prefix to suffix
}