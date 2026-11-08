package com.binay.vscroll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.binay.vscroll.data.local.entity.DailyWatchRecordEntity
import kotlinx.coroutines.flow.Flow

data class DailySummaryProjection(
    val totalWatchTime: Long?,
    val videoCount: Int,
    val sessionCount: Int?
)

data class DailyBreakdownProjection(
    val dateString: String,
    val totalWatchTime: Long?,
    val videoCount: Int,
    val sessionCount: Int?
)

@Dao
interface DailyWatchRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DailyWatchRecordEntity): Long

    @Query("SELECT * FROM daily_watch_records WHERE date_string = :dateString AND media_store_id = :mediaStoreId LIMIT 1")
    suspend fun getRecord(dateString: String, mediaStoreId: Long): DailyWatchRecordEntity?

    @Query(
        """
        UPDATE daily_watch_records
        SET watch_time_ms = watch_time_ms + :additionalWatchTimeMs,
            session_count = session_count + :additionalSessions,
            last_watched_at = :lastWatchedAt
        WHERE date_string = :dateString AND media_store_id = :mediaStoreId
        """
    )
    suspend fun updateExisting(
        dateString: String,
        mediaStoreId: Long,
        additionalWatchTimeMs: Long,
        additionalSessions: Int,
        lastWatchedAt: Long
    ): Int

    @Query(
        """
        SELECT 
            SUM(watch_time_ms) AS totalWatchTime,
            COUNT(DISTINCT media_store_id) AS videoCount,
            SUM(session_count) AS sessionCount
        FROM daily_watch_records
        WHERE date_string = :dateString
        """
    )
    suspend fun getDaySummary(dateString: String): DailySummaryProjection?

    @Query(
        """
        SELECT 
            SUM(watch_time_ms) AS totalWatchTime,
            COUNT(DISTINCT media_store_id) AS videoCount,
            SUM(session_count) AS sessionCount
        FROM daily_watch_records
        WHERE date_string = :dateString
        """
    )
    fun observeDaySummary(dateString: String): Flow<DailySummaryProjection?>

    @Query(
        """
        SELECT 
            date_string AS dateString,
            SUM(watch_time_ms) AS totalWatchTime,
            COUNT(DISTINCT media_store_id) AS videoCount,
            SUM(session_count) AS sessionCount
        FROM daily_watch_records
        WHERE date_string >= :startDateString
        GROUP BY date_string
        ORDER BY date_string ASC
        """
    )
    fun observeBreakdownFromDate(startDateString: String): Flow<List<DailyBreakdownProjection>>

    @Query(
        """
        SELECT 
            SUM(watch_time_ms) AS totalWatchTime,
            COUNT(DISTINCT media_store_id) AS videoCount,
            SUM(session_count) AS sessionCount
        FROM daily_watch_records
        WHERE date_string >= :startDateString
        """
    )
    fun observeRangeSummary(startDateString: String): Flow<DailySummaryProjection?>

    @Query("DELETE FROM daily_watch_records WHERE media_store_id = :mediaStoreId")
    suspend fun deleteForVideo(mediaStoreId: Long): Int

    @Query("DELETE FROM daily_watch_records")
    suspend fun clearAllDailyRecords(): Int
}
