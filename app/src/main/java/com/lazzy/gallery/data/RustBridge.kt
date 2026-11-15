package com.lazzy.gallery.data

object RustBridge {
    private var isLoaded: Boolean = false

    init {
        try {
            System.loadLibrary("gallery_rust_core")
            isLoaded = true
        } catch (t: Throwable) {
            isLoaded = false
        }
    }

    fun isAvailable(): Boolean = isLoaded

    fun nativeDecodeThumbnail(fd: Int, targetWidth: Int, targetHeight: Int): ByteArray? {
        if (!isLoaded) return null
        return try {
            decodeThumbnailNative(fd, targetWidth, targetHeight)
        } catch (t: Throwable) {
            null
        }
    }

    fun nativePreprocessOcr(fd: Int): ByteArray? {
        if (!isLoaded) return null
        return try {
            preprocessOcrNative(fd)
        } catch (t: Throwable) {
            null
        }
    }

    fun nativeExtractExif(fd: Int): String? {
        if (!isLoaded) return null
        return try {
            extractExifNative(fd)
        } catch (t: Throwable) {
            null
        }
    }

    fun nativeComputePHash(fd: Int): Long {
        if (!isLoaded) return 0L
        return try {
            computePHashNative(fd)
        } catch (t: Throwable) {
            0L
        }
    }

    private external fun decodeThumbnailNative(fd: Int, targetWidth: Int, targetHeight: Int): ByteArray?
    private external fun preprocessOcrNative(fd: Int): ByteArray?
    private external fun extractExifNative(fd: Int): String?
    private external fun computePHashNative(fd: Int): Long
}
