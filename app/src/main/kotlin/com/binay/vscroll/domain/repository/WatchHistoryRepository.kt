package com.binay.vscroll.domain.repository

import com.binay.vscroll.domain.model.ActivityDashboardData
import com.binay.vscroll.domain.model.WatchHistoryItem
import com.binay.vscroll.domain.model.VideoItem
import kotlinx.coroutines.flow.Flow

interface WatchHistoryRepository {

    suspend fun recordPlayback(
        video: VideoItem,
        watchTimeMs: Long,
        startWallClockMs: Long,
        endWallClockMs: Long,
        isNewSession: Boolean,
        lastPositionMs: Long
    )

    fun observeAllHistory(): Flow<List<WatchHistoryItem>>

    fun observeDashboardData(goalMinutes: Int?): Flow<ActivityDashboardData>

    suspend fun deleteHistoryItem(mediaStoreId: Long)

    suspend fun clearAllHistory()
}

