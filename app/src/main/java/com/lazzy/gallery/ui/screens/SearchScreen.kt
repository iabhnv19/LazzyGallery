package com.lazzy.gallery.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lazzy.gallery.ui.components.MediaGridThumbnail
import com.lazzy.gallery.ui.viewmodel.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SearchScreen(
    viewModel: MediaViewModel,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    onMediaClick: (Long) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val mediaItems by viewModel.mediaItems.collectAsState()
    val ocrStatus by viewModel.ocrStatus.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadOcrStatus()
    }

    val isSearchActiveOrNotEmpty = isSearchActive || searchQuery.isNotEmpty()

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    val filteredItems = remember(searchQuery, mediaItems) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            mediaItems.filter { item ->
                item.displayName.contains(searchQuery, ignoreCase = true) ||
                        item.albumName.contains(searchQuery, ignoreCase = true) ||
                        item.mimeType.contains(searchQuery, ignoreCase = true) ||
                        (item.ocrText?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    val useDynamicColor by viewModel.useDynamicColor.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val headerBackground = if (useDynamicColor) {
        androidx.compose.ui.graphics.Color(0xFF0D0E12)
    } else {
        primaryColor.copy(alpha = 0.22f).compositeOver(MaterialTheme.colorScheme.background)
    }

    val animatedHorizontalPadding by animateDpAsState(
        targetValue = if (isSearchActiveOrNotEmpty) 8.dp else 16.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 300f),
        label = "HorizontalPaddingAnim"
    )

    val animatedTopPadding by animateDpAsState(
        targetValue = if (isSearchActiveOrNotEmpty) 4.dp else 16.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 300f),
        label = "TopPaddingAnim"
    )

    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isSearchActiveOrNotEmpty) 16.dp else 28.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 300f),
        label = "CornerRadiusAnim"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Search",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
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
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = animatedHorizontalPadding)
                        .padding(top = animatedTopPadding)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (it.isNotEmpty()) isSearchActive = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    isSearchActive = true
                                }
                            },
                        placeholder = { Text("Search photos, text and more...") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search"
                            )
                        },
                        trailingIcon = {
                            if (isSearchActiveOrNotEmpty) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    isSearchActive = false
                                    focusManager.clearFocus()
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear and close search"
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(animatedCornerRadius),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    if (searchQuery.isBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Search your media",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Find photos, videos, albums, or text inside photos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Photo Text Search (OCR)",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (ocrStatus.isScanning) {
                                        "Scanning photos for text: ${ocrStatus.indexedCount} / ${ocrStatus.totalCount}"
                                    } else if (ocrStatus.indexedCount > 0) {
                                        "${ocrStatus.indexedCount} photos indexed for text search"
                                    } else {
                                        "Scan images to search text inside screenshots, documents & receipts"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )

                                if (ocrStatus.isScanning) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    LinearProgressIndicator(
                                        progress = {
                                            if (ocrStatus.totalCount > 0) ocrStatus.indexedCount.toFloat() / ocrStatus.totalCount else 0f
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { viewModel.startOcrIndexing() },
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(if (ocrStatus.indexedCount > 0) "Re-index" else "Index Photos")
                                        }
                                        if (ocrStatus.indexedCount > 0) {
                                            OutlinedButton(
                                                onClick = { viewModel.clearOcrIndexing() },
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Clear Data")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (filteredItems.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No results found for \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val gridState = rememberLazyGridState()
                    val currentViewedId by viewModel.currentViewedMediaId.collectAsState()

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = 140.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = filteredItems,
                            key = { it.id },
                            contentType = { if (it.isVideo) "video_thumbnail" else "photo_thumbnail" }
                        ) { item ->
                            val onClick = remember(item.id) {
                                {
                                    viewModel.setCurrentViewedMediaId(item.id)
                                    onMediaClick(item.id)
                                }
                            }
                            MediaGridThumbnail(
                                media = item,
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
    }
}
