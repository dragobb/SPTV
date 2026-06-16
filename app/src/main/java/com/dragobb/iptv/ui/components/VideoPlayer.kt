package com.dragobb.iptv.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    exoPlayer: ExoPlayer,
    channelName: String,
    isBuffering: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isMinimized: Boolean = false,
    onClose: (() -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
    onMiniPlayer: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var resizeMode by rememberSaveable { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    
    val neonPurple = Color(0xFF8E5AFF)

    // Auto-hide controls logic
    LaunchedEffect(showControls, isBuffering) {
        if (showControls && !isBuffering && !isMinimized) {
            delay(4000)
            showControls = false
        }
    }

    val activity = context.findActivity()
    val window = activity?.window
    if (window != null && !isMinimized) {
        val controller = remember(window) { WindowInsetsControllerCompat(window, window.decorView) }
        LaunchedEffect(isFullscreen, showControls) {
            if (isFullscreen && !showControls) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(modifier = modifier
        .fillMaxSize()
        .background(Color.Black)
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { 
            if (!isMinimized) showControls = !showControls 
            else onExpand?.invoke()
        }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(if (isMinimized) 32.dp else 56.dp),
                color = neonPurple,
                strokeWidth = if (isMinimized) 3.dp else 5.dp
            )
        }

        // --- Premium Overlay Controls ---
        AnimatedVisibility(
            visible = showControls && !isMinimized,
            enter = fadeIn(animationSpec = tween(400)) + slideInVertically(initialOffsetY = { it / 10 }),
            exit = fadeOut(animationSpec = tween(400)) + slideOutVertically(targetOffsetY = { it / 10 }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(0.7f),
                            Color.Transparent,
                            Color.Black.copy(0.7f)
                        )
                    )
                )
                .safeDrawingPadding()
                .padding(20.dp)
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val act = context.findActivity()
                            act?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            onBack()
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(0.4f))
                            .border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    
                    Spacer(Modifier.width(20.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Now Streaming",
                            color = neonPurple,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                        )
                        Text(
                            text = channelName,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Bottom Controls
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ControlIconButton(
                        icon = Icons.Default.AspectRatio,
                        onClick = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                        }
                    )

                    if (onMiniPlayer != null) {
                        ControlIconButton(
                            icon = Icons.Default.PictureInPicture,
                            onClick = onMiniPlayer
                        )
                    }

                    ControlIconButton(
                        icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        onClick = {
                            isFullscreen = !isFullscreen
                            val act = context.findActivity()
                            act?.requestedOrientation = if (isFullscreen) 
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE 
                            else 
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    )
                }
            }
        }

        if (isMinimized) {
            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.TopEnd)
                            .background(Color.Black.copy(0.6f), shape = RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                
                if (onExpand != null) {
                     IconButton(
                        onClick = onExpand,
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.BottomEnd)
                            .background(Color.Black.copy(0.6f), shape = RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.OpenInFull, "Expand", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ControlIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(0.4f))
            .border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
