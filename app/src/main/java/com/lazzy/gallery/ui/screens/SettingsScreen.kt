package com.lazzy.gallery.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import com.lazzy.gallery.ui.navigation.Screen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lazzy.gallery.ui.theme.AccentPalettes
import com.lazzy.gallery.ui.viewmodel.MediaViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MediaViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val useDynamicColor by viewModel.useDynamicColor.collectAsState()
    val accentColorIndex by viewModel.accentColorIndex.collectAsState()
    val libraryColumns by viewModel.libraryColumns.collectAsState()
    val albumColumns by viewModel.albumColumns.collectAsState()
    val autoPlayVideo by viewModel.autoPlayVideo.collectAsState()
    val loopVideo by viewModel.loopVideo.collectAsState()
    val animateGifsThumbnail by viewModel.animateGifsThumbnail.collectAsState()
    val dateTimeFormat by viewModel.dateTimeFormat.collectAsState()
    val showHiddenItems by viewModel.showHiddenItems.collectAsState()
    val defaultStartScreen by viewModel.defaultStartScreen.collectAsState()

    var cacheSizeBytes by remember {
        mutableLongStateOf(getDirectorySize(context.cacheDir))
    }
    val cacheSizeText = remember(cacheSizeBytes) { formatSize(cacheSizeBytes) }

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
                        "Settings",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp, horizontal = 16.dp)
            ) {
                SettingsCategoryCard(title = "Appearance") {
                    ListItem(
                        headlineContent = { Text("Dark Theme") },
                        supportingContent = { Text("Always use dark theme for the gallery") },
                        leadingContent = {
                            Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { checked ->
                                    viewModel.setDarkMode(checked)
                                    Toast.makeText(context, "Theme updated", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )

                    ListItem(
                        headlineContent = { Text("Dynamic Color") },
                        supportingContent = { Text("Use Material You dynamic color palette") },
                        leadingContent = {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Switch(
                                checked = useDynamicColor,
                                onCheckedChange = { checked ->
                                    viewModel.setDynamicColor(checked)
                                    Toast.makeText(context, "Dynamic color updated", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )

                    if (!useDynamicColor) {
                        ListItem(
                            headlineContent = { Text("Accent Color") },
                            supportingContent = {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    Text(
                                        text = "Selected: ${AccentPalettes.getOrElse(accentColorIndex) { AccentPalettes[0] }.name}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        AccentPalettes.forEachIndexed { index, palette ->
                                            val selected = index == accentColorIndex
                                            val color = if (isDarkMode) palette.darkPrimary else palette.lightPrimary
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .clickable { viewModel.setAccentColorIndex(index) }
                                                    .then(
                                                        if (selected) {
                                                            Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                                        } else {
                                                            Modifier
                                                        }
                                                    )
                                            )
                                        }
                                    }
                                }
                            },
                            leadingContent = {
                                Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                        )
                    }
                }

                SettingsCategoryCard(title = "Grid Layout") {
                    ListItem(
                        headlineContent = { Text("Library Grid Columns: $libraryColumns") },
                        supportingContent = {
                            Slider(
                                value = libraryColumns.toFloat(),
                                onValueChange = { viewModel.setLibraryColumns(it.toInt()) },
                                valueRange = 2f..5f,
                                steps = 2
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.GridView, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )

                    ListItem(
                        headlineContent = { Text("Album Grid Columns: $albumColumns") },
                        supportingContent = {
                            Slider(
                                value = albumColumns.toFloat(),
                                onValueChange = { viewModel.setAlbumColumns(it.toInt()) },
                                valueRange = 1f..3f,
                                steps = 1
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.ViewAgenda, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )

                    ListItem(
                        headlineContent = { Text("Default Opening Screen") },
                        supportingContent = {
                            Text(if (defaultStartScreen == Screen.Albums.route) "Albums Folder View" else "Recent Photos View")
                        },
                        leadingContent = {
                            Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable {
                            val next = if (defaultStartScreen == Screen.Albums.route) Screen.Recent.route else Screen.Albums.route
                            viewModel.setDefaultStartScreen(next)
                            Toast.makeText(context, "Default start screen updated", Toast.LENGTH_SHORT).show()
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                }

                SettingsCategoryCard(title = "Playback & Media") {
                    ListItem(
                        headlineContent = { Text("Play Video Automatically") },
                        supportingContent = { Text("Start playing videos instantly when opened") },
                        leadingContent = {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Switch(
                                checked = autoPlayVideo,
                                onCheckedChange = { checked ->
                                    viewModel.setAutoPlayVideo(checked)
                                    Toast.makeText(context, "Auto-play updated", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )

                    ListItem(
                        headlineContent = { Text("Loop Video") },
                        supportingContent = { Text("Continuously repeat video playback") },
                        leadingContent = {
                            Icon(Icons.Default.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Switch(
                                checked = loopVideo,
                                onCheckedChange = { checked ->
                                    viewModel.setLoopVideo(checked)
                                    Toast.makeText(context, "Loop setting updated", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )

                    ListItem(
                        headlineContent = { Text("Animate GIFs at Thumbnail") },
                        supportingContent = { Text("Play GIF animations in grid view") },
                        leadingContent = {
                            Icon(Icons.Default.Animation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Switch(
                                checked = animateGifsThumbnail,
                                onCheckedChange = { checked ->
                                    viewModel.setAnimateGifsThumbnail(checked)
                                    Toast.makeText(context, "Thumbnail animation updated", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )

                    ListItem(
                        headlineContent = { Text("Show Hidden Items") },
                        supportingContent = { Text("Include hidden media files in library") },
                        leadingContent = {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Switch(
                                checked = showHiddenItems,
                                onCheckedChange = { checked ->
                                    viewModel.setShowHiddenItems(checked)
                                    Toast.makeText(context, "Hidden items visibility updated", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )

                    val formats = listOf("MMM dd, yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy")
                    ListItem(
                        headlineContent = { Text("Date & Time Format") },
                        supportingContent = { Text("Current: $dateTimeFormat (Tap to change)") },
                        leadingContent = {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable {
                            val currentIndex = formats.indexOf(dateTimeFormat)
                            val nextIndex = if (currentIndex < 0 || currentIndex >= formats.size - 1) 0 else currentIndex + 1
                            val nextFormat = formats[nextIndex]
                            viewModel.setDateTimeFormat(nextFormat)
                            Toast.makeText(context, "Date format set to $nextFormat", Toast.LENGTH_SHORT).show()
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                }

                SettingsCategoryCard(title = "Storage & Cache") {
                    ListItem(
                        headlineContent = { Text("Clear Thumbnail Cache") },
                        supportingContent = { Text("Free up storage space ($cacheSizeText used)") },
                        leadingContent = {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable {
                            try {
                                context.cacheDir.deleteRecursively()
                                cacheSizeBytes = getDirectorySize(context.cacheDir)
                                viewModel.loadMedia()
                                Toast.makeText(context, "Cache cleared & display refreshed", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "Failed to clear cache", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )

                    ListItem(
                        headlineContent = { Text("Rescan Media Library") },
                        supportingContent = { Text("Force refresh MediaStore and local database") },
                        leadingContent = {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable {
                            viewModel.loadMedia()
                            Toast.makeText(context, "Rescanning media library...", Toast.LENGTH_SHORT).show()
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                }

                SettingsCategoryCard(title = "About") {
                    ListItem(
                        headlineContent = { Text("Lazzy Gallery") },
                        supportingContent = { Text("Version 1.0.0 (Stable)") },
                        leadingContent = {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )

                    ListItem(
                        headlineContent = { Text("Developer") },
                        supportingContent = { Text("iabhnv19 (GitHub)") },
                        leadingContent = {
                            Icon(Icons.Default.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iabhnv19"))
                            context.startActivity(intent)
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )

                    ListItem(
                        headlineContent = { Text("Open Source License") },
                        supportingContent = { Text("Apache License 2.0") },
                        leadingContent = {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.apache.org/licenses/LICENSE-2.0"))
                            context.startActivity(intent)
                        },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCategoryCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val cardContainerColor = primaryColor.copy(alpha = 0.12f)
        .compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                color = primaryColor,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
        )
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardContainerColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                content = content
            )
        }
    }
}

private fun getDirectorySize(file: File): Long {
    var size: Long = 0
    if (file.isDirectory) {
        file.listFiles()?.forEach { child ->
            size += getDirectorySize(child)
        }
    } else {
        size = file.length()
    }
    return size
}

private fun formatSize(size: Long): String {
    val kb = size / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.2f KB", kb)
        else -> "$size B"
    }
}
