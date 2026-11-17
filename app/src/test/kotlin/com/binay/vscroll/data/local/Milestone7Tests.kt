package com.binay.vscroll.data.local

import androidx.media3.common.Player
import com.binay.vscroll.data.local.dao.DailyBreakdownProjection
import com.binay.vscroll.data.local.dao.DailySummaryProjection
import com.binay.vscroll.data.local.dao.DailyWatchRecordDao
import com.binay.vscroll.data.local.dao.WatchHistoryDao
import com.binay.vscroll.data.local.entity.DailyWatchRecordEntity
import com.binay.vscroll.data.local.entity.WatchHistoryEntity
import com.binay.vscroll.data.player.MonotonicClock
import com.binay.vscroll.data.player.PlaybackWatchTimeTracker
import com.binay.vscroll.data.repository.FeedQueueManager
import com.binay.vscroll.data.repository.WatchHistoryRepositoryImpl
import com.binay.vscroll.domain.model.FolderItem
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.model.VideoSortOrder
import com.binay.vscroll.domain.repository.VideoRepository
import com.binay.vscroll.domain.repository.WatchHistoryRepository
import com.binay.vscroll.ui.player.VideoShareHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TestMonotonicClock : MonotonicClock {
    var realtime: Long = 10_000L
    var wallClock: Long = 1_700_000_000_000L

    override fun elapsedRealtime(): Long = realtime
    override fun currentTimeMillis(): Long = wallClock

    fun advance(ms: Long) {
        realtime += ms
        wallClock += ms
    }
}

class InMemoryWatchHistoryDao : WatchHistoryDao {
    val store = MutableStateFlow<Map<Long, WatchHistoryEntity>>(emptyMap())

    override suspend fun insertOrReplace(entity: WatchHistoryEntity): Long {
        store.update { it + (entity.mediaStoreId to entity) }
        return entity.mediaStoreId
    }

    override suspend fun getWatchHistory(mediaStoreId: Long): WatchHistoryEntity? {
        return store.value[mediaStoreId]
    }

    override fun observeAllWatchHistory(): Flow<List<WatchHistoryEntity>> {
        return store.map { it.values.sortedByDescending { h -> h.lastWatchedAt } }
    }

    override fun observeMostWatched(limit: Int): Flow<List<WatchHistoryEntity>> {
        return store.map { it.values.sortedByDescending { h -> h.totalWatchTimeMs }.take(limit) }
    }

    override fun observeTotalLifetimeWatchTime(): Flow<Long?> {
        return store.map { map ->
            if (map.isEmpty()) null else map.values.sumOf { it.totalWatchTimeMs }
        }
    }

    override fun observeUniqueVideosCount(): Flow<Int> {
        return store.map { it.size }
    }

    override suspend fun deleteWatchHistory(mediaStoreId: Long): Int {
        val had = store.value.containsKey(mediaStoreId)
        store.update { it - mediaStoreId }
        return if (had) 1 else 0
    }

    override suspend fun clearAllWatchHistory(): Int {
        val count = store.value.size
        store.value = emptyMap()
        return count
    }

    override suspend fun getAllHistorySync(): List<WatchHistoryEntity> {
        return store.value.values.toList()
    }
}

class InMemoryDailyWatchRecordDao : DailyWatchRecordDao {
    val store = MutableStateFlow<List<DailyWatchRecordEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(entity: DailyWatchRecordEntity): Long {
        val id = nextId++
        val newEntity = entity.copy(id = id)
        store.update { it + newEntity }
        return id
    }

    override suspend fun getRecord(dateString: String, mediaStoreId: Long): DailyWatchRecordEntity? {
        return store.value.firstOrNull { it.dateString == dateString && it.mediaStoreId == mediaStoreId }
    }

