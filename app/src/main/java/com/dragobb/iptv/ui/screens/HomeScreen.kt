package com.dragobb.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.dragobb.iptv.ui.components.ChannelCard
import com.dragobb.iptv.ui.components.ShimmerItem
import com.dragobb.iptv.ui.models.Channel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isLoading: Boolean,
    channels: List<Channel>,
    recentChannels: List<Channel>,
    country: String,
    selectedCategory: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onRefresh: () -> Unit,
    onMenuClick: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()

    val groupedChannels = remember(channels, selectedCategory, searchQuery) {
        val filtered = channels.filter { channel ->
            val matchesCategory = if (selectedCategory == "All") true
            else channel.category.contains(selectedCategory, ignoreCase = true)

            val matchesSearch = channel.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }

        if (selectedCategory != "All" || searchQuery.isNotEmpty()) {
            val key = if (searchQuery.isNotEmpty()) "Search Results" else selectedCategory
            mapOf(key to filtered)
        } else {
            filtered.groupBy { it.category }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "StairPlay TV",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "📍 $country",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            state = pullToRefreshState,
            modifier = Modifier.padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Search Bar Item
                item {
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .height(52.dp),
                        placeholder = { Text("Search channels...", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        shape = RoundedCornerShape(12.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        singleLine = true
                    )
                }

                if (isLoading && channels.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LottieLoadingView()
                        }
                    }
                    items(3) {
                        CategoryRowShimmer()
                    }
                } else {
                    // 1. Recently Watched Row
                    if (recentChannels.isNotEmpty() && selectedCategory == "All" && searchQuery.isEmpty()) {
                        item(key = "recently_watched") {
                            ChannelRow(
                                title = "Recently Watched",
                                channels = recentChannels,
                                onChannelClick = onChannelClick,
                                onToggleFavorite = onToggleFavorite
                            )
                        }
                    }

                    // 2. Dynamic Categories Rows
                    groupedChannels.forEach { (category, categoryChannels) ->
                        if (categoryChannels.isNotEmpty()) {
                            item(key = category) {
                                ChannelRow(
                                    title = category,
                                    channels = categoryChannels,
                                    onChannelClick = onChannelClick,
                                    onToggleFavorite = onToggleFavorite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelRow(
    title: String,
    channels: List<Channel>,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title.uppercase(),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.primary
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                ChannelCard(
                    channel = channel,
                    onChannelClick = onChannelClick,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier.width(220.dp)
                )
            }
        }
    }
}

@Composable
fun LottieLoadingView() {
    // Premium TV Loading Animation
    val composition by rememberLottieComposition(LottieCompositionSpec.Url("https://lottie.host/8e2f8d22-2615-46c0-b5d1-d97157d19e9f/8Y1P9I2fK9.json"))
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = Modifier.size(180.dp)
    )
}

@Composable
fun CategoryRowShimmer() {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .width(120.dp)
                .height(16.dp)
                .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(3) {
                ShimmerItem(modifier = Modifier.width(220.dp).aspectRatio(16f/9f))
            }
        }
    }
}
