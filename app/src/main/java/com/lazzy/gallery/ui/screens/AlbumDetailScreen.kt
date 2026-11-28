package com.lazzy.gallery.ui.screens

import android.app.PendingIntent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lazzy.gallery.ui.components.MediaGridThumbnail
import com.lazzy.gallery.ui.viewmodel.MediaViewModel

private val AlbumBoundsTransform = BoundsTransform { _, _ ->
    tween(
        durationMillis = 300,
        easing = androidx.compose.animation.core.FastOutSlowInEasing
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun AlbumDetailScreen(
    albumName: String,
    viewModel: MediaViewModel,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onBack: () -> Unit,
    onMediaClick: (Long) -> Unit,
    onIntentSenderRequest: (PendingIntent) -> Unit
) {
    val mediaItems by viewModel.albumMediaItems.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    LaunchedEffect(albumName) {
        viewModel.loadAlbumMedia(albumName)
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val headerBackground = primaryColor.copy(alpha = 0.22f).compositeOver(MaterialTheme.colorScheme.background)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    val titleSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedBounds(
                                rememberSharedContentState(key = "album-title-$albumName"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = AlbumBoundsTransform
                            )
                        }
                    } else Modifier

                    Text(
                        albumName,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = titleSharedModifier
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            if (mediaItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No media in $albumName",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        items = mediaItems,
                        key = { it.id },
                        contentType = { if (it.isVideo) "video_thumbnail" else "photo_thumbnail" }
                    ) { item ->
                        val onClick = remember(item.id) { { viewModel.setCurrentViewedMediaId(item.id); onMediaClick(item.id) } }
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
