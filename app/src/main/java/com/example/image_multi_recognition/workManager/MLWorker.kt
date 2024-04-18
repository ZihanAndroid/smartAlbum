package com.example.image_multi_recognition.workManager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.graphics.Rect
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.image_multi_recognition.AppData
import com.example.image_multi_recognition.DefaultConfiguration
import com.example.image_multi_recognition.MainActivity
import com.example.image_multi_recognition.R
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.repository.UserSettingRepository
import com.example.image_multi_recognition.util.ExifHelper
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.util.getCallSiteInfoFunc
import com.example.image_multi_recognition.viewmodel.basic.DetectedImage
import com.example.image_multi_recognition.viewmodel.basic.ImageLabelIdentity
import com.example.image_multi_recognition.viewmodel.basic.LabelingState
import com.example.image_multi_recognition.viewmodel.basic.RectKey
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.objects.ObjectDetector
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.IOException
import java.time.Instant

// https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running#long-running-kotlin
// https://developer.android.com/training/dependency-injection/hilt-jetpack#workmanager
// you can add dependency to other repositories, objectDetector, etc. easily by Hilt
@HiltWorker
class MLWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val repository: ImageRepository,
    private val settingRepository: UserSettingRepository,
    private val objectDetector: ObjectDetector,
    private val imageLabeler: ImageLabeler,
    private val moshi: Moshi,
) : CoroutineWorker(context, parameters) {
    // imageId: object count
    private val imageObjectsMap: MutableMap<Long, Int> = mutableMapOf()
    private val labelImagesMap: MutableMap<String, MutableSet<ImageInfo>> = mutableMapOf()
    private val imageLabelConfidence: MutableMap<ImageLabelIdentity, Float> = mutableMapOf()

    private val rectMap = mutableMapOf<RectKey, Rect?>()
    private val _labelingStateFlow = MutableStateFlow(LabelingState(0, false))

    // imageId: rect
    private val detectedObjectFlow: MutableSharedFlow<DetectedImage> = MutableSharedFlow()

    // updated when scanImages is called
    private var excludedLabels: Set<String> = emptySet()
    private var labelingConfidence: Float = 0.7f
    private var workCanceled = false

    override suspend fun doWork(): Result {
        Log.d(getCallSiteInfo(), "doWork is called!")
        // album: 0 to label unlabeled images from all albums, 1 to label images from certain album
        val album = inputData.getLong("album", -1).apply {
            Log.d(getCallSiteInfo(), "album: $this")
        }
        if (album == -1L) return Result.failure()
        val imageInfoList = if (album == 0L) {
            repository.getAllUnlabeledImagesList()
        } else {
            repository.getUnlabeledImagesListByAlbum(album)
        }
        val notificationBuilder = startForegroundService(album)
        resetLabeling()
        coroutineScope {
            val outerCoroutineScope = this
            val collectJob = launch {
                try {
                    detectedObjectFlow.collect { detectedImage ->
                        launch {
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
                                imageLabeler.process(inputImage).addOnSuccessListener { originalLabelList ->
                                    val labelList = originalLabelList.filter { it.text !in excludedLabels }
                                    // runs in UI Main thread
                                    val labels = mutableListOf<ImageLabel>()
                                    if (labelList.isNotEmpty()) {
                                        Log.d(getCallSiteInfoFunc(), "labelList: ${labelList.joinToString()}")
                                        if (inputImage == detectedImage.inputImage) {
                                            // pick at most two labels for a whole image
                                            labelList.filter { it.confidence >= labelingConfidence }
                                                .sortedBy { it.confidence }.reversed().let { list ->
                                                    list.subList(0, if (list.size > 1) 2 else list.size)
                                                        .forEach { label ->
                                                            // deduplication
                                                            if (labelImagesMap[label.text] == null || detectedImage.imageInfo !in labelImagesMap[label.text]!!) {
                                                                labels.add(label)
                                                            }
                                                        }
                                                }
                                        } else {
                                            labelList.maxBy { it.confidence }.let { label ->
                                                val identity =
                                                    ImageLabelIdentity(detectedImage.imageInfo, label.text)
                                                if (label.confidence >= labelingConfidence) {
                                                    if (labelImagesMap[label.text] == null || detectedImage.imageInfo !in labelImagesMap[label.text]!! ||
                                                        // for the whole image, its confidence does not in imageLabelConfidence (represented by 0)
                                                        // so we always override a whole image label by a partial image label
                                                        label.confidence > (imageLabelConfidence[identity] ?: 0f)
                                                    ) {
                                                        // confidence is for partial image only.
                                                        // for a whole image, we only check duplication
                                                        imageLabelConfidence[identity] = label.confidence
                                                        labels.add(label)
                                                    }
                                                }
                                            }
                                        }
                                        labels.forEach { label ->
                                            if (labelImagesMap[label.text] == null) {
                                                labelImagesMap[label.text] = mutableSetOf()
                                            }
                                            labelImagesMap[label.text]!!.add(detectedImage.imageInfo)
                                            rectMap[RectKey(detectedImage.imageInfo.id, label.text)] =
                                                detectedImage.rect
                                            Log.d(
                                                getCallSiteInfo(),
                                                "${detectedImage.imageInfo} is recognized as ${label.text}"
                                            )
                                        }
                                    }
                                    labelingStateChange(detectedImage.imageInfo.id)
                                    if (_labelingStateFlow.value.labelingDone) cancel()
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
                } catch (e: CancellationException) {
                    // once the CoroutineWorkManager is canceled, you will receive a CancellationException here
                    if (!_labelingStateFlow.value.labelingDone) {
                        workCanceled = true
                        settingRepository.updateLabelingStatus(AppData.LabelingStatus.CANCELED)
                    }
                    throw e
                }
            }
            launch {
                // monitor whether the job has been done
                while (true) {
                    if (_labelingStateFlow.value.labelingDone) {
                        // make all the jobs inside coroutineScope finished programmatically, because flow.collect() never ends
                        collectJob.cancel()
                        break
                    }
                    delay(1000)
                }
            }
            imageObjectsMap.putAll(imageInfoList.map { it.id to -1 })
            // update the excludedLabels for any possible changes
            excludedLabels = settingRepository.excludedLabelsSetFlow.first()
            labelingConfidence = settingRepository.imageLabelingConfidenceFlow.first()
            launch {
                imageInfoList.forEach { imageInfo ->
                    withContext(Dispatchers.IO) {
                        val imageHandled = repository.getInputImage(imageInfo.fullImageFile)
                        if (imageHandled == null) {
                            imageObjectsMapRemoval()
                        } else {
                            objectDetector.process(imageHandled).addOnSuccessListener { detectedObjList ->
                                imageObjectsMap[imageInfo.id] = detectedObjList.size + 1
                                // note you should use outerCoroutineScope to launch the following task
                                // you should not use the coroutineScope from "withContext(Dispatchers.IO)" above
                                outerCoroutineScope.launch {
                                    detectedObjList.forEach { detectedObject ->
                                        detectedObjectFlow.emit(
                                            DetectedImage(imageInfo, imageHandled, detectedObject.boundingBox)
                                        )
                                        Log.d(
                                            getCallSiteInfo(),
                                            "Request is sent for $imageInfo with ${detectedObject.boundingBox}"
                                        )
                                    }
                                    Log.d(getCallSiteInfo(), "Request is send for $imageInfo")
                                    detectedObjectFlow.emit(
                                        DetectedImage(imageInfo, imageHandled, null)
                                    )
                                }
                            }.addOnFailureListener { e ->
                                Log.e(getCallSiteInfo(), e.stackTraceToString())
                                imageObjectsMapRemoval()
                            }.addOnCanceledListener {
                                Log.w(getCallSiteInfo(), "Object detecting task is cancelled")
                                imageObjectsMapRemoval()
                            }
                        }
                    }
                }
            }
            // track progress
            launch {
                do {
                    setProgress(
                        workDataOf(
                            PROGRESS_KEY to _labelingStateFlow.value.labeledImageCount,
                            TOTAL_KEY to imageObjectsMap.size,
                            FINISHED_KEY to _labelingStateFlow.value.labelingDone
                        )
                    )
                    if (!workCanceled) {
                        notificationBuilder.updateNotification(_labelingStateFlow.value.let {
                            "${applicationContext.getString(R.string.loading)}...    ${it.labeledImageCount}/${imageInfoList.size}"
                        })
                    }
                    // once the CoroutineWorker is canceled. all the coroutines are canceled automatically
                    delay(100)
                } while (!_labelingStateFlow.value.labelingDone)
            }
        }
        return if (_labelingStateFlow.value.labelingDone) {
            // We store the result in DataStore, because WorkManager works in the background
            // and the Activity that starts the WorkManager may be destroyed before the work is finished.
            // Store the result in file and DataStore makes sure that the next time the activity started again, it can still access the previous result
            settingRepository.updateLabelingStatus(AppData.LabelingStatus.FINISHED)
            val adapter: JsonAdapter<LabelingResult> = moshi.adapter(LabelingResult::class.java)
            adapter.toJson(LabelingResult(album, labelImagesMap)).let { jsonStr ->
                val fileName = "workerResult_${Instant.now()}"
                try {
                    // save file in app's scoped internal storage
                    File(applicationContext.filesDir, DefaultConfiguration.WORKER_RESULT_DIR).let { dir ->
                        if (!dir.exists()) dir.mkdirs()
                    }
                    File(File(applicationContext.filesDir, DefaultConfiguration.WORKER_RESULT_DIR), fileName).writeText(
                        jsonStr
                    )
                    Log.d(
                        getCallSiteInfo(),
                        "worker result file: [${File(applicationContext.filesDir, fileName)}] is created"
                    )
                    settingRepository.updateWorkerResultFileName(fileName)
                } catch (e: IOException) {
                    Log.e(getCallSiteInfoFunc(), e.stackTraceToString())
                }
            }
            // send final notification
            // https://stackoverflow.com/questions/60693832/workmanager-keep-notification-after-work-is-done
            notificationManager.notify(
                NOTIFICATION_ID + 1,
                notificationBuilder.setContentText(applicationContext.getString(R.string.labeling_finished))
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .build()
            )
            Result.success()
        } else {
            // we ignore the failed tasks
            Log.w(getCallSiteInfo(), "A work in WorkManager failed")
            settingRepository.updateLabelingStatus(AppData.LabelingStatus.NOT_STARTED)
            Result.failure()
        }
    }

    // return whether the task has finished
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
    private fun imageObjectsMapRemoval() {
        kotlinx.coroutines.internal.synchronized(this) {
            val prevLabeledImageCount = _labelingStateFlow.value.labeledImageCount
            if (prevLabeledImageCount + 1 == imageObjectsMap.size) {
                _labelingStateFlow.value =
                    _labelingStateFlow.value.copy(labeledImageCount = prevLabeledImageCount + 1, labelingDone = true)
            } else {
                _labelingStateFlow.value = _labelingStateFlow.value.copy(labeledImageCount = prevLabeledImageCount + 1)
            }
        }
    }

    private fun resetLabeling() {
        imageObjectsMap.clear()
        labelImagesMap.clear()
        rectMap.clear()
        _labelingStateFlow.value.labeledImageCount = 0
        _labelingStateFlow.value.labelingDone = false
    }

    // Notification
    // Channels are used to group notifications and allow users to filter out unwanted notifications
    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val NOTIFICATION_ID = 0xA1
    private fun createNotificationChannel() {
        val channelId = applicationContext.getString(R.string.notification_channel_id)
        val channelName = applicationContext.getString(R.string.notification_channel_name)
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_DEFAULT)

        notificationManager.createNotificationChannel(channel)
    }

    private fun getNotificationBuilder(pendingIntent: PendingIntent, progress: String): NotificationCompat.Builder {
        val channelId = applicationContext.getString(R.string.notification_channel_id)
        val title = applicationContext.getString(R.string.notification_title)
        val cancel = applicationContext.getString(R.string.cancel)
        // This PendingIntent can be used to cancel the worker
        val cancelIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)

        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(progress)
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.drawable.baseline_star_24)
            .setOngoing(true)
            .addAction(R.drawable.baseline_cancel_24, cancel, cancelIntent)
    }

    private fun getPendingIntentForDeepLink(deepLink: String): PendingIntent {
        val intent = Intent(
            Intent.ACTION_VIEW,
            deepLink.toUri(),
            applicationContext,
            MainActivity::class.java
        )
        return PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private suspend fun startForegroundService(album: Long): NotificationCompat.Builder {
        createNotificationChannel()
        // an Intent for a composable deep link, when notification is clicked, move to the navigation destination
        val pendingIntent = getPendingIntentForDeepLink(DefaultConfiguration.ML_DEEP_LINK)
        //     if (album == 0L) {
        //     getPendingIntentForDeepLink(DefaultConfiguration.ML_DEEP_LINK)
        // } else {
        //     getPendingIntentForDeepLink(DefaultConfiguration.ML_ALBUM_DEEP_LINK + "/${album}")
        // }
        val notificationBuilder = getNotificationBuilder(pendingIntent, "started")
        setForeground(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ForegroundInfo(NOTIFICATION_ID, notificationBuilder.build(), FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                ForegroundInfo(NOTIFICATION_ID, notificationBuilder.build())
            }
        )
        return notificationBuilder
    }

    private fun NotificationCompat.Builder.updateNotification(newText: String) {
        setContentText(newText).setSilent(true)
        notificationManager.notify(NOTIFICATION_ID, build())
    }

    companion object {
        const val PROGRESS_KEY = "PROGRESS_KEY"
        const val FINISHED_KEY = "FINISHED_KEY"
        const val TOTAL_KEY = "TOTAL_KEY"
    }
}

data class LabelingResult(
    @field:Json(name = "album") val album: Long,
    @field:Json(name = "labelImagesMap") val labelImagesMap: Map<String, Set<ImageInfo>>,
)