package com.lazzy.gallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.Dimension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import android.util.Size as AndroidSize
import coil.size.Size as CoilSize

class MediaStoreThumbnailFetcher(
    private val context: Context,
    private val uri: Uri
) : Fetcher {

    companion object {
        // Persist thumbnails on a low-priority worker.
        private val diskSaverDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread {
                Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)
                runnable.run()
            }.apply {
                name = "thumb-disk-saver"
                isDaemon = true
            }
        }.asCoroutineDispatcher()

        private val diskSaverScope = CoroutineScope(diskSaverDispatcher)
    }

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val mediaId = try {
            ContentUris.parseId(uri)
        } catch (_: Exception) {
            uri.lastPathSegment?.toLongOrNull() ?: uri.hashCode().toLong()
        }

        val thumbDir = File(context.cacheDir, "thumbnails")
        if (!thumbDir.exists()) {
            thumbDir.mkdirs()
        }
        val thumbFile = File(thumbDir, "$mediaId.jpg")

        // Read the local thumbnail cache first.
        if (thumbFile.exists() && thumbFile.length() > 0) {
            try {
                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inMutable = false
                }
                var bitmap = BitmapFactory.decodeFile(thumbFile.absolutePath, opts)
                if (bitmap == null) {
                    bitmap = BitmapFactory.decodeFile(thumbFile.absolutePath)
                }

                if (bitmap != null) {
                    val drawable = BitmapDrawable(context.resources, bitmap)
                    return@withContext DrawableResult(
                        drawable = drawable,
                        isSampled = true,
                        dataSource = DataSource.DISK
                    )
                } else {
                    thumbFile.delete()
                }
            } catch (_: Exception) {
                thumbFile.delete()
            }
        }

        // 1. Try Android's system pre-rendered thumbnail provider first (like Fossify)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val rawBitmap = context.contentResolver.loadThumbnail(uri, AndroidSize(300, 300), null)
                if (rawBitmap != null) {
                    val bmpToCompress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && rawBitmap.config == Bitmap.Config.HARDWARE) {
                        rawBitmap.copy(Bitmap.Config.RGB_565, false) ?: rawBitmap
                    } else rawBitmap

                    diskSaverScope.launch {
                        try {
                            val tempFile = File(thumbDir, "$mediaId.tmp")
                            FileOutputStream(tempFile).use { out ->
                                bmpToCompress.compress(Bitmap.CompressFormat.JPEG, 80, out)
                            }
                            tempFile.renameTo(thumbFile)
                        } catch (_: Throwable) { }
                    }

                    val drawable = BitmapDrawable(context.resources, rawBitmap)
                    return@withContext DrawableResult(
                        drawable = drawable,
                        isSampled = true,
                        dataSource = DataSource.DISK
                    )
                }
            } catch (_: Throwable) { }
        }

        // 2. Fallback to native Rust decoding if system thumbnail misses
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                val jpegBytes = pfd.use { descriptor ->
                    RustBridge.nativeDecodeThumbnail(descriptor.fd, 300, 300)
                }
                if (jpegBytes != null && jpegBytes.isNotEmpty()) {
                    val opts = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                        inMutable = false
                    }
                    var bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, opts)
                    if (bitmap == null) {
                        bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                    }
                    if (bitmap != null) {
                        diskSaverScope.launch {
                            try {
                                val tempFile = File(thumbDir, "$mediaId.tmp")
                                FileOutputStream(tempFile).use { out ->
                                    out.write(jpegBytes)
                                }
                                tempFile.renameTo(thumbFile)
                            } catch (_: Throwable) { }
                        }

                        val drawable = BitmapDrawable(context.resources, bitmap)
                        return@withContext DrawableResult(
                            drawable = drawable,
                            isSampled = true,
                            dataSource = DataSource.DISK
                        )
                    }
                }
            }
        } catch (_: Throwable) { }
        return@withContext null
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                isMediaStoreUri(data) &&
                isThumbnailSize(options.size)
            ) {
                return MediaStoreThumbnailFetcher(context, data)
            }
            return null
        }

        private fun isMediaStoreUri(uri: Uri): Boolean {
            return uri.scheme == ContentResolver.SCHEME_CONTENT &&
                    (uri.authority == MediaStore.AUTHORITY || uri.authority?.contains("media", ignoreCase = true) == true)
        }

        private fun isThumbnailSize(size: CoilSize): Boolean {
            if (size == CoilSize.ORIGINAL) return false
            val width = size.width as? Dimension.Pixels ?: return false
            val height = size.height as? Dimension.Pixels ?: return false
            return width.px <= 512 && height.px <= 512
        }
    }
}
