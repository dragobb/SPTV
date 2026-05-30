package com.dragobb.iptv.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.dragobb.iptv.ui.models.Channel

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    channel: Channel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isMinimized: Boolean = false,
    onClose: (() -> Unit)? = null,
    onExpand: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isBuffering by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
                }

                override fun onPlayerError(error: PlaybackException) {
                    isBuffering = false
                    errorMessage = when (error.errorCode) {
                        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Server Error: Channel link expired or blocked."
                        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "No internet connection."
                        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "Video format not supported."
                        else -> "Playback Error: ${error.localizedMessage}"
                    }
                }
            })
        }
    }

    LaunchedEffect(channel.streamUrl) {
        errorMessage = null
        val mediaItem = MediaItem.fromUri(channel.streamUrl)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            exoPlayer.release()
            context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(modifier = modifier
        .fillMaxSize()
        .background(Color.Black)
        .then(if (isMinimized && onExpand != null) Modifier.clickable { onExpand() } else Modifier)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isBuffering && errorMessage == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = if (isMinimized) 2.dp else 4.dp
            )
        }

        // Overlay Controls
        if (!isMinimized) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(Color.Black.copy(0.5f), shape = RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }

                IconButton(
                    onClick = {
                        isFullscreen = !isFullscreen
                        val activity = context.findActivity()
                        activity?.requestedOrientation = if (isFullscreen) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(0.5f), shape = RoundedCornerShape(12.dp))
                ) {
                    Icon(if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Fullscreen", tint = Color.White)
                }
            }
        } else {
            // Minimized controls (Close button only usually)
            Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.TopEnd)
                            .background(Color.Black.copy(0.5f), shape = RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
                
                if (onExpand != null) {
                     IconButton(
                        onClick = onExpand,
                        modifier = Modifier
                            .size(32.dp)
                            .align(Alignment.BottomEnd)
                            .background(Color.Black.copy(0.5f), shape = RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.OpenInFull, "Expand", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Error Dialog
        if (errorMessage != null && !isMinimized) {
            AlertDialog(
                onDismissRequest = { errorMessage = null },
                title = { Text("Playback Error") },
                text = { Text(errorMessage!!) },
                confirmButton = {
                    Button(onClick = { errorMessage = null; onBack() }) { Text("OK") }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(24.dp)
            )
        }
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
