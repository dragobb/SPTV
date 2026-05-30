package com.dragobb.iptv

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
class MainActivity : ComponentActivity() {
    private var isCurrentlyPlaying by mutableStateOf(false)
    private var isSystemPiP by mutableStateOf(false)

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
            val repository = remember { IptvRepository(context) }

            val viewModel: IptvViewModel = viewModel(
                factory = IptvViewModelFactory(
                    repository = repository,
                    favoritesDao = database.favoritesDao(),
                    recentChannelDao = database.recentChannelDao()
                )
            )

            val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
            
            // Sync current state to activity for PiP trigger
            SideEffect {
                isCurrentlyPlaying = selectedChannel != null
            }

            IPTVTheme {
                IPTVApp(viewModel, isSystemPiP)
            }
        }
    }

    override fun onUserLeaveHint() {
        if (isCurrentlyPlaying) {
            enterPiPMode()
        }
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

@Composable
fun IPTVApp(viewModel: IptvViewModel, isInSystemPiP: Boolean) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedChannel by viewModel.selectedChannel.collectAsStateWithLifecycle()
    val favoriteChannels by viewModel.favoriteChannels.collectAsStateWithLifecycle()
    val recentChannels by viewModel.recentChannels.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isPlayerMinimized by viewModel.isPlayerMinimized.collectAsStateWithLifecycle()
    
    var currentDestination by remember { mutableStateOf(AppDestinations.HOME) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    var isCategoriesExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedChannel != null) {
        if (isPlayerMinimized) {
            viewModel.selectChannel(null)
        } else {
            viewModel.setPlayerMinimized(true)
        }
    }

    if (isInSystemPiP && selectedChannel != null) {
        VideoPlayer(
            channel = selectedChannel!!,
            onBack = { viewModel.selectChannel(null) },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = selectedChannel == null || isPlayerMinimized,
            drawerContent = {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(300.dp)
                        .background(Color.Black.copy(alpha = 0.95f))
                        .border(1.dp, Color.White.copy(0.1f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            "STAIRPLAY TV",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Spacer(Modifier.height(40.dp))
                        
                        AppDestinations.entries.forEach { destination ->
                            NavigationItem(
                                label = destination.label,
                                icon = destination.icon,
                                isSelected = currentDestination == destination,
                                onClick = {
                                    currentDestination = destination
                                    scope.launch { drawerState.close() }
                                }
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(0.05f))
                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isCategoriesExpanded = !isCategoriesExpanded }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Category, null, tint = Color.Gray)
                            Spacer(Modifier.width(12.dp))
                            Text("Categories", modifier = Modifier.weight(1f), color = Color.White)
                            Icon(
                                imageVector = if (isCategoriesExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color.Gray
                            )
                        }

                        AnimatedVisibility(
                            visible = isCategoriesExpanded,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(start = 36.dp, top = 8.dp)) {
                                if (uiState is IptvUiState.Success) {
                                    (uiState as IptvUiState.Success).categories.forEach { category ->
                                        Text(
                                            text = category,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.setCategory(category)
                                                    currentDestination = AppDestinations.HOME
                                                    scope.launch { drawerState.close() }
                                                }
                                                .padding(vertical = 10.dp),
                                            color = if (selectedCategory == category) MaterialTheme.colorScheme.primary else Color.LightGray,
                                            fontWeight = if (selectedCategory == category) FontWeight.Bold else FontWeight.Normal
                                        )
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
                        is IptvUiState.Loading -> {
                            HomeScreen(
                                isLoading = true,
                                channels = emptyList(),
                                recentChannels = emptyList(),
                                country = "Loading...",
                                selectedCategory = selectedCategory,
                                searchQuery = searchQuery,
                                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                onChannelClick = {},
                                onToggleFavorite = {},
                                onRefresh = { viewModel.loadChannels() },
                                onMenuClick = { scope.launch { drawerState.open() } }
                            )
                        }

                        is IptvUiState.Success -> {
                            when (currentDestination) {
                                AppDestinations.HOME -> HomeScreen(
                                    isLoading = false,
                                    channels = state.channels,
                                    recentChannels = recentChannels,
                                    country = state.country,
                                    selectedCategory = selectedCategory,
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                                    onChannelClick = { viewModel.selectChannel(it) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onRefresh = { viewModel.loadChannels() },
                                    onMenuClick = { scope.launch { drawerState.open() } }
                                )

                                AppDestinations.FAVORITES -> FavoritesScreen(
                                    favoriteChannels = favoriteChannels,
                                    onChannelClick = { viewModel.selectChannel(it) },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onMenuClick = { scope.launch { drawerState.open() } }
                                )

                                AppDestinations.SETTINGS -> SettingsScreen(
                                    viewModel = viewModel,
                                    onMenuClick = { scope.launch { drawerState.open() } }
                                )
                            }
                        }

                        is IptvUiState.Error -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Error: ${state.message}",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                // Main Player Logic
                if (selectedChannel != null) {
                    if (!isPlayerMinimized) {
                        // Full Screen Player
                        VideoPlayer(
                            channel = selectedChannel!!,
                            onBack = { viewModel.setPlayerMinimized(true) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Minimized Player (Floating)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .width(200.dp)
                                .height(112.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .clickable { viewModel.setPlayerMinimized(false) }
                        ) {
                            VideoPlayer(
                                channel = selectedChannel!!,
                                onBack = { viewModel.selectChannel(null) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            } // End Box (Main Content)
        } // End ModalNavigationDrawer
    } // End PiP Else block
} // End IPTVApp Function

@Composable
fun NavigationItem(label: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
            )
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
            )
        }
    }
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    FAVORITES("Favorites", Icons.Default.Favorite),
    SETTINGS("Settings", Icons.Default.Settings),
}
