package com.lazzy.gallery.data.local

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.lazzy.gallery.data.MediaModel

@Entity(
    tableName = "media_index",
    indices = [
        Index(value = ["dateAdded"]),
        Index(value = ["isTrashed"]),
        Index(value = ["isHidden"]),
        Index(value = ["albumName"]),
        Index(value = ["isTrashed", "isHidden", "dateAdded"]),
        Index(value = ["albumName", "isTrashed", "isHidden", "dateAdded"])
    ]
)
data class MediaEntity(
    @PrimaryKey val id: Long,
    val uriString: String,
    val displayName: String,
    val dateAdded: Long,
    val dateModified: Long,
    val albumName: String,
    val isVideo: Boolean,
    val duration: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val orientation: Int,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val isHidden: Boolean = false,
    val ocrText: String? = null,
    val pHash: Long = 0L
) {
    fun toMediaModel(): MediaModel {
        return MediaModel(
            id = id,
            uri = Uri.parse(uriString),
            displayName = displayName,
            dateAdded = dateAdded,
            dateModified = dateModified,
            albumName = albumName,
            isVideo = isVideo,
            duration = duration,
            size = size,
            width = width,
            height = height,
            mimeType = mimeType,
            orientation = orientation,
            isFavorite = isFavorite,
            isTrashed = isTrashed,
            ocrText = ocrText
        )
    }
}
