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

    fun nativeDecodeThumbnailRgba(fd: Int, targetWidth: Int, targetHeight: Int): ByteArray? {
        if (!isLoaded) return null
        return try {
            decodeThumbnailRgbaNative(fd, targetWidth, targetHeight)
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

    fun nativeComputePHashesBatch(fds: IntArray): LongArray? {
        if (!isLoaded || fds.isEmpty()) return null
        return try {
            computePHashesBatchNative(fds)
        } catch (t: Throwable) {
            null
        }
    }

    fun nativeGroupDuplicateHashes(hashes: LongArray, maxDistance: Int = 6): IntArray? {
        if (!isLoaded || hashes.size < 2) return null
        return try {
            groupDuplicateHashesNative(hashes, maxDistance)
        } catch (t: Throwable) {
            null
        }
    }

    private external fun decodeThumbnailNative(fd: Int, targetWidth: Int, targetHeight: Int): ByteArray?
    private external fun decodeThumbnailRgbaNative(fd: Int, targetWidth: Int, targetHeight: Int): ByteArray?
    private external fun preprocessOcrNative(fd: Int): ByteArray?
    private external fun extractExifNative(fd: Int): String?
    private external fun computePHashNative(fd: Int): Long
    private external fun computePHashesBatchNative(fds: IntArray): LongArray?
    private external fun groupDuplicateHashesNative(hashes: LongArray, maxDistance: Int): IntArray?
}
