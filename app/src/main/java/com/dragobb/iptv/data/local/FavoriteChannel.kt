package com.dragobb.iptv.data.local

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

@Immutable
@Entity(tableName = "favorites")
data class FavoriteChannel(
    @PrimaryKey val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String?,
    val category: String,
    val country: String
)