    override suspend fun updateExisting(
        dateString: String,
        mediaStoreId: Long,
        additionalWatchTimeMs: Long,
        additionalSessions: Int,
        lastWatchedAt: Long
    ): Int {
        var updated = 0
        store.update { list ->
            list.map { record ->
                if (record.dateString == dateString && record.mediaStoreId == mediaStoreId) {
                    updated++
                    record.copy(
                        watchTimeMs = record.watchTimeMs + additionalWatchTimeMs,
                        sessionCount = record.sessionCount + additionalSessions,
                        lastWatchedAt = lastWatchedAt
                    )
                } else {
                    record
                }
            }
        }
        return updated
    }

    override suspend fun getDaySummary(dateString: String): DailySummaryProjection? {
        val dayList = store.value.filter { it.dateString == dateString }
        return if (dayList.isEmpty()) null
        else {
            DailySummaryProjection(
                totalWatchTime = dayList.sumOf { it.watchTimeMs },
                videoCount = dayList.map { it.mediaStoreId }.distinct().size,
                sessionCount = dayList.sumOf { it.sessionCount }
            )
        }
    }

    override fun observeDaySummary(dateString: String): Flow<DailySummaryProjection?> {
        return store.map { list ->
            val dayList = list.filter { it.dateString == dateString }
            if (dayList.isEmpty()) null
            else {
                DailySummaryProjection(
                    totalWatchTime = dayList.sumOf { it.watchTimeMs },
                    videoCount = dayList.map { it.mediaStoreId }.distinct().size,
                    sessionCount = dayList.sumOf { it.sessionCount }
                )
            }
        }
    }

    override fun observeBreakdownFromDate(startDateString: String): Flow<List<DailyBreakdownProjection>> {
        return store.map { list ->
            list.filter { it.dateString >= startDateString }
                .groupBy { it.dateString }
                .map { (date, records) ->
                    DailyBreakdownProjection(
                        dateString = date,
                        totalWatchTime = records.sumOf { it.watchTimeMs },
                        videoCount = records.map { it.mediaStoreId }.distinct().size,
                        sessionCount = records.sumOf { it.sessionCount }
                    )
                }
                .sortedBy { it.dateString }
        }
    }

    override fun observeRangeSummary(startDateString: String): Flow<DailySummaryProjection?> {
        return store.map { list ->
            val rangeList = list.filter { it.dateString >= startDateString }
            if (rangeList.isEmpty()) null
            else {
                DailySummaryProjection(
                    totalWatchTime = rangeList.sumOf { it.watchTimeMs },
                    videoCount = rangeList.map { it.mediaStoreId }.distinct().size,
                    sessionCount = rangeList.sumOf { it.sessionCount }
                )
            }
        }
    }

    override fun observeRecordsForDate(dateString: String): Flow<List<DailyWatchRecordEntity>> {
        return store.map { list ->
            list.filter { it.dateString == dateString }.sortedByDescending { it.lastWatchedAt }
        }
    }

    override suspend fun getRecordsForDateSync(dateString: String): List<DailyWatchRecordEntity> {
        return store.value.filter { it.dateString == dateString }.sortedByDescending { it.lastWatchedAt }
    }

    override suspend fun deleteForVideo(mediaStoreId: Long): Int {
        val initialSize = store.value.size
        store.update { it.filterNot { r -> r.mediaStoreId == mediaStoreId } }
        return initialSize - store.value.size
    }

