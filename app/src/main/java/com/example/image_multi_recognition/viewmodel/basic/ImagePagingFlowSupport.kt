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
    fun Flow<PagingData<ImageInfo>>.convertImageInfoPagingFlow(): Flow<PagingData<UiModel>> {
        var count = 0
        val epochTime = LocalDateTime.ofInstant(Instant.EPOCH, ZoneId.of("UTC"))

        return this.map { pagingData ->
            pagingData.map { UiModel.Item(it, ++count) }
        }.map { pagingData ->
            pagingData.insertSeparators { before, after ->
                if (after == null) null
                else {
                    //Timestamp(before.imageInfo.timestamp)
                    val timeBefore = before?.imageInfo?.timestamp?.let { ExifHelper.timestampToLocalDataTime(it) }
                    val timeAfter = ExifHelper.timestampToLocalDataTime(after.imageInfo.timestamp)
                    // For the first scan, before is null, after is not null; for the last scan, after is null, before is not null
                    if (timeBefore == null || timeBefore.year != timeAfter.year || timeBefore.month != timeAfter.month) {
                        if(timeAfter != epochTime) {    // exclude UTC time: 1970/1/1 ...
                            UiModel.ItemHeaderYearMonth(
                                year = timeAfter.year,
                                month = timeAfter.month,
                                dayOfMonth = timeAfter.dayOfMonth,
                                dayOfWeek = timeAfter.dayOfWeek
                            ).apply { Log.d(getCallSiteInfo(), "UiModel.ItemHeaderYearMonth is created: $this") }
                        }else null
                    } else if (timeBefore.dayOfMonth != timeAfter.dayOfMonth) {
                        if(timeAfter != epochTime) {
                            UiModel.ItemHeaderDay(
                                year = timeAfter.year,
                                month = timeAfter.month,
                                dayOfMonth = timeAfter.dayOfMonth,
                                dayOfWeek = timeAfter.dayOfWeek
                            ).apply { Log.d(getCallSiteInfo(), "UiModel.ItemHeaderDay is created: $this") }
                        }else null
                    } else null
                }
            }
        }
    }
}