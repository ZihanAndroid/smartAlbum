package com.example.image_multi_recognition.domain

import androidx.paging.PagingData
import com.example.image_multi_recognition.db.AlbumWithLatestImage
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.repository.UserSettingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetImageChangeUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
    private val settingRepository: UserSettingRepository,
) {
    private var prevThumbnailQuality: Float? = null
    operator fun invoke(): Flow<ImageChange> = combine(
        imageRepository.mediaStoreChangeFlow,
        settingRepository.imageCacheEnabledFlow,
        settingRepository.thumbNailQualityFlow
    ) { mediaStoreChange, thumbnailEnabled, thumbnailQuality ->
        ImageChange(
            mediaStoreChange = mediaStoreChange,
            thumbnailEnabled = thumbnailEnabled,
            thumbnailQuality = thumbnailQuality,
            isThumbnailQualityChange = if (prevThumbnailQuality == null) false else prevThumbnailQuality != thumbnailQuality,
        ).apply { prevThumbnailQuality = thumbnailQuality }
    }
}

data class ImageChange(
    val mediaStoreChange: Boolean,
    val thumbnailEnabled: Boolean,
    val thumbnailQuality: Float,
    val isThumbnailQualityChange: Boolean,
)