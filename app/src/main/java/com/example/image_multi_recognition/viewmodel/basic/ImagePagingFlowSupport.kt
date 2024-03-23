package com.example.image_multi_recognition.viewmodel.basic

import android.util.Log
import androidx.paging.PagingData
import androidx.paging.insertSeparators
import androidx.paging.map
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.util.ExifHelper
import com.example.image_multi_recognition.util.getCallSiteInfo
import com.example.image_multi_recognition.viewmodel.UiModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

interface ImagePagingFlowSupport {
    val imageIdOriginalIndexMap: MutableMap<Long, Int>

    fun getValidOriginalIndexAfterDeletion(imageId: Long, deletedImageIds: Set<Long>): Int {
        assert(imageId !in deletedImageIds)
        val imageIdIndex = imageIdOriginalIndexMap[imageId]!!
        var bias = 0
        deletedImageIds.forEach { deletedImageId ->
            if(imageIdOriginalIndexMap[deletedImageId]!! < imageIdIndex ) ++bias
        }
        return imageIdIndex-bias
    }

    fun Flow<PagingData<ImageInfo>>.convertImageInfoPagingFlow(): Flow<PagingData<UiModel>> {
        var count = 0
        val epochTime = LocalDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC"))

        return this.map { pagingData ->
            // there are two ways to get more pagingData
            // (1) fetch more ImageInfo from DB
            // (2) the PagingSource is invalid due to DB change (like changing "favorite" property of ImageInfo and update DB)
            pagingData.map { imageInfo ->
                // Log.d(getCallSiteInfo(), "original index value: $count")
                if (imageInfo.id !in imageIdOriginalIndexMap) {
                    // do not publish a new originalIndex for imageInfo that has been handled
                    UiModel.Item(imageInfo).apply {
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