package com.binay.vscroll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Cross-reference junction entity linking virtual collections to MediaStore video IDs.
 * Supports many-to-many relationships (a video can belong to multiple collections).
 * When a collection is deleted, cascade deletion removes its mappings without touching videos.
 */
@Entity(
    tableName = "collection_videos",
    primaryKeys = ["collection_id", "media_store_id"],
    foreignKeys = [
        ForeignKey(
            entity = VideoCollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collection_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["collection_id"]),
        Index(value = ["media_store_id"]),
        Index(value = ["added_at"])
    ]
)
data class CollectionVideoCrossRefEntity(
    @ColumnInfo(name = "collection_id")
    val collectionId: Long,

    @ColumnInfo(name = "media_store_id")
    val mediaStoreId: Long,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)

