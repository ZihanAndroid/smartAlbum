package com.example.image_multi_recognition.viewmodel

import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.util.ExifHelper
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
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
    val album: String = savedStateHandle.get<String>("album")!!
    val initialKey: Int = savedStateHandle.get<Int>("initialKey")!!

    private val _pagingFlow: MutableStateFlow<Flow<PagingData<ImageInfo>>> = MutableStateFlow(emptyFlow())
    val pagingFlow: StateFlow<Flow<PagingData<ImageInfo>>>
        get() = _pagingFlow

    //    private val _rectListFlow = MutableStateFlow<MutableList<Rect>>(mutableListOf())
//    val rectListFlow: StateFlow<List<Rect>>
//        get() = _rectListFlow
    var imageSize: Pair<Int, Int> = Pair(0, 0)

    private val _imageLabelFlow = MutableStateFlow<MutableList<ImageLabelResult>>(mutableListOf())
    val imageLabelFlow: StateFlow<List<ImageLabelResult>>
        get() = _imageLabelFlow
    private var requestSent = false

    private var currentPage: Int = initialKey
    private var imageLabelList = mutableListOf<ImageLabelResult>()
    private var finishedLabelingTask = 0
    private var labelAdded = 0

    init {
        setPagingFlow(album, initialKey)
    }

    private fun setPagingFlow(album: String, initialKey: Int) {
        _pagingFlow.value = repository.getImagePagingFlow(album, initialKey).cachedIn(viewModelScope)
    }

    // this method starts a relatively long-running task, if a user presses the "label" button first,
    // then swipe to next page immediately, the previous labeling results may show in the next page!
    // To avoid this, you cannot cancel the callback from "OnSuccessListener" of "objectDetector",
    // instead, you should avoid doing something when you know the task is not needed in the callback
    fun detectAndLabelImage(imageInfo: ImageInfo) {
        // handle the request only once for each page
        if (requestSent) return
        requestSent = true
        viewModelScope.launch {
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
                    // choose the label with max confidence and confidence >= 0.6
                    if (!labelList.isNullOrEmpty()) {
                        Log.d(getCallSiteInfoFunc(), "recognized label: $labelList")
                        if (rect != rootRect) {
                            labelList.filter { it.confidence >= 0.6 }.maxBy { it.confidence }.let { label: ImageLabel ->
                                imageLabelList.add(ImageLabelResult(imageInfo.id, rect, label.text))
                            }
                        } else {
                            labelList.filter { it.confidence >= 0.6 }.sortedBy { it.confidence }.reversed()
                                .subList(0, if (labelList.size > 1) 2 else labelList.size).forEach { label ->
                                    imageLabelList.add(ImageLabelResult(imageInfo.id, null, label.text))
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
            _imageLabelFlow.value = if(imageLabelList.isEmpty()) ImageLabelResult.EmptyResultList.toMutableList() else imageLabelList
            Log.d(getCallSiteInfoFunc(), "recognized label list: $imageLabelList")
        }
    }

    // clearPage does not change states
    fun clearPage(nextPage: Int) {
        // clear list does not change the reference, so the recomposition will be triggered by this "clearRectList()"
        _imageLabelFlow.value.clear()
        requestSent = false
        currentPage = nextPage
        finishedLabelingTask = 0
        // create a new list to change the reference (so that recomposition can be triggered when you run "_imageLabelFlow.value = imageLabelList")
        imageLabelList = mutableListOf()
        labelAdded = 0
    }
}

data class ImageLabelResult(
    val imageId: Long,
    val rect: Rect?,    // when rect is null, it means that the label is for the whole image, not a part of the image
    val label: String
){
    companion object{
        // a special object to identify an empty labeling result from the initial empty list
        val EmptyResultList = listOf(ImageLabelResult(-1, null, ""))
        fun isEmptyResult(resultList: List<ImageLabelResult>) = resultList.size == 1 && resultList[0] == EmptyResultList[0]
    }
}