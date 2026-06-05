package com.dragobb.iptv.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dragobb.iptv.ui.viewmodels.IptvViewModel
import com.dragobb.iptv.ui.viewmodels.ViewMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: IptvViewModel,
    onMenuClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    
    val isSafeMode by viewModel.isSafeMode.collectAsState()
    val manualCountry by viewModel.manualCountryOverride.collectAsState()
    val customPlaylists by viewModel.customPlaylists.collectAsState()
    val isHardwareAcc by viewModel.isHardwareAcceleration.collectAsState()
    val isBgPlay by viewModel.isBackgroundPlay.collectAsState()
    val currentViewMode by viewModel.viewMode.collectAsState()
    
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    
    var showAddUrlDialog by remember { mutableStateOf(false) }
    var newUrlInput by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Color(0xFF0F0F0F), // Deep black background
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "SYSTEM SETTINGS", 
                        fontWeight = FontWeight.Black, 
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 2.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMenuClick()
                    }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color(0xFF0F0F0F).copy(alpha = 0.9f)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            SettingsSection(title = "Content & Filtering") {
                SettingsDropdownItem(
                    title = "Manual Country Override",
                    description = manualCountry ?: "Auto (Detected)",
                    icon = Icons.Default.Public,
                    options = listOf("Auto", "Philippines", "USA", "UK", "Japan", "Germany"),
                    onSelect = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.manualCountryOverride.value = if (it == "Auto") null else it 
                    }
                )

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

                // Playlist Manager Section
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Playlists", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        IconButton(onClick = { showAddUrlDialog = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    
                    customPlaylists.forEach { url ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(0.03f))
                                .border(0.5.dp, Color.White.copy(0.05f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(url, modifier = Modifier.weight(1f), maxLines = 1, fontSize = 11.sp, color = Color.Gray)
                            IconButton(onClick = { viewModel.removePlaylist(url) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.5f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            SettingsSection(title = "Video & Playback") {
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
            }

            SettingsSection(title = "UI & Personalization") {
                SettingsSelectorItem(
                    title = "View Mode",
                    options = listOf("Grid", "List"),
                    selected = if (currentViewMode == ViewMode.GRID) "Grid" else "List",
                    onSelect = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.viewMode.value = if (it == "Grid") ViewMode.GRID else ViewMode.LIST 
                    }
                )
            }

            SettingsSection(title = "Storage & Data") {
                SettingsActionItem(
                    title = "Force Refresh Playlist",
                    description = "Reload channels from sources",
                    icon = Icons.Default.Sync,
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.refreshChannels() 
                    }
                )
            }
            Spacer(Modifier.height(120.dp))
        }
    }

    // Dialogs remain functional but styled to match
    if (showAddUrlDialog) {
        AlertDialog(
            onDismissRequest = { showAddUrlDialog = false },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("Add M3U Playlist", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newUrlInput,
                    onValueChange = { newUrlInput = it },
                    placeholder = { Text("https://example.com/playlist.m3u", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addPlaylist(newUrlInput)
                    showAddUrlDialog = false
                    newUrlInput = ""
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddUrlDialog = false }) { Text("Cancel") } }
        )
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false; pinInput = "" },
            containerColor = Color(0xFF1A1A1A),
            title = { Text("Parental Lock", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter PIN to disable Safe Mode", fontSize = 14.sp, color = Color.Gray)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
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
                }) { Text("Verify") }
            },
            dismissButton = { TextButton(onClick = { showPinDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(), 
            style = MaterialTheme.typography.labelSmall, 
            color = MaterialTheme.colorScheme.primary, 
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp), 
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.03f))
                .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
        ) {
            Column { content() }
        }
    }
}

@Composable
fun SettingsToggleItem(title: String, description: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.98f else 1f, animationSpec = tween(100), label = "scale")

    ListItem(
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        headlineContent = { Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp) }, 
        supportingContent = { Text(description, color = Color.Gray, fontSize = 12.sp) }, 
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }, 
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, thumbContent = { if (checked) Icon(Icons.Default.Check, null, Modifier.size(SwitchDefaults.IconSize)) }) }, 
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
        headlineContent = { Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp) }, 
        supportingContent = { Text(description, color = Color.Gray, fontSize = 12.sp) }, 
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }, 
        trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = Color.DarkGray) }, 
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun SettingsSelectorItem(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp), 
        horizontalArrangement = Arrangement.SpaceBetween, 
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Row(
            modifier = Modifier
                .background(Color.White.copy(0.05f), RoundedCornerShape(12.dp))
                .border(0.5.dp, Color.White.copy(0.1f), RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "scale")

                Box(
                    modifier = Modifier
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable(interactionSource = interactionSource, indication = null) { onSelect(option) }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(option, color = if (isSelected) Color.Black else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Black)
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
            headlineContent = { Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp) }, 
            supportingContent = { Text(description, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }, 
            leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }, 
            trailingContent = { Icon(Icons.Default.ArrowDropDown, null, tint = Color.DarkGray) }, 
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
        DropdownMenu(
            expanded = expanded, 
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1A1A1A)).border(0.5.dp, Color.White.copy(0.1f))
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 14.sp) }, 
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}
