package com.lazzy.gallery.data

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.exifinterface.media.ExifInterface
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.filter
import androidx.paging.map
import androidx.print.PrintHelper
import com.lazzy.gallery.data.local.GalleryDatabase
import com.lazzy.gallery.data.local.MediaEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Keep the latest list available while screens are changing.
object GlobalMediaCache {
    var cachedMedia: List<MediaModel> = emptyList()
    var isInitialized: Boolean = false
}

class MediaRepository(context: Context) {

    private val context: Context = context.applicationContext

    companion object {
        @Volatile
        private var INSTANCE: MediaRepository? = null

        fun getInstance(context: Context): MediaRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        private val syncMutex = Mutex()
        @Volatile
        private var lastSyncTimeMs: Long = 0L
        private const val SYNC_COOLDOWN_MS = 5_000L

        // Columns used when querying images.
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.ORIENTATION
        )

        // Columns used when querying videos.
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION
        )
    }

    private val prefs = context.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE)
    private val database = GalleryDatabase.getInstance(context)
    private val mediaDao = database.mediaDao()

    fun getLibraryColumns(): Int {
        return prefs.getInt("library_columns", 3)
    }

    fun setLibraryColumns(columns: Int) {
        prefs.edit().putInt("library_columns", columns).apply()
    }

    fun getAlbumColumns(): Int {
        return prefs.getInt("album_columns", 2)
    }

    fun setAlbumColumns(columns: Int) {
        prefs.edit().putInt("album_columns", columns).apply()
    }

    fun isDarkMode(): Boolean = prefs.getBoolean("dark_mode", true)
    fun setDarkMode(enabled: Boolean) = prefs.edit().putBoolean("dark_mode", enabled).apply()

    fun isDynamicColor(): Boolean = prefs.getBoolean("dynamic_color", true)
    fun setDynamicColor(enabled: Boolean) = prefs.edit().putBoolean("dynamic_color", enabled).apply()

    fun getAccentColorIndex(): Int = prefs.getInt("accent_color_index", 0)
    fun setAccentColorIndex(index: Int) = prefs.edit().putInt("accent_color_index", index).apply()

    fun getAutoPlayVideo(): Boolean = prefs.getBoolean("auto_play_video", true)
    fun setAutoPlayVideo(enabled: Boolean) = prefs.edit().putBoolean("auto_play_video", enabled).apply()

    fun getLoopVideo(): Boolean = prefs.getBoolean("loop_video", false)
    fun setLoopVideo(enabled: Boolean) = prefs.edit().putBoolean("loop_video", enabled).apply()

    fun getAnimateGifsThumbnail(): Boolean = prefs.getBoolean("animate_gifs_thumbnail", true)
    fun setAnimateGifsThumbnail(enabled: Boolean) = prefs.edit().putBoolean("animate_gifs_thumbnail", enabled).apply()

    fun getDateTimeFormat(): String = prefs.getString("date_time_format", "MMM dd, yyyy") ?: "MMM dd, yyyy"
    fun setDateTimeFormat(format: String) = prefs.edit().putString("date_time_format", format).apply()

    fun getShowHiddenItems(): Boolean = prefs.getBoolean("show_hidden_items", false)
    fun setShowHiddenItems(enabled: Boolean) = prefs.edit().putBoolean("show_hidden_items", enabled).apply()

    fun getDefaultStartScreen(): String = prefs.getString("default_start_screen", "recent") ?: "recent"
    fun setDefaultStartScreen(route: String) = prefs.edit().putString("default_start_screen", route).apply()

    fun getRightButtonAction(): String = prefs.getString("right_button_action", "favorites") ?: "favorites"
    fun setRightButtonAction(action: String) = prefs.edit().putString("right_button_action", action).apply()

    fun getFavoriteIds(): Set<Long> {
        val set = prefs.getStringSet("favorite_ids", emptySet()) ?: emptySet()
        return set.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun getCachedMediaSync(): List<MediaModel> {
        if (GlobalMediaCache.isInitialized && GlobalMediaCache.cachedMedia.isNotEmpty()) {
            return GlobalMediaCache.cachedMedia
        }
        // Use the snapshot before falling back to Room.
        val snapshot = loadSnapshot()
        if (snapshot.isNotEmpty()) {
            GlobalMediaCache.cachedMedia = snapshot
            GlobalMediaCache.isInitialized = true
            return snapshot
        }

        // Fall back to Room (load recent batch first for instant startup).
        try {
            val entities = mediaDao.getRecentMediaBlocking(120)
            val favoriteIds = getFavoriteIds()
            val hiddenIds = getHiddenIds()
            if (entities.isNotEmpty()) {
                val result = entities.map { it.toMediaModel().copy(isFavorite = favoriteIds.contains(it.id)) }
                    .filter { !hiddenIds.contains(it.id) }
                GlobalMediaCache.cachedMedia = result
                GlobalMediaCache.isInitialized = true
                return result
            }
        } catch (_: Exception) { }

        return emptyList()
    }

    private fun saveSnapshot(mediaList: List<MediaModel>) {
        try {
            val file = context.cacheDir.resolve("media_snapshot.json")
            val jsonArray = org.json.JSONArray()
            mediaList.take(150).forEach { model ->
                val obj = org.json.JSONObject().apply {
                    put("id", model.id)
                    put("uri", model.uri.toString())
                    put("name", model.displayName)
                    put("date", model.dateAdded)
                    put("video", model.isVideo)
                    put("album", model.albumName)
                    put("size", model.size)
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString())
        } catch (_: Exception) { }
    }

    private fun loadSnapshot(): List<MediaModel> {
        try {
            val file = context.cacheDir.resolve("media_snapshot.json")
            if (!file.exists()) return emptyList()
            val jsonArray = org.json.JSONArray(file.readText())
            val list = mutableListOf<MediaModel>()
            val favoriteIds = getFavoriteIds()
            val hiddenIds = getHiddenIds()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.getLong("id")
                val uri = Uri.parse(obj.getString("uri"))
                val name = obj.getString("name")
                val date = obj.getLong("date")
                val isVideo = obj.getBoolean("video")
                val album = obj.getString("album")
                val size = obj.getLong("size")
                if (!hiddenIds.contains(id)) {
                    list.add(
                        MediaModel(
                            id = id,
                            uri = uri,
                            displayName = name,
                            dateAdded = date,
                            dateModified = date,
                            albumName = album,
                            isVideo = isVideo,
                            duration = 0L,
                            size = size,
                            width = 0,
                            height = 0,
                            mimeType = if (isVideo) "video/*" else "image/*",
                            orientation = 0,
                            isFavorite = favoriteIds.contains(id),
                            isTrashed = false
                        )
                    )
                }
            }
            return list
        } catch (_: Exception) {
            return emptyList()
        }
    }

    fun toggleFavorite(mediaId: Long): Boolean {
        val current = getFavoriteIds().toMutableSet()
        val isFav = if (current.contains(mediaId)) {
            current.remove(mediaId)
            false
        } else {
            current.add(mediaId)
            true
        }
        prefs.edit().putStringSet("favorite_ids", current.map { it.toString() }.toSet()).apply()
        return isFav
    }

    fun getHiddenIds(): Set<Long> {
        val set = prefs.getStringSet("hidden_ids", emptySet()) ?: emptySet()
        return set.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun toggleHideMedia(mediaId: Long): Boolean {
        val current = getHiddenIds().toMutableSet()
        val isHidden = if (current.contains(mediaId)) {
            current.remove(mediaId)
            false
        } else {
            current.add(mediaId)
            true
        }
        prefs.edit().putStringSet("hidden_ids", current.map { it.toString() }.toSet()).apply()
        return isHidden
    }

    fun getPinnedAlbums(): Set<String> {
        return prefs.getStringSet("pinned_albums", emptySet()) ?: emptySet()
    }

    fun togglePinAlbum(albumName: String): Boolean {
        val current = getPinnedAlbums().toMutableSet()
        val isPinned = if (current.contains(albumName)) {
            current.remove(albumName)
            false
        } else {
            current.add(albumName)
            true
        }
        prefs.edit().putStringSet("pinned_albums", current).apply()
        return isPinned
    }

    fun getHiddenAlbums(): Set<String> {
        return prefs.getStringSet("hidden_albums", emptySet()) ?: emptySet()
    }

    fun toggleHideAlbum(albumName: String): Boolean {
        val current = getHiddenAlbums().toMutableSet()
        val isHidden = if (current.contains(albumName)) {
            current.remove(albumName)
            false
        } else {
            current.add(albumName)
            true
        }
        prefs.edit().putStringSet("hidden_albums", current).apply()
        return isHidden
    }

    /** Warms the on-disk thumbnail cache in the background. */
    suspend fun aggressivePrecacheThumbnails(limit: Int = 300) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext
        val thumbDir = File(context.cacheDir, "thumbnails").apply { if (!exists()) mkdirs() }
        val allEntities = try {
            mediaDao.getAllMedia().take(limit)
        } catch (_: Exception) {
            emptyList()
        }

        for (entity in allEntities) {
            val thumbFile = File(thumbDir, "${entity.id}.jpg")
            if (!thumbFile.exists() || thumbFile.length() == 0L) {
                try {
                    val rawBitmap = context.contentResolver.loadThumbnail(
                        entity.toMediaModel().uri,
                        android.util.Size(300, 300),
                        null
                    )
                    val tempFile = File(thumbDir, "${entity.id}.tmp")
                    val softwareBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && rawBitmap.config == android.graphics.Bitmap.Config.HARDWARE) {
                        rawBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                    } else null
                    val bmp = softwareBitmap ?: rawBitmap
                    java.io.FileOutputStream(tempFile).use { out ->
                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 78, out)
                    }
                    softwareBitmap?.recycle()
                    tempFile.renameTo(thumbFile)
                } catch (_: Exception) { }
            }
        }
    }

    suspend fun fetchAlbumInfo(albumName: String): AlbumInfo = withContext(Dispatchers.IO) {
        val items = mediaDao.getMediaByAlbum(albumName).map { it.toMediaModel() }
        val totalCount = items.size
        val photoCount = items.count { !it.isVideo }
        val videoCount = items.count { it.isVideo }
        val totalBytes = items.sumOf { it.size }
        val latestDate = items.maxOfOrNull { it.dateAdded } ?: 0L

        var path = "Internal storage / $albumName"
        val firstUri = items.firstOrNull()?.uri
        if (firstUri != null) {
            try {
                val proj = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    arrayOf(MediaStore.MediaColumns.RELATIVE_PATH)
                } else {
                    arrayOf(MediaStore.MediaColumns.DATA)
                }
                context.contentResolver.query(firstUri, proj, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val p = cursor.getString(0)
                        if (!p.isNullOrBlank()) {
                            path = p
                        }
                    }
                }
            } catch (_: Exception) { }
        }

        AlbumInfo(
            name = albumName,
            mediaCount = totalCount,
            photoCount = photoCount,
            videoCount = videoCount,
            totalSize = totalBytes,
            path = path,
            latestDate = latestDate
        )
    }

    fun getTrashedIds(): Set<Long> {
        val set = prefs.getStringSet("local_trashed_ids", emptySet()) ?: emptySet()
        return set.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun setLocalTrashed(mediaIds: List<Long>, isTrashed: Boolean) {
        val current = getTrashedIds().toMutableSet()
        if (isTrashed) {
            current.addAll(mediaIds)
        } else {
            current.removeAll(mediaIds.toSet())
        }
        prefs.edit().putStringSet("local_trashed_ids", current.map { it.toString() }.toSet()).apply()

        val dao = mediaDao
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dao.updateTrashed(mediaIds, isTrashed)
            } catch (_: Exception) { }
        }
    }

    @OptIn(FlowPreview::class)
    fun observeMediaStoreChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }

        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
        } catch (_: Exception) { }

        awaitClose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (_: Exception) { }
        }
    }.debounce(500L)

    /** Syncs MediaStore changes into Room without clearing the existing table. */
    private suspend fun queryMediaStoreIdAndModified(): Map<Long, Long> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<Long, Long>()
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_MODIFIED)
        val uris = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        for (uri in uris) {
            try {
                val bundle = Bundle().apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                    }
                }
                val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.contentResolver.query(uri, projection, bundle, null)
                } else {
                    context.contentResolver.query(uri, projection, null, null, null)
                }
                cursor?.use { c ->
                    val idCol = c.getColumnIndex(MediaStore.MediaColumns._ID)
                    val modCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    if (idCol != -1 && modCol != -1) {
                        while (c.moveToNext()) {
                            map[c.getLong(idCol)] = c.getLong(modCol)
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        map
    }

    private suspend fun queryEntitiesForIds(targetIds: Set<Long>): List<MediaEntity> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaEntity>()
        val hiddenIds = getHiddenIds()
        val favoriteIds = getFavoriteIds()
        val localTrashedIds = getTrashedIds()
        targetIds.chunked(400).forEach { idChunk ->
            val idListStr = idChunk.joinToString(",")
            val selection = "${MediaStore.MediaColumns._ID} IN ($idListStr)"
            val list = mutableListOf<MediaModel>()
            queryMediaStoreInternal(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageProjection, false, false, favoriteIds, list, selection)
            queryMediaStoreInternal(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoProjection, false, true, favoriteIds, list, selection)
            list.forEach { media ->
                val trashed = media.isTrashed || localTrashedIds.contains(media.id)
                result.add(
                    MediaEntity(
                        id = media.id,
                        uriString = media.uri.toString(),
                        displayName = media.displayName,
                        dateAdded = media.dateAdded,
                        dateModified = media.dateModified,
                        albumName = media.albumName,
                        isVideo = media.isVideo,
                        duration = media.duration,
                        size = media.size,
                        width = media.width,
                        height = media.height,
                        mimeType = media.mimeType,
                        orientation = media.orientation,
                        isFavorite = media.isFavorite,
                        isTrashed = trashed,
                        isHidden = hiddenIds.contains(media.id)
                    )
                )
            }
        }
        result
    }

    suspend fun syncMediaStoreWithRoom(force: Boolean = false): Unit = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && lastSyncTimeMs != 0L && (now - lastSyncTimeMs) < SYNC_COOLDOWN_MS) {
                return@withContext
            }
            syncMediaStoreWithRoomInternal()
            lastSyncTimeMs = System.currentTimeMillis()
        }
    }

    private suspend fun syncMediaStoreWithRoomInternal() {
        val existingRoomMap = try {
            mediaDao.getExistingIdAndModified().associate { it.id to it.dateModified }
        } catch (_: Exception) {
            emptyMap()
        }

        val mediaStoreMap = queryMediaStoreIdAndModified()

        // Populate Room on the first run.
        if (existingRoomMap.isEmpty()) {
            val freshActive = queryFreshMediaStore(false)
            val freshTrashed = queryFreshMediaStore(true)
            val allFresh = freshActive + freshTrashed
            val hiddenIds = getHiddenIds()
            val entities = allFresh.map { media ->
                MediaEntity(
                    id = media.id,
                    uriString = media.uri.toString(),
                    displayName = media.displayName,
                    dateAdded = media.dateAdded,
                    dateModified = media.dateModified,
                    albumName = media.albumName,
                    isVideo = media.isVideo,
                    duration = media.duration,
                    size = media.size,
                    width = media.width,
                    height = media.height,
                    mimeType = media.mimeType,
                    orientation = media.orientation,
                    isFavorite = media.isFavorite,
                    isTrashed = media.isTrashed,
                    isHidden = hiddenIds.contains(media.id)
                )
            }
            if (entities.isNotEmpty()) {
                try {
                    mediaDao.insertAll(entities)
                } catch (_: Exception) { }
            }
            return
        }

        // Remove items deleted outside the app.
        val localTrashedIds = getTrashedIds()
        val deletedIds = existingRoomMap.keys - mediaStoreMap.keys - localTrashedIds
        if (deletedIds.isNotEmpty()) {
            deletedIds.chunked(400).forEach { chunk ->
                try {
                    mediaDao.deleteByIds(chunk)
                } catch (_: Exception) { }
            }
        }

        // Add new items and refresh changed items.
        val modifiedIds = mediaStoreMap.filter { (id, dateMod) ->
            val existingMod = existingRoomMap[id]
            existingMod != null && existingMod != dateMod
        }.keys

        val newIds = mediaStoreMap.keys - existingRoomMap.keys
        val newOrModifiedIds = modifiedIds + newIds

        if (newOrModifiedIds.isNotEmpty()) {
            val entities = queryEntitiesForIds(newOrModifiedIds)
            if (entities.isNotEmpty()) {
                try {
                    mediaDao.insertAll(entities)
                } catch (_: Exception) { }
            }
        }

        if (deletedIds.isNotEmpty() || newOrModifiedIds.isNotEmpty()) {
            val updatedEntities = try { mediaDao.getAllMedia() } catch (_: Exception) { emptyList() }
            val favoriteIds = getFavoriteIds()
            val hiddenIds = getHiddenIds()
            val updatedModels = updatedEntities.map { it.toMediaModel().copy(isFavorite = favoriteIds.contains(it.id)) }
                .filter { !hiddenIds.contains(it.id) }
            GlobalMediaCache.cachedMedia = updatedModels
            GlobalMediaCache.isInitialized = true
        }
    }

    suspend fun hasCachedMedia(): Boolean = withContext(Dispatchers.IO) {
        try {
            mediaDao.getCount() > 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getAllActiveIds(): List<Long> = withContext(Dispatchers.IO) {
        val hiddenIds = getHiddenIds()
        try {
            mediaDao.getAllActiveIds().filter { !hiddenIds.contains(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getUrisByIds(ids: List<Long>): List<Uri> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val result = mutableListOf<Uri>()
        ids.chunked(400).forEach { chunk ->
            try {
                mediaDao.getUriStringsByIds(chunk).forEach { result.add(Uri.parse(it)) }
            } catch (_: Exception) { }
        }
        result
    }

    suspend fun fetchAllMedia(includeTrashed: Boolean = false, includeHidden: Boolean = false): List<MediaModel> = withContext(Dispatchers.IO) {
        if (includeTrashed) {
            val trashedEntities = try { mediaDao.getTrashedMedia() } catch (_: Exception) { emptyList() }
            if (trashedEntities.isEmpty()) {
                val fresh = queryFreshMediaStore(includeTrashed = true)
                val localTrashedIds = getTrashedIds()
                return@withContext fresh.filter { it.isTrashed || localTrashedIds.contains(it.id) }
            }
            val favoriteIds = getFavoriteIds()
            return@withContext trashedEntities.map { it.toMediaModel().copy(isFavorite = favoriteIds.contains(it.id)) }
        }

        var cachedEntities = try {
            mediaDao.getAllMedia()
        } catch (_: Exception) {
            emptyList()
        }

        if (cachedEntities.isEmpty()) {
            syncMediaStoreWithRoom()
            cachedEntities = try { mediaDao.getAllMedia() } catch (_: Exception) { emptyList() }
        }

        val favoriteIds = getFavoriteIds()
        val hiddenIds = getHiddenIds()
        var cachedModels = cachedEntities.map { it.toMediaModel().copy(isFavorite = favoriteIds.contains(it.id)) }
        if (!includeHidden) {
            cachedModels = cachedModels.filter { !hiddenIds.contains(it.id) }
        }
        if (!includeHidden) {
            GlobalMediaCache.cachedMedia = cachedModels
            GlobalMediaCache.isInitialized = true
        }
        return@withContext cachedModels
    }

    fun observeAllMediaFlow(): Flow<List<MediaModel>> {
        val favoriteIds = getFavoriteIds()
        val hiddenIds = getHiddenIds()
        val hiddenAlbums = getHiddenAlbums()
        return mediaDao.getAllMediaFlow().map { entities ->
            entities.map { it.toMediaModel().copy(isFavorite = favoriteIds.contains(it.id)) }
                .filter { !hiddenIds.contains(it.id) && !hiddenAlbums.contains(it.albumName) }
        }
    }

    fun observeAlbumsFlow(): Flow<List<Album>> {
        return mediaDao.getAlbumSummariesFlow().map { rows ->
            val pinned = getPinnedAlbums()
            val hidden = getHiddenAlbums()
            rows.filter { !hidden.contains(it.albumName) }
                .map { row ->
                    Album(
                        name = row.albumName,
                        coverUri = Uri.parse(row.coverUriString),
                        mediaCount = row.mediaCount,
                        isPinned = pinned.contains(row.albumName)
                    )
                }
                .sortedWith(
                    compareByDescending<Album> { it.isPinned }
                        .thenByDescending { it.mediaCount }
                )
        }
    }

    fun getPagedMediaFlow(sortOrder: SortOrder = SortOrder.DATE_DESC): Flow<PagingData<MediaModel>> {
        val favoriteIds = getFavoriteIds()
        val hiddenIds = getHiddenIds()
        val hiddenAlbums = getHiddenAlbums()
        val orderClause = when (sortOrder) {
            SortOrder.DATE_DESC -> "dateAdded DESC"
            SortOrder.DATE_ASC -> "dateAdded ASC"
            SortOrder.NAME_ASC -> "displayName COLLATE NOCASE ASC"
            SortOrder.NAME_DESC -> "displayName COLLATE NOCASE DESC"
            SortOrder.SIZE_DESC -> "size DESC"
            SortOrder.SIZE_ASC -> "size ASC"
        }
        val sql = "SELECT * FROM media_index WHERE isTrashed = 0 AND isHidden = 0 ORDER BY $orderClause"
        return Pager(
            config = PagingConfig(
                pageSize = 40,
                prefetchDistance = 30,
                initialLoadSize = 60,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                mediaDao.pagingSourceRaw(androidx.sqlite.db.SimpleSQLiteQuery(sql))
            }
        ).flow.map { pagingData ->
            pagingData.map { entity ->
                entity.toMediaModel().copy(isFavorite = favoriteIds.contains(entity.id))
            }.filter { model ->
                !hiddenIds.contains(model.id) && !hiddenAlbums.contains(model.albumName)
            }
        }
    }

    private suspend fun queryFreshMediaStore(includeTrashed: Boolean = false): List<MediaModel> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaModel>()
        val favoriteIds = getFavoriteIds()
        val localTrashedIds = getTrashedIds()
        val hiddenIds = getHiddenIds()

        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.ORIENTATION
        )

        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DURATION
        )

        queryMediaStoreInternal(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection,
            includeTrashed,
            isVideo = false,
            favoriteIds = favoriteIds,
            mediaList = mediaList
        )

        queryMediaStoreInternal(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            includeTrashed,
            isVideo = true,
            favoriteIds = favoriteIds,
            mediaList = mediaList
        )

        val result = if (includeTrashed) {
            mediaList.filter { it.isTrashed || localTrashedIds.contains(it.id) }
        } else {
            mediaList.filter { !it.isTrashed && !localTrashedIds.contains(it.id) }
        }

        val finalResult = if (includeTrashed) {
            result
        } else {
            result.filter { !hiddenIds.contains(it.id) }
        }

        finalResult.sortedByDescending { it.dateAdded }
    }

    private fun Cursor.getSafeString(columnName: String, default: String = ""): String {
        val idx = getColumnIndex(columnName)
        return if (idx != -1 && !isNull(idx)) getString(idx) else default
    }

    private fun Cursor.getSafeLong(columnName: String, default: Long = 0L): Long {
        val idx = getColumnIndex(columnName)
        return if (idx != -1 && !isNull(idx)) getLong(idx) else default
    }

    private fun Cursor.getSafeInt(columnName: String, default: Int = 0): Int {
        val idx = getColumnIndex(columnName)
        return if (idx != -1 && !isNull(idx)) getInt(idx) else default
    }

    private fun queryMediaStoreInternal(
        uri: Uri,
        projection: Array<String>,
        includeTrashed: Boolean,
        isVideo: Boolean,
        favoriteIds: Set<Long>,
        mediaList: MutableList<MediaModel>,
        selectionOverride: String? = null
    ) {
        try {
            val cursor = if (selectionOverride != null) {
                context.contentResolver.query(uri, projection, selectionOverride, null, null)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val queryArgs = Bundle().apply {
                    if (includeTrashed) {
                        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                    } else {
                        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_EXCLUDE)
                    }
                    putStringArray(
                        ContentResolver.QUERY_ARG_SORT_COLUMNS,
                        arrayOf(MediaStore.MediaColumns.DATE_ADDED)
                    )
                    putInt(
                        ContentResolver.QUERY_ARG_SORT_DIRECTION,
                        ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
                    )
                }
                context.contentResolver.query(uri, projection, queryArgs, null)
            } else {
                val selection = if (includeTrashed) {
                    "${MediaStore.MediaColumns.IS_TRASHED} = 1"
                } else {
                    "${MediaStore.MediaColumns.IS_TRASHED} = 0"
                }
                val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
                context.contentResolver.query(uri, projection, selection, null, sortOrder)
            }

            cursor?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getSafeLong(MediaStore.MediaColumns._ID)
                    if (id <= 0) continue
                    val name = c.getSafeString(MediaStore.MediaColumns.DISPLAY_NAME, if (isVideo) "Video" else "Image")
                    val dateAdded = c.getSafeLong(MediaStore.MediaColumns.DATE_ADDED)
                    val dateModified = c.getSafeLong(MediaStore.MediaColumns.DATE_MODIFIED)
                    val album = c.getSafeString(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, "Internal")
                    val size = c.getSafeLong(MediaStore.MediaColumns.SIZE)
                    val width = c.getSafeInt(MediaStore.MediaColumns.WIDTH)
                    val height = c.getSafeInt(MediaStore.MediaColumns.HEIGHT)
                    val mime = c.getSafeString(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/*" else "image/*")
                    val extraVal = if (isVideo) c.getSafeInt(MediaStore.Video.Media.DURATION) else c.getSafeInt(MediaStore.Images.Media.ORIENTATION)
                    val contentUri = ContentUris.withAppendedId(uri, id)

                    mediaList.add(
                        MediaModel(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            dateAdded = dateAdded,
                            dateModified = dateModified,
                            albumName = album,
                            isVideo = isVideo,
                            duration = if (isVideo) extraVal.toLong() else 0L,
                            size = size,
                            width = width,
                            height = height,
                            mimeType = mime,
                            orientation = if (isVideo) 0 else extraVal,
                            isFavorite = favoriteIds.contains(id),
                            isTrashed = includeTrashed
                        )
                    )
                }
            }
        } catch (_: Exception) { }
    }

    suspend fun fetchAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val allMedia = fetchAllMedia()
        if (allMedia.isEmpty()) return@withContext emptyList()
        allMedia.groupBy { it.albumName }.mapNotNull { (name, media) ->
            val firstUri = media.firstOrNull()?.uri ?: return@mapNotNull null
            Album(
                name = name,
                coverUri = firstUri,
                mediaCount = media.size
            )
        }.sortedByDescending { it.mediaCount }
    }

    suspend fun fetchMediaByAlbum(albumName: String): List<MediaModel> = withContext(Dispatchers.IO) {
        fetchAllMedia().filter { it.albumName == albumName }
    }

    suspend fun fetchFavoriteMedia(): List<MediaModel> = withContext(Dispatchers.IO) {
        fetchAllMedia().filter { it.isFavorite }
    }

    private fun formatFocalLength(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            if (raw.contains('/')) {
                val parts = raw.split('/')
                val num = parts[0].toDouble()
                val den = parts[1].toDouble()
                if (den != 0.0) {
                    val valMm = num / den
                    if (valMm == valMm.toLong().toDouble()) "${valMm.toLong()} mm" else String.format(Locale.US, "%.1f mm", valMm)
                } else raw
            } else {
                val valMm = raw.toDouble()
                "${valMm.toInt()} mm"
            }
        } catch (_: Exception) {
            if (raw.endsWith("mm", ignoreCase = true)) raw else "${raw}mm"
        }
    }

    private fun formatExposureTime(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val valSec = raw.toDouble()
            if (valSec < 1.0 && valSec > 0.0) {
                val denom = (1.0 / valSec).toInt()
                "1/${denom}s"
            } else {
                String.format(Locale.US, "%.2fs", valSec)
            }
        } catch (_: Exception) {
            if (raw.endsWith("s", ignoreCase = true)) raw else "${raw}s"
        }
    }

    private fun formatAperture(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val valF = if (raw.contains('/')) {
                val parts = raw.split('/')
                parts[0].toDouble() / parts[1].toDouble()
            } else raw.toDouble()
            String.format(Locale.US, "f/%.2f", valF)
        } catch (_: Exception) {
            if (raw.startsWith("f/", ignoreCase = true)) raw else "f/$raw"
        }
    }

    fun getExifInfo(media: MediaModel): ExifMetadata {
        var cameraModel: String? = null
        var aperture: String? = null
        var iso: String? = null
        var exposureTime: String? = null
        var focalLength: String? = null

        try {
            context.contentResolver.openFileDescriptor(media.uri, "r")?.use { pfd ->
                val json = RustBridge.nativeExtractExif(pfd.fd)
                if (json != null) {
                    val obj = org.json.JSONObject(json)
                    if (obj.has("camera_model") && !obj.isNull("camera_model")) {
                        cameraModel = obj.getString("camera_model")
                    }
                }
            }
        } catch (_: Exception) { }

        if (cameraModel.isNullOrBlank()) {
            try {
                context.contentResolver.openInputStream(media.uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)
                    aperture = exif.getAttribute(ExifInterface.TAG_F_NUMBER) ?: exif.getAttribute(ExifInterface.TAG_APERTURE_VALUE)
                    iso = exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) ?: @Suppress("DEPRECATION") exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)
                    exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)
                    focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
                }
            } catch (_: Exception) { }
        }

        val sizeMb = String.format(Locale.getDefault(), "%.2f MB", media.size / (1024f * 1024f))
        val resolution = if (media.width > 0 && media.height > 0) "${media.width} x ${media.height}" else "Unknown"
        val dateFormatted = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(Date(media.dateAdded * 1000L))

        val durationFormatted = if (media.isVideo && media.duration > 0) {
            val totalSeconds = media.duration / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        } else null

        return ExifMetadata(
            fileName = media.displayName,
            path = media.uri.toString(),
            sizeFormatted = sizeMb,
            resolution = resolution,
            dateFormatted = dateFormatted,
            mimeType = media.mimeType.ifEmpty { if (media.isVideo) "video/*" else "image/*" },
            durationFormatted = durationFormatted,
            cameraModel = cameraModel,
            aperture = formatAperture(aperture),
            iso = iso?.let { "ISO $it" },
            exposureTime = formatExposureTime(exposureTime),
            focalLength = formatFocalLength(focalLength)
        )
    }

    fun copyToClipboard(media: MediaModel) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newUri(context.contentResolver, media.displayName, media.uri)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Copy failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun printImage(media: MediaModel) {
        try {
            val printHelper = PrintHelper(context)
            printHelper.scaleMode = PrintHelper.SCALE_MODE_FIT
            context.contentResolver.openInputStream(media.uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                bitmap?.let {
                    printHelper.printBitmap(media.displayName, it)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Print failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun showOnMap(media: MediaModel) {
        try {
            context.contentResolver.openInputStream(media.uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    val lat = latLong[0]
                    val lng = latLong[1]
                    val mapUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(media.displayName)})")
                    val intent = Intent(Intent.ACTION_VIEW, mapUri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return
                }
            }
            Toast.makeText(context, "No location info found in this photo", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Location unavailable", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun renameMedia(media: MediaModel, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
            }
            val rows = context.contentResolver.update(media.uri, values, null, null)
            rows > 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun createNewAlbum(albumName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, ".nomedia")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$albumName")
                }
            }
            val targetUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            resolver.insert(targetUri, values) != null
        } catch (_: Exception) {
            false
        }
    }

    suspend fun copyMediaToAlbum(media: MediaModel, targetAlbumName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "Copy_${media.displayName}")
                put(MediaStore.MediaColumns.MIME_TYPE, media.mimeType.ifEmpty { "image/jpeg" })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$targetAlbumName")
                }
            }
            val targetUri = if (media.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val newUri = resolver.insert(targetUri, values) ?: return@withContext false

            resolver.openInputStream(media.uri)?.use { inputStream ->
                resolver.openOutputStream(newUri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareMedia(uris: List<Uri>, mimeType: String = "*/*") {
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val chooser = Intent.createChooser(intent, "Share media")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun setWallpaper(uri: Uri) {
        val intent = Intent(Intent.ACTION_ATTACH_DATA).apply {
            setDataAndType(uri, "image/*")
            putExtra("mimeType", "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Set as")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun openWith(uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Open with")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    suspend fun getOcrIndexedCount(): Int = withContext(Dispatchers.IO) {
        try { mediaDao.getOcrIndexedCount() } catch (_: Exception) { 0 }
    }

    suspend fun clearOcrData() = withContext(Dispatchers.IO) {
        try { mediaDao.clearAllOcrData() } catch (_: Exception) { }
    }

    suspend fun runOcrIndexing(onProgress: (indexed: Int, total: Int) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
            )
            val allMedia = mediaDao.getAllMedia().filter { !it.isVideo }
            val total = allMedia.size
            var indexed = mediaDao.getOcrIndexedCount()
            onProgress(indexed, total)

            val unindexed = allMedia.filter { it.ocrText == null }
            var current = indexed

            for (item in unindexed) {
                try {
                    val uri = Uri.parse(item.uriString)
                    var bitmap: android.graphics.Bitmap? = null
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            val preprocessedBytes = RustBridge.nativePreprocessOcr(pfd.fd)
                            if (preprocessedBytes != null && preprocessedBytes.isNotEmpty()) {
                                bitmap = android.graphics.BitmapFactory.decodeByteArray(preprocessedBytes, 0, preprocessedBytes.size)
                            }
                        }
                    } catch (_: Exception) { }

                    if (bitmap == null) {
                        bitmap = loadDownscaledBitmap(context, uri, 1024)
                    }

                    val resolvedBitmap = bitmap
                    if (resolvedBitmap != null) {
                        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(resolvedBitmap, 0)
                        val text = recognizer.process(image).awaitText().text.trim()
                        val textToSave = if (text.isNotBlank()) text else ""
                        mediaDao.updateOcrText(item.id, textToSave)
                        if (textToSave.isNotEmpty()) {
                            current++
                        }
                    } else {
                        mediaDao.updateOcrText(item.id, "")
                    }
                } catch (_: Exception) {
                    try { mediaDao.updateOcrText(item.id, "") } catch (_: Exception) { }
                }
                onProgress(current, total)
            }
        } catch (_: Exception) { }
    }

    private fun loadDownscaledBitmap(context: Context, uri: Uri, maxDimension: Int): android.graphics.Bitmap? {
        return try {
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, options)
            }
            val w = options.outWidth
            val h = options.outHeight
            if (w <= 0 || h <= 0) return null

            var sampleSize = 1
            while (w / sampleSize > maxDimension || h / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (_: Exception) { null }
    }

    private suspend fun com.google.android.gms.tasks.Task<com.google.mlkit.vision.text.Text>.awaitText(): com.google.mlkit.vision.text.Text =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result -> continuation.resume(result) {} }
            addOnFailureListener { exception -> continuation.resumeWith(Result.failure(exception)) }
        }

    fun createTrashIntent(uris: List<Uri>, trash: Boolean): PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                MediaStore.createTrashRequest(context.contentResolver, uris, trash)
            } catch (_: Exception) { null }
        } else {
            null
        }
    }

    fun createDeleteIntent(uris: List<Uri>): PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                MediaStore.createDeleteRequest(context.contentResolver, uris)
            } catch (_: Exception) { null }
        } else {
            null
        }
    }

    suspend fun findDuplicateMedia(onProgress: (scanned: Int, total: Int) -> Unit): List<List<MediaModel>> = withContext(Dispatchers.IO) {
        val allMedia = try { mediaDao.getAllMedia().filter { !it.isVideo } } catch (_: Exception) { emptyList() }
        val total = allMedia.size
        if (total < 2) return@withContext emptyList()

        val hashedItems = mutableListOf<Pair<MediaModel, Long>>()
        var scanned = 0
        for (item in allMedia) {
            var hash = item.pHash
            if (hash == 0L) {
                try {
                    context.contentResolver.openFileDescriptor(Uri.parse(item.uriString), "r")?.use { pfd ->
                        hash = RustBridge.nativeComputePHash(pfd.fd)
                    }
                    if (hash != 0L) {
                        mediaDao.updatePHash(item.id, hash)
                    }
                } catch (_: Throwable) { }
            }
            if (hash != 0L) {
                hashedItems.add(item.toMediaModel() to hash)
            }
            scanned++
            onProgress(scanned, total)
        }

        if (hashedItems.size < 2) return@withContext emptyList()

        val hashArray = LongArray(hashedItems.size) { hashedItems[it].second }
        val groupedIndices = RustBridge.nativeGroupDuplicateHashes(hashArray, 6)
        if (groupedIndices != null) {
            val duplicateGroups = mutableListOf<List<MediaModel>>()
            var currentGroup = mutableListOf<MediaModel>()
            for (idx in groupedIndices) {
                if (idx == -1) {
                    if (currentGroup.size > 1) {
                        duplicateGroups.add(currentGroup)
                    }
                    currentGroup = mutableListOf()
                } else if (idx in hashedItems.indices) {
                    currentGroup.add(hashedItems[idx].first)
                }
            }
            if (currentGroup.size > 1) {
                duplicateGroups.add(currentGroup)
            }
            return@withContext duplicateGroups
        }

        emptyList()
    }
}
