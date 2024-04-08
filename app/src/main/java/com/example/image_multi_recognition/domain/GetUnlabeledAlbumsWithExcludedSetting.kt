package com.example.image_multi_recognition.domain

import android.util.Log
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.example.image_multi_recognition.db.AlbumWithLatestImage
import com.example.image_multi_recognition.db.ImageInfo
import com.example.image_multi_recognition.repository.ImageRepository
import com.example.image_multi_recognition.repository.UserSettingRepository
import com.example.image_multi_recognition.util.AlbumPathDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetUnlabeledAlbumsUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
    private val settingRepository: UserSettingRepository,
) {
    operator fun CoroutineScope.invoke(): Flow<PagingData<AlbumWithLatestImage>> = combine(
        // note that the flow returned by Pager.flow is a hot flow
        imageRepository.getUnlabeledAlbumPagerFlow().cachedIn(this),
        settingRepository.excludedAlbumPathsSetFlow
    ) { pagingDataFlow, excludedAlbumPaths ->
        Log.d(
            "",
            "change detected for excludedAlbumPaths: $excludedAlbumPaths "
        )
        pagingDataFlow.filter {
            AlbumPathDecoder.decode(it.album).absolutePath !in excludedAlbumPaths
        }
    }
}

@Singleton
class GetAllUnlabeledImagesUseCase @Inject constructor(
    private val imageRepository: ImageRepository,
    private val settingRepository: UserSettingRepository,
) {
    operator fun invoke(): Flow<List<ImageInfo>> = combine(
        imageRepository.getAllUnlabeledImages(),
        settingRepository.excludedAlbumPathsSetFlow
    ) { imagesList, excludedAlbumPaths ->
        imagesList.filter { AlbumPathDecoder.decode(it.album).absolutePath !in excludedAlbumPaths }
    }
}