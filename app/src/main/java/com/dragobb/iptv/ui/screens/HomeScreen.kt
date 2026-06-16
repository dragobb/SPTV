package com.dragobb.iptv.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.airbnb.lottie.compose.*
import com.dragobb.iptv.R
import com.dragobb.iptv.ui.components.ChannelCard
import com.dragobb.iptv.ui.components.ChannelListItem
import com.dragobb.iptv.ui.components.ShimmerItem
import com.dragobb.iptv.ui.models.Channel
import com.dragobb.iptv.ui.viewmodels.ViewMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isLoading: Boolean,
    channels: List<Channel>,
    recentChannels: List<Channel>,
    favoriteChannels: List<Channel> = emptyList(), // Added to show on Home
    country: String,
    selectedCategory: String,
    searchQuery: String,
    viewMode: ViewMode,
    searchHistory: List<String> = emptyList(),
    categories: List<String> = emptyList(),
    onCategoryClick: (String) -> Unit = {},
    onSearchQueryChange: (String) -> Unit,
    onClearSearchHistory: () -> Unit = {},
    onDeleteSearchItem: (String) -> Unit = {},
    onChannelClick: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onRefresh: () -> Unit,
    onMenuClick: () -> Unit,
    isExploreMode: Boolean = false, // Added to differentiate tabs
    forceScrollToCategories: Boolean = false
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    
    var isSearchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Premium Colors
    val neonPurple = Color(0xFF8E5AFF)
    val deepBlack = Color(0xFF080808)
    val darkSurface = Color(0xFF121212)

    val bgColor by animateColorAsState(
        targetValue = when {
            selectedCategory.contains("Movie", true) -> Color(0xFF1A0B0B)
            selectedCategory.contains("Sport", true) -> Color(0xFF0B1A0B)
            selectedCategory.contains("Philippines", true) -> Color(0xFF0B0B1A)
            else -> deepBlack
        },
        animationSpec = tween(1000),
        label = "bgColor"
    )

    // Ensure we are using the passed channels correctly
    val filteredChannels = remember(channels) { channels }

    val groupedChannels = remember(filteredChannels, selectedCategory, searchQuery) {
        if ((selectedCategory != "All") || searchQuery.isNotEmpty()) {
            val key = if (searchQuery.isNotEmpty()) "Search Results" else selectedCategory
            mapOf(key to filteredChannels)
        } else {
            filteredChannels.groupBy { it.category }
        }
    }

    val featuredChannels = remember(channels) {
        channels.asSequence()
            .filter { it.category.contains("Philippines", ignoreCase = true) || it.isFavorite }
            .take(5)
            .toList()
    }

    val isSpotlightVisible by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    // Logic: Force scroll to categories if Explore tab clicked
    LaunchedEffect(forceScrollToCategories) {
        if (forceScrollToCategories && categories.isNotEmpty()) {
            listState.animateScrollToItem(1)
        }
    }

    // Clear search when deactivated
    LaunchedEffect(isSearchActive) {
        if (!isSearchActive) {
            onSearchQueryChange("")
        } else {
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = isSearchActive,
                        transitionSpec = {
                            if (targetState) {
                                (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                            } else {
                                (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                            }
                        },
                        label = "topBarContent"
                    ) { active ->
                        if (active) {
                            TextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                placeholder = { Text("Search channels...", color = Color.Gray, fontSize = 16.sp) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = neonPurple
                                ),
                                singleLine = true,
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchQueryChange("") }) {
                                            Icon(Icons.Default.Close, null, tint = Color.Gray)
                                        }
                                    }
                                }
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "STAIRPLAY",
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        letterSpacing = 2.sp,
                                        fontSize = 18.sp
                                    ),
                                    color = Color.White
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = neonPurple.copy(0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = " $country ",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 1.sp
                                        ),
                                        color = neonPurple
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (isSearchActive) {
                        IconButton(onClick = { isSearchActive = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.app),
                            contentDescription = "Logo",
                            modifier = Modifier.padding(start = 16.dp).size(32.dp)
                        )
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, null, tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = bgColor.copy(alpha = 0.95f)
                ),
                windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp) // Force no extra insets
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
                contentPadding = PaddingValues(bottom = 16.dp) // Reduced from 120.dp
            ) {
                // EXPLORE MODE: Show Spotlight and Categories
                if (isExploreMode && searchQuery.isEmpty()) {
                    if (featuredChannels.isNotEmpty() && selectedCategory == "All") {
                        item(contentType = "Spotlight") {
                            SpotlightBanner(featuredChannels, isSpotlightVisible, onChannelClick)
                        }
                    }

                    if (categories.isNotEmpty()) {
                        item(key = "categories_row", contentType = "Categories") {
                            CategoryCircleRow(categories, selectedCategory, onCategoryClick)
                        }
                    }
                }

                // HOME MODE: Show Recent and Favorites rows
                if (!isExploreMode && searchQuery.isEmpty()) {
                    if (recentChannels.isNotEmpty()) {
                        item(key = "recently_watched", contentType = "ChannelRow") {
                            ChannelRow("Recently Watched", recentChannels, onChannelClick, onToggleFavorite)
                        }
                    }
                    
                    if (favoriteChannels.isNotEmpty()) {
                        item(key = "favorites_row", contentType = "ChannelRow") {
                            ChannelRow("My Favorites", favoriteChannels, onChannelClick, onToggleFavorite)
                        }
                    }
                    
                    // Also show some General content on Home if available
                    val homeChannels = channels.take(10)
                    if (homeChannels.isNotEmpty()) {
                         item(key = "suggested_row", contentType = "ChannelRow") {
                            ChannelRow("Suggested for You", homeChannels, onChannelClick, onToggleFavorite)
                        }
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
                    // Search results or Full list in Explore
                    if (isExploreMode || searchQuery.isNotEmpty()) {
                        if (viewMode == ViewMode.GRID) {
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
                                    onChannelClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onChannelClick(it)
                                    },
                                    onToggleFavorite = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleFavorite(it)
                                    },
                                    modifier = Modifier.padding(vertical = 2.dp)
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
fun CategoryCircleRow(
    categories: List<String>,
    selectedCategory: String,
    onCategoryClick: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val neonPurple = Color(0xFF8E5AFF)

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(categories) { category ->
            val isSelected = category == selectedCategory
            val scale by animateFloatAsState(if (isSelected) 1.2f else 1f, label = "catScale")
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(70.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCategoryClick(category)
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) neonPurple else Color.White.copy(0.05f))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color.White else Color.White.copy(0.1f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when {
                        category == "All" -> Icons.Default.AllInclusive
                        category.contains("Movie", true) -> Icons.Default.Movie
                        category.contains("Sport", true) -> Icons.Default.SportsEsports
                        category.contains("Philippines", true) -> Icons.Default.Flag
                        category.contains("News", true) -> Icons.Default.Newspaper
                        category.contains("Music", true) -> Icons.Default.MusicNote
                        category.contains("Kids", true) -> Icons.Default.ChildCare
                        else -> Icons.Default.Tv
                    }
                    Icon(
                        icon,
                        null,
                        tint = if (isSelected) Color.White else Color.White.copy(0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 10.sp
                    ),
                    color = if (isSelected) Color.White else Color.White.copy(0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SpotlightBanner(channels: List<Channel>, isVisible: Boolean, onChannelClick: (Channel) -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState { channels.size }
    val neonPurple = Color(0xFF8E5AFF)

    LaunchedEffect(isVisible) {
        if (isVisible) {
            while (true) {
                delay(6.seconds)
                if (channels.isNotEmpty()) {
                    val nextPage = (pagerState.currentPage + 1) % channels.size
                    pagerState.animateScrollToPage(nextPage)
                }
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(vertical = 12.dp)
    ) { page ->
        if (page < channels.size) {
            val channel = channels[page]
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

            Card(
                onClick = { onChannelClick(channel) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .graphicsLayer {
                        alpha = 1f - (kotlin.math.abs(pageOffset) * 0.4f)
                        scaleX = 1f - (kotlin.math.abs(pageOffset) * 0.15f)
                        scaleY = 1f - (kotlin.math.abs(pageOffset) * 0.15f)
                    },
                shape = RoundedCornerShape(28.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(channel.logoUrl)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        contentScale = ContentScale.Fit
                    )
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.3f), Color.Black.copy(0.95f)))
                    ))
                    Column(modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)) {
                        Surface(color = neonPurple, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                "FEATURED",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                ),
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            channel.name,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp
                            ),
                            color = Color.White
                        )
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
    val neonPurple = Color(0xFF8E5AFF)
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent line for category
            Box(modifier = Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(neonPurple))
            Spacer(Modifier.width(12.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    fontSize = 13.sp
                ),
                color = Color.White.copy(alpha = 0.95f)
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                    modifier = Modifier.width(140.dp)
                )
            }
        }
    }
}

@Composable
fun LottieLoadingView() {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading_animation))
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
    LottieAnimation(composition = composition, progress = { progress }, modifier = Modifier.size(160.dp))
}

@Composable
fun CategoryRowShimmer() {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp).width(120.dp).height(16.dp).background(Color.White.copy(0.05f), RoundedCornerShape(4.dp)))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(3) { ShimmerItem(modifier = Modifier.width(200.dp).aspectRatio(16f/10f)) }
        }
    }
}
