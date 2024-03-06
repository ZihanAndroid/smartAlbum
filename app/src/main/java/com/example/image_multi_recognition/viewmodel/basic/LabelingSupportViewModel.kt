package com.example.image_multi_recognition.viewmodel.basic

import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.util.ExifHelper
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.util.toPagingSource
import com.example.image_multi_recognition.viewmodel.LabelUiModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.objects.ObjectDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

abstract class LabelingSupportViewModel (
    private val repository: ImageRepository,
    private val objectDetector: ObjectDetector,
    private val imageLabeler: ImageLabeler,
) : ViewModel() {
    // imageId: object count
    val imageObjectsMap: MutableMap<Long, Int> = mutableMapOf()
    private val labelImagesMap: ConcurrentHashMap<String, MutableList<ImageInfo>> = ConcurrentHashMap()
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
                valueMapper = { LabelUiModel.Item(it) }
            )
        }
    ).flow

    private val _labelingStateFlow = MutableStateFlow(LabelingState(0, false))
    val labelingStateFlow: StateFlow<LabelingState>
        get() = _labelingStateFlow

    // imageId: rect
    private val detectedObjectFlow: MutableSharedFlow<DetectedImage> = MutableSharedFlow()

    private val _scanPaused = MutableStateFlow(false)
    val scanPaused: StateFlow<Boolean>
        get() = _scanPaused
    var scanCancelled: Boolean = false

    init {
        viewModelScope.launch {
            detectedObjectFlow.collect { detectedImage ->
                if(scanCancelled){
                    return@collect
                }else{
                    while(scanPaused.value){
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
                                if(scanCancelled){
                                    return@addOnSuccessListener
                                }
                                // runs in UI Main thread
                                if (labelList.size > 1) {
                                    labelList.maxBy { it.confidence }.let { label ->
                                        if (label.confidence > DefaultConfiguration.ACCEPTED_CONFIDENCE) {
                                            if (labelImagesMap[label.text] == null) {
                                                labelImagesMap[label.text] = mutableListOf()
                                            }
                                            // deduplication
                                            if(detectedImage.imageInfo !in labelImagesMap[label.text]!!){
                                                labelImagesMap[label.text]!!.add(detectedImage.imageInfo)
                                            }
                                            Log.d(getCallSiteInfo(), "${detectedImage.imageInfo} is recognized as ${label.text}")
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

    fun scanImages(imageInfoList: List<ImageInfo>) {
        resetLabeling()
        _scanPaused.value = false
        scanCancelled = false
        imageObjectsMap.putAll(imageInfoList.map { it.id to -1 })
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                imageInfoList.forEach { imageInfo ->
                    if(scanCancelled){
                        return@withContext
                    }else{
                        while(scanPaused.value){
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
                                if(scanCancelled){
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
                                        Log.d(getCallSiteInfo(), "Request is send for $imageInfo with ${detectedObject.boundingBox}")
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

    fun reverseScanPaused(){
        _scanPaused.value = !_scanPaused.value
    }
    fun resumeScanPaused(){
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

    private fun imageObjectsMapRemoval(imageId: Long) {
        imageObjectsMap.remove(imageId)
        if (_labelingStateFlow.value.labeledImageCount == imageObjectsMap.size) {
            _labelingStateFlow.value = _labelingStateFlow.value.copy(labelingDone = true)
        }
    }

    private fun resetLabeling() {
        imageObjectsMap.clear()
        labelImagesMap.clear()
        _labelingStateFlow.value.labeledImageCount = 0
        _labelingStateFlow.value.labelingDone = false
    }
}

data class DetectedImage(
    val imageInfo: ImageInfo,
    val inputImage: InputImage,
    val rect: Rect? // if Rect is null, it means label the whole image instead of part of it
)

data class LabelingState(
    var labeledImageCount: Int,
    var labelingDone: Boolean
)