package com.binay.vscroll.domain.model

data class DailyActivityBar(
    val dateString: String,      // "YYYY-MM-DD"
    val dayLabel: String,        // "Mon", "Tue", "Today"
    val watchTimeMs: Long,
    val sessionsCount: Int,
    val normalizedHeight: Float  // 0.0f to 1.0f relative to max day
)

data class ActivityDashboardData(
    val todayWatchTimeMs: Long = 0L,
    val todayVideosCount: Int = 0,
    val todaySessionsCount: Int = 0,
    val reminderMinutes: Int? = null,
    val reminderProgress: Float = 0.0f, // 0.0 to 1.0+ progress towards break reminder
    val weeklyWatchTimeMs: Long = 0L,
    val weeklyVideosCount: Int = 0,
    val weeklySessionsCount: Int = 0,
    val weeklyBreakdown: List<DailyActivityBar> = emptyList(),
    val lifetimeWatchTimeMs: Long = 0L,
    val lifetimeUniqueVideosCount: Int = 0,
    val lifetimeAverageWatchTimePerVideoMs: Long = 0L,
    val mostWatchedVideos: List<WatchHistoryItem> = emptyList(),
    val recentHistory: List<WatchHistoryItem> = emptyList()
) {
    // Backward compatibility aliases
    val dailyGoalMinutes: Int? get() = reminderMinutes
    val todayGoalProgress: Float get() = reminderProgress
    val formattedTodayWatchTime: String
        get() = formatDuration(todayWatchTimeMs)

    val formattedWeeklyWatchTime: String
        get() = formatDuration(weeklyWatchTimeMs)

    val formattedLifetimeWatchTime: String
        get() = formatDuration(lifetimeWatchTimeMs)

    val formattedAverageWatchTime: String
        get() = formatDuration(lifetimeAverageWatchTimePerVideoMs)

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val sec = totalSec % 60
        val min = (totalSec / 60) % 60
        val hr = totalSec / 3600
        return when {
            hr > 0 -> "${hr}h ${min}m"
            min > 0 -> "${min}m ${sec}s"
            else -> "${sec}s"
        }
    }
}

data class DailyWatchSessionItem(
    val mediaStoreId: Long,
    val title: String,
    val folderName: String,
    val watchTimeMs: Long,
    val sessionCount: Int,
    val lastWatchedAt: Long,
    val formattedTime: String,
    val formattedDuration: String
)

data class DailyScreenTimeDetails(
    val dateString: String,
    val formattedFullDate: String,
    val totalWatchTimeMs: Long = 0L,
    val formattedTotalWatchTime: String = "0m",
    val totalVideosWatched: Int = 0,
    val averageWatchTimePerVideoMs: Long = 0L,
    val formattedAverageWatchTime: String = "0m",
    val totalSessions: Int = 0,
    val mostWatchedVideo: WatchHistoryItem? = null,
    val sessions: List<DailyWatchSessionItem> = emptyList()
)

