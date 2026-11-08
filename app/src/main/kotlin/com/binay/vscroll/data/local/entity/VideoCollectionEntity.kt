package com.binay.vscroll.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing an app-managed virtual collection.
 * Virtual collections exist purely in the local Room database and
 * are independent from physical Android storage folders.
 */
@Entity(
    tableName = "video_collections",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["updated_at"])
    ]
)
data class VideoCollectionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

