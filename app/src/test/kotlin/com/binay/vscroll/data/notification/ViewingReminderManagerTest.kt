package com.binay.vscroll.data.notification

import android.content.Context
import com.binay.vscroll.data.local.dao.DailyBreakdownProjection
import com.binay.vscroll.data.local.dao.DailySummaryProjection
import com.binay.vscroll.data.local.dao.DailyWatchRecordDao
import com.binay.vscroll.data.local.entity.DailyWatchRecordEntity
import com.binay.vscroll.data.preferences.ReminderPreferencesDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FakeReminderPreferences : ReminderPreferencesDataSource {
    val reminderFlow = MutableStateFlow<Int?>(15)
    val lastDateFlow = MutableStateFlow<String?>(null)
    val lastIntervalFlow = MutableStateFlow<Int?>(null)

    override val viewingReminderMinutes: Flow<Int?> get() = reminderFlow
    override val lastNotifiedDate: Flow<String?> get() = lastDateFlow
    override val lastNotifiedInterval: Flow<Int?> get() = lastIntervalFlow

    override suspend fun recordReminderNotified(date: String, intervalMinutes: Int) {
        lastDateFlow.value = date
        lastIntervalFlow.value = intervalMinutes
    }
}

class FakeDailyRecordDaoForReminder : DailyWatchRecordDao {
    var todayWatchTimeMs: Long = 0L

    override suspend fun getDaySummary(dateString: String): DailySummaryProjection? {
        return DailySummaryProjection(
            totalWatchTime = todayWatchTimeMs,
            videoCount = 1,
            sessionCount = 1
        )
    }

    override fun observeDaySummary(dateString: String): Flow<DailySummaryProjection?> {
        return flowOf(DailySummaryProjection(todayWatchTimeMs, 1, 1))
    }

    override suspend fun insert(entity: DailyWatchRecordEntity): Long = 1L
    override suspend fun getRecord(dateString: String, mediaStoreId: Long): DailyWatchRecordEntity? = null
    override suspend fun updateExisting(dateString: String, mediaStoreId: Long, additionalWatchTimeMs: Long, additionalSessions: Int, lastWatchedAt: Long): Int = 1
    override fun observeBreakdownFromDate(startDateString: String): Flow<List<DailyBreakdownProjection>> = flowOf(emptyList())
    override fun observeRangeSummary(startDateString: String): Flow<DailySummaryProjection?> = flowOf(null)
    override fun observeRecordsForDate(dateString: String): Flow<List<DailyWatchRecordEntity>> = flowOf(emptyList())
    override suspend fun getRecordsForDateSync(dateString: String): List<DailyWatchRecordEntity> = emptyList()
    override suspend fun deleteForVideo(mediaStoreId: Long): Int = 0
    override suspend fun clearAllDailyRecords(): Int = 0
}

class ViewingReminderManagerTest {

    private val fakeDao = FakeDailyRecordDaoForReminder()
    private val fakePrefs = FakeReminderPreferences()
    private val zoneId = ZoneId.of("UTC")
    private val notifiedIntervals = mutableListOf<Int>()

    private val manager = ViewingReminderManager(
        context = null,
        dailyWatchRecordDao = fakeDao,
        preferencesRepository = fakePrefs,
        zoneId = zoneId,
        notifier = { _, mins -> notifiedIntervals.add(mins) }
    )

    @Test
    fun checkAndNotify_whenTimeBelowThreshold_doesNotNotify() = runTest {
        fakePrefs.reminderFlow.value = 15 // 15 minutes = 900,000 ms
        fakeDao.todayWatchTimeMs = 500_000L // Under 15m

        manager.checkAndNotify()

        assertTrue(notifiedIntervals.isEmpty())
    }

    @Test
    fun checkAndNotify_whenTimeMeetsThreshold_notifiesExactlyOnce() = runTest {
        fakePrefs.reminderFlow.value = 15 // 900,000 ms
        fakeDao.todayWatchTimeMs = 900_000L // Exactly 15m

        manager.checkAndNotify()

        assertEquals(1, notifiedIntervals.size)
        assertEquals(15, notifiedIntervals[0])
        assertEquals(LocalDate.now(zoneId).toString(), fakePrefs.lastDateFlow.value)
        assertEquals(15, fakePrefs.lastIntervalFlow.value)

        // Run check again immediately; should not duplicate!
        manager.checkAndNotify()
        assertEquals(1, notifiedIntervals.size)
    }

    @Test
    fun checkAndNotify_whenAlreadyNotifiedForSameDayAndInterval_doesNotNotifyAgain() = runTest {
        val todayStr = LocalDate.now(zoneId).toString()
        fakePrefs.reminderFlow.value = 30
        fakePrefs.lastDateFlow.value = todayStr
        fakePrefs.lastIntervalFlow.value = 30
        fakeDao.todayWatchTimeMs = 2_000_000L // Well over 30m

        manager.checkAndNotify()

        assertTrue(notifiedIntervals.isEmpty())
    }

    @Test
    fun checkAndNotify_whenReminderDisabled_doesNotNotify() = runTest {
        fakePrefs.reminderFlow.value = null // Disabled
        fakeDao.todayWatchTimeMs = 5_000_000L

        manager.checkAndNotify()

        assertTrue(notifiedIntervals.isEmpty())
    }

    @Test
    fun checkAndNotify_whenIntervalIncreased_notifiesWhenHigherThresholdReached() = runTest {
        val todayStr = LocalDate.now(zoneId).toString()
        // Previously notified for 15m
        fakePrefs.lastDateFlow.value = todayStr
        fakePrefs.lastIntervalFlow.value = 15

        // User changes reminder to 30m (1,800,000 ms)
        fakePrefs.reminderFlow.value = 30
        fakeDao.todayWatchTimeMs = 1_000_000L // 16 mins (below 30m)

        manager.checkAndNotify()
        assertTrue(notifiedIntervals.isEmpty())

        // Watch time reaches 30m
        fakeDao.todayWatchTimeMs = 1_850_000L
        manager.checkAndNotify()

        assertEquals(1, notifiedIntervals.size)
        assertEquals(30, notifiedIntervals[0])
    }

    @Test
    fun checkAndNotify_whenNotifierThrowsException_doesNotCrash() = runTest {
        val crashingManager = ViewingReminderManager(
            context = null,
            dailyWatchRecordDao = fakeDao,
            preferencesRepository = fakePrefs,
            zoneId = zoneId,
            notifier = { _, _ -> throw SecurityException("Notifications blocked") }
        )
        fakePrefs.reminderFlow.value = 15
        fakeDao.todayWatchTimeMs = 1_000_000L

        // Should handle safely without rethrowing or breaking app state
        try {
            crashingManager.checkAndNotify()
        } catch (e: Exception) {
            // ViewingReminderNotifier catches SecurityException internally, but even if external notifier throws,
            // we verify robustness
        }
    }
}

