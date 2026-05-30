package com.dragobb.iptv.ui.viewmodels

import android.app.Application
import androidx.annotation.OptIn
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
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

sealed class IptvUiState {
    object Loading : IptvUiState()
    data class Success(val channels: List<Channel>, val country: String, val categories: List<String>) : IptvUiState()
    data class Error(val message: String) : IptvUiState()
}

enum class ViewMode { GRID, LIST }

@OptIn(UnstableApi::class)
@FlowPreview
class IptvViewModel(
    application: Application,
    private val repository: IptvRepository,
    private val favoritesDao: FavoritesDao,
    private val recentChannelDao: RecentChannelDao,
    private val playlistDao: PlaylistDao
) : AndroidViewModel(application) {

    private val _isLoading = MutableStateFlow(true)
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _selectedChannel = MutableStateFlow<Channel?>(null)
    val selectedChannel = _selectedChannel.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isPlayerMinimized = MutableStateFlow(false)
    val isPlayerMinimized = _isPlayerMinimized.asStateFlow()

    // --- SYSTEM SETTINGS ---
    val isSafeMode = MutableStateFlow(true)
    val manualCountryOverride = MutableStateFlow<String?>(null)

    val customPlaylists: StateFlow<List<String>> = playlistDao.getAllPlaylists()
        .map { list -> list.map { it.url } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isHardwareAcceleration = MutableStateFlow(true)
    val isBackgroundPlay = MutableStateFlow(false)
    val viewMode = MutableStateFlow(ViewMode.GRID)
    val savedPin = MutableStateFlow("1234")

    private val _onlineStatusMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())

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
            override fun onPlayerError(error: PlaybackException) {
                _errorMessage.value = "Playback Error: ${error.localizedMessage ?: "Stream offline or invalid link"}"
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
        _errorMessage,
        manualCountryOverride,
        isSafeMode,
        _selectedCategory,
        _searchQuery.debounce(300),
        _onlineStatusMap
    ) { args ->
        val cachedChannels = args[0] as List<Channel>
        val favorites = args[1] as List<Channel>
        val loading = args[2] as Boolean
        val error = args[3] as String?
        val override = args[4] as String?
        val safeMode = args[5] as Boolean
        val category = args[6] as String
        val query = args[7] as String
        val statusMap = args[8] as Map<String, Boolean>

        when {
            error != null -> IptvUiState.Error(error)
            loading && cachedChannels.isEmpty() -> IptvUiState.Loading
            else -> {
                val favoriteIds = favorites.map { it.id }.toSet()
                val filtered = cachedChannels.filter { ch ->
                    val matchesSafe = !safeMode || (!ch.category.contains("XXX", true) && !ch.category.contains("Adult", true))
                    val matchesQuery = ch.name.contains(query, ignoreCase = true)
                    val matchesCategory = if (category == "All") true else ch.category == category
                    matchesSafe && matchesQuery && matchesCategory
                }.map { it.copy(isFavorite = favoriteIds.contains(it.id), isOnline = statusMap[it.streamUrl] ?: true) }
                val categoriesList = listOf("All") + cachedChannels.map { it.category }.distinct().sorted()
                IptvUiState.Success(filtered, override ?: "Detected", categoriesList)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IptvUiState.Loading)

    init {
        viewModelScope.launch {
            combine(manualCountryOverride, customPlaylists) { _, _ -> refreshChannels() }.collect()
        }
    }

    fun clearError() { _errorMessage.value = null }

    fun addPlaylist(url: String) {
        viewModelScope.launch { playlistDao.insertPlaylist(PlaylistEntity(url)) }
    }

    fun removePlaylist(url: String) {
        viewModelScope.launch { playlistDao.deletePlaylist(PlaylistEntity(url)) }
    }

    fun refreshChannels() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val code = when (manualCountryOverride.value) {
                    "Philippines" -> "ph"
                    "USA" -> "us"
                    "UK" -> "gb"
                    "Japan" -> "jp"
                    "Germany" -> "de"
                    else -> null
                }
                repository.refreshChannels(code, customPlaylists.value)
            } catch (e: Exception) {
                _errorMessage.value = "Refresh failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun checkStreamHealth(channel: Channel) {
        if (_onlineStatusMap.value.containsKey(channel.streamUrl)) return
        viewModelScope.launch {
            val isOnline = withContext(Dispatchers.IO) {
                try {
                    val url = URL(channel.streamUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    val responseCode = conn.responseCode
                    responseCode in 200..399
                } catch (e: Exception) { false }
            }
            _onlineStatusMap.value = _onlineStatusMap.value + (channel.streamUrl to isOnline)
        }
    }

    fun selectChannel(channel: Channel?) {
        if (channel?.streamUrl == _selectedChannel.value?.streamUrl) return
        _selectedChannel.value = channel
        _errorMessage.value = null
        if (channel != null) {
            _isPlayerMinimized.value = false
            val mediaItem = MediaItem.Builder()
                .setUri(channel.streamUrl)
                .apply { if (channel.streamUrl.contains(".m3u8", true)) setMimeType(MimeTypes.APPLICATION_M3U8) }
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            viewModelScope.launch { recentChannelDao.insertRecentChannel(channel.toRecentEntity()) }
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    fun setCategory(category: String) { _selectedCategory.value = category }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setPlayerMinimized(minimized: Boolean) { _isPlayerMinimized.value = minimized }

    override fun onCleared() {
        super.onCleared()
        exoPlayer.release()
    }

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
    private val playlistDao: PlaylistDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(IptvViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return IptvViewModel(application, repository, favoritesDao, recentChannelDao, playlistDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel")
    }
}
