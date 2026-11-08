package com.binay.vscroll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_interactions",
    indices = [
        Index(value = ["is_liked"]),
        Index(value = ["is_bookmarked"]),
        Index(value = ["liked_at"]),
        Index(value = ["bookmarked_at"])
    ]
)
data class VideoInteractionEntity(
    @PrimaryKey
    @ColumnInfo(name = "media_store_id")
    val mediaStoreId: Long,

    @ColumnInfo(name = "content_uri")
    val contentUri: String,

    @ColumnInfo(name = "cached_title")
    val cachedTitle: String,

    @ColumnInfo(name = "cached_folder_name")
    val cachedFolderName: String,

    @ColumnInfo(name = "cached_duration_ms")
    val cachedDurationMs: Long = 0L,

    @ColumnInfo(name = "cached_size_bytes")
    val cachedSizeBytes: Long = 0L,

    @ColumnInfo(name = "cached_width")
    val cachedWidth: Int = 0,

    @ColumnInfo(name = "cached_height")
    val cachedHeight: Int = 0,

    @ColumnInfo(name = "cached_mime_type")
    val cachedMimeType: String = "video/mp4",

    @ColumnInfo(name = "cached_date_added")
    val cachedDateAdded: Long = 0L,

    @ColumnInfo(name = "is_liked")
    val isLiked: Boolean = false,

    @ColumnInfo(name = "liked_at")
    val likedAt: Long? = null,

    @ColumnInfo(name = "is_bookmarked")
    val isBookmarked: Boolean = false,

    @ColumnInfo(name = "bookmarked_at")
    val bookmarkedAt: Long? = null,

    @ColumnInfo(name = "tags", defaultValue = "''")
    val tags: String = "",

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

