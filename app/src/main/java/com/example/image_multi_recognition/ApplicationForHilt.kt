package com.example.image_multi_recognition

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ApplicationForHilt : Application(), ImageLoaderFactory {
    // Coil ImageLoader
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
//            .diskCache {  // we do not need diskCache here, because we do not load images through network in this app
//                DiskCache.Builder()
//                    .directory(this.cacheDir.resolve("image_cache"))
//                    .maxSizePercent(0.02)
//                    .build()
//            }
            .build()
    }
}