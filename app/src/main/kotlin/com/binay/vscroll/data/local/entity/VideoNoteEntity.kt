package com.binay.vscroll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_notes",
    indices = [
        Index(value = ["updated_at"])
    ]
)
data class VideoNoteEntity(
    @PrimaryKey
    @ColumnInfo(name = "media_store_id")
    val mediaStoreId: Long,

    @ColumnInfo(name = "note_text")
    val noteText: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "cached_title")
    val cachedTitle: String = "",

    @ColumnInfo(name = "cached_folder_name")
    val cachedFolderName: String = "",

    @ColumnInfo(name = "cached_duration_ms")
    val cachedDurationMs: Long = 0L,

    @ColumnInfo(name = "cached_uri")
    val cachedUri: String = ""
)

