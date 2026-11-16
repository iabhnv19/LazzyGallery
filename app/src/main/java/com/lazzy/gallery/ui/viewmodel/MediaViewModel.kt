package com.lazzy.gallery.ui.viewmodel

import android.app.PendingIntent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.lazzy.gallery.data.Album
import com.lazzy.gallery.data.AlbumInfo
import com.lazzy.gallery.data.ExifMetadata
import com.lazzy.gallery.data.FilterType
import com.lazzy.gallery.data.MediaModel
import com.lazzy.gallery.data.MediaRepository
import com.lazzy.gallery.data.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class OcrStatus(
    val indexedCount: Int = 0,
    val totalCount: Int = 0,
    val isScanning: Boolean = false
)

data class DuplicateStatus(
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val isScanning: Boolean = false,
    val duplicateGroups: List<List<MediaModel>> = emptyList()
)

class MediaViewModel(private val repository: MediaRepository) : ViewModel() {

    private val mediaLoadMutex = Mutex()

    // Media before sorting and filtering.
    private val _rawMediaItems = MutableStateFlow<List<MediaModel>>(emptyList())
    private val _isInitialLoadComplete = MutableStateFlow(false)
    val isInitialLoadComplete: StateFlow<Boolean> = _isInitialLoadComplete

    // Items selected for batch actions.
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode

    // Current library sort and filter.
    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    private val _filterType = MutableStateFlow(FilterType.ALL)
    val filterType: StateFlow<FilterType> = _filterType

    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    private val _albumMediaItems = MutableStateFlow<List<MediaModel>>(emptyList())
    val albumMediaItems: StateFlow<List<MediaModel>> = _albumMediaItems

    private val _trashedItems = MutableStateFlow<List<MediaModel>>(emptyList())
    val trashedItems: StateFlow<List<MediaModel>> = _trashedItems

    private val _favoriteMediaItems = MutableStateFlow<List<MediaModel>>(emptyList())
    val favoriteMediaItems: StateFlow<List<MediaModel>> = _favoriteMediaItems

    private val _currentViewedMediaId = MutableStateFlow<Long?>(null)
    val currentViewedMediaId: StateFlow<Long?> = _currentViewedMediaId

    private val _libraryColumns = MutableStateFlow(repository.getLibraryColumns())
    val libraryColumns: StateFlow<Int> = _libraryColumns

    private val _albumColumns = MutableStateFlow(repository.getAlbumColumns())
    val albumColumns: StateFlow<Int> = _albumColumns

    private val _isDarkMode = MutableStateFlow(repository.isDarkMode())
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val _useDynamicColor = MutableStateFlow(repository.isDynamicColor())
    val useDynamicColor: StateFlow<Boolean> = _useDynamicColor

    private val _accentColorIndex = MutableStateFlow(repository.getAccentColorIndex())
    val accentColorIndex: StateFlow<Int> = _accentColorIndex

    private val _autoPlayVideo = MutableStateFlow(repository.getAutoPlayVideo())
    val autoPlayVideo: StateFlow<Boolean> = _autoPlayVideo

    private val _loopVideo = MutableStateFlow(repository.getLoopVideo())
    val loopVideo: StateFlow<Boolean> = _loopVideo

    private val _animateGifsThumbnail = MutableStateFlow(repository.getAnimateGifsThumbnail())
    val animateGifsThumbnail: StateFlow<Boolean> = _animateGifsThumbnail

    private val _dateTimeFormat = MutableStateFlow(repository.getDateTimeFormat())
    val dateTimeFormat: StateFlow<String> = _dateTimeFormat

    private val _showHiddenItems = MutableStateFlow(repository.getShowHiddenItems())
    val showHiddenItems: StateFlow<Boolean> = _showHiddenItems

    private val _defaultStartScreen = MutableStateFlow(repository.getDefaultStartScreen())
    val defaultStartScreen: StateFlow<String> = _defaultStartScreen

    private val _rightButtonAction = MutableStateFlow(repository.getRightButtonAction())
    val rightButtonAction: StateFlow<String> = _rightButtonAction

