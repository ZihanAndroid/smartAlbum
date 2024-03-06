package com.example.image_multi_recognition.util

import android.graphics.Rect
import android.util.Log
import java.util.*

// placing labels inside rects to minimize the overlap among those labels
object LabelPlacingStrategy {
    fun placeLabels(rectList: List<Rect>): List<PlacingResult> {
        if (rectList.isEmpty()) return emptyList()
        val remainingList = LinkedList(rectList)
        val rectToIndexMap = mapOf(*(rectList.mapIndexed { index, rect -> rect to index }.toTypedArray()))
        // set orderList[0] to empty value. (midPoint, rect)
        val orderedList = mutableListOf(Pair(0, 0) to Rect())
        var prevRect = remainingList[0]
        remainingList.removeFirst()
        // form a list of rect in clock-wise order of their position
        while (remainingList.isNotEmpty()) {
            val midPoint = getMidPoint(prevRect)
            val nextPointIndexed = remainingList.mapIndexed { index, rect: Rect ->
                getMidPoint(rect) to index
            }.minWith { p1x, p2x ->
                val p1 = p1x.first
                val p2 = p2x.first
                // compare by quadrant, smaller one comes first
                val p1Quadrant = p1.quadrantTo(midPoint)
                val p2Quadrant = p2.quadrantTo(midPoint)
                if (p1Quadrant == p2Quadrant) {
                    // compare by degrees, smaller one comes first
                    val p1Degree = p1.degreeTo(midPoint)
                    val p2Degree = p2.degreeTo(midPoint)
                    if (p1Degree == p2Degree) {
                        // compare by distance, smaller one comes first
                        p1.distanceSquareTo(midPoint) - p2.distanceSquareTo(midPoint)
                    } else {
                        p1Degree - p2Degree
                    }
                } else {
                    p1Quadrant - p2Quadrant
                }
            }
            val currentRect = remainingList[nextPointIndexed.second]
            orderedList.add(nextPointIndexed.first to currentRect)
            remainingList.remove(currentRect)
            prevRect = currentRect
        }
        // recover the value of orderedList[0]
        orderedList[0] = Pair(getMidPoint(rectList[0]), rectList[0])
        Log.d(getCallSiteInfoFunc(), "orderedList: $orderedList")
        // after sorting by clock-wise order,
        // finding the most appropriate position to place each rect based on its position to the previous rect
        return orderedList.mapIndexed { index, ordered ->
            val prevOrdered = if (index == 0) orderedList.last() else orderedList[index - 1]
            val curRect = ordered.second
            // put the label within the rect
            when (ordered.first.quadrantTo(prevOrdered.first)) {
                1 -> PlacingResult(curRect.right, curRect.top, 1)
                2 -> PlacingResult(curRect.right, curRect.bottom, 2)
                3 -> PlacingResult(curRect.left, curRect.bottom, 3)
                4 -> PlacingResult(curRect.left, curRect.top, 4)
                else -> throw RuntimeException("Unexpected value obtained!")   // should never be here
            } to rectToIndexMap[curRect]
        }.sortedBy { it.second }
            .map { it.first }   // sort the result according to the index of curRect in the original rectList
    }

    // make the whole label inside the boundary of rect
    fun convertPlacingResult(
        placingResultList: List<PlacingResult>,
        labelWidth: List<Int>,
        labelHeight: List<Int>,
        widthProportion: Double,
        heightProportion: Double,
        boundaryWidth: Int,
        boundaryHeight: Int
    ): List<Pair<Int, Int>> {
        return placingResultList.mapIndexed { index, placingResult ->
            when (placingResult.quadrant) {
                1 -> Pair(placingResult.x * widthProportion - labelWidth[index], placingResult.y * heightProportion)
                2 -> Pair(
                    placingResult.x * widthProportion - labelWidth[index],
                    placingResult.y * heightProportion - labelHeight[index]
                )

                3 -> Pair(placingResult.x * widthProportion, placingResult.y * heightProportion - labelHeight[index])
                4 -> Pair(placingResult.x * widthProportion, placingResult.y * heightProportion)
                else -> throw RuntimeException("Unexpected value obtained!")   // should never be here
            }
        }.mapIndexed { index, pair ->
            Pair(pair.first.toInt(), pair.second.toInt()).adjustBoundary(
                labelWidth = labelWidth[index],
                labelHeight = labelHeight[index],
                boundaryWidth = boundaryWidth,
                boundaryHeight = boundaryHeight
            )
        }
    }

    // avoid making the label exceed the boundary of an image
    private fun Pair<Int, Int>.adjustBoundary(
        labelWidth: Int,
        labelHeight: Int,
        boundaryWidth: Int,
        boundaryHeight: Int
    ): Pair<Int, Int> {
        val x = if (first < 0) 0
        else if (first + labelWidth > boundaryWidth) boundaryWidth - labelWidth
        else first
        val y = if (second < 0) 0
        else if (second + labelHeight > boundaryHeight) boundaryHeight - labelHeight
        else second
        return Pair(x, y)
    }

    private fun getMidPoint(rect: Rect): Pair<Int, Int> = rect.left + rect.width() / 2 to rect.top + rect.height() / 2
    private fun <T : Comparable<T>> Pair<T, T>.quadrantTo(p1: Pair<T, T>): Int =
        when {
            first >= p1.first && second <= p1.second -> 1
            first >= p1.first && second >= p1.second -> 2
            first <= p1.first && second >= p1.second -> 3
            first <= p1.first && second <= p1.second -> 4
            else -> throw RuntimeException("Unexpected condition reached!")   // should never be here
        }

    private fun Pair<Int, Int>.degreeTo(p1: Pair<Int, Int>): Int =
        if (first == p1.first) {
            if (second >= p1.second) Int.MAX_VALUE
            else Int.MIN_VALUE
        } else {
            (second - p1.second) / (first - p1.first)
        }

    private fun Pair<Int, Int>.distanceSquareTo(p1: Pair<Int, Int>): Int =
        if (first == p1.first) {
            second - p1.second
        } else if (second == p1.second) {
            first - p1.first
        } else {
            (first - p1.first) * (first - p1.first) + (second - p1.second) * (second - p1.second)
        }
}

/* quadrant: the corner to place the label
    quadrant 4-----------------------quadrant 1
        |                               |
        |                               |
        |                               |
    quadrant 3-----------------------quadrant 2
 */
data class PlacingResult(
    val x: Int,
    val y: Int,
    val quadrant: Int
)

// Note the result from LabelPlacingStrategy is based on the original rects;
// However, the selected labels and original labels are different (only part of the labels would be selected by the user),
// As a result, if the same LabelPlacingStrategy applies, the position of the same label before and after labeling may change.
// To avoid the problem, cache the previous labeling result and use it at the appropriate time
object CachedLabelPlacingStrategy {
    private val labelRectMap = mutableMapOf<String, PlacingResult>()

    // if rectList is null, then returning the previous result
    fun placeLabels(labelList: List<String>, rectList: List<Rect>? = null): List<PlacingResult> {
        if (!rectList.isNullOrEmpty()) assert(labelList.size == rectList.size)
        if (!rectList.isNullOrEmpty()) {
            with(LabelPlacingStrategy.placeLabels(rectList).mapIndexed { index, placingResult ->
                labelList[index] to placingResult
            }){
                labelRectMap.putAll(this)
            }
        }
        return labelList.map { labelRectMap[it]!! }
    }
}