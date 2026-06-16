package com.dragobb.iptv

import android.app.Application
import android.content.res.Configuration
import android.app.PictureInPictureParams
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.dragobb.iptv.data.IptvRepository
import com.dragobb.iptv.data.local.AppDatabase
import com.dragobb.iptv.ui.components.VideoPlayer
import com.dragobb.iptv.ui.screens.FavoritesScreen
import com.dragobb.iptv.ui.screens.HomeScreen
import com.dragobb.iptv.ui.screens.SettingsScreen
import com.dragobb.iptv.ui.theme.IPTVTheme
import com.dragobb.iptv.ui.viewmodels.IptvUiState
import com.dragobb.iptv.ui.viewmodels.IptvViewModel
import com.dragobb.iptv.ui.viewmodels.IptvViewModelFactory
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Global Premium Colors
val NeonPurple = Color(0xFF8E5AFF)
val DeepBlack = Color(0xFF080808)
val DarkSurface = Color(0xFF121212)

class MainActivity : ComponentActivity() {
    private var isCurrentlyPlaying by mutableStateOf(value = false)
    private var isSystemPiP by mutableStateOf(false)

    @OptIn(FlowPreview::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val imageLoader = ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .allowHardware(true)
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val appContext = context.applicationContext
            val database = remember { AppDatabase.getDatabase(appContext) }
            val repository = remember { IptvRepository(appContext, database.channelDao()) }

            val viewModel: IptvViewModel = viewModel(
                factory = IptvViewModelFactory(
                    application = appContext as Application,
                    repository = repository,
                    favoritesDao = database.favoritesDao(),
                    recentChannelDao = database.recentChannelDao(),
                    playlistDao = database.playlistDao(),
                    searchHistoryDao = database.searchHistoryDao(),
                )
            )

            val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
            SideEffect { isCurrentlyPlaying = selectedChannel != null }

            IPTVTheme {
                IPTVApp(
                    viewModel = viewModel,
                    isInSystemPiP = isSystemPiP,
                    onEnterPiP = { enterPiPMode() }
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isSystemPiP = isInPictureInPictureMode
    }

    private fun enterPiPMode() {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
        enterPictureInPictureMode(params)
    }
}

@OptIn(FlowPreview::class, UnstableApi::class)
@Composable
fun IPTVApp(
    viewModel: IptvViewModel,
    isInSystemPiP: Boolean,
    onEnterPiP: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
    val favoriteChannels by viewModel.favoriteChannels.collectAsStateWithLifecycle()
    val recentChannels by viewModel.recentChannels.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isPlayerMinimized by viewModel.isPlayerMinimized.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val isBuffering by viewModel.isBuffering.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    BackHandler(enabled = selectedChannel != null) {
        viewModel.selectChannel(null)
    }

    // Unified Global Error Dialog
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("System Alert", fontWeight = FontWeight.Bold) },
            text = { Text(errorMessage!!) },
            confirmButton = {
                Button(
                    onClick = { 
                        viewModel.clearError()
                        if (selectedChannel != null) viewModel.selectChannel(null)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("OK", color = Color.White) }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (isInSystemPiP && (selectedChannel != null)) {
        VideoPlayer(
            exoPlayer = viewModel.exoPlayer,
            channelName = selectedChannel?.name ?: "Unknown Channel",
            isBuffering = isBuffering,
            onBack = { viewModel.selectChannel(null) },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Scaffold(
            containerColor = DeepBlack,
            bottomBar = {
                NavigationBar(
                    containerColor = DeepBlack,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .border(0.5.dp, Color.White.copy(0.05f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                ) {
                    AppDestinations.entries.forEach { destination ->
                        val isSelected = currentDestination == destination
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentDestination = destination
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                    tint = if (isSelected) NeonPurple else Color.Gray
                                )
                            },
                            label = {
                                Text(
                                    text = destination.label,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = NeonPurple.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }
        ) { padding ->
            // Main Content Area with Neon Aura Background
            Box(modifier = Modifier.fillMaxSize().background(DeepBlack)) {
                // Background Gradient Aura
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
                        .background(
                            Brush.radialGradient(
                                colors = listOf(NeonPurple.copy(alpha = 0.08f), Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(0f, 0f),
                                radius = 1500f
                            )
                        )
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    when (val state = uiState) {
                        is IptvUiState.Loading -> HomeScreen(
                            isLoading = true,
                            channels = emptyList(),
                            recentChannels = emptyList(),
                            country = "Loading...",
                            selectedCategory = selectedCategory,
                            searchQuery = searchQuery,
                            viewMode = viewMode,
                            searchHistory = searchHistory,
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            onClearSearchHistory = { viewModel.clearSearchHistory() },
                            onDeleteSearchItem = { viewModel.deleteSearchItem(it) },
                            onChannelClick = {},
                            onToggleFavorite = {},
                            onRefresh = { viewModel.refreshChannels() },
                            onMenuClick = { /* No longer needed */ }
                        )
                        is IptvUiState.Success -> {
                            when (currentDestination) {
                                AppDestinations.HOME, AppDestinations.EXPLORE -> HomeScreen(
                                    isLoading = false,
                                    channels = state.channels,
                                    recentChannels = recentChannels,
                                    favoriteChannels = favoriteChannels, // Pass favorites
                                    country = state.country,
                                    selectedCategory = selectedCategory,
                                    searchQuery = searchQuery,
                                    viewMode = viewMode,
                                    searchHistory = searchHistory,
                                    categories = state.categories,
                                    onCategoryClick = { viewModel.setCategory(it) },
                                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                    onClearSearchHistory = { viewModel.clearSearchHistory() },
                                    onDeleteSearchItem = { viewModel.deleteSearchItem(it) },
                                    onChannelClick = { viewModel.selectChannel(it) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onRefresh = { viewModel.refreshChannels() },
                                    onMenuClick = { /* No longer needed */ },
                                    isExploreMode = currentDestination == AppDestinations.EXPLORE
                                )
                                AppDestinations.FAVORITES -> FavoritesScreen(favoriteChannels, {viewModel.selectChannel(it)}, {viewModel.toggleFavorite(it)}, {})
                                AppDestinations.SETTINGS -> SettingsScreen(viewModel) {}
                            }
                        }
                    }
                }

                if (selectedChannel != null) {
                    if (!isPlayerMinimized) {
                        VideoPlayer(
                            exoPlayer = viewModel.exoPlayer,
                            channelName = selectedChannel?.name ?: "Unknown Channel",
                            isBuffering = isBuffering,
                            onBack = { viewModel.selectChannel(null) },
                            onMiniPlayer = onEnterPiP,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val config = LocalConfiguration.current
                        val density = LocalDensity.current
                        val screenW = with(density) { config.screenWidthDp.dp.toPx() }
                        val screenH = with(density) { config.screenHeightDp.dp.toPx() }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .navigationBarsPadding()
                                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                                .padding(16.dp)
                                .width(220.dp)
                                .height(124.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black)
                                .border(1.dp, NeonPurple.copy(0.3f), RoundedCornerShape(24.dp))
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        offsetX = (offsetX + dragAmount.x).coerceIn(-screenW + 300f, 0f)
                                        offsetY = (offsetY + dragAmount.y).coerceIn(-screenH + 400f, 0f)
                                    }
                                }
                                .clickable { viewModel.setPlayerMinimized(false) }
                        ) {
                            VideoPlayer(
                                exoPlayer = viewModel.exoPlayer,
                                channelName = selectedChannel?.name ?: "Unknown Channel",
                                isBuffering = isBuffering,
                                isMinimized = true,
                                onBack = { viewModel.selectChannel(null) },
                                onClose = { viewModel.selectChannel(null) },
                                onExpand = { viewModel.setPlayerMinimized(false) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home), 
    EXPLORE("Explore", Icons.Default.Explore),
    FAVORITES("Favorites", Icons.Default.Favorite), 
    SETTINGS("Settings", Icons.Default.Settings)
}
