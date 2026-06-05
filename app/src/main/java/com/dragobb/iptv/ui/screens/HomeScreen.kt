package com.dragobb.iptv.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
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
    onMenuClick: () -> Unit
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()

    // Optimization: Pre-calculate colors using graphicsLayer alpha for smoother transitions
    val bgColor by animateColorAsState(
        targetValue = when {
            selectedCategory.contains("Movie", true) -> Color(0xFF1A0B0B)
            selectedCategory.contains("Sport", true) -> Color(0xFF0B1A0B)
            selectedCategory.contains("Philippines", true) -> Color(0xFF0B0B1A)
            else -> Color(0xFF0F0F0F)
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
        modifier = Modifier.fillMaxSize(),
        containerColor = bgColor,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "STAIRPLAY TV", 
                            fontWeight = FontWeight.Black, 
                            style = MaterialTheme.typography.titleMedium,
                            letterSpacing = 2.sp
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(0.1f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Text(
                                text = " 📍 $country ", 
                                style = MaterialTheme.typography.labelSmall, 
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) { 
                        Icon(Icons.Default.Menu, "Menu", tint = Color.White) 
                    }
                },
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
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                if (viewMode == ViewMode.GRID && featuredChannels.isNotEmpty() && selectedCategory == "All" && searchQuery.isEmpty()) {
                    item(contentType = "Spotlight") { SpotlightBanner(featuredChannels, onChannelClick) }
                }

                item(contentType = "SearchField") {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp)),
                            placeholder = { Text("Search TV channels...", fontSize = 14.sp, color = Color.Gray) },
                            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                            shape = RoundedCornerShape(16.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                    }
                }

                if (isLoading && channels.isEmpty()) {
                    item(contentType = "Loading") { 
                        Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) { 
                            LottieLoadingView() 
                        } 
                    }
                    items(3, contentType = { "Shimmer" }) { CategoryRowShimmer() }
                } else {
                    if (viewMode == ViewMode.GRID) {
                        if (recentChannels.isNotEmpty() && selectedCategory == "All" && searchQuery.isEmpty()) {
                            item(key = "recently_watched", contentType = "ChannelRow") {
                                ChannelRow("Recently Watched", recentChannels, onChannelClick, onToggleFavorite)
                            }
                        }

                        groupedChannels.forEach { (category, categoryChannels) ->
                            if (categoryChannels.isNotEmpty()) {
                                item(key = category, contentType = "ChannelRow") {
                                    ChannelRow(category, categoryChannels, onChannelClick, onToggleFavorite)
                                }
                            }
                        }
                    } else {
                        items(
                            items = filteredChannels,
                            key = { it.id },
                            contentType = { "ListItem" }
                        ) { channel ->
                            ChannelListItem(
                                channel = channel,
                                onChannelClick = onChannelClick,
                                onToggleFavorite = onToggleFavorite,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpotlightBanner(channels: List<Channel>, onChannelClick: (Channel) -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { channels.size })
    
    LaunchedEffect(Unit) {
        while (true) {
            delay(6000)
            val nextPage = (pagerState.currentPage + 1) % channels.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .padding(vertical = 16.dp)
    ) { page ->
        val channel = channels[page]
        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        
        Card(
            onClick = { onChannelClick(channel) },
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .graphicsLayer {
                    // Subtle parallax effect
                    alpha = 1f - (kotlin.math.abs(pageOffset) * 0.3f)
                    scaleX = 1f - (kotlin.math.abs(pageOffset) * 0.1f)
                    scaleY = 1f - (kotlin.math.abs(pageOffset) * 0.1f)
                },
            shape = RoundedCornerShape(20.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(channel.logoUrl)
                        .crossfade(true)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.9f)))))
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                        Text("FEATURED", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.Black, fontWeight = FontWeight.Black)
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
    onToggleFavorite: (Channel) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 14.dp)) {
        Text(
            text = title.uppercase(),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Black, 
                letterSpacing = 2.sp,
                fontSize = 12.sp
            ),
            color = Color.White.copy(alpha = 0.9f)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = channels,
                key = { it.id },
                contentType = { "ChannelCard" }
            ) { channel ->
                ChannelCard(
                    channel = channel,
                    onChannelClick = onChannelClick,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier.width(180.dp)
                )
            }
        }
    }
}

@Composable
fun LottieLoadingView() {
    val composition by rememberLottieComposition(LottieCompositionSpec.Url("https://lottie.host/8e2f8d22-2615-46c0-b5d1-d97157d19e9f/8Y1P9I2fK9.json"))
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
    LottieAnimation(composition = composition, progress = { progress }, modifier = Modifier.size(150.dp))
}

@Composable
fun CategoryRowShimmer() {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).width(100.dp).height(14.dp).background(Color.White.copy(0.05f), RoundedCornerShape(4.dp)))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(3) { ShimmerItem(modifier = Modifier.width(180.dp).aspectRatio(16f/9f)) }
        }
    }
}
