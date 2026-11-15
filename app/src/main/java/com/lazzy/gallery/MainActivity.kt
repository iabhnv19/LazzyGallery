package com.lazzy.gallery

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lazzy.gallery.data.MediaRepository
import com.lazzy.gallery.ui.components.FloatingNavigationBar
import com.lazzy.gallery.ui.navigation.Screen
import com.lazzy.gallery.ui.screens.AlbumDetailScreen
import com.lazzy.gallery.ui.screens.AlbumsScreen
import com.lazzy.gallery.ui.screens.FavoritesScreen
import com.lazzy.gallery.ui.screens.PhotoDetailScreen
import com.lazzy.gallery.ui.screens.RecentPhotosScreen
import com.lazzy.gallery.ui.screens.RecycleBinScreen
import com.lazzy.gallery.ui.screens.SearchScreen
import com.lazzy.gallery.ui.screens.SettingsScreen
import com.lazzy.gallery.ui.theme.GalleryTheme
import com.lazzy.gallery.ui.viewmodel.MediaViewModel

class MainActivity : ComponentActivity() {

    private var viewModel: MediaViewModel? = null
    private var isFirstResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainScreen(
                onViewModelReady = { vm -> viewModel = vm }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFirstResume) {
            isFirstResume = false
        } else {
            // Refresh after returning from another app.
            viewModel?.refreshOnResume()
        }
    }
}

