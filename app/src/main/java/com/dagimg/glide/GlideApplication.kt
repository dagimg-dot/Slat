package com.dagimg.glide

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.dagimg.glide.di.AppContainer
import com.dagimg.glide.di.DefaultAppContainer

class GlideApplication : Application(), ImageLoaderFactory {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        appContainer = DefaultAppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .allowHardware(true)
            .build()
    }

    companion object {
        lateinit var instance: GlideApplication
            private set

        val container: AppContainer
            get() = instance.appContainer
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as GlideApplication).appContainer
