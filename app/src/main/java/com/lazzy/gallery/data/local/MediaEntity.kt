package com.lazzy.gallery.data.local

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lazzy.gallery.data.MediaModel

@Entity(tableName = "media_index")
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
    val ocrText: String? = null
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
