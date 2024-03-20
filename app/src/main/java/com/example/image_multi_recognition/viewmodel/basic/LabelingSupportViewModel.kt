package com.example.image_multi_recognition.viewmodel.basic

import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.db.ImageLabel
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.util.ControlledRunner
import com.example.image_multi_recognition.util.ExifHelper
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.util.toPagingSource
import com.example.image_multi_recognition.viewmodel.LabelUiModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.objects.ObjectDetector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.internal.synchronized

abstract class LabelingSupportViewModel(
    private val repository: ImageRepository,
    private val objectDetector: ObjectDetector,
    private val imageLabeler: ImageLabeler,
) : ViewModel() {
    // imageId: object count
    val imageObjectsMap: MutableMap<Long, Int> = mutableMapOf()
    val labelImagesMap: MutableMap<String, MutableSet<ImageInfo>> = mutableMapOf()
    val labelImagesFlow = Pager(
        initialKey = 0,
        config = PagingConfig(
            pageSize = 50,
            enablePlaceholders = true
        ),
        pagingSourceFactory = {
            labelImagesMap.toPagingSource(
                itemsPerPage = 50,
                keyMapper = { LabelUiModel.Label(it) },
                valueMapper = { key, value ->
                    LabelUiModel.Item(value, key)
                }
            )
        }
    ).flow
    val controlledRunner = ControlledRunner<Unit>()

    private val rectMap = mutableMapOf<RectKey, Rect?>()
    private val _labelingStateFlow = MutableStateFlow(LabelingState(0, false))
    val labelingStateFlow: StateFlow<LabelingState>
        get() = _labelingStateFlow

    // imageId: rect
    private val detectedObjectFlow: MutableSharedFlow<DetectedImage> = MutableSharedFlow()

    private val _scanPaused = MutableStateFlow(false)
    val scanPaused: StateFlow<Boolean>
        get() = _scanPaused
    var scanCancelled: Boolean = false

    private val _labelAdding: MutableStateFlow<Boolean?> = MutableStateFlow(null)
    val labelAddingStateFlow: StateFlow<Boolean?>
        get() = _labelAdding

    init {
        viewModelScope.launch {
            detectedObjectFlow.collect { detectedImage ->
                if (scanCancelled) {
                    return@collect
                } else {
                    while (scanPaused.value) {
                        delay(1000)
                    }
                }
                withContext(Dispatchers.IO) {
                    if (detectedImage.rect == null) {
                        detectedImage.inputImage
                    } else {
                        detectedImage.inputImage.bitmapInternal?.let { bitmap ->
                            InputImage.fromBitmap(
                                ExifHelper.cropBitmap(bitmap, detectedImage.rect),
                                detectedImage.imageInfo.rotationDegree
                            )
                        }
                    }?.let { inputImage ->
                        imageLabeler.process(inputImage)
                            .addOnSuccessListener { labelList ->
                                if (scanCancelled) {
                                    return@addOnSuccessListener
                                }
                                // runs in UI Main thread
                                if (labelList.size > 1) {
                                    labelList.maxBy { it.confidence }.let { label ->
                                        if (label.confidence > DefaultConfiguration.ACCEPTED_CONFIDENCE) {
                                            if (labelImagesMap[label.text] == null) {
                                                labelImagesMap[label.text] = mutableSetOf()
                                            }
                                            // deduplication
                                            if (detectedImage.imageInfo !in labelImagesMap[label.text]!!) {
                                                labelImagesMap[label.text]!!.add(detectedImage.imageInfo)
                                                rectMap[RectKey(detectedImage.imageInfo.id, label.text)] =
                                                    detectedImage.rect
                                            }
                                            Log.d(
                                                getCallSiteInfo(),
                                                "${detectedImage.imageInfo} is recognized as ${label.text}"
                                            )
                                        }
                                    }
                                }
                                labelingStateChange(detectedImage.imageInfo.id)
                            }.addOnFailureListener { e ->
                                Log.e(getCallSiteInfo(), e.stackTraceToString())
                                labelingStateChange(detectedImage.imageInfo.id)
                            }.addOnCanceledListener {
                                Log.w(getCallSiteInfo(), "Labeling task is cancelled")
                                labelingStateChange(detectedImage.imageInfo.id)
                            }
                    }
                }
            }
        }
    }

    open fun scanImages(imageInfoList: List<ImageInfo>) {
        resetLabeling()
        _scanPaused.value = false
        scanCancelled = false
        imageObjectsMap.putAll(imageInfoList.map { it.id to -1 })
        viewModelScope.launch {
            imageInfoList.forEach { imageInfo ->
                withContext(Dispatchers.IO) {
                    if (scanCancelled) {
                        return@withContext
                    } else {
                        while (scanPaused.value) {
                            delay(1000)
                            Log.d(getCallSiteInfo(), "paused here!")
                        }
                    }
                    val imageHandled = withContext(Dispatchers.IO) {
                        repository.getInputImage(imageInfo.fullImageFile)
                    }
                    if (imageHandled == null) {
                        imageObjectsMapRemoval(imageInfo.id)
                    } else {
                        objectDetector.process(imageHandled)
                            .addOnSuccessListener { detectedObjList ->
                                if (scanCancelled) {
                                    return@addOnSuccessListener
                                }
                                imageObjectsMap[imageInfo.id] = detectedObjList.size + 1
                                viewModelScope.launch {
                                    detectedObjList.forEach { detectedObject ->
                                        detectedObjectFlow.emit(
                                            DetectedImage(
                                                imageInfo,
                                                imageHandled,
                                                detectedObject.boundingBox
                                            )
                                        )
                                        Log.d(
                                            getCallSiteInfo(),
                                            "Request is send for $imageInfo with ${detectedObject.boundingBox}"
                                        )
                                    }
                                    Log.d(getCallSiteInfo(), "Request is send for $imageInfo")
                                    detectedObjectFlow.emit(DetectedImage(imageInfo, imageHandled, null))
                                }
                            }.addOnFailureListener { e ->
                                Log.e(getCallSiteInfo(), e.stackTraceToString())
                                imageObjectsMapRemoval(imageInfo.id)
                            }.addOnCanceledListener {
                                Log.w(getCallSiteInfo(), "Object detecting task is cancelled")
                                imageObjectsMapRemoval(imageInfo.id)
                            }
                    }
                }
            }
        }
    }

    fun addSelectedImageLabel(labelImageMap: Map<String, List<Long>>) {
        if(labelImageMap.isNotEmpty()) {
            viewModelScope.launch {
                controlledRunner.joinPreviousOrRun {
                    _labelAdding.value = true
                    repository.insertImageLabels(
                        labelImageMap.flatMap { labelImages ->
                            labelImages.value.map { imageId ->
                                ImageLabel(imageId, labelImages.key, rectMap[RectKey(imageId, labelImages.key)])
                            }
                        }
                    )
                    _labelAdding.value = false
                }
            }
        }
    }

    fun reverseScanPaused() {
        _scanPaused.value = !_scanPaused.value
    }

    fun resumeScanPaused() {
        _scanPaused.value = false
    }

    private fun labelingStateChange(imageId: Long) {
        imageObjectsMap[imageId] = imageObjectsMap[imageId]!! - 1
        if (imageObjectsMap[imageId] == 0) {
            val newLabelingState =
                _labelingStateFlow.value.copy(labeledImageCount = _labelingStateFlow.value.labeledImageCount + 1)
            if (newLabelingState.labeledImageCount == imageObjectsMap.size) {
                newLabelingState.labelingDone = true
            }
            _labelingStateFlow.value = newLabelingState
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private fun imageObjectsMapRemoval(imageId: Long) {
        // imageObjectsMap.remove(imageId)
        synchronized(this) {
            val prevLabeledImageCount = _labelingStateFlow.value.labeledImageCount
            if (prevLabeledImageCount + 1 == imageObjectsMap.size) {
                _labelingStateFlow.value =
                    _labelingStateFlow.value.copy(labeledImageCount = prevLabeledImageCount + 1, labelingDone = true)
            } else {
                _labelingStateFlow.value = _labelingStateFlow.value.copy(labeledImageCount = prevLabeledImageCount + 1)
            }
        }
    }

    fun resetLabelAdding(){
        _labelAdding.value = null
    }

    private fun resetLabeling() {
        imageObjectsMap.clear()
        labelImagesMap.clear()
        rectMap.clear()
        _labelingStateFlow.value.labeledImageCount = 0
        _labelingStateFlow.value.labelingDone = false
    }
}

data class DetectedImage(
    val imageInfo: ImageInfo,
    val inputImage: InputImage,
    val rect: Rect? // if Rect is null, it means label the whole image instead of part of it
)

data class RectKey(
    val imageId: Long,
    val label: String
)

data class LabelingState(
    var labeledImageCount: Int,
    var labelingDone: Boolean
)