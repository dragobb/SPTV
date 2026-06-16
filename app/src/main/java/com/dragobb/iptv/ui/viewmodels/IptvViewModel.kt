package com.dragobb.iptv.ui.viewmodels

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.dragobb.iptv.data.IptvRepository
import com.dragobb.iptv.data.local.*
import com.dragobb.iptv.ui.models.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

sealed class IptvUiState {
    object Loading : IptvUiState()
    data class Success(val channels: List<Channel>, val country: String, val categories: List<String>) : IptvUiState()
}

enum class ViewMode { GRID, LIST }

@OptIn(UnstableApi::class)
class IptvViewModel(
    application: Application,
    private val repository: IptvRepository,
    private val favoritesDao: FavoritesDao,
    private val recentChannelDao: RecentChannelDao,
    private val playlistDao: PlaylistDao,
    private val searchHistoryDao: SearchHistoryDao,
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("iptv_settings", Context.MODE_PRIVATE)

    private val _isLoading = MutableStateFlow(value = true)
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering = _isBuffering.asStateFlow()

    private val _selectedChannel = MutableStateFlow<Channel?>(null)
    val selectedChannel = _selectedChannel.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isPlayerMinimized = MutableStateFlow(false)
    val isPlayerMinimized = _isPlayerMinimized.asStateFlow()

    // Persistent Settings
    val isSafeMode = MutableStateFlow(prefs.getBoolean("is_safe_mode", true))
    val manualCountryOverride = MutableStateFlow(prefs.getString("manual_country", null))
    val isHardwareAcceleration = MutableStateFlow(prefs.getBoolean("is_hardware_accel", true))
    val isBackgroundPlay = MutableStateFlow(prefs.getBoolean("is_bg_play", false))
    val viewMode = MutableStateFlow(
        try {
            ViewMode.valueOf(prefs.getString("view_mode", ViewMode.GRID.name) ?: ViewMode.GRID.name)
        } catch (e: Exception) {
            ViewMode.GRID
        }
    )
    val savedPin = MutableStateFlow(prefs.getString("saved_pin", "1234") ?: "1234")

    // Search History Flow
    val searchHistory: StateFlow<List<String>> = searchHistoryDao.getRecentSearches()
        .map { searches -> searches.map { it.query } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Database-backed Playlists
    val customPlaylists: StateFlow<List<String>> = playlistDao.getAllPlaylists()
        .map { list -> list.map { it.url } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application).apply {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
        setMediaSourceFactory(DefaultMediaSourceFactory(application).setDataSourceFactory(dataSourceFactory))
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        setHandleAudioBecomingNoisy(true)
    }.build().apply {
        playWhenReady = true
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING || (playbackState == Player.STATE_IDLE && _selectedChannel.value != null)
            }

            override fun onPlayerError(error: PlaybackException) {
                _isBuffering.value = false
                _errorMessage.value = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Server Error: Link expired or blocked."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network connection failed."
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Codec Error: Format not supported."
                    else -> "Streaming Error: ${error.localizedMessage ?: "Offline"}"
                }
            }
        })
    }

    val favoriteChannels: StateFlow<List<Channel>> = favoritesDao.getAllFavorites()
        .map { favorites -> favorites.map { it.toChannel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentChannels: StateFlow<List<Channel>> = recentChannelDao.getRecentChannels()
        .map { recents -> recents.map { it.toChannel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @Suppress("UNCHECKED_CAST")
    val uiState: StateFlow<IptvUiState> = combine(
        repository.getCachedChannels(),
        favoriteChannels,
        _isLoading,
        manualCountryOverride,
        isSafeMode,
        _selectedCategory,
        _searchQuery.debounce(300.milliseconds)
    ) { args ->
        val cachedChannels = args[0] as List<Channel>
        val favorites = args[1] as List<Channel>
        val loading = args[2] as Boolean
        val override = args[3] as String?
        val safeMode = args[4] as Boolean
        val category = args[5] as String
        val query = args[6] as String

        if (loading && cachedChannels.isEmpty()) {
            IptvUiState.Loading
        } else {
            val favoriteIds = favorites.asSequence().map { it.id }.toSet()
            val isAll = category == "All"
            val isQueryEmpty = query.isEmpty()

            // Optimized list filtering. Moved logic matching from HomeScreen directly here off main thread.
            val filtered = cachedChannels.filter { ch ->
                val matchesSafe = !safeMode || (!ch.category.contains("XXX", true) && !ch.category.contains("Adult", true))
                if (!matchesSafe) return@filter false

                val matchesCategory = isAll || ch.category.contains(category, ignoreCase = true)
                if (!matchesCategory) return@filter false

                val matchesQuery = isQueryEmpty || ch.name.contains(query, ignoreCase = true)
                matchesQuery
            }.map { it.copy(isFavorite = favoriteIds.contains(it.id)) }

            val categoriesList = listOf("All") + cachedChannels.map { it.category }.distinct().sorted()
            IptvUiState.Success(filtered, override ?: "Detected", categoriesList)
        }
    }.flowOn(Dispatchers.Default) // Inilipat natin ang load ng filtering sa background thread
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IptvUiState.Loading)

    init {
        viewModelScope.launch {
            combine(manualCountryOverride, customPlaylists) { _, _ -> refreshChannels() }.collect()
        }

        viewModelScope.launch { isSafeMode.collect { v -> prefs.edit { putBoolean("is_safe_mode", v) } } }
        viewModelScope.launch { manualCountryOverride.collect { v -> prefs.edit { putString("manual_country", v) } } }
        viewModelScope.launch { isHardwareAcceleration.collect { v -> prefs.edit { putBoolean("is_hardware_accel", v) } } }
        viewModelScope.launch { isBackgroundPlay.collect { v -> prefs.edit { putBoolean("is_bg_play", v) } } }
        viewModelScope.launch { viewMode.collect { v -> prefs.edit { putString("view_mode", v.name) } } }
        viewModelScope.launch { savedPin.collect { v -> prefs.edit { putString("saved_pin", v) } } }
    }

    fun clearError() { _errorMessage.value = null }

    fun addPlaylist(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch { playlistDao.insertPlaylist(PlaylistEntity(url)) }
    }

    fun removePlaylist(url: String) {
        viewModelScope.launch { playlistDao.deletePlaylist(PlaylistEntity(url)) }
    }

    fun refreshChannels() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val code = when (manualCountryOverride.value) {
                    "Philippines" -> "ph"; "USA" -> "us"; "Japan" -> "jp"; "Germany" -> "de"; else -> null
                }
                repository.refreshChannels(code, customPlaylists.value)
            } catch (_: Exception) {
                _errorMessage.value = "Failed to update channel list."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectChannel(channel: Channel?) {
        if (channel?.streamUrl == _selectedChannel.value?.streamUrl) return
        _selectedChannel.value = channel
        _errorMessage.value = null
        if (channel != null) {
            _isBuffering.value = true
            _isPlayerMinimized.value = false
            try {
                val sanitizedUrl = channel.streamUrl.trim()
                val mediaItem = MediaItem.Builder()
                    .setUri(sanitizedUrl.toUri())
                    .apply { if (sanitizedUrl.contains(".m3u8", true)) setMimeType(MimeTypes.APPLICATION_M3U8) }
                    .build()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
                viewModelScope.launch { recentChannelDao.insertRecentChannel(channel.toRecentEntity()) }
            } catch (e: Exception) {
                _isBuffering.value = false
                _errorMessage.value = "Error: Invalid stream format."
                _selectedChannel.value = null
            }
        } else {
            exoPlayer.stop(); exoPlayer.clearMediaItems(); _isBuffering.value = false
        }
    }

    fun setCategory(category: String) { _selectedCategory.value = category }
    fun setSearchQuery(query: String) { 
        _searchQuery.value = query 
        if (query.length >= 3) {
            viewModelScope.launch {
                searchHistoryDao.insertSearch(SearchHistoryEntity(query.trim()))
            }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { searchHistoryDao.clearAll() }
    }

    fun deleteSearchItem(query: String) {
        viewModelScope.launch { searchHistoryDao.deleteSearch(SearchHistoryEntity(query)) }
    }
    fun setPlayerMinimized(minimized: Boolean) { _isPlayerMinimized.value = minimized }
    override fun onCleared() { super.onCleared(); exoPlayer.release() }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            val isFav = favoriteChannels.value.any { it.id == channel.id }
            if (isFav) favoritesDao.deleteFavorite(channel.toFavoriteEntity())
            else favoritesDao.insertFavorite(channel.toFavoriteEntity())
        }
    }

    private fun Channel.toFavoriteEntity() = FavoriteChannel(id, name, streamUrl, logoUrl, category, country)
    private fun FavoriteChannel.toChannel() = Channel(id, name, logoUrl, streamUrl, category, country, true)
    private fun Channel.toRecentEntity() = RecentChannel(id, name, logoUrl, streamUrl, category, country)
    private fun RecentChannel.toChannel() = Channel(id, name, logoUrl, streamUrl, category, country, false)
}

class IptvViewModelFactory(
    private val application: Application,
    private val repository: IptvRepository,
    private val favoritesDao: FavoritesDao,
    private val recentChannelDao: RecentChannelDao,
    private val playlistDao: PlaylistDao,
    private val searchHistoryDao: SearchHistoryDao,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(IptvViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return IptvViewModel(application, repository, favoritesDao, recentChannelDao, playlistDao, searchHistoryDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel")
    }
}