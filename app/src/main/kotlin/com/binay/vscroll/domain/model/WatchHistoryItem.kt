package com.binay.vscroll.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class HistoryGroup(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("Earlier this week"),
    OLDER("Older")
}

data class WatchHistoryItem(
    val mediaStoreId: Long,
    val cachedTitle: String,
    val cachedFolderName: String,
    val contentUri: String,
    val cachedDurationMs: Long,
    val firstWatchedAt: Long,
    val lastWatchedAt: Long,
    val totalWatchTimeMs: Long,
    val sessionCount: Int,
    val lastPositionMs: Long = 0L,
    val videoItem: VideoItem? = null,
    val isAvailable: Boolean = (videoItem != null)
) {
    val title: String
        get() = videoItem?.title ?: cachedTitle

    val folderName: String
        get() = videoItem?.folderName ?: cachedFolderName

    val durationMillis: Long
        get() = videoItem?.durationMillis ?: cachedDurationMs

    val formattedDuration: String
        get() {
            val totalSeconds = (durationMillis / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val hours = minutes / 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

    val formattedTotalWatchTime: String
        get() {
            val totalSec = totalWatchTimeMs / 1000
            val sec = totalSec % 60
            val min = (totalSec / 60) % 60
            val hr = totalSec / 3600
            return when {
                hr > 0 -> "${hr}h ${min}m"
                min > 0 -> "${min}m ${sec}s"
                else -> "${sec}s"
            }
        }

    val formattedLastWatched: String
        get() {
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            return sdf.format(Date(lastWatchedAt))
        }

    val historyGroup: HistoryGroup
        get() {
            val nowCal = Calendar.getInstance()
            val itemCal = Calendar.getInstance().apply { timeInMillis = lastWatchedAt }

            val nowYear = nowCal.get(Calendar.YEAR)
            val nowDayOfYear = nowCal.get(Calendar.DAY_OF_YEAR)

            val itemYear = itemCal.get(Calendar.YEAR)
            val itemDayOfYear = itemCal.get(Calendar.DAY_OF_YEAR)

            return when {
                nowYear == itemYear && nowDayOfYear == itemDayOfYear -> HistoryGroup.TODAY
                nowYear == itemYear && (nowDayOfYear - itemDayOfYear) == 1 -> HistoryGroup.YESTERDAY
                nowYear == itemYear && (nowDayOfYear - itemDayOfYear) in 2..6 -> HistoryGroup.THIS_WEEK
                else -> HistoryGroup.OLDER
            }
        }
}

