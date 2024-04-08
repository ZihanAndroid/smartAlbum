package com.example.image_multi_recognition.viewmodel.basic

import android.util.Log
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.insertSeparators
import androidx.paging.map
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.util.ExifHelper
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.model.UiModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

interface ImagePagingFlowSupport {
    fun getValidOriginalIndex(imageId: Long): Int
    fun Flow<PagingData<ImageInfo>>.convertImageInfoPagingFlow(pagingSourceType: PagingSourceType): Flow<PagingData<UiModel>>
    enum class PagingSourceType {
        UNLABELED_IMAGE, LABEL_IMAGE, IMAGE
    }
}

// Not Singleton
class ImagePagingFlowSupportImpl @Inject constructor(
    private val repository: ImageRepository,
) : ImagePagingFlowSupport {
    private val imageIdOriginalIndexMap: MutableMap<Long, Int> = mutableMapOf()

    private var prevPagingSource: PagingSource<Int, ImageInfo>? = null
    private fun previousInvalid(pagingSourceType: ImagePagingFlowSupport.PagingSourceType): Boolean {
        return when (pagingSourceType) {
            ImagePagingFlowSupport.PagingSourceType.UNLABELED_IMAGE -> false
            ImagePagingFlowSupport.PagingSourceType.LABEL_IMAGE -> {
                // prevLabelImagePagingSource has not been initialized yet, try to initialize it first
                if(prevPagingSource == null) prevPagingSource = repository.prevLabelImagePagingSource
                prevPagingSource?.invalid?.apply {
                    // Log.d("res","paging flow invalid: $this")
                    if (this) {
                        prevPagingSource = repository.prevLabelImagePagingSource
                    }
                } ?: false
            }

            ImagePagingFlowSupport.PagingSourceType.IMAGE -> {
                if(prevPagingSource == null) prevPagingSource = repository.prevImagePagingSource
                prevPagingSource?.invalid?.apply {
                    if (this) {
                        prevPagingSource = repository.prevImagePagingSource
                    }
                } ?: false
            }
        }
    }

    override fun getValidOriginalIndex(imageId: Long): Int {
        return imageIdOriginalIndexMap[imageId]!!
    }

    override fun Flow<PagingData<ImageInfo>>.convertImageInfoPagingFlow(pagingSourceType: ImagePagingFlowSupport.PagingSourceType): Flow<PagingData<UiModel>> {
        var count = 0
        val epochTime = LocalDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC"))

        return this.map { pagingData ->
            // there are two ways to get more pagingData
            // (1) fetch more ImageInfo from DB
            // (2) the PagingSource is invalid due to DB change (like changing "favorite" property of ImageInfo and update DB)
            pagingData.map { imageInfo ->
                // first we check the whether the previouPaging source is valid,
                // if not (like the user may take new photos and causes changes in MediaStore, which is captured by our app)
                // we discard previous imageIdOriginalIndexMap
                if (previousInvalid(pagingSourceType)) {
                    imageIdOriginalIndexMap.clear()
                    count = 0
                }
                if (imageInfo.id !in imageIdOriginalIndexMap) {
                    // do not publish a new originalIndex for imageInfo that has been handled
                    UiModel.Item(imageInfo).apply {
                        // pagingData.insertSeparators() below at other items to paging.
                        // we need to get rid of these items to pass the correct originalIndex for navigation
                        imageIdOriginalIndexMap[this.imageInfo.id] = ++count
                    }
                } else {
                    UiModel.Item(imageInfo)
                }
            }
        }.map { pagingData ->
            pagingData.insertSeparators { before, after ->
                if (after == null) null
                else {
                    // Timestamp(before.imageInfo.timestamp)
                    val timeBefore = before?.imageInfo?.timestamp?.let { ExifHelper.timestampToLocalDataTime(it) }
                    val timeAfter = ExifHelper.timestampToLocalDataTime(after.imageInfo.timestamp)
                    // For the first scan, before is null, after is not null; for the last scan, after is null, before is not null
                    if (timeBefore == null || timeBefore.year != timeAfter.year || timeBefore.month != timeAfter.month) {
                        if (timeAfter != epochTime) {    // exclude UTC time: 1970/1/1 ...
                            UiModel.ItemHeaderYearMonth(
                                year = timeAfter.year,
                                month = timeAfter.month,
                                dayOfMonth = timeAfter.dayOfMonth,
                                dayOfWeek = timeAfter.dayOfWeek
                            ).apply { Log.d(getCallSiteInfo(), "UiModel.ItemHeaderYearMonth is created: $this") }
                        } else null
                    } else if (timeBefore.dayOfMonth != timeAfter.dayOfMonth) {
                        if (timeAfter != epochTime) {
                            UiModel.ItemHeaderDay(
                                year = timeAfter.year,
                                month = timeAfter.month,
                                dayOfMonth = timeAfter.dayOfMonth,
                                dayOfWeek = timeAfter.dayOfWeek
                            ).apply { Log.d(getCallSiteInfo(), "UiModel.ItemHeaderDay is created: $this") }
                        } else null
                    } else null
                }
            }
        }
    }
}