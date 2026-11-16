package com.lazzy.gallery

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Build
import android.os.Process
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.lazzy.gallery.data.MediaStoreThumbnailFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class GalleryApplication : Application(), ImageLoaderFactory, ComponentCallbacks2 {

    override fun onCreate() {
        super.onCreate()
        registerComponentCallbacks(this)
        // Preload media cache and aggressively pre-cache recent thumbnails immediately on app start
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = com.lazzy.gallery.data.MediaRepository(this@GalleryApplication)
                repo.getCachedMediaSync()
                repo.aggressivePrecacheThumbnails(200)
            } catch (_: Exception) { }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            try {
                coil.Coil.imageLoader(this).memoryCache?.clear()
            } catch (_: Throwable) { }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            coil.Coil.imageLoader(this).memoryCache?.clear()
        } catch (_: Throwable) { }
    }

    override fun newImageLoader(): ImageLoader {
        // Optimized 3-thread linear queue with LOWEST priority to decode thumbnails
        // ultra-fast without ever competing for UI thread resources.
        val decoderDispatcher = Executors.newFixedThreadPool(3) { runnable ->
            Thread {
                Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)
                runnable.run()
            }.apply {
                name = "gallery-decoder"
                isDaemon = true
            }
        }.asCoroutineDispatcher()

        val fetcherDispatcher = Executors.newFixedThreadPool(3) { runnable ->
            Thread {
                Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)
                runnable.run()
            }.apply {
                name = "gallery-fetcher"
                isDaemon = true
            }
        }.asCoroutineDispatcher()

        return ImageLoader.Builder(this)
            .fetcherDispatcher(fetcherDispatcher)
            .decoderDispatcher(decoderDispatcher)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.40)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(600 * 1024 * 1024)
                    .build()
            }
            .components {
                add(MediaStoreThumbnailFetcher.Factory(this@GalleryApplication))
                add(VideoFrameDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .crossfade(false)
            .build()
    }
}
