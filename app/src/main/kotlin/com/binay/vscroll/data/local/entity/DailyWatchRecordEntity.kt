package com.binay.vscroll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_watch_records",
    indices = [
        Index(value = ["date_string", "media_store_id"], unique = true),
        Index(value = ["date_string"])
    ]
)
data class DailyWatchRecordEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "date_string")
    val dateString: String, // "YYYY-MM-DD" local date

    @ColumnInfo(name = "media_store_id")
    val mediaStoreId: Long,

    @ColumnInfo(name = "watch_time_ms")
    val watchTimeMs: Long,

    @ColumnInfo(name = "session_count")
    val sessionCount: Int,

    @ColumnInfo(name = "last_watched_at")
    val lastWatchedAt: Long
)

