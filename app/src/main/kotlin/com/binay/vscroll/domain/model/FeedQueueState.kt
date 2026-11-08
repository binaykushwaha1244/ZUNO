package com.binay.vscroll.domain.model

data class FeedQueueState(
    val context: PlaybackContext,
    val items: List<VideoItem>,
    val currentIndex: Int = 0
) {
    val currentVideo: VideoItem?
        get() = items.getOrNull(currentIndex)

    val hasNext: Boolean
        get() = currentIndex < items.size - 1

    val hasPrevious: Boolean
        get() = currentIndex > 0
}

