package com.binay.vscroll.domain.model

/**
 * Domain representation of an app-managed virtual collection.
 * Distinct from a physical storage folder.
 */
data class VideoCollection(
    val id: Long,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val videoCount: Int = 0,
    val coverVideoId: Long? = null,
    val coverVideoUri: String? = null
)

