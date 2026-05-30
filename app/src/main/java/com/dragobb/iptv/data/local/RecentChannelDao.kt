package com.dragobb.iptv.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentChannelDao {
    @Query("SELECT * FROM recent_channels ORDER BY lastWatched DESC LIMIT 10")
    fun getRecentChannels(): Flow<List<RecentChannel>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentChannel(recentChannel: RecentChannel)

    @Delete
    suspend fun deleteRecent(recentChannel: RecentChannel)

    @Query("DELETE FROM recent_channels")
    suspend fun clearAll()
}
