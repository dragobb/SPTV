package com.dragobb.iptv.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.*
import com.dragobb.iptv.ui.components.ChannelCard
import com.dragobb.iptv.ui.components.ChannelListItem
import com.dragobb.iptv.ui.components.ShimmerItem
import com.dragobb.iptv.ui.models.Channel
import com.dragobb.iptv.ui.viewmodels.ViewMode
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isLoading: Boolean,
    channels: List<Channel>,
    recentChannels: List<Channel>,
    country: String,
    selectedCategory: String,
    searchQuery: String,
    viewMode: ViewMode,
    onSearchQueryChange: (String) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onRefresh: () -> Unit,
    onMenuClick: () -> Unit,
    onCheckHealth: (Channel) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    val bgColor by animateColorAsState(
        targetValue = when {
            selectedCategory.contains("Movie", true) -> Color(0xFF1A0B0B)
            selectedCategory.contains("Sport", true) -> Color(0xFF0B1A0B)
            selectedCategory.contains("Philippines", true) -> Color(0xFF0B0B1A)
            else -> MaterialTheme.colorScheme.background
        },
        animationSpec = tween(1000),
        label = "bgColor"
    )

    val filteredChannels = remember(channels, selectedCategory, searchQuery) {
        channels.filter { channel ->
            val matchesCategory = if (selectedCategory == "All") true
            else channel.category.contains(selectedCategory, ignoreCase = true)
            val matchesSearch = channel.name.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    val groupedChannels = remember(filteredChannels, selectedCategory, searchQuery) {
        if (selectedCategory != "All" || searchQuery.isNotEmpty()) {
            val key = if (searchQuery.isNotEmpty()) "Search Results" else selectedCategory
            mapOf(key to filteredChannels)
        } else {
            filteredChannels.groupBy { it.category }
        }
    }

    val featuredChannels = remember(channels) {
        channels.filter { it.category.contains("Philippines", true) || it.isFavorite }.take(5)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = bgColor,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("StairPlay TV", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                        Text(text = "📍 $country", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, "Menu") }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = bgColor.copy(alpha = 0.9f)
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
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                // Spotlight only on Grid/Rows Home
                if (viewMode == ViewMode.GRID && featuredChannels.isNotEmpty() && selectedCategory == "All" && searchQuery.isEmpty()) {
                    item { SpotlightBanner(featuredChannels, onChannelClick) }
                }

                item {
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(54.dp),
                        placeholder = { Text("Search TV channels...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = Color.White.copy(alpha = 0.05f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        singleLine = true
                    )
                }

                if (isLoading && channels.isEmpty()) {
                    item { Box(modifier = Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) { LottieLoadingView() } }
                    items(3) { CategoryRowShimmer() }
                } else {
                    if (viewMode == ViewMode.GRID) {
                        // --- ROW VIEW (Netflix Style) ---
                        if (recentChannels.isNotEmpty() && selectedCategory == "All" && searchQuery.isEmpty()) {
                            item(key = "recently_watched") {
                                ChannelRow("Recently Watched", recentChannels, onChannelClick, onToggleFavorite, onCheckHealth)
                            }
                        }

                        groupedChannels.forEach { (category, categoryChannels) ->
                            if (categoryChannels.isNotEmpty()) {
                                item(key = category) {
                                    ChannelRow(category, categoryChannels, onChannelClick, onToggleFavorite, onCheckHealth)
                                }
                            }
                        }
                    } else {
                        // --- LIST VIEW (Vertical Style) ---
                        items(filteredChannels, key = { it.id }) { channel ->
                            PaddingValues(horizontal = 16.dp, vertical = 4.dp).let {
                                Box(modifier = Modifier.padding(it)) {
                                    ChannelListItem(
                                        channel = channel,
                                        onChannelClick = onChannelClick,
                                        onToggleFavorite = onToggleFavorite,
                                        onAppear = onCheckHealth
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpotlightBanner(channels: List<Channel>, onChannelClick: (Channel) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { channels.size })
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            val nextPage = (pagerState.currentPage + 1) % channels.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxWidth().height(220.dp).padding(16.dp)
    ) { page ->
        val channel = channels[page]
        Card(
            onClick = { onChannelClick(channel) },
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.8f)))))
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                        Text("FEATURED", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Text(channel.name, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Black)
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
    onToggleFavorite: (Channel) -> Unit,
    onCheckHealth: (Channel) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title.uppercase(),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
            color = MaterialTheme.colorScheme.primary
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(channels, key = { it.id }) { channel ->
                ChannelCard(
                    channel = channel,
                    onChannelClick = onChannelClick,
                    onToggleFavorite = onToggleFavorite,
                    onAppear = onCheckHealth,
                    modifier = Modifier.width(220.dp)
                )
            }
        }
    }
}

@Composable
fun LottieLoadingView() {
    val composition by rememberLottieComposition(LottieCompositionSpec.Url("https://lottie.host/8e2f8d22-2615-46c0-b5d1-d97157d19e9f/8Y1P9I2fK9.json"))
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
    LottieAnimation(composition = composition, progress = { progress }, modifier = Modifier.size(180.dp))
}

@Composable
fun CategoryRowShimmer() {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).width(120.dp).height(16.dp).background(Color.Gray.copy(0.2f), RoundedCornerShape(4.dp)))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(3) { ShimmerItem(modifier = Modifier.width(200.dp).aspectRatio(16f/9f)) }
        }
    }
}
