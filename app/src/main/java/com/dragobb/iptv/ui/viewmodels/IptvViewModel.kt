package com.dragobb.iptv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.dragobb.iptv.data.IptvRepository
import com.dragobb.iptv.data.local.FavoriteChannel
import com.dragobb.iptv.data.local.FavoritesDao
import com.dragobb.iptv.data.local.RecentChannel
import com.dragobb.iptv.data.local.RecentChannelDao
import com.dragobb.iptv.ui.models.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class IptvUiState {
    object Loading : IptvUiState()
    data class Success(val channels: List<Channel>, val country: String, val categories: List<String>) : IptvUiState()
    data class Error(val message: String) : IptvUiState()
}

enum class ViewMode { GRID, LIST }

class IptvViewModel(
    private val repository: IptvRepository,
    private val favoritesDao: FavoritesDao,
    private val recentChannelDao: RecentChannelDao
) : ViewModel() {

    private val _remoteChannels = MutableStateFlow<List<Channel>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _errorMessage = MutableStateFlow<String?>(null)

    private val _selectedChannel = MutableStateFlow<Channel?>(null)
    val selectedChannel = _selectedChannel.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isPlayerMinimized = MutableStateFlow(false)
    val isPlayerMinimized = _isPlayerMinimized.asStateFlow()

    // --- SYSTEM SETTINGS STATES ---
    val isSafeMode = MutableStateFlow(true)
    val manualCountryOverride = MutableStateFlow<String?>(null)
    val customM3uUrl = MutableStateFlow("")
    val isHardwareAcceleration = MutableStateFlow(true)
    val isBackgroundPlay = MutableStateFlow(false)
    val viewMode = MutableStateFlow(ViewMode.GRID)

    val favoriteChannels: StateFlow<List<Channel>> = favoritesDao.getAllFavorites()
        .map { favorites -> favorites.map { it.toChannel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentChannels: StateFlow<List<Channel>> = recentChannelDao.getRecentChannels()
        .map { recents -> recents.map { it.toChannel() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<IptvUiState> = combine(
        _remoteChannels,
        favoriteChannels,
        _isLoading,
        _errorMessage,
        manualCountryOverride,
        isSafeMode
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val remote = args[0] as List<Channel>
        @Suppress("UNCHECKED_CAST")
        val favorites = args[1] as List<Channel>

        val loading = args[2] as Boolean
        val error = args[3] as String?
        val override = args[4] as String?
        val safeMode = args[5] as Boolean

        when {
            error != null -> IptvUiState.Error(error)
            loading -> IptvUiState.Loading
            else -> {
                val favoriteIds = favorites.map { it.id }.toSet()

                // 1. Filter and Rename category within the channels themselves
                val filtered = remote
                    .filter { !safeMode || (!it.category.contains("XXX", true) && !it.category.contains("Adult", true)) }
                    .map {
                        val updatedCategory = if (it.category.equals("Philippines", ignoreCase = true)) "Special Channels" else it.category
                        it.copy(
                            isFavorite = favoriteIds.contains(it.id),
                            category = updatedCategory
                        )
                    }

                // 2. Build the category list using the renamed values
                val categories = listOf("All") + filtered.map { it.category }.distinct().sorted()

                // 3. Handle Country Display Name
                val rawCountry = override ?: repository.getDetectedCountryCode()
                val displayCountry = if (rawCountry.equals("Philippines", ignoreCase = true) || rawCountry.equals("PH", ignoreCase = true)) {
                    "Special Channels"
                } else {
                    rawCountry.uppercase()
                }

                IptvUiState.Success(filtered, displayCountry, categories)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IptvUiState.Loading)

    init {
        viewModelScope.launch {
            manualCountryOverride.collect {
                loadChannels()
            }
        }
    }

    fun loadChannels() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val countryCode = manualCountryOverride.value?.let { country ->
                    when (country) {
                        "Philippines" -> "ph"
                        "USA" -> "us"
                        "UK" -> "gb"
                        "Japan" -> "jp"
                        "Germany" -> "de"
                        else -> null
                    }
                }

                val channels = repository.fetchChannels(countryCode)
                _remoteChannels.value = channels
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load channels: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectChannel(channel: Channel?) {
        _selectedChannel.value = channel
        if (channel != null) {
            _isPlayerMinimized.value = false
            addToRecent(channel)
        }
    }

    private fun addToRecent(channel: Channel) {
        viewModelScope.launch {
            recentChannelDao.insertRecentChannel(channel.toRecentEntity())
        }
    }

    fun setCategory(category: String) { _selectedCategory.value = category }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setPlayerMinimized(minimized: Boolean) { _isPlayerMinimized.value = minimized }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            val isFav = favoriteChannels.value.any { it.id == channel.id }
            if (isFav) favoritesDao.deleteFavorite(channel.toFavoriteEntity())
            else favoritesDao.insertFavorite(channel.toFavoriteEntity())
        }
    }

    private fun Channel.toFavoriteEntity() = FavoriteChannel(
        id, name, streamUrl, logoUrl, category, country
    )

    private fun FavoriteChannel.toChannel() = Channel(
        id, name, logoUrl, streamUrl, category, country, true
    )

    private fun Channel.toRecentEntity() = RecentChannel(
        id, name, logoUrl, streamUrl, category, country
    )

    private fun RecentChannel.toChannel() = Channel(
        id, name, logoUrl, streamUrl, category, country, false
    )
}

class IptvViewModelFactory(
    private val repository: IptvRepository,
    private val favoritesDao: FavoritesDao,
    private val recentChannelDao: RecentChannelDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(IptvViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return IptvViewModel(repository, favoritesDao, recentChannelDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel")
    }
}