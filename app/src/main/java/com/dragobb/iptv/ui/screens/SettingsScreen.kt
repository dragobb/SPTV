package com.dragobb.iptv.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dragobb.iptv.R
import com.dragobb.iptv.ui.viewmodels.IptvViewModel
import com.dragobb.iptv.ui.viewmodels.ViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: IptvViewModel,
    onMenuClick: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    
    val isSafeMode by viewModel.isSafeMode.collectAsState()
    val manualCountry by viewModel.manualCountryOverride.collectAsState()
    val customPlaylists by viewModel.customPlaylists.collectAsState()
    val isHardwareAcc by viewModel.isHardwareAcceleration.collectAsState()
    val isBgPlay by viewModel.isBackgroundPlay.collectAsState()
    val currentViewMode by viewModel.viewMode.collectAsState()
    
    var showPinDialog by remember { mutableStateOf(value = false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    
    var showAddUrlDialog by remember { mutableStateOf(false) }
    var newUrlInput by remember { mutableStateOf("") }
    
    var showAboutDialog by remember { mutableStateOf(false) }

    val neonPurple = Color(0xFF8E5AFF)
    val deepBlack = Color(0xFF080808)

    Scaffold(
        containerColor = deepBlack,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Settings", 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                navigationIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.app),
                        contentDescription = "Logo",
                        modifier = Modifier.padding(start = 16.dp).size(32.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = deepBlack.copy(alpha = 0.9f)
                ),
                windowInsets = TopAppBarDefaults.windowInsets
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // --- Content & Filtering Section ---
            SettingsHeader("Content & Filtering")
            
            SettingsDropdownItem(
                title = "Manual Country Override",
                description = manualCountry ?: "Auto (Detected)",
                icon = Icons.Default.Public,
                options = listOf("Auto", "Philippines", "USA", "Japan", "Germany"),
                onSelect = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.manualCountryOverride.value = if (it == "Auto") null else it 
                }
            )
            DividerLine()

            SettingsToggleItem(
                title = "Safe Mode / NSFW Filter",
                description = "Hide adult content (PIN protected)",
                icon = Icons.Default.Security,
                checked = isSafeMode,
                onCheckedChange = { newValue ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (!newValue) { showPinDialog = true } else { viewModel.isSafeMode.value = true }
                }
            )
            DividerLine()

            // Playlist Manager Section (Uber Style)
            SettingsActionItem(
                title = "Manage Playlists",
                description = "${customPlaylists.size} active sources",
                icon = Icons.Default.Link,
                onClick = { showAddUrlDialog = true }
            )
            DividerLine()

            // --- Video & Playback Section ---
            SettingsHeader("Video & Playback")
            
            SettingsToggleItem(
                title = "Hardware Acceleration",
                description = "Use GPU for decoding",
                icon = Icons.Default.Memory,
                checked = isHardwareAcc,
                onCheckedChange = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.isHardwareAcceleration.value = it 
                }
            )
            DividerLine()

            SettingsToggleItem(
                title = "Background Audio",
                description = "Keep playing audio when screen is off",
                icon = Icons.Default.Headset,
                checked = isBgPlay,
                onCheckedChange = { 
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.isBackgroundPlay.value = it 
                }
            )
            DividerLine()

            // --- UI & Personalization Section ---
            SettingsHeader("UI & Personalization")
            
            SettingsSelectorItem(
                title = "View Mode",
                options = listOf("Grid", "List"),
                selected = if (currentViewMode == ViewMode.GRID) "Grid" else "List",
                onSelect = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.viewMode.value = if (it == "Grid") ViewMode.GRID else ViewMode.LIST 
                }
            )
            DividerLine()

            // --- Storage & Data Section ---
            SettingsHeader("Storage & Data")
            
            SettingsActionItem(
                title = "Force Refresh Playlist",
                description = "Reload channels from sources",
                icon = Icons.Default.Sync,
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.refreshChannels() 
                }
            )
            DividerLine()

            // --- App Info Section ---
            SettingsHeader("App Info")
            
            SettingsActionItem(
                title = "About StairPlay",
                description = "Version 1.0.0 • ABServices",
                icon = Icons.Default.Info,
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showAboutDialog = true
                }
            )
            DividerLine()

            Spacer(Modifier.height(120.dp))
        }
    }

    // Dialogs
    if (showAddUrlDialog) {
        AlertDialog(
            onDismissRequest = { showAddUrlDialog = false },
            containerColor = Color(0xFF121212),
            title = { Text("M3U Playlists", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newUrlInput,
                        onValueChange = { newUrlInput = it },
                        placeholder = { Text("https://example.com/playlist.m3u", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    customPlaylists.forEach { url ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(0.05f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(url, modifier = Modifier.weight(1f), maxLines = 1, fontSize = 11.sp, color = Color.Gray)
                            IconButton(onClick = { viewModel.removePlaylist(url) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.5f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newUrlInput.isNotEmpty()) viewModel.addPlaylist(newUrlInput)
                    newUrlInput = ""
                }) { Text("Add", color = neonPurple) }
            },
            dismissButton = { TextButton(onClick = { showAddUrlDialog = false }) { Text("Close", color = Color.Gray) } }
        )
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false; pinInput = "" },
            containerColor = Color(0xFF121212),
            title = { Text("Parental Lock", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter PIN to disable Safe Mode", fontSize = 14.sp, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if ((it.length <= 4) && it.all { c -> c.isDigit() }) pinInput = it },
                        label = { Text("4-Digit PIN") },
                        singleLine = true,
                        isError = pinError,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pinInput == viewModel.savedPin.value) {
                        viewModel.isSafeMode.value = false
                        showPinDialog = false
                        pinError = false
                    } else { pinError = true }
                    pinInput = ""
                }) { Text("Verify", color = neonPurple) }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text("Cancel", color = Color.Gray) } }
        )
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val neonPurple = Color(0xFF8E5AFF)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121212),
        shape = RoundedCornerShape(28.dp),
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "StairPlay TV",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White
                )
                Text(
                    "Version 1.0.0 (Stable)",
                    style = MaterialTheme.typography.labelMedium,
                    color = neonPurple
                )
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "A premium, high-performance IPTV streaming application built with Jetpack Compose.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "Created by",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(0.4f)
                )
                Text(
                    "ABServices",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "© 2026 All Rights Reserved",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = neonPurple),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun SettingsHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            fontSize = 14.sp
        ),
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
fun DividerLine() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp), // Align with text start
        thickness = 0.5.dp,
        color = Color.White.copy(alpha = 0.08f)
    )
}

