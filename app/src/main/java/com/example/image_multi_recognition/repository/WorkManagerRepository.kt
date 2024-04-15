package com.example.image_multi_recognition.repository

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.image_multi_recognition.workManager.MLWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class WorkManagerRepository @Inject constructor(
    @ApplicationContext val context: Context,
) {
    private val workManager = WorkManager.getInstance(context)

    // album: 0 for all unlabeled images, >0 for unlabeled images in certain album
    fun CoroutineScope.sendImageLabelingRequest(
        album: Long,
        onProgressChange: (Int, Int, Boolean) -> Unit,
        onWorkCanceled: () -> Unit,
        onWorkFinished: () -> Unit,
    ) {
        val mlWorkerBuilder = OneTimeWorkRequestBuilder<MLWorker>()
        Data.Builder().putLong("album", album).build().let { inputData ->
            mlWorkerBuilder.setInputData(inputData)
        }
        mlWorkerBuilder.build().let { workRequest ->
            launch {
                workManager.getWorkInfoByIdFlow(workRequest.id).collect { workInfoNullable: WorkInfo? ->
                    workInfoNullable?.let { workInfo ->
                        val labeledCount = workInfo.progress.getInt(MLWorker.PROGRESS_KEY, 0)
                        val totalCount = workInfo.progress.getInt(MLWorker.TOTAL_KEY, -1)
                        val labelingFinished = workInfo.progress.getBoolean(MLWorker.FINISHED_KEY, false)
                        if (totalCount != -1) onProgressChange(labeledCount, totalCount, labelingFinished)
                        if(workInfo.state == WorkInfo.State.FAILED || workInfo.state == WorkInfo.State.CANCELLED){
                            onWorkCanceled()
                        }else if(workInfo.state == WorkInfo.State.SUCCEEDED){
                            onWorkFinished()
                        }
                    }
                }
            }
            workManager.enqueue(workRequest)
        }
    }
}