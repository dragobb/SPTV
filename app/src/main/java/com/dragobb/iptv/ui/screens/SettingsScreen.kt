package com.dragobb.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
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
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("System Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onMenuClick()
                    }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
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

                // Playlist Manager
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("Playlists", fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(onClick = { showAddUrlDialog = true }) {
                            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    
                    customPlaylists.forEach { url ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color.White.copy(0.05f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(url, modifier = Modifier.weight(1f), maxLines = 1, fontSize = 12.sp, color = Color.LightGray)
                            IconButton(onClick = { viewModel.removePlaylist(url) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.7f), modifier = Modifier.size(16.dp))
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
            Spacer(Modifier.height(100.dp))
        }
    }

    if (showAddUrlDialog) {
        AlertDialog(
            onDismissRequest = { showAddUrlDialog = false },
            title = { Text("Add M3U Playlist") },
            text = {
                OutlinedTextField(
                    value = newUrlInput,
                    onValueChange = { newUrlInput = it },
                    placeholder = { Text("https://example.com/playlist.m3u") },
                    modifier = Modifier.fillMaxWidth()
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
            title = { Text("Parental Lock") },
            text = {
                Column {
                    Text("Enter PIN to disable Safe Mode")
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                        label = { Text("4-Digit PIN") },
                        singleLine = true,
                        isError = pinError,
                        modifier = Modifier.fillMaxWidth()
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
        Text(text = title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp), letterSpacing = 1.sp)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
    }
}

@Composable
fun SettingsToggleItem(title: String, description: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) }, leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}

@Composable
fun SettingsActionItem(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(modifier = Modifier.clickable { onClick() }, headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) }, leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(Icons.Default.ChevronRight, null) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}

@Composable
fun SettingsDropdownItem(title: String, description: String, icon: ImageVector, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ListItem(modifier = Modifier.clickable { expanded = true }, headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) }, supportingContent = { Text(description, color = MaterialTheme.colorScheme.primary) }, leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(Icons.Default.ArrowDropDown, null) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}

@Composable
fun SettingsEditItem(title: String, value: String, icon: ImageVector, onValueChange: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, fontWeight = FontWeight.SemiBold)
        }
        TextField(value = text, onValueChange = { text = it; onValueChange(it) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), placeholder = { Text("Enter M3U URL...") }, colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent), shape = RoundedCornerShape(12.dp))
    }
}

@Composable
fun SettingsSelectorItem(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontWeight = FontWeight.SemiBold)
        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { index, option ->
                SegmentedButton(selected = selected == option, onClick = { onSelect(option) }, shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)) { Text(option) }
            }
        }
    }
}