@Composable
fun SettingsToggleItem(title: String, description: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, animationSpec = tween(100), label = "scale")

    ListItem(
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }, 
        supportingContent = { Text(description, color = Color.Gray, fontSize = 13.sp) }, 
        leadingContent = { Icon(icon, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(24.dp)) }, 
        trailingContent = { 
            Switch(
                checked = checked, 
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF8E5AFF))
            ) 
        }, 
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SettingsActionItem(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, animationSpec = tween(100), label = "scale")

    ListItem(
        modifier = Modifier
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .graphicsLayer { scaleX = scale; scaleY = scale }, 
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }, 
        supportingContent = { Text(description, color = Color.Gray, fontSize = 13.sp) }, 
        leadingContent = { Icon(icon, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(24.dp)) }, 
        trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(0.3f)) }, 
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SettingsSelectorItem(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), 
        horizontalArrangement = Arrangement.SpaceBetween, 
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.GridView, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        
        Row(
            modifier = Modifier
                .background(Color.White.copy(0.05f), RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF8E5AFF) else Color.Transparent)
                        .clickable { onSelect(option) }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(option, color = if (isSelected) Color.White else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SettingsDropdownItem(title: String, description: String, icon: ImageVector, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, label = "scale")

    Box {
        ListItem(
            modifier = Modifier
                .clickable(interactionSource = interactionSource, indication = null) { expanded = true }
                .graphicsLayer { scaleX = scale; scaleY = scale }, 
            headlineContent = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }, 
            supportingContent = { Text(description, color = Color(0xFF8E5AFF), fontSize = 13.sp) }, 
            leadingContent = { Icon(icon, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(24.dp)) }, 
            trailingContent = { Icon(Icons.Default.ArrowDropDown, null, tint = Color.White.copy(0.3f)) }, 
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        DropdownMenu(
            expanded = expanded, 
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF121212)).border(0.5.dp, Color.White.copy(0.1f))
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 14.sp, color = Color.White) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}
