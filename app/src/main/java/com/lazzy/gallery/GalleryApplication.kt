package com.lazzy.gallery

import android.app.Application
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

class GalleryApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // Preload media cache in background on application start for instant cold boot
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = com.lazzy.gallery.data.MediaRepository(this@GalleryApplication)
                repo.getCachedMediaSync()
            } catch (_: Exception) { }
        }
    }

    override fun newImageLoader(): ImageLoader {
        // Linear 2-thread sequential queue with low thread priority so thumbnail decoding
        // runs linearly 1-by-1 in the background without causing scroll lag or thread contention.
        val decoderDispatcher = Executors.newFixedThreadPool(2) { runnable ->
            Thread {
                Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)
                runnable.run()
            }.apply {
                name = "gallery-decoder"
                isDaemon = true
            }
        }.asCoroutineDispatcher()

        val fetcherDispatcher = Executors.newFixedThreadPool(2) { runnable ->
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
                    .maxSizePercent(0.45)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(300 * 1024 * 1024)
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
