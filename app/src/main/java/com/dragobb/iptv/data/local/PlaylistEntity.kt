package com.dragobb.iptv.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_playlists")
data class PlaylistEntity(
    @PrimaryKey val url: String,
    val name: String = "Custom M3U"
)
