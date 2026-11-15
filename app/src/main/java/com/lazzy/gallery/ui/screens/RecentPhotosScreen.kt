package com.lazzy.gallery.ui.screens

import android.app.PendingIntent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lazzy.gallery.ui.components.ExpressiveSortDropdownMenu
import com.lazzy.gallery.ui.components.MediaGridThumbnail
import com.lazzy.gallery.ui.viewmodel.MediaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private fun findItemIndexAtPosition(gridState: LazyGridState, hitOffset: Offset): Int? {
    val x = hitOffset.x.toInt()
    val y = hitOffset.y.toInt()
    val visibleItems = gridState.layoutInfo.visibleItemsInfo
    val exact = visibleItems.firstOrNull { item ->
        x >= item.offset.x && x <= item.offset.x + item.size.width &&
        y >= item.offset.y && y <= item.offset.y + item.size.height
    }
    if (exact != null) return exact.index

    return visibleItems.firstOrNull { item ->
        x >= item.offset.x - 16 && x <= item.offset.x + item.size.width + 16 &&
        y >= item.offset.y - 16 && y <= item.offset.y + item.size.height + 16
    }?.index
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun RecentPhotosScreen(
    viewModel: MediaViewModel,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onMediaClick: (Long) -> Unit,
    onIntentSenderRequest: (PendingIntent) -> Unit
) {
    val mediaItems by viewModel.mediaItems.collectAsState()
    val isInitialLoadComplete by viewModel.isInitialLoadComplete.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val currentSort by viewModel.sortOrder.collectAsState()
    val libraryColumns by viewModel.libraryColumns.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val headerBackground = primaryColor.copy(alpha = 0.22f).compositeOver(MaterialTheme.colorScheme.background)

    var showSortMenu by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // State used while selecting a range by dragging.
    var autoScrollSpeed by remember { mutableFloatStateOf(0f) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var isSelectingRange by remember { mutableStateOf(true) }
    var initialSelectedIdsOnDrag by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var currentDragPosition by remember { mutableStateOf<Offset?>(null) }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedIds.size} selected",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                        IconButton(onClick = { viewModel.shareSelectedMedia() }) {
                            Icon(Icons.Default.Share, contentDescription = "Share Selected")
                        }
                        IconButton(onClick = {
                            val intent = viewModel.trashSelectedMedia()
                            intent?.let { onIntentSenderRequest(it) }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            "Library",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            )
                        )
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                            }
                            ExpressiveSortDropdownMenu(
                                expanded = showSortMenu,
                                currentSort = currentSort,
                                onSortSelected = { order ->
                                    viewModel.setSortOrder(order)
                                },
                                onDismissRequest = { showSortMenu = false }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = headerBackground,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        containerColor = headerBackground
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            tonalElevation = 1.dp
        ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        viewModel.loadMedia()
                        delay(500)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
            ) {
                if (mediaItems.isEmpty()) {
                    if (isInitialLoadComplete) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No photos or videos found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    val gridState = rememberLazyGridState()
                    val currentViewedId by viewModel.currentViewedMediaId.collectAsState()

                    LaunchedEffect(currentViewedId) {
                        currentViewedId?.let { id ->
                            val index = mediaItems.indexOfFirst { it.id == id }
                            if (index >= 0) {
                                gridState.scrollToItem(index)
                            }
                        }
                    }

                    LaunchedEffect(autoScrollSpeed) {
                        if (autoScrollSpeed != 0f) {
                            while (isActive && autoScrollSpeed != 0f) {
                                gridState.scrollBy(autoScrollSpeed)
                                currentDragPosition?.let { pos ->
                                    findItemIndexAtPosition(gridState, pos)?.let { idx ->
                                        if (dragStartIndex >= 0) {
                                            val start = min(dragStartIndex, idx)
                                            val end = max(dragStartIndex, idx)
                                            val rangeIds = (start..end).mapNotNull { mediaItems.getOrNull(it)?.id }.toSet()
                                            val updated = if (isSelectingRange) {
                                                initialSelectedIdsOnDrag + rangeIds
                                            } else {
                                                initialSelectedIdsOnDrag - rangeIds
                                            }
                                            viewModel.setSelectedIds(updated)
                                        }
                                    }
                                }
                                delay(16)
                            }
                        }
                    }

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(libraryColumns),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                            .pointerInput(isSelectionMode, mediaItems) {
                                val viewConfig = viewConfiguration
                                val holdTimeout = if (isSelectionMode) 160L else viewConfig.longPressTimeoutMillis
                                val touchSlop = viewConfig.touchSlop

                                awaitEachGesture {
                                    val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                                    val startPos = down.position
                                    val startIdx = findItemIndexAtPosition(gridState, startPos) ?: return@awaitEachGesture
                                    val pointerId = down.id

                                    var isDragSelect = false
                                    val holdSuccess = try {
                                        withTimeout(holdTimeout) {
                                            while (true) {
                                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                                val change = event.changes.firstOrNull { it.id == pointerId }
                                                if (change == null || change.isConsumed || !change.pressed) {
                                                    return@withTimeout false
                                                }
                                                if ((change.position - startPos).getDistance() > touchSlop) {
                                                    return@withTimeout false
                                                }
                                            }
                                            true
                                        }
                                    } catch (_: Exception) {
                                        true
                                    }

                                    if (holdSuccess) {
                                        isDragSelect = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dragStartIndex = startIdx
                                        initialSelectedIdsOnDrag = viewModel.selectedIds.value
                                        val startItem = mediaItems.getOrNull(startIdx)
                                        if (startItem != null) {
                                            val willSelect = !initialSelectedIdsOnDrag.contains(startItem.id)
                                            isSelectingRange = willSelect
                                            val initialSet = if (willSelect) {
                                                initialSelectedIdsOnDrag + startItem.id
                                            } else {
                                                initialSelectedIdsOnDrag - startItem.id
                                            }
                                            viewModel.setSelectedIds(initialSet)
                                        }

                                        while (isDragSelect) {
                                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == pointerId }
                                            if (change == null || !change.pressed) {
                                                isDragSelect = false
                                                break
                                            }

                                            change.consume()
                                            val pos = change.position
                                            currentDragPosition = pos

                                            val currentIdx = findItemIndexAtPosition(gridState, pos)
                                            if (currentIdx != null) {
                                                val start = min(startIdx, currentIdx)
                                                val end = max(startIdx, currentIdx)
                                                val rangeIds = (start..end).mapNotNull { mediaItems.getOrNull(it)?.id }.toSet()
                                                val newSelection = if (isSelectingRange) {
                                                    initialSelectedIdsOnDrag + rangeIds
                                                } else {
                                                    initialSelectedIdsOnDrag - rangeIds
                                                }
                                                viewModel.setSelectedIds(newSelection)
                                            }

                                            // Scroll when the pointer nears an edge.
                                            val gridHeight = size.height.toFloat()
                                            val topEdge = 130f
                                            val bottomEdge = gridHeight - 130f
                                            val scrollMaxSpeed = 28f

                                            when {
                                                pos.y < topEdge -> {
                                                    val factor = ((topEdge - pos.y) / topEdge).coerceIn(0f, 1f)
                                                    autoScrollSpeed = -scrollMaxSpeed * factor
                                                }
                                                pos.y > bottomEdge -> {
                                                    val factor = ((pos.y - bottomEdge) / 130f).coerceIn(0f, 1f)
                                                    autoScrollSpeed = scrollMaxSpeed * factor
                                                }
                                                else -> {
                                                    autoScrollSpeed = 0f
                                                }
                                            }
                                        }

                                        autoScrollSpeed = 0f
                                        dragStartIndex = -1
                                        currentDragPosition = null
                                    }
                                }
                            },
                        contentPadding = PaddingValues(
                            top = 16.dp,
                            bottom = 140.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = mediaItems,
                            key = { it.id },
                            contentType = { if (it.isVideo) "video_thumbnail" else "photo_thumbnail" }
                        ) { item ->
                            val isSelected = selectedIds.contains(item.id)
                            val onClick = remember(item.id, isSelectionMode) {
                                {
                                    if (isSelectionMode) {
                                        viewModel.toggleSelection(item.id)
                                    } else {
                                        viewModel.setCurrentViewedMediaId(item.id)
                                        onMediaClick(item.id)
                                    }
                                }
                            }
                            val onLongClick = remember(item.id) {
                                { viewModel.toggleSelection(item.id) }
                            }
                            MediaGridThumbnail(
                                media = item,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                isTargetSharedItem = currentViewedId == item.id,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onClick = onClick,
                                onLongClick = onLongClick
                            )
                        }
                    }
                }
            }
        }
      }
    }
}