    fun setRightButtonAction(action: String) {
        repository.setRightButtonAction(action)
        _rightButtonAction.value = action
    }

    fun setLibraryColumns(columns: Int) {
        repository.setLibraryColumns(columns)
        _libraryColumns.value = columns
    }

    fun setAlbumColumns(columns: Int) {
        repository.setAlbumColumns(columns)
        _albumColumns.value = columns
    }

    fun setDarkMode(enabled: Boolean) {
        repository.setDarkMode(enabled)
        _isDarkMode.value = enabled
    }

    fun setDynamicColor(enabled: Boolean) {
        repository.setDynamicColor(enabled)
        _useDynamicColor.value = enabled
    }

    fun setAccentColorIndex(index: Int) {
        repository.setAccentColorIndex(index)
        _accentColorIndex.value = index
    }

    fun setAutoPlayVideo(enabled: Boolean) {
        repository.setAutoPlayVideo(enabled)
        _autoPlayVideo.value = enabled
    }

    fun setLoopVideo(enabled: Boolean) {
        repository.setLoopVideo(enabled)
        _loopVideo.value = enabled
    }

    fun setAnimateGifsThumbnail(enabled: Boolean) {
        repository.setAnimateGifsThumbnail(enabled)
        _animateGifsThumbnail.value = enabled
    }

    fun setDateTimeFormat(format: String) {
        repository.setDateTimeFormat(format)
        _dateTimeFormat.value = format
    }

    fun setShowHiddenItems(enabled: Boolean) {
        repository.setShowHiddenItems(enabled)
        _showHiddenItems.value = enabled
        loadMedia()
    }

    fun setDefaultStartScreen(route: String) {
        repository.setDefaultStartScreen(route)
        _defaultStartScreen.value = route
    }

    private val _ocrStatus = MutableStateFlow(OcrStatus())
    val ocrStatus: StateFlow<OcrStatus> = _ocrStatus

