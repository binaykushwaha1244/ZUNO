package com.binay.vscroll.domain.model

/**
 * Supported expressive emoji reactions for local videos.
 */
enum class ReactionType(val emoji: String, val label: String) {
    LOVE("❤️", "Love"),
    LIKE("👍", "Like"),
    HAHA("😂", "Haha"),
    WOW("😮", "Wow"),
    SAD("😢", "Sad"),
    FIRE("🔥", "Fire");

    companion object {
        fun fromString(value: String?): ReactionType? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}

/**
 * Domain model representing user-facing interaction states for a local video.
 */
data class UserVideoInteraction(
    val mediaStoreId: Long,
    val isLiked: Boolean = false,
    val likedAt: Long? = null,
    val isBookmarked: Boolean = false,
    val bookmarkedAt: Long? = null,
    val note: String? = null,
    val noteUpdatedAt: Long? = null,
    val reaction: ReactionType? = null
)

/**
 * Represents a saved/liked video matched with local MediaStore availability.
 */
data class UserVideoItem(
    val mediaStoreId: Long,
    val title: String,
    val folderName: String,
    val durationMillis: Long,
    val sizeBytes: Long,
    val contentUriString: String,
    val isAvailable: Boolean,
    val isLiked: Boolean,
    val likedAt: Long?,
    val isBookmarked: Boolean,
    val bookmarkedAt: Long?,
    val videoItem: VideoItem?,
    val reaction: ReactionType? = null
) {
    val formattedDuration: String
        get() = videoItem?.formattedDuration ?: formatMillis(durationMillis)

    val formattedSize: String
        get() = videoItem?.formattedSize ?: formatBytes(sizeBytes)

    companion object {
        fun formatMillis(millis: Long): String {
            if (millis <= 0) return "00:00"
            val totalSeconds = millis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 MB"
            val mb = bytes / (1024.0 * 1024.0)
            return if (mb >= 1024.0) {
                String.format("%.1f GB", mb / 1024.0)
            } else {
                String.format("%.1f MB", mb)
            }
        }
    }
}

/**
 * Represents a private personal note attached to a video, with availability state.
 */
data class UserNoteItem(
    val mediaStoreId: Long,
    val noteText: String,
    val createdAt: Long,
    val updatedAt: Long,
    val title: String,
    val folderName: String,
    val durationMillis: Long,
    val contentUriString: String,
    val isAvailable: Boolean,
    val videoItem: VideoItem?
)

