package com.lazzy.gallery.data

object RustBridge {
    init {
        try {
            System.loadLibrary("gallery_rust_core")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    /**
     * Decodes an image thumbnail natively in Rust using a file descriptor (fd).
     * Returns compressed JPEG bytes, or null if native decoding fails.
     */
    external fun nativeDecodeThumbnail(fd: Int, targetWidth: Int, targetHeight: Int): ByteArray?

    /**
     * Preprocesses an image for OCR natively in Rust.
     */
    external fun nativePreprocessOcr(fd: Int): ByteArray?

    /**
     * Extracts EXIF metadata natively in Rust and returns a JSON string.
     */
    external fun nativeExtractExif(fd: Int): String?
}
