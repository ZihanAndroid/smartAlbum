package com.example.image_multi_recognition.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.image_multi_recognition.db.ImageBound
import com.example.image_multi_recognition.repository.ImageRepository
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.objects.ObjectDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class ImageLabelingViewModel @Inject constructor(
    private val objectDetector: ObjectDetector,
    private val imageLabeler: ImageLabeler,
    //private val glideRequestBuilder: RequestBuilder<Bitmap>,
    private val repository: ImageRepository
) : ViewModel() {
    private val detectedObjectsFlow = MutableSharedFlow<List<ImageBound>>()

    private val _labelingDoneFlow = MutableStateFlow(true)
    val labelingDoneFlow: StateFlow<Boolean>
        get() = _labelingDoneFlow
    val generatedLabelsFlow = MutableSharedFlow<ObjectDetectedImageData>()

    val _showImageDetailFlag = MutableStateFlow(false)
    val showImageDetailFlag: StateFlow<Boolean>
        get() = _showImageDetailFlag
    lateinit var currentImageUri: String

    //-------------------------------------------------
//    init {
//        viewModelScope.launch {
//            detectedObjectsFlow.collect {
//                repository.registerUnLabeledImage(it)
//            }
//        }
//    }
//
//    fun detectImageFromUri(uri: Uri) {
//        viewModelScope.launch {
//            withContext(Dispatchers.IO) {
//                // https://stackoverflow.com/questions/48036945/does-glide-automatically-prevent-duplicate-calls
//                glideRequestBuilder.load(uri)
//                    .into(object : CustomTarget<Bitmap>() {
//                        override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
//                            objectDetector.process(InputImage.fromBitmap(resource, 0))
//                                .addOnSuccessListener { detectedObjects ->
//                                    Log.d("detectImageFromUri", detectedObjects.joinToString {
//                                        "${it.trackingId}: ${it.boundingBox}"
//                                    })
//                                    viewModelScope.launch {
//                                        detectedObjectsFlow.emit(detectedObjects.map { obj ->
//                                            ImageBound(
//                                                uri = uri.toString(),
//                                                rect = obj.boundingBox
//                                            )
//                                        })
//                                    }
//                                }.addOnFailureListener { exception ->
//                                    Log.e(
//                                        "ImageRecognitionViewModel.detectImageFromUri",
//                                        exception.stackTraceToString()
//                                    )
//                                }
//                        }
//
//                        override fun onLoadCleared(placeholder: Drawable?) {
//                        }
//                    })
//            }
//        }
//    }
//
//    private fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap{
//        return Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.right-rect.left, rect.bottom-rect.top)
//    }
//
//    // instead of returning a Map<Rect, String>, emit the result into generatedLabelsFlow
//    fun recognizeImage(imageInfo: ImageInfo, bitmap: Bitmap) {
//        _labelingDoneFlow.value = false
//        val deferred = viewModelScope.async {
//            val rectList: List<Rect> = repository.getBoundsFromUri(imageInfo.uri)
//            withContext(Dispatchers.Default){
//                val resMap = mutableMapOf<Rect, String>()
//                rectList.forEach { rect ->
//                    val clippedImage = cropBitmap(bitmap, rect)
//                    // imageLabeler.process is a computation intensive task, run it in Dispatchers.IO instead of Main thread
//                    imageLabeler.process(clippedImage, 0).addOnSuccessListener {labelList->
//                        // select the recognized label with max confidence
//                        resMap[rect] = labelList.maxBy { it.confidence }.text
//                    }.addOnFailureListener {
//                        Log.e(
//                            "ImageRecognitionViewModel.recognizeImage",
//                            it.stackTraceToString()
//                        )
//                    }
//                }
//                resMap
//            }
//        }
//        viewModelScope.launch {
//            deferred.await().let {
//                generatedLabelsFlow.emit(ObjectDetectedImageData(imageInfo, it))
//                _labelingDoneFlow.value = true
//            }
//        }
//    }
//
//    fun labelImage(labelData: UserLabeledImageData) {
//        viewModelScope.launch {
//            repository.registerLabeledImage(labelData.labels.map { ImageLabel(uri = labelData.imageInfo.uri.toString(), label = it) })
//        }
//    }
//
//    fun openImageDetailComposable(imageUri: String){
//        _showImageDetailFlag.value = true
//        currentImageUri = imageUri
//    }
}

