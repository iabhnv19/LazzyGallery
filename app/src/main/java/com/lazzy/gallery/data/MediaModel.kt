package com.lazzy.gallery.data

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class MediaModel(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long, // Unix timestamp in seconds
    val dateModified: Long = 0L, // Unix timestamp in seconds
    val albumName: String,
    val isVideo: Boolean = false,
    val duration: Long = 0L, // Milliseconds
    val size: Long = 0L, // Bytes
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "",
    val orientation: Int = 0,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val ocrText: String? = null
)

@Immutable
data class Album(
    val name: String,
    val coverUri: Uri,
    val mediaCount: Int,
    val isPinned: Boolean = false,
    val isHidden: Boolean = false
)

@Immutable
data class AlbumInfo(
    val name: String,
    val mediaCount: Int,
    val photoCount: Int,
    val videoCount: Int,
    val totalSize: Long,
    val path: String,
    val latestDate: Long
)

enum class SortOrder(val displayName: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    SIZE_DESC("Size (Largest)"),
    SIZE_ASC("Size (Smallest)")
}

enum class FilterType(val displayName: String) {
    ALL("All"),
    IMAGES_ONLY("Images"),
    VIDEOS_ONLY("Videos"),
    FAVORITES("Favorites"),
    SCREENSHOTS("Screenshots")
}

@Immutable
data class ExifMetadata(
    val fileName: String,
    val path: String,
    val sizeFormatted: String,
    val resolution: String,
    val dateFormatted: String,
    val mimeType: String,
    val durationFormatted: String?,
    val cameraModel: String?,
    val aperture: String?,
    val iso: String?,
    val exposureTime: String?,
    val focalLength: String?
)
