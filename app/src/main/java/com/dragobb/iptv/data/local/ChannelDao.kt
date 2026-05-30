package com.dragobb.iptv.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun clearAll()

    @Query("SELECT * FROM channels WHERE country = :countryCode")
    fun getChannelsByCountry(countryCode: String): Flow<List<ChannelEntity>>
}
