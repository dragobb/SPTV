package com.dragobb.iptv.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorites")
    fun getAllFavorites(): Flow<List<FavoriteChannel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(channel: FavoriteChannel)

    @Delete
    suspend fun deleteFavorite(channel: FavoriteChannel)

    @Query("SELECT EXISTS(SELECT * FROM favorites WHERE id = :channelId)")
    fun isFavorite(channelId: String): Flow<Boolean>
}
