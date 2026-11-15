package com.lazzy.gallery.ui.screens

import android.app.PendingIntent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lazzy.gallery.data.MediaModel
import com.lazzy.gallery.ui.components.MediaGridThumbnail
import com.lazzy.gallery.ui.viewmodel.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun RecycleBinScreen(
    viewModel: MediaViewModel,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onIntentSenderRequest: (PendingIntent) -> Unit
) {
    val trashedItems by viewModel.trashedItems.collectAsState()
    var selectedItem by remember { mutableStateOf<MediaModel?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadTrashedMedia()
    }

    if (selectedItem != null) {
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color(0xFF141418),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f),
            title = {
                Text(
                    text = "Manage Trashed Item",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "Do you want to restore this item or delete it forever?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedItem?.let {
                            val intent = viewModel.restoreMedia(listOf(it.uri))
                            intent?.let { pendingIntent ->
                                onIntentSenderRequest(pendingIntent)
                            }
                        }
                        selectedItem = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Restore", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedItem?.let {
                            val intent = viewModel.deleteMediaPermanently(listOf(it.uri))
                            intent?.let { pendingIntent ->
                                onIntentSenderRequest(pendingIntent)
                            }
                        }
                        selectedItem = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete Forever", fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
    }

    val useDynamicColor by viewModel.useDynamicColor.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val headerBackground = if (useDynamicColor) {
        androidx.compose.ui.graphics.Color(0xFF0D0E12)
    } else {
        primaryColor.copy(alpha = 0.22f).compositeOver(MaterialTheme.colorScheme.background)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Trash",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    ) 
                },
                actions = {
                    if (trashedItems.isNotEmpty()) {
                        IconButton(onClick = {
                            val uris = trashedItems.map { it.uri }
                            val intent = viewModel.deleteMediaPermanently(uris)
                            intent?.let { onIntentSenderRequest(it) }
                        }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Empty Bin")
                        }
                    }
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
            if (trashedItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Nothing in Trash",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else {
                val gridState = rememberLazyGridState()
                val isScrolling by remember { derivedStateOf { gridState.isScrollInProgress } }
                val currentViewedId by viewModel.currentViewedMediaId.collectAsState()

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(
                        top = 16.dp,
                        bottom = 140.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = trashedItems,
                        key = { it.id },
                        contentType = { if (it.isVideo) "video_thumbnail" else "photo_thumbnail" }
                    ) { item ->
                        val onClick = remember(item.id) { { selectedItem = item } }
                        MediaGridThumbnail(
                            media = item,
                            isScrolling = isScrolling,
                            isTargetSharedItem = currentViewedId == item.id,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = onClick
                        )
                    }
                }
            }
        }
    }
}
