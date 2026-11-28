package com.lazzy.gallery.ui.screens

import android.app.Activity
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.lazzy.gallery.data.ExifMetadata
import com.lazzy.gallery.data.MediaModel
import com.lazzy.gallery.ui.components.DeleteActionBottomSheet
import com.lazzy.gallery.ui.components.ExifInfoInlinePanel
import com.lazzy.gallery.ui.components.VideoPlayerView
import com.lazzy.gallery.ui.components.softDropShadow
import com.lazzy.gallery.ui.viewmodel.MediaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.graphics.Rect as AndroidRect

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun PhotoDetailScreen(
    mediaId: Long,
    albumName: String = "",
    source: String = "",
    viewModel: MediaViewModel,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onBack: () -> Unit,
    onIntentSenderRequest: (PendingIntent) -> Unit
) {
    val rawAll by viewModel.mediaItems.collectAsState()
    val albumMedia by viewModel.albumMediaItems.collectAsState()
    val favoritesMedia by viewModel.favoriteMediaItems.collectAsState()
    val trashedMedia by viewModel.trashedItems.collectAsState()
    val albums by viewModel.albums.collectAsState()

    val mediaItems = remember(rawAll, albumMedia, favoritesMedia, trashedMedia, albumName, source, mediaId) {
        val list = when (source) {
            "album" -> {
                val decoded = if (albumName.isNotBlank()) {
                    try { java.net.URLDecoder.decode(albumName, "UTF-8") } catch (_: Exception) { albumName }
                } else ""
                if (decoded.isNotBlank()) {
                    val filtered = rawAll.filter { it.albumName.equals(decoded, ignoreCase = true) }
                    if (filtered.isNotEmpty()) filtered else albumMedia
                } else {
                    albumMedia
                }
            }
            "favorites" -> {
                val favs = favoritesMedia.ifEmpty { rawAll.filter { it.isFavorite } }
                if (favs.isNotEmpty()) favs else rawAll
            }
            "bin" -> trashedMedia
            else -> rawAll
        }
        if (list.any { it.id == mediaId }) list else rawAll
    }

    val initialIndex = remember(mediaId, mediaItems) {
        mediaItems.indexOfFirst { it.id == mediaId }.coerceAtLeast(0)
    }

    if (mediaItems.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = initialIndex) { mediaItems.size }
    val currentMedia = mediaItems.getOrNull(pagerState.currentPage) ?: return

    var showControls by remember { mutableStateOf(true) }
    var showExifSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var exifInfo by remember { mutableStateOf<ExifMetadata?>(null) }
    var isAmbientLoaded by remember { mutableStateOf(false) }
    var isSlideshowActive by remember { mutableStateOf(false) }

    val exifAnimProgress by animateFloatAsState(
        targetValue = if (showExifSheet) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "exifAnimProgress"
    )

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    var showAlbumSelectorDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    val view = LocalView.current

    // Use light content over the dark viewer.
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose { }
    }

    LaunchedEffect(currentMedia.id) {
        viewModel.setCurrentViewedMediaId(currentMedia.id)
        isAmbientLoaded = false
        delay(400)
        isAmbientLoaded = true
        rotationAngle = 0f
    }

    LaunchedEffect(isSlideshowActive, pagerState.currentPage) {
        if (isSlideshowActive) {
            delay(3000)
            val next = (pagerState.currentPage + 1) % mediaItems.size
            pagerState.animateScrollToPage(next)
        }
    }



    LaunchedEffect(currentMedia.id) {
        exifInfo = viewModel.getExifInfo(currentMedia)
    }

    if (showDeleteDialog) {
        DeleteActionBottomSheet(
            title = "Do you want to move this photo to trash?",
            onDelete = {
                showDeleteDialog = false
                val intent = viewModel.trashMedia(listOf(currentMedia.uri))
                intent?.let { onIntentSenderRequest(it) }
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("File Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        viewModel.renameMedia(currentMedia, renameText.trim())
                    }
                    showRenameDialog = false
                }) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAlbumSelectorDialog) {
        AlertDialog(
            onDismissRequest = { showAlbumSelectorDialog = false },
            title = { Text("Select Target Album") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    albums.forEach { album ->
                        Surface(
                            onClick = {
                                viewModel.copyMediaToAlbum(currentMedia, album.name)
                                showAlbumSelectorDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = album.name,
                                modifier = Modifier.padding(16.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAlbumSelectorDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Use a small blurred copy of the image as the background.
        AnimatedVisibility(
            visible = isAmbientLoaded,
            enter = fadeIn(animationSpec = tween(1200)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                val context = LocalContext.current
                val ambientImageRequest = remember(currentMedia.uri) {
                    ImageRequest.Builder(context)
                        .data(currentMedia.uri)
                        .size(100, 100)
                        .allowHardware(true)
                        .build()
                }

                AsyncImage(
                    model = ambientImageRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 2.0f
                            scaleY = 2.0f
                            alpha = 0.55f
                        }
            .blur(32.dp),
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.60f),
                                    Color.Black.copy(alpha = 0.20f),
                                    Color.Black.copy(alpha = 0.90f)
                                )
                            )
                        )
                )
            }
        }

        var isImageZoomed by remember { mutableStateOf(false) }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !showExifSheet && !isImageZoomed,
            beyondViewportPageCount = 0,
            key = { page -> mediaItems.getOrNull(page)?.id ?: page },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = exifAnimProgress.coerceIn(0f, 1f)
                    val scale = 1f - (0.15f * progress)
                    scaleX = scale
                    scaleY = scale
                    translationY = -80.dp.toPx() * progress
                    shape = RoundedCornerShape(24.dp)
                    clip = progress > 0.05f
                }
        ) { page ->
            val media = mediaItems.getOrNull(page) ?: return@HorizontalPager

            if (media.isVideo) {
                VideoPlayerView(
                    uri = media.uri,
                    showControls = showControls,
                    onToggleControls = { showControls = !showControls },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val isCurrent = media.id == currentMedia.id
                ZoomableImageViewer(
                    media = media,
                    isCurrentPage = page == pagerState.currentPage,
                    rotationAngle = if (page == pagerState.currentPage) rotationAngle else 0f,
                    sharedTransitionScope = if (isCurrent) sharedTransitionScope else null,
                    animatedVisibilityScope = if (isCurrent) animatedVisibilityScope else null,
                    onZoomChanged = { isImageZoomed = it },
                    onToggleControls = { showControls = !showControls }
                )
            }
        }

        if (isSlideshowActive) {
            Surface(
                onClick = { isSlideshowActive = false },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = "Exit Slideshow ✕",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = showControls && !isSlideshowActive,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp, bottom = 12.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .height(50.dp)
                        .weight(1f, fill = false)
                        .softDropShadow(
                            color = Color.Black.copy(alpha = 0.25f),
                            borderRadius = 25.dp,
                            blurRadius = 14.dp
                        ),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }
                        Text(
                            text = currentMedia.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 2.dp, end = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Surface(
                    modifier = Modifier
                        .height(50.dp)
                        .wrapContentWidth()
                        .softDropShadow(
                            color = Color.Black.copy(alpha = 0.25f),
                            borderRadius = 25.dp,
                            blurRadius = 14.dp
                        ),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = { rotationAngle = (rotationAngle + 90f) % 360f },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RotateRight,
                                contentDescription = "Rotate",
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = { viewModel.shareSingleMedia(currentMedia) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = {
                                showExifSheet = !showExifSheet
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showControls && !isSlideshowActive,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        ) {
            Surface(
                modifier = Modifier
                    .height(52.dp)
                    .wrapContentWidth()
                    .softDropShadow(
                        color = Color.Black.copy(alpha = 0.25f),
                        borderRadius = 26.dp,
                        blurRadius = 14.dp
                    ),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.toggleFavorite(currentMedia.id) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (currentMedia.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            modifier = Modifier.size(22.dp),
                            tint = if (currentMedia.isFavorite) Color.Red else Color.White
                        )
                    }

                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(22.dp),
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            modifier = Modifier.size(22.dp),
                            tint = Color.White
                        )
                    }

                    MoreOptionsBottomSheet(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        currentMedia = currentMedia,
                        onRenameClick = {
                            renameText = currentMedia.displayName
                            showRenameDialog = true
                            showMoreMenu = false
                        },
                        onHideClick = {
                            viewModel.toggleHideMedia(currentMedia.id)
                            showMoreMenu = false
                        },
                        onCopyClipboardClick = {
                            viewModel.copyToClipboard(currentMedia)
                            showMoreMenu = false
                        },
                        onCopyToClick = {
                            showAlbumSelectorDialog = true
                            showMoreMenu = false
                        },
                        onOpenWithClick = {
                            viewModel.openWith(currentMedia)
                            showMoreMenu = false
                        },
                        onSetAsClick = {
                            viewModel.setWallpaper(currentMedia)
                            showMoreMenu = false
                        },
                        onPrintClick = {
                            viewModel.printImage(currentMedia)
                            showMoreMenu = false
                        },
                        onShowOnMapClick = {
                            viewModel.showOnMap(currentMedia)
                            showMoreMenu = false
                        },
                        onSlideshowClick = {
                            isSlideshowActive = true
                            showMoreMenu = false
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showExifSheet && exifInfo != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.48f)
        ) {
            val safeExif = exifInfo
            if (safeExif != null) {
                ExifInfoInlinePanel(
                    exif = safeExif,
                    onClose = { showExifSheet = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private val DetailBoundsTransform = BoundsTransform { _, _ ->
    tween(
        durationMillis = 420,
        easing = androidx.compose.animation.core.FastOutSlowInEasing
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ZoomableImageViewer(
    media: MediaModel,
    isCurrentPage: Boolean = true,
    rotationAngle: Float = 0f,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onZoomChanged: (Boolean) -> Unit = {},
    onToggleControls: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember(media.id) { mutableFloatStateOf(1f) }
    var offset by remember(media.id) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(scale) {
        onZoomChanged(scale > 1.05f)
    }

    // Reset zoom when this page leaves the pager.
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage && scale != 1f) {
            scale = 1f
            offset = Offset.Zero
            onZoomChanged(false)
        }
    }

    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = "media-image-${media.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = DetailBoundsTransform
            )
        }
    } else Modifier

    val isZoomed = scale > 1.02f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(media.id) {
                detectTapGestures(
                    onTap = { onToggleControls() },
                    onDoubleTap = {
                        if (scale > 1.2f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 3f
                        }
                    }
                )
            }
            .then(
                if (isZoomed) {
                    Modifier.pointerInput(media.id, isZoomed) {
                        detectTransformGestures(panZoomLock = true) { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1.02f) {
                                offset += pan
                            } else {
                                scale = 1f
                                offset = Offset.Zero
                            }
                        }
                    }
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        val fullImageRequest = remember(media.uri, media.id) {
            ImageRequest.Builder(context)
                .data(media.uri)
                .placeholderMemoryCacheKey("thumb_${media.id}")
                .memoryCacheKey("full_${media.id}")
                .diskCacheKey("full_${media.id}")
                .size(coil.size.Size.ORIGINAL)
                .crossfade(false)
                .allowHardware(true)
                .diskCachePolicy(CachePolicy.ENABLED)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .build()
        }
        AsyncImage(
            model = fullImageRequest,
            contentDescription = media.displayName,
            modifier = Modifier
                .fillMaxSize()
                .then(sharedModifier)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    rotationZ = rotationAngle,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentScale = ContentScale.Fit
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreOptionsBottomSheet(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    currentMedia: MediaModel,
    onRenameClick: () -> Unit,
    onHideClick: () -> Unit,
    onCopyClipboardClick: () -> Unit,
    onCopyToClick: () -> Unit,
    onOpenWithClick: () -> Unit,
    onSetAsClick: () -> Unit,
    onPrintClick: () -> Unit,
    onShowOnMapClick: () -> Unit,
    onSlideshowClick: () -> Unit
) {
    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            containerColor = Color(0xFF18181C),
            contentColor = Color.White,
            scrimColor = Color.Black.copy(alpha = 0.6f),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                BottomSheetMenuItem(label = "Rename", icon = Icons.Default.Edit, onClick = onRenameClick)
                BottomSheetMenuItem(label = "Hide", icon = Icons.Default.VisibilityOff, onClick = onHideClick)
                BottomSheetMenuItem(label = "Copy to clipboard", icon = Icons.Default.ContentCopy, onClick = onCopyClipboardClick)
                BottomSheetMenuItem(label = "Copy to", icon = Icons.Default.FolderCopy, onClick = onCopyToClick)
                BottomSheetMenuItem(label = "Open with", icon = Icons.AutoMirrored.Filled.OpenInNew, onClick = onOpenWithClick)
                if (!currentMedia.isVideo) {
                    BottomSheetMenuItem(label = "Set as", icon = Icons.Default.Wallpaper, onClick = onSetAsClick)
                }
                BottomSheetMenuItem(label = "Print", icon = Icons.Default.Print, onClick = onPrintClick)
                BottomSheetMenuItem(label = "Show on map", icon = Icons.Default.Map, onClick = onShowOnMapClick)
                BottomSheetMenuItem(label = "Slideshow", icon = Icons.Default.Slideshow, onClick = onSlideshowClick)
                Spacer(modifier = Modifier.height(36.dp))
            }
        }
    }
}

@Composable
private fun BottomSheetMenuItem(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = Color.White.copy(alpha = 0.8f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White
            )
        }
    }
}
