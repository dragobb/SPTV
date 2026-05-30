package com.dragobb.iptv.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_channels")
data class RecentChannel(
    @PrimaryKey val id: String,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val category: String,
    val country: String,
    val lastWatched: Long = System.currentTimeMillis()
)
