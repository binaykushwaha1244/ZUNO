package com.binay.vscroll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "watch_history",
    indices = [
        Index(value = ["last_watched_at"]),
        Index(value = ["total_watch_time_ms"])
    ]
)
data class WatchHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "media_store_id")
    val mediaStoreId: Long,

    @ColumnInfo(name = "cached_title")
    val cachedTitle: String,

    @ColumnInfo(name = "cached_folder_name")
    val cachedFolderName: String,

    @ColumnInfo(name = "content_uri")
    val contentUri: String,

    @ColumnInfo(name = "cached_duration_ms")
    val cachedDurationMs: Long = 0L,

    @ColumnInfo(name = "first_watched_at")
    val firstWatchedAt: Long,

    @ColumnInfo(name = "last_watched_at")
    val lastWatchedAt: Long,

    @ColumnInfo(name = "total_watch_time_ms")
    val totalWatchTimeMs: Long,

    @ColumnInfo(name = "session_count")
    val sessionCount: Int,

    @ColumnInfo(name = "last_position_ms")
    val lastPositionMs: Long = 0L
)

