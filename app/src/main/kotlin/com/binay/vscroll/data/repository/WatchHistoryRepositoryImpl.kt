package com.binay.vscroll.data.repository

import com.binay.vscroll.data.local.dao.DailyWatchRecordDao
import com.binay.vscroll.data.local.dao.WatchHistoryDao
import com.binay.vscroll.data.local.entity.DailyWatchRecordEntity
import com.binay.vscroll.data.local.entity.WatchHistoryEntity
import com.binay.vscroll.domain.model.ActivityDashboardData
import com.binay.vscroll.domain.model.DailyActivityBar
import com.binay.vscroll.domain.model.DailyScreenTimeDetails
import com.binay.vscroll.domain.model.DailyWatchSessionItem
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.model.WatchHistoryItem
import com.binay.vscroll.domain.repository.VideoRepository
import com.binay.vscroll.domain.repository.WatchHistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class WatchHistoryRepositoryImpl(
    private val watchHistoryDao: WatchHistoryDao,
    private val dailyWatchRecordDao: DailyWatchRecordDao,
    private val videoRepository: VideoRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : WatchHistoryRepository {

    override suspend fun recordPlayback(
        video: VideoItem,
        watchTimeMs: Long,
        startWallClockMs: Long,
        endWallClockMs: Long,
        isNewSession: Boolean,
        lastPositionMs: Long
    ) = withContext(ioDispatcher) {
        if (watchTimeMs <= 0) return@withContext

        // 1. Update watch_history aggregate table
        val existing = watchHistoryDao.getWatchHistory(video.id)
        val updatedHistory = if (existing != null) {
            existing.copy(
                lastWatchedAt = endWallClockMs,
                totalWatchTimeMs = existing.totalWatchTimeMs + watchTimeMs,
                sessionCount = existing.sessionCount + (if (isNewSession) 1 else 0),
                lastPositionMs = lastPositionMs,
                cachedTitle = video.title,
                cachedFolderName = video.folderName,
                contentUri = video.uriString,
                cachedDurationMs = video.durationMillis
            )
        } else {
            WatchHistoryEntity(
                mediaStoreId = video.id,
                cachedTitle = video.title,
                cachedFolderName = video.folderName,
                contentUri = video.uriString,
                cachedDurationMs = video.durationMillis,
                firstWatchedAt = startWallClockMs,
                lastWatchedAt = endWallClockMs,
                totalWatchTimeMs = watchTimeMs,
                sessionCount = if (isNewSession) 1 else 0,
                lastPositionMs = lastPositionMs
            )
        }
        watchHistoryDao.insertOrReplace(updatedHistory)

        // 2. Midnight and Date Split Handling for daily_watch_records
        val dateIntervals = splitDurationByCalendarDay(startWallClockMs, endWallClockMs, zoneId)
        var remainingSessionCount = if (isNewSession) 1 else 0
        for ((dateString, chunkMs) in dateIntervals) {
            val sessionInc = remainingSessionCount
            remainingSessionCount = 0 // Session count attributed to the first day segment of the session
            val updated = dailyWatchRecordDao.updateExisting(
                dateString = dateString,
                mediaStoreId = video.id,
                additionalWatchTimeMs = chunkMs,
                additionalSessions = sessionInc,
                lastWatchedAt = endWallClockMs
            )
            if (updated == 0) {
                dailyWatchRecordDao.insert(
                    DailyWatchRecordEntity(
                        dateString = dateString,
                        mediaStoreId = video.id,
                        watchTimeMs = chunkMs,
                        sessionCount = sessionInc,
                        lastWatchedAt = endWallClockMs
                    )
                )
            }
        }
    }

    override fun observeAllHistory(): Flow<List<WatchHistoryItem>> {
        return combine(
            watchHistoryDao.observeAllWatchHistory(),
            videoRepository.getVideos()
        ) { historyEntities, availableVideos ->
            val videosById = availableVideos.associateBy { it.id }
            historyEntities.map { entity ->
                val videoItem = videosById[entity.mediaStoreId]
                WatchHistoryItem(
                    mediaStoreId = entity.mediaStoreId,
                    cachedTitle = entity.cachedTitle,
                    cachedFolderName = entity.cachedFolderName,
                    contentUri = entity.contentUri,
                    cachedDurationMs = entity.cachedDurationMs,
                    firstWatchedAt = entity.firstWatchedAt,
                    lastWatchedAt = entity.lastWatchedAt,
                    totalWatchTimeMs = entity.totalWatchTimeMs,
                    sessionCount = entity.sessionCount,
                    lastPositionMs = entity.lastPositionMs,
                    videoItem = videoItem,
                    isAvailable = (videoItem != null)
                )
            }
        }
    }

    override fun observeDashboardData(goalMinutes: Int?): Flow<ActivityDashboardData> {
        val todayDate = LocalDate.now(zoneId)
        val todayStr = todayDate.toString()
        val sevenDaysAgoStr = todayDate.minusDays(6).toString()

        val daySummaryFlow = dailyWatchRecordDao.observeDaySummary(todayStr)
        val weeklySummaryFlow = dailyWatchRecordDao.observeRangeSummary(sevenDaysAgoStr)
        val breakdownFlow = dailyWatchRecordDao.observeBreakdownFromDate(sevenDaysAgoStr)
        val lifetimeTimeFlow = watchHistoryDao.observeTotalLifetimeWatchTime()
        val lifetimeUniqueFlow = watchHistoryDao.observeUniqueVideosCount()
        val mostWatchedFlow = watchHistoryDao.observeMostWatched(5)
        val allVideosFlow = videoRepository.getVideos()

        // Combine summary and lifetime flows
        val statsFlow = combine(
            daySummaryFlow,
            weeklySummaryFlow,
            breakdownFlow,
            lifetimeTimeFlow,
            lifetimeUniqueFlow
        ) { todaySummary, weeklySummary, breakdownList, lifetimeTime, lifetimeUnique ->
            DashboardMetrics(
                todayWatchTime = todaySummary?.totalWatchTime ?: 0L,
                todayVideos = todaySummary?.videoCount ?: 0,
                todaySessions = todaySummary?.sessionCount ?: 0,
                weeklyWatchTime = weeklySummary?.totalWatchTime ?: 0L,
                weeklyVideos = weeklySummary?.videoCount ?: 0,
                weeklySessions = weeklySummary?.sessionCount ?: 0,
                lifetimeWatchTime = lifetimeTime ?: 0L,
                lifetimeUnique = lifetimeUnique,
                breakdownList = breakdownList
            )
        }

        return combine(
            statsFlow,
            mostWatchedFlow,
            observeAllHistory(),
            allVideosFlow
        ) { metrics, mostWatchedEntities, allHistory, videos ->
            val videosById = videos.associateBy { it.id }
            val mostWatchedItems = mostWatchedEntities.map { entity ->
                val v = videosById[entity.mediaStoreId]
                WatchHistoryItem(
                    mediaStoreId = entity.mediaStoreId,
                    cachedTitle = entity.cachedTitle,
                    cachedFolderName = entity.cachedFolderName,
                    contentUri = entity.contentUri,
                    cachedDurationMs = entity.cachedDurationMs,
                    firstWatchedAt = entity.firstWatchedAt,
                    lastWatchedAt = entity.lastWatchedAt,
                    totalWatchTimeMs = entity.totalWatchTimeMs,
                    sessionCount = entity.sessionCount,
                    lastPositionMs = entity.lastPositionMs,
                    videoItem = v,
                    isAvailable = (v != null)
                )
            }

            val avgWatchTimePerVideo = if (metrics.lifetimeUnique > 0) {
                metrics.lifetimeWatchTime / metrics.lifetimeUnique
            } else {
                0L
            }

            val goalMs = (goalMinutes ?: 0) * 60_000L
            val todayGoalProgress = if (goalMs > 0) {
                (metrics.todayWatchTime.toFloat() / goalMs.toFloat()).coerceAtLeast(0f)
            } else {
                0.0f
            }

            // Build 7-day breakdown bars
            val breakdownMap = metrics.breakdownList.associateBy { it.dateString }
            var maxDailyTime = 1L
            val rawBars = (6 downTo 0).map { daysAgo ->
                val date = todayDate.minusDays(daysAgo.toLong())
                val dStr = date.toString()
                val entry = breakdownMap[dStr]
                val timeMs = entry?.totalWatchTime ?: 0L
                val sess = entry?.sessionCount ?: 0
                if (timeMs > maxDailyTime) maxDailyTime = timeMs

                val label = if (daysAgo == 0) {
                    "Today"
                } else {
                    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                }
                Triple(dStr, label, timeMs to sess)
            }

            val bars = rawBars.map { (dStr, label, stats) ->
                val (timeMs, sess) = stats
                val norm = (timeMs.toFloat() / maxDailyTime.toFloat()).coerceIn(0.04f, 1.0f)
                DailyActivityBar(
                    dateString = dStr,
                    dayLabel = label,
                    watchTimeMs = timeMs,
                    sessionsCount = sess,
                    normalizedHeight = if (timeMs > 0) norm else 0.04f
                )
            }

            ActivityDashboardData(
                todayWatchTimeMs = metrics.todayWatchTime,
                todayVideosCount = metrics.todayVideos,
                todaySessionsCount = metrics.todaySessions,
                reminderMinutes = goalMinutes,
                reminderProgress = todayGoalProgress,
                weeklyWatchTimeMs = metrics.weeklyWatchTime,
                weeklyVideosCount = metrics.weeklyVideos,
                weeklySessionsCount = metrics.weeklySessions,
                weeklyBreakdown = bars,
                lifetimeWatchTimeMs = metrics.lifetimeWatchTime,
                lifetimeUniqueVideosCount = metrics.lifetimeUnique,
                lifetimeAverageWatchTimePerVideoMs = avgWatchTimePerVideo,
                mostWatchedVideos = mostWatchedItems,
                recentHistory = allHistory
            )
        }
    }

    override fun observeDailyScreenTime(dateString: String): Flow<DailyScreenTimeDetails> {
        return combine(
            dailyWatchRecordDao.observeRecordsForDate(dateString),
            watchHistoryDao.observeAllWatchHistory(),
            videoRepository.getVideos()
        ) { records, allHistory, videos ->
            val historyMap = allHistory.associateBy { it.mediaStoreId }
            val videoMap = videos.associateBy { it.id }
            val zone = zoneId
            val parsedDate = try {
                LocalDate.parse(dateString)
            } catch (e: Exception) {
                LocalDate.now()
            }
            val fullDateFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
            val formattedFullDate = parsedDate.format(fullDateFormatter)

            val totalTimeMs = records.sumOf { it.watchTimeMs }
            val videoCount = records.size
            val totalSessions = records.sumOf { it.sessionCount }
            val avgTimeMs = if (videoCount > 0) totalTimeMs / videoCount else 0L

            val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())

            val sessionItems = records.map { record ->
                val history = historyMap[record.mediaStoreId]
                val video = videoMap[record.mediaStoreId]
                val title = video?.title ?: history?.cachedTitle ?: "Video #${record.mediaStoreId}"
                val folder = video?.folderName ?: history?.cachedFolderName ?: "Videos"
                val lastWatchedDateTime = Instant.ofEpochMilli(record.lastWatchedAt).atZone(zone)
                val formattedTime = lastWatchedDateTime.format(timeFormatter)
                val startWallTime = maxOf(0L, record.lastWatchedAt - record.watchTimeMs)
                val startDateTime = Instant.ofEpochMilli(startWallTime).atZone(zone)
                val sessionSpan = "${startDateTime.format(timeFormatter)} – $formattedTime"

                DailyWatchSessionItem(
                    mediaStoreId = record.mediaStoreId,
                    title = title,
                    folderName = folder,
                    watchTimeMs = record.watchTimeMs,
                    sessionCount = record.sessionCount,
                    lastWatchedAt = record.lastWatchedAt,
                    formattedTime = sessionSpan,
                    formattedDuration = formatDurationTimeSpan(record.watchTimeMs)
                )
            }

            val mostWatchedRecord = records.maxByOrNull { it.watchTimeMs }
            val mostWatchedHistory = mostWatchedRecord?.let { historyMap[it.mediaStoreId] }
            val mostWatchedVideo = mostWatchedRecord?.let { videoMap[it.mediaStoreId] }
            val mostWatchedItem = mostWatchedRecord?.let { r ->
                WatchHistoryItem(
                    mediaStoreId = r.mediaStoreId,
                    cachedTitle = mostWatchedHistory?.cachedTitle ?: mostWatchedVideo?.title ?: "Video #${r.mediaStoreId}",
                    cachedFolderName = mostWatchedHistory?.cachedFolderName ?: mostWatchedVideo?.folderName ?: "Videos",
                    contentUri = mostWatchedHistory?.contentUri ?: mostWatchedVideo?.uriString ?: "",
                    cachedDurationMs = mostWatchedHistory?.cachedDurationMs ?: mostWatchedVideo?.durationMillis ?: 0L,
                    firstWatchedAt = mostWatchedHistory?.firstWatchedAt ?: r.lastWatchedAt,
                    lastWatchedAt = r.lastWatchedAt,
                    totalWatchTimeMs = r.watchTimeMs,
                    sessionCount = r.sessionCount,
                    lastPositionMs = mostWatchedHistory?.lastPositionMs ?: 0L,
                    videoItem = mostWatchedVideo
                )
            }

            DailyScreenTimeDetails(
                dateString = dateString,
                formattedFullDate = formattedFullDate,
                totalWatchTimeMs = totalTimeMs,
                formattedTotalWatchTime = formatDurationTimeSpan(totalTimeMs),
                totalVideosWatched = videoCount,
                averageWatchTimePerVideoMs = avgTimeMs,
                formattedAverageWatchTime = formatDurationTimeSpan(avgTimeMs),
                totalSessions = totalSessions,
                mostWatchedVideo = mostWatchedItem,
                sessions = sessionItems
            )
        }
    }

    private fun formatDurationTimeSpan(ms: Long): String {
        val totalSec = ms / 1000
        val sec = totalSec % 60
        val min = (totalSec / 60) % 60
        val hr = totalSec / 3600
        return when {
            hr > 0 && min > 0 -> "${hr}h ${min}m"
            hr > 0 -> "${hr}h"
            min > 0 && sec > 0 -> "${min}m ${sec}s"
            min > 0 -> "${min}m"
            sec > 0 -> "${sec}s"
            else -> "0m"
        }
    }

    override suspend fun deleteHistoryItem(mediaStoreId: Long): Unit = withContext(ioDispatcher) {
        watchHistoryDao.deleteWatchHistory(mediaStoreId)
        dailyWatchRecordDao.deleteForVideo(mediaStoreId)
        Unit
    }

    override suspend fun clearAllHistory(): Unit = withContext(ioDispatcher) {
        watchHistoryDao.clearAllWatchHistory()
        dailyWatchRecordDao.clearAllDailyRecords()
        Unit
    }

    companion object {
        /**
         * Splits a duration spanning startWallClockMs to endWallClockMs across local calendar midnight boundaries.
         * Returns a list of (dateString, durationMs) where the sum of durationMs equals (endWallClockMs - startWallClockMs).
         */
        fun splitDurationByCalendarDay(
            startWallClockMs: Long,
            endWallClockMs: Long,
            zoneId: ZoneId
        ): List<Pair<String, Long>> {
            if (endWallClockMs <= startWallClockMs) return emptyList()

            val result = mutableListOf<Pair<String, Long>>()
            var currentStart = startWallClockMs

            while (currentStart < endWallClockMs) {
                val currentDateTime = Instant.ofEpochMilli(currentStart).atZone(zoneId)
                val currentDate = currentDateTime.toLocalDate()
                val nextDayStart = currentDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

                val segmentEnd = minOf(endWallClockMs, nextDayStart)
                val segmentDuration = segmentEnd - currentStart
                if (segmentDuration > 0) {
                    result.add(currentDate.toString() to segmentDuration)
                }
                currentStart = segmentEnd
            }

            return result
        }
    }
}

private data class DashboardMetrics(
    val todayWatchTime: Long,
    val todayVideos: Int,
    val todaySessions: Int,
    val weeklyWatchTime: Long,
    val weeklyVideos: Int,
    val weeklySessions: Int,
    val lifetimeWatchTime: Long,
    val lifetimeUnique: Int,
    val breakdownList: List<com.binay.vscroll.data.local.dao.DailyBreakdownProjection>
)
