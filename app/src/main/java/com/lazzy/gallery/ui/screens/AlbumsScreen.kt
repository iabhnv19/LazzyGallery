package com.lazzy.gallery.ui.screens

import android.app.PendingIntent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Precision
import com.lazzy.gallery.data.Album
import com.lazzy.gallery.data.AlbumInfo
import com.lazzy.gallery.ui.components.GalleryOverflowMenu
import com.lazzy.gallery.ui.viewmodel.MediaViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AlbumBoundsTransform = BoundsTransform { _, _ ->
    tween(
        durationMillis = 420,
        easing = androidx.compose.animation.core.FastOutSlowInEasing
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumsScreen(
    viewModel: MediaViewModel,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onAlbumClick: (String) -> Unit,
    onNavigateToBin: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onIntentSenderRequest: (PendingIntent) -> Unit = {}
) {
    val context = LocalContext.current
    val albums by viewModel.albums.collectAsState()
    val albumColumns by viewModel.albumColumns.collectAsState()
    val useDynamicColor by viewModel.useDynamicColor.collectAsState()
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    var selectedAlbumForOptions by remember { mutableStateOf<Album?>(null) }
    var albumInfoToShow by remember { mutableStateOf<AlbumInfo?>(null) }
    var albumToDelete by remember { mutableStateOf<Album?>(null) }

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color(0xFF141418),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f),
            title = {
                Text(
                    text = "New folder",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Enter name for the new folder:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text("Folder name", color = Color.White.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            coroutineScope.launch {
                                viewModel.createNewAlbum(newFolderName.trim())
                                showNewFolderDialog = false
                                newFolderName = ""
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewFolderDialog = false
                        newFolderName = ""
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f))
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    selectedAlbumForOptions?.let { album ->
        ModalBottomSheet(
            onDismissRequest = { selectedAlbumForOptions = null },
            sheetState = bottomSheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(56.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(album.coverUri)
                                .size(150, 150)
                                .allowHardware(true)
                                .build(),
                            contentDescription = album.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${album.mediaCount} items" + if (album.isPinned) " • Pinned" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                ListItem(
                    headlineContent = {
                        Text(if (album.isPinned) "Unpin album" else "Pin album", fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = {
                        Text(if (album.isPinned) "Remove from top of grid" else "Keep this album pinned at the top")
                    },
                    leadingContent = {
                        Icon(Icons.Default.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                val target = selectedAlbumForOptions
                                selectedAlbumForOptions = null
                                target?.let {
                                    viewModel.togglePinAlbum(it.name)
                                    Toast.makeText(
                                        context,
                                        if (it.isPinned) "${it.name} unpinned" else "${it.name} pinned to top",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )
                )

                ListItem(
                    headlineContent = {
                        Text("Hide folder", fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = {
                        Text("Hide this folder and its photos from library")
                    },
                    leadingContent = {
                        Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                val target = selectedAlbumForOptions
                                selectedAlbumForOptions = null
                                target?.let {
                                    viewModel.toggleHideAlbum(it.name)
                                    Toast.makeText(context, "${it.name} hidden", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                )

                ListItem(
                    headlineContent = {
                        Text("Album info", fontWeight = FontWeight.SemiBold)
                    },
                    supportingContent = {
                        Text("View storage path, total size, and counts")
                    },
                    leadingContent = {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                val target = selectedAlbumForOptions
                                selectedAlbumForOptions = null
                                target?.let {
                                    coroutineScope.launch {
                                        val info = viewModel.getAlbumInfo(it.name)
                                        albumInfoToShow = info
                                    }
                                }
                            }
                        )
                )

                ListItem(
                    headlineContent = {
                        Text("Delete album", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    },
                    supportingContent = {
                        Text("Delete all ${album.mediaCount} items in this folder", color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                    },
                    leadingContent = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                val target = selectedAlbumForOptions
                                selectedAlbumForOptions = null
                                albumToDelete = target
                            }
                        )
                )
            }
        }
    }

    albumInfoToShow?.let { info ->
        AlertDialog(
            onDismissRequest = { albumInfoToShow = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            title = {
                Text(
                    text = info.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    InfoItem(label = "Items", value = "${info.mediaCount} items (${info.photoCount} photos, ${info.videoCount} videos)")
                    InfoItem(label = "Total Size", value = Formatter.formatFileSize(context, info.totalSize))
                    InfoItem(label = "Location", value = info.path)
                    if (info.latestDate > 0L) {
                        val dateStr = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(info.latestDate * 1000L))
                        InfoItem(label = "Latest photo", value = dateStr)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { albumInfoToShow = null }) {
                    Text("Close", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    albumToDelete?.let { album ->
        AlertDialog(
            onDismissRequest = { albumToDelete = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            icon = {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = {
                Text("Delete ${album.name}?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text("Are you sure you want to delete this album and all ${album.mediaCount} items in it? This will move them to the recycle bin.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = albumToDelete
                        albumToDelete = null
                        target?.let {
                            coroutineScope.launch {
                                val pendingIntent = viewModel.deleteAlbum(it.name)
                                if (pendingIntent != null) {
                                    onIntentSenderRequest(pendingIntent)
                                } else {
                                    Toast.makeText(context, "Could not delete album", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { albumToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val headerBackground = primaryColor.copy(alpha = 0.22f).compositeOver(MaterialTheme.colorScheme.background)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Albums",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    )
                },
                actions = {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    GalleryOverflowMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        onNewFolderClick = { showNewFolderDialog = true },
                        onSettingsClick = onNavigateToSettings
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = headerBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
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
            if (albums.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No albums found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(albumColumns),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(
                        top = 20.dp,
                        bottom = 140.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(
                        items = albums,
                        key = { it.name },
                        contentType = { "album_card" }
                    ) { album ->
                        val onClick = remember(album.name) { { onAlbumClick(album.name) } }
                        val onLongClick = remember(album) { { selectedAlbumForOptions = album } }
                        AlbumCard(
                            album = album,
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

@Composable
private fun InfoItem(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumCard(
    album: Album,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val coverUriStr = album.coverUri.toString()
    val imageRequest = remember(coverUriStr) {
        ImageRequest.Builder(context)
            .data(album.coverUri)
            .size(300, 300)
            .precision(Precision.EXACT)
            .allowHardware(true)
            .crossfade(true)
            .crossfade(180)
            .apply {
                if (coverUriStr.contains("video", ignoreCase = true)) {
                    decoderFactory(VideoFrameDecoder.Factory())
                    videoFrameMillis(0)
                }
            }
            .build()
    }

    val titleSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = "album-title-${album.name}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = AlbumBoundsTransform
            )
        }
    } else Modifier

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxSize(),
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = album.name,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            if (album.isPinned) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f))
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned album",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .then(titleSharedModifier)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${album.mediaCount} items",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
    }
}