    fun loadOcrStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val count = repository.getOcrIndexedCount()
            _ocrStatus.value = _ocrStatus.value.copy(indexedCount = count)
        }
    }

    fun startOcrIndexing() {
        if (_ocrStatus.value.isScanning) return
        viewModelScope.launch(Dispatchers.IO) {
            _ocrStatus.value = _ocrStatus.value.copy(isScanning = true)
            repository.runOcrIndexing { indexed, total ->
                _ocrStatus.value = OcrStatus(indexedCount = indexed, totalCount = total, isScanning = true)
            }
            _ocrStatus.value = _ocrStatus.value.copy(isScanning = false)
            loadMedia()
        }
    }

    fun clearOcrIndexing() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearOcrData()
            loadOcrStatus()
            loadMedia()
        }
    }

    private val _duplicateStatus = MutableStateFlow(DuplicateStatus())
    val duplicateStatus: StateFlow<DuplicateStatus> = _duplicateStatus

    fun scanForDuplicates() {
        if (_duplicateStatus.value.isScanning) return
        viewModelScope.launch(Dispatchers.IO) {
            _duplicateStatus.value = DuplicateStatus(isScanning = true)
            val groups = repository.findDuplicateMedia { scanned, total ->
                _duplicateStatus.value = DuplicateStatus(scannedCount = scanned, totalCount = total, isScanning = true)
            }
            _duplicateStatus.value = DuplicateStatus(isScanning = false, duplicateGroups = groups)
        }
    }

    fun clearDuplicates() {
        _duplicateStatus.value = DuplicateStatus()
    }

    fun setCurrentViewedMediaId(id: Long?) {
        _currentViewedMediaId.value = id
    }

    val mediaItems: StateFlow<List<MediaModel>> = combine(
        _rawMediaItems,
        _sortOrder,
        _filterType
    ) { items, sort, filter ->
        val filtered = when (filter) {
            FilterType.ALL -> items
            FilterType.IMAGES_ONLY -> items.filter { !it.isVideo }
            FilterType.VIDEOS_ONLY -> items.filter { it.isVideo }
            FilterType.FAVORITES -> items.filter { it.isFavorite }
            FilterType.SCREENSHOTS -> items.filter { it.albumName.contains("screenshot", ignoreCase = true) || it.displayName.contains("screenshot", ignoreCase = true) }
        }

        if (sort == SortOrder.DATE_DESC) {
            filtered
        } else {
            when (sort) {
                SortOrder.DATE_ASC -> filtered.sortedBy { it.dateAdded }
                SortOrder.NAME_ASC -> filtered.sortedBy { it.displayName.lowercase() }
                SortOrder.NAME_DESC -> filtered.sortedByDescending { it.displayName.lowercase() }
                SortOrder.SIZE_DESC -> filtered.sortedBy { it.size }
                SortOrder.SIZE_ASC -> filtered.sortedBy { it.size }
                else -> filtered
            }
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        emptyList()
    )

    val pagedMediaItems: Flow<PagingData<MediaModel>> = repository.getPagedMediaFlow()
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Show cached data while the database syncs.
            try {
                val cached = repository.getCachedMediaSync()
                if (cached.isNotEmpty()) {
                    updateMediaState(cached)
                    _isInitialLoadComplete.value = true
                }
            } catch (_: Exception) { }

            // Sync MediaStore with Room immediately on startup in background.
            try {
                repository.syncMediaStoreWithRoom()
            } catch (_: Exception) { }

            // Keep the UI in sync with Room.
            launch {
                repository.observeAllMediaFlow().collect { list ->
                    val currentIds = _rawMediaItems.value.map { it.id }
                    val newIds = list.map { it.id }
                    if (currentIds != newIds) {
                        updateMediaState(list)
                        if (list.isNotEmpty()) {
                            _isInitialLoadComplete.value = true
                        }
                    }
                }
            }

            // Listen for changes made by other apps (after initial cold boot).
            launch {
                kotlinx.coroutines.delay(3500)
                repository.observeMediaStoreChanges().collect {
                    // Refresh Room so its flow can update the UI.
                    repository.syncMediaStoreWithRoom()
                }
            }
        }
    }

    fun forceRefreshMediaStore() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.syncMediaStoreWithRoom()
            val loaded = repository.fetchAllMedia()
            updateMediaState(loaded)
            _isInitialLoadComplete.value = true
        }
    }

    fun loadMedia(publishLoadedState: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            mediaLoadMutex.withLock {
                val loaded = repository.fetchAllMedia()
                if (publishLoadedState || _rawMediaItems.value.isEmpty()) {
                    if (_rawMediaItems.value != loaded) {
                        updateMediaState(loaded)
                    }
                }
                _isInitialLoadComplete.value = true
            }
        }
    }

    /** Rechecks MediaStore when the activity returns to the foreground. */
    fun refreshOnResume() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.syncMediaStoreWithRoom()
        }
    }

    private fun updateMediaState(list: List<MediaModel>) {
        if (_rawMediaItems.value == list) return
        _rawMediaItems.value = list
        val favs = list.filter { it.isFavorite }
        if (_favoriteMediaItems.value != favs) {
            _favoriteMediaItems.value = favs
        }
        recomputeAlbums(list)
    }

    fun togglePinAlbum(albumName: String) {
        repository.togglePinAlbum(albumName)
        recomputeAlbums(_rawMediaItems.value)
    }

    fun toggleHideAlbum(albumName: String) {
        repository.toggleHideAlbum(albumName)
        recomputeAlbums(_rawMediaItems.value)
        loadMedia()
    }

    suspend fun getAlbumInfo(albumName: String): AlbumInfo {
        return repository.fetchAlbumInfo(albumName)
    }

    suspend fun deleteAlbum(albumName: String): PendingIntent? {
        val items = repository.fetchMediaByAlbum(albumName)
        val uris = items.map { it.uri }
        if (uris.isEmpty()) return null
        return repository.createTrashIntent(uris, true) ?: repository.createDeleteIntent(uris)
    }

    private fun recomputeAlbums(list: List<MediaModel>) {
        viewModelScope.launch(Dispatchers.IO) {
            val pinned = repository.getPinnedAlbums()
            val hidden = repository.getHiddenAlbums()
            val computed = list
                .filter { !hidden.contains(it.albumName) }
                .groupBy { it.albumName }
                .mapNotNull { (name, media) ->
                    media.firstOrNull()?.let { first ->
                        Album(
                            name = name,
                            coverUri = first.uri,
                            mediaCount = media.size,
                            isPinned = pinned.contains(name)
                        )
                    }
                }
                .sortedWith(
                    compareByDescending<Album> { it.isPinned }
                        .thenByDescending { it.mediaCount }
                )
            if (_albums.value != computed) {
                _albums.value = computed
            }
        }
    }

    fun loadAlbumMedia(albumName: String) {
        // Show the current library before querying Room.
        val inMemory = _rawMediaItems.value.filter { it.albumName == albumName }
        if (inMemory.isNotEmpty()) {
            _albumMediaItems.value = inMemory
        }
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = repository.fetchMediaByAlbum(albumName)
            if (loaded.isNotEmpty()) {
                _albumMediaItems.value = loaded
            }
        }
    }

    fun loadTrashedMedia() {
        viewModelScope.launch(Dispatchers.IO) {
            _trashedItems.value = repository.fetchAllMedia(includeTrashed = true).filter { it.isTrashed }
        }
    }

    fun loadFavoriteMedia() {
        viewModelScope.launch(Dispatchers.IO) {
            _favoriteMediaItems.value = repository.fetchFavoriteMedia()
        }
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setFilterType(type: FilterType) {
        _filterType.value = type
    }

    fun setSelectedIds(ids: Set<Long>) {
        _selectedIds.value = ids
        _isSelectionMode.value = ids.isNotEmpty()
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedIds.value = current
        _isSelectionMode.value = current.isNotEmpty()
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun selectAll() {
        _selectedIds.value = mediaItems.value.map { it.id }.toSet()
        _isSelectionMode.value = mediaItems.value.isNotEmpty()
    }

    fun trashMedia(uris: List<Uri>): PendingIntent? {
        return repository.createTrashIntent(uris, true)
    }

    fun restoreMedia(uris: List<Uri>): PendingIntent? {
        return repository.createTrashIntent(uris, false)
    }

    fun deleteMediaPermanently(uris: List<Uri>): PendingIntent? {
        return repository.createDeleteIntent(uris)
    }

    fun trashSelectedMedia(): PendingIntent? {
        val uris = mediaItems.value.filter { _selectedIds.value.contains(it.id) }.map { it.uri }
        clearSelection()
        return trashMedia(uris)
    }

    fun shareSelectedMedia() {
        val uris = mediaItems.value.filter { _selectedIds.value.contains(it.id) }.map { it.uri }
        clearSelection()
        repository.shareMedia(uris)
    }

    fun shareSingleMedia(media: MediaModel) {
        repository.shareMedia(listOf(media.uri), media.mimeType)
    }

    fun toggleHideMedia(mediaId: Long) {
        repository.toggleHideMedia(mediaId)
        loadMedia()
    }

    fun toggleFavorite(mediaId: Long): Boolean {
        val isFav = repository.toggleFavorite(mediaId)
        loadMedia()
        loadFavoriteMedia()
        return isFav
    }

    suspend fun getExifInfo(media: MediaModel): ExifMetadata = withContext(Dispatchers.IO) {
        repository.getExifInfo(media)
    }

    fun renameMedia(media: MediaModel, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.renameMedia(media, newName)
            loadMedia()
        }
    }

    fun copyToClipboard(media: MediaModel) {
        repository.copyToClipboard(media)
    }

    fun openWith(media: MediaModel) {
        repository.openWith(media.uri, media.mimeType)
    }

    fun setWallpaper(media: MediaModel) {
        repository.setWallpaper(media.uri)
    }

    fun printImage(media: MediaModel) {
        repository.printImage(media)
    }

    fun showOnMap(media: MediaModel) {
        repository.showOnMap(media)
    }

    fun createNewAlbum(albumName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.createNewAlbum(albumName)
            loadMedia()
        }
    }

    fun copyMediaToAlbum(media: MediaModel, targetAlbumName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.copyMediaToAlbum(media, targetAlbumName)
            loadMedia()
        }
    }
}
