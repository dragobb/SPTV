package com.dragobb.iptv.ui.models

data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val category: String,
    val country: String,
    val isFavorite: Boolean = false,
    val isOnline: Boolean? = null // null = Checking, true = Online, false = Offline
)
