package com.example.image_multi_recognition.viewmodel

import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.db.LabelInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.util.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.objects.ObjectDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SingleImageViewModel @Inject constructor(
    private val repository: ImageRepository,
    private val objectDetector: ObjectDetector,
    private val imageLabeler: ImageLabeler,
    savedStateHandle: SavedStateHandle  // this parameter is set by hiltViewModel() automatically
) : ViewModel() {
    // get navigation arguments
    val album: Long = savedStateHandle.get<Long>("album")!!
    val initialKey: Int = savedStateHandle.get<Int>("initialKey")!!
    private val controlledLabelingRunner = ControlledRunner<Unit>()
    private val controlledLabelingDoneRunner = ControlledRunner<Unit>()

    private val _pagingFlow: MutableStateFlow<Flow<PagingData<ImageInfo>>> = MutableStateFlow(emptyFlow())
    val pagingFlow: StateFlow<Flow<PagingData<ImageInfo>>>
        get() = _pagingFlow

    private val _imageLabelStateFlow = MutableStateFlow(ImageLabelLists())
    val imageLabelStateFlow: StateFlow<ImageLabelLists>
        get() = _imageLabelStateFlow

    var labelingStart = false
    private var currentPage: Int = initialKey

    // label: confidence
    private var partImageLabelConfidenceMap = mutableMapOf<String, Float>()
    private var partImageLabelMap = mutableMapOf<String, ImageLabelResult>()
    private var wholeImageLabelList = mutableListOf<ImageLabelResult>()
    private var finishedLabelingTask = 0
    var imageSize: Pair<Int, Int> = Pair(0, 0)
    // selectedLabelSet is stateless, the change of it does not need to trigger recomposition, so no need to use State
    var selectedLabelSet = mutableSetOf<String>()

    // For InputView, ordered by label name first, then its count
    private var orderedLabelList: List<LabelInfo> = emptyList()

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

    init {
        setPagingFlow(album, initialKey)
        viewModelScope.launch {
            orderedLabelList = repository.getAllOrderedLabelList()
            Log.d(getCallSiteInfoFunc(), "initial popup labels: ${orderedLabelList.map { it.label }}")
        }
    }

    private fun setPagingFlow(album: Long, initialKey: Int) {
        _pagingFlow.value = repository.getImagePagingFlow(album, initialKey).cachedIn(viewModelScope)
    }

    // this method starts a relatively long-running task, if a user presses the "label" button first,
    // then swipe to next page immediately, the previous labeling results may show in the next page!
    // To avoid this, you cannot cancel the callback from "OnSuccessListener" of "objectDetector",
    // instead, you should avoid doing something when you know the task is not needed in the callback
    fun detectAndLabelImage(imageInfo: ImageInfo) {
        // handle the request only once for each page
        Log.d(getCallSiteInfoFunc(), "labelingStart: $labelingStart")
        if (labelingStart) return
        labelingStart = true
        // create a new list to change the reference (so that recomposition can be triggered when you run "_imageLabelFlow.value = imageLabelList")
        partImageLabelMap = mutableMapOf()
        wholeImageLabelList = mutableListOf()
        partImageLabelConfidenceMap.clear()
        selectedLabelSet.clear()

        viewModelScope.launch {
            controlledLabelingRunner.joinPreviousOrRun {
                val currentPageWhenRunning = currentPage
                val imageHandled = withContext(Dispatchers.IO) {
                    repository.getInputImage(imageInfo.fullImageFile)
                } ?: return@joinPreviousOrRun
                Log.d(
                    getCallSiteInfo(),
                    "image size for object detection: (width: ${imageHandled.width}, height: ${imageHandled.height})"
                )
                // the imageSize used by object detection may different from the size of the image displayed in CustomLayout
                imageSize = imageHandled.width to imageHandled.height

                objectDetector.process(imageHandled)
                    .addOnSuccessListener { detectedObjects ->
                        // onSuccessListener can be called anytime in the main thread,
                        // but the good thing is that the main thread is a single thread, everything in it is sequential
                        Log.d(
                            getCallSiteInfo(),
                            "Detected object bounds: ${detectedObjects.joinToString { it.boundingBox.toString() }}"
                        )
                        // Note that the OnSuccessListener runs in the UI main thread!
                        Log.d(
                            getCallSiteInfoFunc(),
                            "currentThread: ${Thread.currentThread().id}: ${Thread.currentThread().name}"
                        )
                        // change state to trigger recomposition
                        val detectedRectList = detectedObjects.map { it.boundingBox }.toMutableList()
                        // if the user has already swiped to the next page, we just ignore the result
                        if (currentPageWhenRunning == currentPage) {
                            // _rectListFlow.value = detectedRectList
                            val rootRect = Rect()   // a rect for the whole image, we also label the whole image
                            detectedRectList.add(rootRect)
                            // image labeling
                            labelingImage(imageInfo, imageHandled, detectedRectList, rootRect, currentPageWhenRunning)
                        } else {
                            Log.d(
                                getCallSiteInfoFunc(),
                                "current task is canceled, user moved from Page:$currentPageWhenRunning to Page:$currentPage"
                            )
                        }
                    }.addOnFailureListener { e ->
                        Log.e(getCallSiteInfo(), e.stackTraceToString())
                    }
            }
        }
    }

    private fun labelingImage(
        imageInfo: ImageInfo,
        imageHandled: InputImage,
        detectedRectList: List<Rect>,
        rootRect: Rect,
        currentPageWhenRunning: Int
    ) {
        imageHandled.bitmapInternal?.let { bitmap ->
            detectedRectList.forEach { rect ->
                imageLabeler.process(
                    if (rect == rootRect) {
                        imageHandled
                    } else {
                        InputImage.fromBitmap(
                            ExifHelper.cropBitmap(bitmap, rect),
                            imageInfo.rotationDegree
                        )
                    }
                ).addOnSuccessListener { labelList: List<ImageLabel>? ->
                    // choose the label with max confidence and confidence >= 0.6
                    if (!labelList.isNullOrEmpty()) {
                        Log.d(getCallSiteInfoFunc(), "recognized label: $labelList")
                        if (rect != rootRect) {
                            labelList.filter { it.confidence >= 0.6 }.maxBy { it.confidence }.let { label: ImageLabel ->
                                // deduplication, choose the label with maximum confidence
                                if (partImageLabelConfidenceMap[label.text] == null || label.confidence > partImageLabelConfidenceMap[label.text]!!) {
                                    partImageLabelConfidenceMap[label.text] = label.confidence
                                    partImageLabelMap[label.text] = ImageLabelResult(imageInfo.id, rect, label.text)
                                }
                            }
                        } else {
                            // set maximum 2 whole image labels
                            with(labelList.filter { it.confidence >= 0.7 }.sortedBy { it.confidence }.reversed()){
                                if(isNotEmpty()){
                                    subList(0, if (size > 1) 2 else size).forEach { label ->
                                        wholeImageLabelList.add(ImageLabelResult(imageInfo.id, rootRect, label.text))
                                    }
                                }
                            }
                        }
                    } else {
                        Log.d(getCallSiteInfoFunc(), "empty label for: $rect")
                    }
                    ++finishedLabelingTask
                    checkLabelingTaskCompletion(detectedRectList.size, currentPageWhenRunning)
                }.addOnFailureListener { e ->
                    Log.e(getCallSiteInfo(), e.stackTraceToString())
                    ++finishedLabelingTask
                    checkLabelingTaskCompletion(detectedRectList.size, currentPageWhenRunning)
                }.addOnCanceledListener {
                    Log.d(getCallSiteInfo(), "Labeling task is cancelled")
                    ++finishedLabelingTask
                    checkLabelingTaskCompletion(detectedRectList.size, currentPageWhenRunning)
                }
            }
        } ?: Log.e(getCallSiteInfoFunc(), "bitmapInternal of imageHandled is null!")
    }

    private fun checkLabelingTaskCompletion(requiredTaskCount: Int, currentPageWhenRunning: Int) {
        if (currentPage == currentPageWhenRunning && requiredTaskCount == finishedLabelingTask) {
            finishedLabelingTask = 0
            // Bad implementation, MutableStateFlow compares the new value with the old one by "equals()" method, not by reference.
            // When you set _imageLabelStateFlow.value.partImageLabelList and _imageLabelStateFlow.value.wholeImageLabelList
            // you have already changed the value stored in _imageLabelStateFlow,
            // then you set the same _imageLabelStateFlow.value.copy() (which has the same lists) to _imageLabelStateFlow.value.
            // And equals() methods of data class compares the property of the instances, not the instance reference
            //      _imageLabelStateFlow.value.partImageLabelList = partImageLabelList
            //      _imageLabelStateFlow.value.wholeImageLabelList = wholeImageLabelList
            //      _imageLabelStateFlow.value = _imageLabelStateFlow.value.copy()

            // The following two conditions must be fulfilled to make MutableStateFlow emit a new value:
            // (1) reference change to the MutableStateFlow.value
            // (2) the changed value is not equal to the previous one by standard of "equals()" method

            // change reference to trigger recomposition
            // val labelSet = setOf(*partImageLabelList.map { it.label }.toTypedArray())
            _imageLabelStateFlow.value = _imageLabelStateFlow.value.copy(
                partImageLabelList = partImageLabelMap.map { it.value },
                // deduplication of whole image labels
                wholeImageLabelList = wholeImageLabelList.filter { it.label !in partImageLabelMap.keys },
                addedLabelList = null,
                labelingDone = false
            )
            Log.d(getCallSiteInfoFunc(), "recognized part image label list: ${partImageLabelMap.map { it.value }}")
            Log.d(getCallSiteInfoFunc(), "recognized whole image label list: $wholeImageLabelList")
        }
    }

    // clearPage does not change states
    fun clearPage(nextPage: Int) {
        _imageLabelStateFlow.value = ImageLabelLists()
        labelingStart = false
        currentPage = nextPage
        selectedLabelSet.clear()
    }

    // reset _imageLabelFlow to start recomposition
    fun resetImageLabelFlow(
        partImageLabelResult: List<ImageLabelResult>?,
        wholeImageLabelResult: List<ImageLabelResult>?,
        addedLabelList: List<String>?,
        labelingDone: Boolean
    ) {
        _imageLabelStateFlow.value = _imageLabelStateFlow.value.copy(
            partImageLabelList = partImageLabelResult,
            wholeImageLabelList = wholeImageLabelResult,
            addedLabelList = addedLabelList,
            labelingDone = labelingDone
        )
    }

    fun setAddedLabelList(labelList: List<String>?) {
        // _addedLabelListFlow.value = labelList.toMutableList()
        _imageLabelStateFlow.value = _imageLabelStateFlow.value.copy(
            addedLabelList = labelList
        )
    }

    fun updateLabelAndResetOrderedList(imageLabels: List<com.example.image_multi_recognition.db.ImageLabel>) {
        viewModelScope.launch {
            controlledLabelingDoneRunner.joinPreviousOrRun {
                orderedLabelList = repository.updateImageLabelAndGetAllOrderedLabelList(imageLabels)
                Log.d(getCallSiteInfoFunc(), "reset popup labels: ${orderedLabelList.map { it.label }}")
            }
        }
    }
}

data class ImageLabelResult(
    val imageId: Long,
    val rect: Rect?,
    val label: String
)

// You should never use a MutableList inside a State like data class (e.g.: ImageLabelState)
// If you do so, then when you add data into the MutableList, the reference of the MutableList does not change,
// so the new value may not reflect to the screen.
// Instead, using List instead of MutableList makes sure
// that the change to the list is always achieved by change list reference
data class ImageLabelLists(
    var partImageLabelList: List<ImageLabelResult>? = null,
    var wholeImageLabelList: List<ImageLabelResult>? = null,
    var addedLabelList: List<String>? = null,
    var labelingDone: Boolean = false
)