private fun checkStoragePermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(
    onViewModelReady: (MediaViewModel) -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val context = LocalContext.current
    val repository = remember { MediaRepository(context) }
    val viewModel: MediaViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MediaViewModel(repository) as T
            }
        }
    )

    remember(viewModel) { onViewModelReady(viewModel) }

    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val useDynamicColor by viewModel.useDynamicColor.collectAsState()
    val accentColorIndex by viewModel.accentColorIndex.collectAsState()
    val defaultStartScreen by viewModel.defaultStartScreen.collectAsState()
    val rightButtonAction by viewModel.rightButtonAction.collectAsState()
    val startDestination = if (defaultStartScreen == Screen.Albums.route) Screen.Albums.route else Screen.Recent.route

    GalleryTheme(
        darkTheme = isDarkMode,
        dynamicColor = useDynamicColor,
        accentColorIndex = accentColorIndex
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.LaunchedEffect(isDarkMode) {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDarkMode
                controller.isAppearanceLightNavigationBars = !isDarkMode
            }
        }

        var hasPermission by remember { mutableStateOf(checkStoragePermissions(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.any { it } || checkStoragePermissions(context)
    }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.loadMedia()
            viewModel.loadTrashedMedia()
        }
    }

    val requestPermissions = {
        val permissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
            }
            else -> {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        permissionLauncher.launch(permissions)
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            requestPermissions()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            if (hasPermission) {
                SharedTransitionLayout {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        enterTransition = {
                            fadeIn(animationSpec = tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                        },
                        exitTransition = {
                            fadeOut(animationSpec = tween(320, easing = androidx.compose.animation.core.FastOutLinearInEasing))
                        },
                        popEnterTransition = {
                            fadeIn(animationSpec = tween(380, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                        },
                        popExitTransition = {
                            fadeOut(animationSpec = tween(320, easing = androidx.compose.animation.core.FastOutLinearInEasing))
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(Screen.Recent.route) {
                            RecentPhotosScreen(
                                viewModel = viewModel,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@composable,
                                onMediaClick = { id ->
                                    navController.navigate(Screen.Detail.createRoute(id, source = "recent"))
                                },
                                onIntentSenderRequest = { pendingIntent ->
                                    intentSenderLauncher.launch(
                                        IntentSenderRequest.Builder(pendingIntent).build()
                                    )
                                }
                            )
                        }
                        composable(Screen.Albums.route) {
                            AlbumsScreen(
                                viewModel = viewModel,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@composable,
                                onAlbumClick = { albumName ->
                                    navController.navigate(Screen.AlbumDetail.createRoute(albumName))
                                },
                                onNavigateToBin = {
                                    navController.navigate(Screen.Bin.route)
                                },
                                onNavigateToSettings = {
                                    navController.navigate(Screen.Settings.route)
                                },
                                onIntentSenderRequest = { pendingIntent ->
                                    intentSenderLauncher.launch(
                                        IntentSenderRequest.Builder(pendingIntent).build()
                                    )
                                }
                            )
                        }
                        composable(
                            route = Screen.AlbumDetail.route,
                            arguments = listOf(navArgument("albumName") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val albumName = backStackEntry.arguments?.getString("albumName") ?: return@composable
                            AlbumDetailScreen(
                                albumName = albumName,
                                viewModel = viewModel,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@composable,
                                onBack = { navController.popBackStack() },
                                onMediaClick = { id ->
                                    navController.navigate(Screen.Detail.createRoute(id, albumName = albumName, source = "album"))
                                },
                                onIntentSenderRequest = { pendingIntent ->
                                    intentSenderLauncher.launch(
                                        IntentSenderRequest.Builder(pendingIntent).build()
                                    )
                                }
                            )
                        }
                        composable(Screen.Bin.route) {
                            RecycleBinScreen(
                                viewModel = viewModel,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@composable,
                                onIntentSenderRequest = { pendingIntent ->
                                    intentSenderLauncher.launch(
                                        IntentSenderRequest.Builder(pendingIntent).build()
                                    )
                                }
                            )
                        }
                        composable(Screen.Search.route) {
                            SearchScreen(
                                viewModel = viewModel,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@composable,
                                onMediaClick = { id ->
                                    navController.navigate(Screen.Detail.createRoute(id, source = "recent"))
                                }
                            )
                        }
                        composable(Screen.Favorites.route) {
                            FavoritesScreen(
                                viewModel = viewModel,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@composable,
                                onMediaClick = { id ->
                                    navController.navigate(Screen.Detail.createRoute(id, source = "favorites"))
                                }
                            )
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = Screen.Detail.route,
                            arguments = listOf(
                                navArgument("mediaId") { type = NavType.LongType },
                                navArgument("albumName") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                },
                                navArgument("source") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                            ),
                            enterTransition = {
                                fadeIn(animationSpec = tween(280))
                            },
                            exitTransition = {
                                fadeOut(animationSpec = tween(240))
                            },
                            popEnterTransition = {
                                fadeIn(animationSpec = tween(280))
                            },
                            popExitTransition = {
                                fadeOut(animationSpec = tween(240))
                            }
                        ) { backStackEntry ->
                            val mediaId = backStackEntry.arguments?.getLong("mediaId") ?: return@composable
                            val albumName = backStackEntry.arguments?.getString("albumName") ?: ""
                            val source = backStackEntry.arguments?.getString("source") ?: ""
                            PhotoDetailScreen(
                                mediaId = mediaId,
                                albumName = albumName,
                                source = source,
                                viewModel = viewModel,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@composable,
                                onBack = { navController.popBackStack() },
                                onIntentSenderRequest = { pendingIntent ->
                                    intentSenderLauncher.launch(
                                        IntentSenderRequest.Builder(pendingIntent).build()
                                    )
                                }
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Permission Required",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Storage/Media permission is required to view your photos and videos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = requestPermissions) {
                        Text("Grant Permission")
                    }
                }
            }

            // Keep the navigation bar on root screens.
            val isTopLevel = currentRoute in listOf(
                Screen.Recent.route,
                Screen.Albums.route,
                Screen.Bin.route,
                Screen.Search.route,
                Screen.Favorites.route
            )
            AnimatedVisibility(
                visible = isTopLevel && hasPermission,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 300f)
                ) + fadeIn(animationSpec = tween(200)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 350f)
                ) + fadeOut(animationSpec = tween(150)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { clip = false }
            ) {
                FloatingNavigationBar(
                    currentRoute = currentRoute ?: startDestination,
                    rightButtonAction = rightButtonAction,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onRightButtonClick = {
                        when (rightButtonAction) {
                            "camera" -> {
                                try {
                                    val intent = android.content.Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(context, "Camera not available", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                            "file_manager" -> {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                                        type = "*/*"
                                        addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Open Files"))
                                } catch (_: Exception) {
                                    try {
                                        val fallback = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                            type = "resource/folder"
                                        }
                                        context.startActivity(fallback)
                                    } catch (_: Exception) {
                                        android.widget.Toast.makeText(context, "File manager not available", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }
  }
}
