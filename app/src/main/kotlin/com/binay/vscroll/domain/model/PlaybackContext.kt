package com.binay.vscroll.domain.model

sealed interface PlaybackContext {
    data class GlobalRandomFeed(
        val sessionSeed: Long = System.currentTimeMillis(),
        val orderPreference: FeedOrderPreference = FeedOrderPreference.RANDOM
    ) : PlaybackContext

    data class FolderSequential(
        val folderId: String,
        val folderName: String,
        val sortOrder: VideoSortOrder,
        val initialVideoId: Long,
        val initialIndex: Int = 0
    ) : PlaybackContext

    data class CollectionSequential(
        val collectionId: Long,
        val collectionName: String,
        val sortOrder: VideoSortOrder,
        val initialVideoId: Long,
        val initialIndex: Int = 0
    ) : PlaybackContext

    data class InteractionSequential(
        val source: InteractionSource,
        val initialVideoId: Long,
        val initialIndex: Int = 0,
        val queueId: String = "interaction_${source.name}_${System.currentTimeMillis()}"
    ) : PlaybackContext
}

enum class InteractionSource(val displayName: String) {
    LIKED("Liked Videos"),
    SAVED("Saved Videos"),
    NOTES("Video Notes")
}

enum class FeedOrderPreference(val displayName: String) {
    RANDOM("Random shuffle"),
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first")
}

