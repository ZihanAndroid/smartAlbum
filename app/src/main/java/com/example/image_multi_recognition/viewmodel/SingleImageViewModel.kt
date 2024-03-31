package com.example.image_multi_recognition.viewmodel

import android.graphics.Rect
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.db.LabelInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.util.ControlledRunner
import com.example.image_multi_recognition.util.ExifHelper
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.viewmodel.basic.ImageFileOperationSupport
import com.example.image_multi_recognition.viewmodel.basic.ImageFileOperationSupportViewModel
import com.example.image_multi_recognition.viewmodel.basic.LabelSearchSupport
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
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SingleImageViewModel @Inject constructor(
    private val repository: ImageRepository,
    private val objectDetector: ObjectDetector,
    private val imageLabeler: ImageLabeler,
    imageFileOperationSupportViewModel: ImageFileOperationSupportViewModel,
    savedStateHandle: SavedStateHandle  // this parameter is set by hiltViewModel() automatically
) : ViewModel(), LabelSearchSupport, ImageFileOperationSupport by imageFileOperationSupportViewModel {
    // get navigation arguments
    val argumentType: Int = savedStateHandle.get<Int>("argumentType")!!
    val argumentValue: String = savedStateHandle.get<String>("argumentValue")!!
    val initialKey: Int = savedStateHandle.get<Int>("initialKey")!!
    var currentAlbum: Long? = null
    var currentLabel: String? = null

    private val controlledLabelingDoneRunner = ControlledRunner<Unit>()

    private val _pagingFlow: MutableStateFlow<Flow<PagingData<ImageInfo>>> = MutableStateFlow(emptyFlow())
    val pagingFlow: StateFlow<Flow<PagingData<ImageInfo>>>
        get() = _pagingFlow

    private val _imageLabelStateFlow = MutableStateFlow(ImageLabelLists())
    val imageLabelStateFlow: StateFlow<ImageLabelLists>
        get() = _imageLabelStateFlow

    private val _labelAddedCacheAvailable = MutableStateFlow(false)
    val labelAddedCacheAvailable: StateFlow<Boolean>
        get() = _labelAddedCacheAvailable

    var labelingClicked = false
    private var currentPage: Int = initialKey
    private var labelingFinished = true

    // label: confidence
    private var partImageLabelConfidenceMap = mutableMapOf<String, Float>()
    private var partImageLabelMap = mutableMapOf<String, ImageLabelResult>()
    private var wholeImageLabelList = mutableListOf<ImageLabelResult>()
    private var finishedLabelingTask = 0
    var imageSize: Pair<Int, Int> = Pair(0, 0)

    // selectedLabelSet is stateless, the change of it does not need to trigger recomposition, so no need to use State
    var selectedLabelSet = mutableSetOf<String>()

    // For InputView, ordered by label name first, then its count
    override var orderedLabelList: List<LabelInfo> = emptyList()

    init {
        when (argumentType) {
            1 -> {
                currentAlbum = argumentValue.toLong()
                currentLabel = null
                setPagingFlow(argumentValue.toLong(), initialKey)
            }

            2 -> {
                currentLabel = argumentValue
                setLabelPagingFlow(argumentValue, initialKey)
            }

            3 -> {
                currentAlbum = argumentValue.toLong()
                currentLabel = ""
                setAlbumUnlabeledPagingFlow(argumentValue.toLong(), initialKey)
            }

            else -> throw RuntimeException("Unrecognized argument type: $argumentType")
        }
        viewModelScope.launch {
            orderedLabelList = repository.getAllOrderedLabelList()
            Log.d(getCallSiteInfoFunc(), "initial popup labels: ${orderedLabelList.map { it.label }}")
        }
    }

    private fun setPagingFlow(album: Long, initialKey: Int) {
        Log.d(getCallSiteInfoFunc(), "initialKey: $initialKey")
        _pagingFlow.value = repository.getImagePagingFlow(album, initialKey).cachedIn(viewModelScope)
    }

    private fun setLabelPagingFlow(label: String, initialKey: Int) {
        _pagingFlow.value = repository.getImagePagingFlowByLabel(label, initialKey).cachedIn(viewModelScope)
    }

    private fun setAlbumUnlabeledPagingFlow(album: Long, initialKey: Int) {
        _pagingFlow.value = repository.getAlbumUnlabeledPagingFlow(album, initialKey).cachedIn(viewModelScope)
    }

    // this method starts a relatively long-running task, if a user presses the "label" button first,
    // then swipe to next page immediately, the previous labeling results may show in the next page!
    // To avoid this, you cannot cancel the callback from "OnSuccessListener" of "objectDetector",
    // instead, you should avoid doing something when you know the task is not needed in the callback
    fun detectAndLabelImage(imageInfo: ImageInfo) {
        if (!labelingFinished) return
        labelingFinished = false
        labelingClicked = true
        viewModelScope.launch {
            // Note using "joinPreviousOrRun()" does not work here,
            // because the following tasks starts other asynchronous tasks (detecting and labeling images),
            // which cannot be controlled by joinPreviousOrRun()!
            // joinPreviousOrRun() applies to tasks that does not start other asynchronous tasks
            // e.g. NOT work here: controlledLabelingRunner.joinPreviousOrRun {...}
            // create a new list to change the reference (so that recomposition can be triggered when you run "_imageLabelFlow.value = imageLabelList")
            partImageLabelMap = mutableMapOf()
            wholeImageLabelList = mutableListOf()
            partImageLabelConfidenceMap.clear()
            selectedLabelSet.clear()
            val currentPageWhenRunning = currentPage
            val imageHandled = withContext(Dispatchers.IO) {
                repository.getInputImage(imageInfo.fullImageFile)
            } ?: return@launch
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
                    // choose the label with max confidence and confidence >= ACCEPTED_CONFIDENCE
                    if (!labelList.isNullOrEmpty()) {
                        Log.d(getCallSiteInfoFunc(), "recognized label: $labelList")
                        if (rect != rootRect) {
                            labelList.filter { it.confidence >= DefaultConfiguration.ACCEPTED_CONFIDENCE }
                                .maxByOrNull { it.confidence }?.let { label: ImageLabel ->
                                // deduplication, choose the label with maximum confidence
                                if (partImageLabelConfidenceMap[label.text] == null || label.confidence > partImageLabelConfidenceMap[label.text]!!) {
                                    partImageLabelConfidenceMap[label.text] = label.confidence
                                    partImageLabelMap[label.text] = ImageLabelResult(imageInfo.id, rect, label.text)
                                }
                            }
                        } else {
                            // set maximum 2 whole image labels
                            with(labelList.filter { it.confidence >= DefaultConfiguration.ACCEPTED_CONFIDENCE }
                                .sortedBy { it.confidence }.reversed()) {
                                if (isNotEmpty()) {
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
                    Log.w(getCallSiteInfo(), "Labeling task is cancelled")
                    ++finishedLabelingTask
                    checkLabelingTaskCompletion(detectedRectList.size, currentPageWhenRunning)
                }
            }
        } ?: Log.e(getCallSiteInfoFunc(), "bitmapInternal of imageHandled is null!")
    }

    // This method is always called from UI main thread. As a result, you do not need synchronization for setting "labelingFinished"
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
            _imageLabelStateFlow.value = _imageLabelStateFlow.value.copy(
                // after labeling, the partImageLabelList and wholeImageLabelList may be empty but not null
                partImageLabelList = partImageLabelMap.map { it.value },
                // deduplication of whole image labels
                wholeImageLabelList = wholeImageLabelList.filter { it.label !in partImageLabelMap.keys },
                addedLabelList = null,
                labelingDone = false
            )
            labelingFinished = true
            Log.d(getCallSiteInfoFunc(), "recognized part image label list: ${partImageLabelMap.map { it.value }}")
            Log.d(getCallSiteInfoFunc(), "recognized whole image label list: $wholeImageLabelList")
        }
    }

    // clearPage does not change states
    fun clearPage(nextPage: Int) {
        _imageLabelStateFlow.value = ImageLabelLists()
        _labelAddedCacheAvailable.value = false
        labelingClicked = false
        currentPage = nextPage
        selectedLabelSet.clear()
    }

    fun setLabelAddedCacheAvailable(){
        _labelAddedCacheAvailable.value = true
    }

    // reset _imageLabelFlow to start recomposition
    fun resetImageLabelFlow(
        partImageLabelResult: List<ImageLabelResult>?,
        wholeImageLabelResult: List<ImageLabelResult>?,
        addedLabelList: List<String>?,
        labelingDone: Boolean,
        preview: Boolean
    ) {
        _imageLabelStateFlow.value = _imageLabelStateFlow.value.copy(
            partImageLabelList = partImageLabelResult,
            wholeImageLabelList = wholeImageLabelResult,
            addedLabelList = addedLabelList,
            labelingDone = labelingDone,
            preview = preview
        )
    }

    fun resetImageLabelFlow() {
        _imageLabelStateFlow.value = ImageLabelLists.InitialLists
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

    private val _imageExifFlow = MutableStateFlow<List<String>>(emptyList())
    val imageExifFlow: StateFlow<List<String>>
        get() = _imageExifFlow

    // get image exif information
    fun getImageInformation(imageFile: File) {
        viewModelScope.launch {
            _imageExifFlow.value = repository.getImageInformation(imageFile)
        }
    }

    fun resetImageInformation() {
        _imageExifFlow.value = emptyList()
    }

    fun setLabelPreview(imageInfo: ImageInfo) {
        viewModelScope.launch {
            // get original image size
            val imageHandled = withContext(Dispatchers.IO) {
                repository.getInputImage(imageInfo.fullImageFile)
            }?.let { inputImage ->
                imageSize = inputImage.width to inputImage.height
                repository.getLabelsByImageId(imageInfo.id).let { imageLabels ->
                    val partImageLabelList =
                        imageLabels.filter { it.rect != null }.map { ImageLabelResult.fromImageLabel(it) }
                    val wholeImageLabelList =
                        imageLabels.filter { it.rect == null }.map { ImageLabelResult.fromImageLabel(it) }
                    resetImageLabelFlow(
                        partImageLabelResult = partImageLabelList,
                        wholeImageLabelResult = wholeImageLabelList,
                        addedLabelList = null,
                        labelingDone = true,
                        preview = true
                    )
                }
            }
        }
    }

}

@Stable
data class ImageLabelResult(
    val imageId: Long,
    val rect: Rect?,
    val label: String
) {
    companion object {
        fun fromImageLabel(imageLabel: com.example.image_multi_recognition.db.ImageLabel): ImageLabelResult {
            return ImageLabelResult(imageLabel.id, imageLabel.rect, imageLabel.label)
        }
    }
}

// You should never use a MutableList inside a State like data class (e.g.: ImageLabelState)
// If you do so, then when you add data into the MutableList, the reference of the MutableList does not change,
// so the new value may not reflect to the screen.
// Instead, using List instead of MutableList makes sure
// that the change to the list is always achieved by changing list reference
data class ImageLabelLists(
    var partImageLabelList: List<ImageLabelResult>? = null,
    var wholeImageLabelList: List<ImageLabelResult>? = null,
    var addedLabelList: List<String>? = null,
    var labelingDone: Boolean = false,
    var preview: Boolean = false
) {
    companion object {
        val InitialLists = ImageLabelLists()
    }
}
