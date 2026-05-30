package com.dragobb.iptv

import android.app.Application
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

class MainActivity : ComponentActivity() {
    private var isCurrentlyPlaying by mutableStateOf(false)
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
            val database = remember { AppDatabase.getDatabase(context) }
            val repository = remember { IptvRepository(context, database.channelDao()) }

            val viewModel: IptvViewModel = viewModel(
                factory = IptvViewModelFactory(
                    application = context.applicationContext as Application,
                    repository = repository,
                    favoritesDao = database.favoritesDao(),
                    recentChannelDao = database.recentChannelDao(),
                    playlistDao = database.playlistDao()
                )
            )

            val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
            SideEffect { isCurrentlyPlaying = selectedChannel != null }

            IPTVTheme {
                IPTVApp(viewModel, isSystemPiP)
            }
        }
    }

    override fun onUserLeaveHint() {
        if (isCurrentlyPlaying) enterPiPMode()
        super.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isSystemPiP = isInPictureInPictureMode
    }

    private fun enterPiPMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }
}

@OptIn(FlowPreview::class, UnstableApi::class)
@Composable
fun IPTVApp(viewModel: IptvViewModel, isInSystemPiP: Boolean) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
    val favoriteChannels by viewModel.favoriteChannels.collectAsStateWithLifecycle()
    val recentChannels by viewModel.recentChannels.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isPlayerMinimized by viewModel.isPlayerMinimized.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val isBuffering by viewModel.isBuffering.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var isCategoriesExpanded by remember { mutableStateOf(false) }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    BackHandler(enabled = selectedChannel != null) {
        if (isPlayerMinimized) viewModel.selectChannel(null)
        else viewModel.setPlayerMinimized(true)
    }

    if (isInSystemPiP && selectedChannel != null) {
        VideoPlayer(
            exoPlayer = viewModel.exoPlayer,
            isBuffering = isBuffering,
            errorMessage = errorMessage,
            onBack = { viewModel.selectChannel(null) },
            onClearError = { viewModel.clearError() },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = selectedChannel == null || isPlayerMinimized,
            drawerContent = {
                Box(modifier = Modifier.fillMaxHeight().width(300.dp).background(Color.Black.copy(alpha = 0.96f)).border(1.dp, Color.White.copy(0.05f))) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
                        Spacer(Modifier.height(48.dp))
                        Text("STAIRPLAY TV", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 2.sp), color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(40.dp))

                        AppDestinations.entries.forEach { destination ->
                            NavigationItem(destination.label, destination.icon, currentDestination == destination) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentDestination = destination
                                scope.launch { drawerState.close() }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(0.05f))
                        Spacer(Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isCategoriesExpanded = !isCategoriesExpanded
                        }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Category, null, tint = Color.Gray)
                            Spacer(Modifier.width(12.dp))
                            Text("Categories", modifier = Modifier.weight(1f), color = Color.White)
                            Icon(if (isCategoriesExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Gray)
                        }

                        AnimatedVisibility(visible = isCategoriesExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                            Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp)) {
                                if (uiState is IptvUiState.Success) {
                                    (uiState as IptvUiState.Success).categories.forEach { category ->
                                        CategoryItem(category, selectedCategory == category) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            viewModel.setCategory(category)
                                            currentDestination = AppDestinations.HOME
                                            scope.launch { drawerState.close() }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
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
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            onChannelClick = {},
                            onToggleFavorite = {},
                            onRefresh = { viewModel.refreshChannels() },
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onCheckHealth = {}
                        )
                        is IptvUiState.Success -> {
                            when (currentDestination) {
                                AppDestinations.HOME -> HomeScreen(
                                    isLoading = false,
                                    channels = state.channels,
                                    recentChannels = recentChannels,
                                    country = state.country,
                                    selectedCategory = selectedCategory,
                                    searchQuery = searchQuery,
                                    viewMode = viewMode,
                                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                    onChannelClick = { viewModel.selectChannel(it) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onRefresh = { viewModel.refreshChannels() },
                                    onMenuClick = { scope.launch { drawerState.open() } },
                                    onCheckHealth = { viewModel.checkStreamHealth(it) }
                                )
                                AppDestinations.FAVORITES -> FavoritesScreen(favoriteChannels, {viewModel.selectChannel(it)}, {viewModel.toggleFavorite(it)}, {scope.launch { drawerState.open() }})
                                AppDestinations.SETTINGS -> SettingsScreen(viewModel, {scope.launch { drawerState.open() }})
                            }
                        }
                        is IptvUiState.Error -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                if (selectedChannel != null) {
                    if (!isPlayerMinimized) {
                        VideoPlayer(
                            exoPlayer = viewModel.exoPlayer,
                            isBuffering = isBuffering,
                            errorMessage = errorMessage,
                            onBack = { viewModel.setPlayerMinimized(true) },
                            onClearError = { viewModel.clearError() },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val config = LocalConfiguration.current
                        val density = LocalDensity.current
                        val screenW = with(density) { config.screenWidthDp.dp.toPx() }
                        val screenH = with(density) { config.screenHeightDp.dp.toPx() }

                        Box(
                            modifier = Modifier.offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                                .padding(16.dp).width(220.dp).height(124.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black).border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(16.dp))
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        offsetX = (offsetX + dragAmount.x).coerceIn(-screenW + 300f, 0f)
                                        offsetY = (offsetY + dragAmount.y).coerceIn(-screenH + 400f, 0f)
                                    }
                                }
                                .clickable { viewModel.setPlayerMinimized(false) }.align(Alignment.BottomEnd)
                        ) {
                            VideoPlayer(
                                exoPlayer = viewModel.exoPlayer,
                                isBuffering = isBuffering,
                                errorMessage = errorMessage,
                                isMinimized = true,
                                onBack = { viewModel.setPlayerMinimized(false) },
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

@Composable
fun CategoryItem(category: String, isSelected: Boolean, onClick: () -> Unit) {
    val icon = remember(category) {
        when {
            category.contains("Movie", true) -> Icons.Default.Movie
            category.contains("Sport", true) -> Icons.Default.SportsEsports
            category.contains("News", true) -> Icons.Default.Newspaper
            category.contains("Philippines", true) -> Icons.Default.Flag
            else -> Icons.Default.Tv
        }
    }
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(category, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray, fontSize = 14.sp)
    }
}

@Composable
fun NavigationItem(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent, shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray)
            Spacer(Modifier.width(12.dp))
            Text(label, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home), FAVORITES("Favorites", Icons.Default.Favorite), SETTINGS("Settings", Icons.Default.Settings)
}