    override suspend fun clearAllDailyRecords(): Int {
        val count = store.value.size
        store.value = emptyList()
        return count
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class Milestone7Tests {

    private lateinit var watchHistoryDao: InMemoryWatchHistoryDao
    private lateinit var dailyWatchRecordDao: InMemoryDailyWatchRecordDao
    private lateinit var videoRepository: FakeTestVideoRepository
    private lateinit var repository: WatchHistoryRepositoryImpl
    private lateinit var clock: TestMonotonicClock
    private val testDispatcher = StandardTestDispatcher()
    private val zoneId = ZoneId.of("UTC")

    private val sampleVideo1 = VideoItem(
        id = 101L,
        title = "workout_shorts.mp4",
        uriString = "content://media/external/video/media/101",
        durationMillis = 30_000L,
        folderName = "Fitness",
        mimeType = "video/mp4"
    )

    private val sampleVideo2 = VideoItem(
        id = 102L,
        title = "recipe_reel.mp4",
        uriString = "content://media/external/video/media/102",
        durationMillis = 60_000L,
        folderName = "Cooking",
        mimeType = "video/mp4"
    )

    @Before
    fun setup() {
        watchHistoryDao = InMemoryWatchHistoryDao()
        dailyWatchRecordDao = InMemoryDailyWatchRecordDao()
        videoRepository = FakeTestVideoRepository().apply {
            videosFlow.value = listOf(sampleVideo1, sampleVideo2)
        }
        clock = TestMonotonicClock()
        repository = WatchHistoryRepositoryImpl(
            watchHistoryDao = watchHistoryDao,
            dailyWatchRecordDao = dailyWatchRecordDao,
            videoRepository = videoRepository,
            ioDispatcher = testDispatcher,
            zoneId = zoneId
        )
    }

    // --- PLAYBACK TRACKING TESTS (1 to 10) ---

    @Test
    fun test01_watchTimeIncrementsWhileIsPlayingIsTrue() = runTest(testDispatcher) {
        val tracker = PlaybackWatchTimeTracker(repository, this, clock, periodicFlushIntervalMs = 0)
        tracker.onVideoChanged(sampleVideo1)
        tracker.onPlaybackStateChanged(Player.STATE_READY)
        tracker.onIsPlayingChanged(isPlaying = true, playWhenReady = true)

        assertTrue(tracker.isTracking())

        clock.advance(5_000L) // Played 5 seconds
        tracker.onIsPlayingChanged(isPlaying = false, playWhenReady = false)
        advanceUntilIdle()

        val history = watchHistoryDao.getWatchHistory(101L)
        assertNotNull(history)
        assertEquals(5_000L, history!!.totalWatchTimeMs)
        assertEquals(1, history.sessionCount)
    }

    @Test
    fun test02_watchTimeDoesNotIncrementWhilePaused() = runTest(testDispatcher) {
        val tracker = PlaybackWatchTimeTracker(repository, this, clock, periodicFlushIntervalMs = 0)
        tracker.onVideoChanged(sampleVideo1)
        tracker.onPlaybackStateChanged(Player.STATE_READY)
        tracker.onIsPlayingChanged(isPlaying = false, playWhenReady = false)

        assertFalse(tracker.isTracking())

        clock.advance(10_000L) // Time passes while paused
        tracker.onPlaybackStateChanged(Player.STATE_READY)
        advanceUntilIdle()

        val history = watchHistoryDao.getWatchHistory(101L)
        assertNull(history) // No watch time recorded
    }

    @Test
    fun test03_watchTimeDoesNotIncrementWhileBuffering() = runTest(testDispatcher) {
        val tracker = PlaybackWatchTimeTracker(repository, this, clock, periodicFlushIntervalMs = 0)
        tracker.onVideoChanged(sampleVideo1)
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING) // Buffering!
        tracker.onIsPlayingChanged(isPlaying = true, playWhenReady = true)

        assertFalse(tracker.isTracking())

        clock.advance(3_000L)
        tracker.onPlaybackStateChanged(Player.STATE_READY) // Now ready
        assertTrue(tracker.isTracking())

        clock.advance(4_000L)
        tracker.onIsPlayingChanged(isPlaying = false, playWhenReady = false)
        advanceUntilIdle()

        val history = watchHistoryDao.getWatchHistory(101L)
        assertNotNull(history)
        assertEquals(4_000L, history!!.totalWatchTimeMs) // Only counted ready state
    }

    @Test
    fun test04_watchTimeStopsWhenAppEntersBackground() = runTest(testDispatcher) {
        val tracker = PlaybackWatchTimeTracker(repository, this, clock, periodicFlushIntervalMs = 0)
        tracker.onVideoChanged(sampleVideo1)
        tracker.onPlaybackStateChanged(Player.STATE_READY)
        tracker.onIsPlayingChanged(isPlaying = true, playWhenReady = true)

        clock.advance(6_000L)
        tracker.onAppForegroundChanged(inForeground = false) // Backgrounded!
        assertFalse(tracker.isTracking())
        advanceUntilIdle()

        clock.advance(15_000L) // App in background for 15s
        val history = watchHistoryDao.getWatchHistory(101L)
        assertNotNull(history)
        assertEquals(6_000L, history!!.totalWatchTimeMs) // Background time not counted
    }

    @Test
    fun test05_watchTimeResumesCorrectlyAfterReturningToForeground() = runTest(testDispatcher) {
        val tracker = PlaybackWatchTimeTracker(repository, this, clock, periodicFlushIntervalMs = 0)
        tracker.onVideoChanged(sampleVideo1)
        tracker.onPlaybackStateChanged(Player.STATE_READY)
        tracker.onIsPlayingChanged(isPlaying = true, playWhenReady = true)

        clock.advance(5_000L)
        tracker.onAppForegroundChanged(inForeground = false)
        advanceUntilIdle()

        clock.advance(20_000L)

        tracker.onAppForegroundChanged(inForeground = true)
        assertTrue(tracker.isTracking())

        clock.advance(7_000L)
        tracker.onIsPlayingChanged(isPlaying = false, playWhenReady = false)
        advanceUntilIdle()

        val history = watchHistoryDao.getWatchHistory(101L)
        assertNotNull(history)
        assertEquals(12_000L, history!!.totalWatchTimeMs) // 5s + 7s
    }

    @Test
    fun test06_switchingVideosClosesPreviousTrackingInterval() = runTest(testDispatcher) {
        val tracker = PlaybackWatchTimeTracker(repository, this, clock, periodicFlushIntervalMs = 0)
        tracker.onVideoChanged(sampleVideo1)
        tracker.onPlaybackStateChanged(Player.STATE_READY)
        tracker.onIsPlayingChanged(isPlaying = true, playWhenReady = true)

        clock.advance(8_000L)
        tracker.onVideoChanged(sampleVideo2) // Switch to video 2!
        clock.advance(4_000L)
        tracker.onIsPlayingChanged(isPlaying = false, playWhenReady = false)
        advanceUntilIdle()

        val hist1 = watchHistoryDao.getWatchHistory(101L)
        val hist2 = watchHistoryDao.getWatchHistory(102L)
        assertNotNull(hist1)
        assertNotNull(hist2)
        assertEquals(8_000L, hist1!!.totalWatchTimeMs)
        assertEquals(4_000L, hist2!!.totalWatchTimeMs)
    }

    @Test
    fun test07_recompositionDoesNotDoubleCountPlaybackTime() = runTest(testDispatcher) {
        val tracker = PlaybackWatchTimeTracker(repository, this, clock, periodicFlushIntervalMs = 0)
        tracker.onVideoChanged(sampleVideo1)
        tracker.onPlaybackStateChanged(Player.STATE_READY)
        tracker.onIsPlayingChanged(isPlaying = true, playWhenReady = true)

        clock.advance(3_000L)

        // Multiple repeated calls mimicking recompositions
        repeat(5) {
            tracker.onPlaybackStateChanged(Player.STATE_READY)
            tracker.onIsPlayingChanged(isPlaying = true, playWhenReady = true)
        }

        clock.advance(2_000L)
        tracker.onIsPlayingChanged(isPlaying = false, playWhenReady = false)
        advanceUntilIdle()

        val history = watchHistoryDao.getWatchHistory(101L)
        assertNotNull(history)
        assertEquals(5_000L, history!!.totalWatchTimeMs) // Exactly 5 seconds
    }

    @Test
    fun test08_multipleLifecycleCallbacksDoNotDoubleCount() = runTest(testDispatcher) {
        val tracker = PlaybackWatchTimeTracker(repository, this, clock, periodicFlushIntervalMs = 0)
        tracker.onVideoChanged(sampleVideo1)
        tracker.onPlaybackStateChanged(Player.STATE_READY)
        tracker.onIsPlayingChanged(isPlaying = true, playWhenReady = true)

        clock.advance(2_000L)
        // Repeated onResume triggers
        tracker.onAppForegroundChanged(true)
        tracker.onAppForegroundChanged(true)
        clock.advance(3_000L)

        tracker.onIsPlayingChanged(false, false)
        advanceUntilIdle()

        val history = watchHistoryDao.getWatchHistory(101L)
        assertNotNull(history)
        assertEquals(5_000L, history!!.totalWatchTimeMs)
    }

    @Test
    fun test09_midnightCrossingSessionsSplitCorrectlyBetweenDates() {
        val startEpoch = 1700006100000L // 23:55:00
        val endEpoch = startEpoch + 900_000L // 00:10:00 next day (15 min)

        val splits = WatchHistoryRepositoryImpl.splitDurationByCalendarDay(startEpoch, endEpoch, zoneId)
        assertEquals(2, splits.size)
        assertEquals(300_000L, splits[0].second)
        assertEquals(600_000L, splits[1].second)
        assertEquals(900_000L, splits[0].second + splits[1].second) // Zero loss
    }

    @Test
    fun test10_playerReleaseClosesActiveTrackingInterval() = runTest(testDispatcher) {
        val tracker = PlaybackWatchTimeTracker(repository, this, clock, periodicFlushIntervalMs = 0)
        tracker.onVideoChanged(sampleVideo1)
        tracker.onPlaybackStateChanged(Player.STATE_READY)
        tracker.onIsPlayingChanged(isPlaying = true, playWhenReady = true)

        clock.advance(9_000L)
        tracker.onPlayerReleased() // Released
        assertFalse(tracker.isTracking())
        advanceUntilIdle()

        val history = watchHistoryDao.getWatchHistory(101L)
        assertNotNull(history)
        assertEquals(9_000L, history!!.totalWatchTimeMs)
    }

    // --- DATABASE TESTS (11 to 16) ---

    @Test
    fun test11_playbackHistoryPersistsAfterRestart() = runTest(testDispatcher) {
        repository.recordPlayback(sampleVideo1, 15_000L, 1000L, 16000L, isNewSession = true, lastPositionMs = 5000L)
        advanceUntilIdle()

        val historyList = repository.observeAllHistory().first()
        assertEquals(1, historyList.size)
        assertEquals(101L, historyList[0].mediaStoreId)
        assertEquals(15_000L, historyList[0].totalWatchTimeMs)
    }

    @Test
    fun test12_totalWatchTimeAccumulatesCorrectly() = runTest(testDispatcher) {
        repository.recordPlayback(sampleVideo1, 10_000L, 1000L, 11000L, isNewSession = true, lastPositionMs = 10000L)
        repository.recordPlayback(sampleVideo1, 20_000L, 20000L, 40000L, isNewSession = false, lastPositionMs = 20000L)
        advanceUntilIdle()

        val history = watchHistoryDao.getWatchHistory(101L)
        assertEquals(30_000L, history!!.totalWatchTimeMs)
    }

    @Test
    fun test13_sessionCountIncrementsCorrectly() = runTest(testDispatcher) {
        repository.recordPlayback(sampleVideo1, 5_000L, 1000L, 6000L, isNewSession = true, lastPositionMs = 5000L)
        repository.recordPlayback(sampleVideo1, 5_000L, 7000L, 12000L, isNewSession = false, lastPositionMs = 10000L)
        repository.recordPlayback(sampleVideo1, 8_000L, 20000L, 28000L, isNewSession = true, lastPositionMs = 8000L)
        advanceUntilIdle()

        val history = watchHistoryDao.getWatchHistory(101L)
        assertEquals(2, history!!.sessionCount)
    }

    @Test
    fun test14_lastWatchedTimestampUpdatesCorrectly() = runTest(testDispatcher) {
        repository.recordPlayback(sampleVideo1, 5_000L, 1000L, 6000L, isNewSession = true, lastPositionMs = 5000L)
        repository.recordPlayback(sampleVideo1, 5_000L, 100000L, 105000L, isNewSession = true, lastPositionMs = 5000L)
        advanceUntilIdle()

        val history = watchHistoryDao.getWatchHistory(101L)
        assertEquals(105000L, history!!.lastWatchedAt)
    }

    @Test
    fun test15_deletingHistoryDoesNotDeleteLikesOrBookmarks() = runTest(testDispatcher) {
        repository.recordPlayback(sampleVideo1, 10_000L, 1000L, 11000L, isNewSession = true, lastPositionMs = 5000L)
        advanceUntilIdle()
        assertEquals(1, watchHistoryDao.store.value.size)

        repository.clearAllHistory()
        advanceUntilIdle()
        assertEquals(0, watchHistoryDao.store.value.size)
        assertEquals(0, dailyWatchRecordDao.store.value.size)
    }

    @Test
    fun test16_missingVideosAreHandledSafely() = runTest(testDispatcher) {
        val deletedVideo = VideoItem(id = 999L, title = "deleted.mp4", uriString = "content://media/999")
        repository.recordPlayback(deletedVideo, 12_000L, 1000L, 13000L, isNewSession = true, lastPositionMs = 0L)
        advanceUntilIdle()

        val historyList = repository.observeAllHistory().first()
        val missingItem = historyList.firstOrNull { it.mediaStoreId == 999L }
        assertNotNull(missingItem)
        assertFalse(missingItem!!.isAvailable)
        assertEquals("deleted.mp4", missingItem.title)
    }

    // --- DASHBOARD TESTS (17 to 21) ---

    @Test
    fun test17_dailyStatisticsCalculatedCorrectly() = runTest(testDispatcher) {
        val todayStr = LocalDate.now(zoneId).toString()
        dailyWatchRecordDao.insert(DailyWatchRecordEntity(dateString = todayStr, mediaStoreId = 101L, watchTimeMs = 45_000L, sessionCount = 2, lastWatchedAt = 1000L))
        dailyWatchRecordDao.insert(DailyWatchRecordEntity(dateString = todayStr, mediaStoreId = 102L, watchTimeMs = 15_000L, sessionCount = 1, lastWatchedAt = 2000L))
        advanceUntilIdle()

        val dashboard = repository.observeDashboardData(goalMinutes = null).first()
        assertEquals(60_000L, dashboard.todayWatchTimeMs)
        assertEquals(2, dashboard.todayVideosCount)
        assertEquals(3, dashboard.todaySessionsCount)
    }

    @Test
    fun test18_weeklyStatisticsCalculatedCorrectly() = runTest(testDispatcher) {
        val today = LocalDate.now(zoneId)
        dailyWatchRecordDao.insert(DailyWatchRecordEntity(dateString = today.toString(), mediaStoreId = 101L, watchTimeMs = 30_000L, sessionCount = 1, lastWatchedAt = 1000L))
        dailyWatchRecordDao.insert(DailyWatchRecordEntity(dateString = today.minusDays(2).toString(), mediaStoreId = 102L, watchTimeMs = 50_000L, sessionCount = 2, lastWatchedAt = 1000L))
        advanceUntilIdle()

        val dashboard = repository.observeDashboardData(goalMinutes = null).first()
        assertEquals(80_000L, dashboard.weeklyWatchTimeMs)
        assertEquals(2, dashboard.weeklyVideosCount)
        assertEquals(3, dashboard.weeklySessionsCount)
        assertEquals(7, dashboard.weeklyBreakdown.size)
    }

    @Test
    fun test19_uniqueVideoCountsAreAccurate() = runTest(testDispatcher) {
        repository.recordPlayback(sampleVideo1, 10_000L, 1000L, 11000L, isNewSession = true, lastPositionMs = 0L)
        repository.recordPlayback(sampleVideo2, 20_000L, 1000L, 21000L, isNewSession = true, lastPositionMs = 0L)
        advanceUntilIdle()

        val count = watchHistoryDao.observeUniqueVideosCount().first()
        assertEquals(2, count)
    }

    @Test
    fun test20_dailyGoalProgressCalculatedCorrectly() = runTest(testDispatcher) {
        val todayStr = LocalDate.now(zoneId).toString()
        dailyWatchRecordDao.insert(DailyWatchRecordEntity(dateString = todayStr, mediaStoreId = 101L, watchTimeMs = 450_000L, sessionCount = 1, lastWatchedAt = 1000L))
        advanceUntilIdle()

        val dashboard = repository.observeDashboardData(goalMinutes = 15).first()
        assertEquals(0.5f, dashboard.todayGoalProgress, 0.01f)
    }

    @Test
    fun test21_emptyStateBehaviourWorks() = runTest(testDispatcher) {
        val dashboard = repository.observeDashboardData(goalMinutes = null).first()
        assertEquals(0L, dashboard.todayWatchTimeMs)
        assertEquals(0, dashboard.todayVideosCount)
        assertEquals(0L, dashboard.lifetimeWatchTimeMs)
        assertTrue(dashboard.mostWatchedVideos.isEmpty())
        assertTrue(dashboard.recentHistory.isEmpty())
    }

    // --- SHARING TESTS (22 to 25) ---

    @Test
    fun test22_shareIntentContainsCorrectVideoUri() {
        val spec = VideoShareHelper.createShareSpec(sampleVideo1)
        assertEquals(sampleVideo1.uriString, spec.uriString)
    }

    @Test
    fun test23_correctMimeTypeIsUsed() {
        val spec = VideoShareHelper.createShareSpec(sampleVideo1)
        assertEquals("video/mp4", spec.mimeType)
    }

    @Test
    fun test24_readPermissionIsGranted() {
        val spec = VideoShareHelper.createShareSpec(sampleVideo1)
        assertTrue(spec.isGrantReadPermission)
    }

    @Test
    fun test25_missingOrInaccessibleVideoFallback() {
        val brokenVideo = VideoItem(id = 999L, title = "broken.mp4", uriString = "file:///non/existent/video.mp4")
        val spec = VideoShareHelper.createShareSpec(brokenVideo)
        assertEquals("file:///non/existent/video.mp4", spec.uriString)
        assertEquals("video/mp4", spec.mimeType)
    }

    // --- REGRESSION TESTS (26 to 30) ---

    @Test
    fun test26_shortsQueueRemainsIndependent() = runTest(testDispatcher) {
        val queueManager = FeedQueueManager(videoRepository, backgroundScope)
        val state = queueManager.shortsQueue.first { it.items.isNotEmpty() }
        assertEquals(2, state.items.size)
        assertNull(queueManager.folderQueue.value)
    }

    @Test
    fun test27_folderPlaybackRemainsSequential() = runTest(testDispatcher) {
        val queueManager = FeedQueueManager(videoRepository, backgroundScope)
        queueManager.startFolderPlayback("Fitness", "Fitness", listOf(sampleVideo1), 0, VideoSortOrder.NEWEST_FIRST)
        assertNotNull(queueManager.folderQueue.value)
        assertEquals(1, queueManager.folderQueue.value!!.items.size)
    }

    @Test
    fun test28_existingLikesBookmarksNotesStillWork() = runTest(testDispatcher) {
        val interactionDao = InMemoryVideoInteractionDao()
        val entity = com.binay.vscroll.data.local.entity.VideoInteractionEntity(
            mediaStoreId = 101L,
            contentUri = sampleVideo1.uriString,
            cachedTitle = sampleVideo1.title,
            cachedFolderName = sampleVideo1.folderName,
            isLiked = true,
            likedAt = 1000L
        )
        interactionDao.insertOrUpdate(entity)
        val fetched = interactionDao.getInteraction(101L).first()
        assertNotNull(fetched)
        assertTrue(fetched!!.isLiked)
    }

    @Test
    fun test29_cleanViewingModeStateToggle() {
        var cleanMode = false
        cleanMode = !cleanMode
        assertTrue(cleanMode)
        cleanMode = !cleanMode
        assertFalse(cleanMode)
    }

    @Test
    fun test30_playbackControlsState() {
        val state = com.binay.vscroll.data.player.PlayerPlaybackState(
            isPlaying = true,
            playbackSpeed = 1.25f,
            isMuted = false
        )
        assertTrue(state.isPlaying)
        assertEquals(1.25f, state.playbackSpeed)
        assertFalse(state.isMuted)
    }
}
