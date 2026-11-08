package com.binay.vscroll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.binay.vscroll.data.local.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: WatchHistoryEntity): Long

    @Query("SELECT * FROM watch_history WHERE media_store_id = :mediaStoreId LIMIT 1")
    suspend fun getWatchHistory(mediaStoreId: Long): WatchHistoryEntity?

    @Query("SELECT * FROM watch_history ORDER BY last_watched_at DESC")
    fun observeAllWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history")
    suspend fun getAllHistorySync(): List<WatchHistoryEntity>

    @Query("SELECT * FROM watch_history ORDER BY total_watch_time_ms DESC LIMIT :limit")
    fun observeMostWatched(limit: Int = 5): Flow<List<WatchHistoryEntity>>

    @Query("SELECT SUM(total_watch_time_ms) FROM watch_history")
    fun observeTotalLifetimeWatchTime(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM watch_history")
    fun observeUniqueVideosCount(): Flow<Int>

    @Query("DELETE FROM watch_history WHERE media_store_id = :mediaStoreId")
    suspend fun deleteWatchHistory(mediaStoreId: Long): Int

    @Query("DELETE FROM watch_history")
    suspend fun clearAllWatchHistory(): Int
}
