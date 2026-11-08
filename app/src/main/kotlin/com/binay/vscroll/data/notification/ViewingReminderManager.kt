package com.binay.vscroll.data.notification

import android.content.Context
import com.binay.vscroll.data.local.dao.DailyWatchRecordDao
import com.binay.vscroll.data.preferences.ReminderPreferencesDataSource
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class ViewingReminderManager(
    private val context: Context?,
    private val dailyWatchRecordDao: DailyWatchRecordDao,
    private val preferencesRepository: ReminderPreferencesDataSource,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val notifier: (Context?, Int) -> Unit = { ctx, mins ->
        ctx?.let { ViewingReminderNotifier.sendBreakReminderNotification(it, mins) }
    }
) {

    suspend fun checkAndNotify() {
        val reminderMinutes = preferencesRepository.viewingReminderMinutes.first() ?: return
        if (reminderMinutes <= 0) return

        val todayDate = LocalDate.now(zoneId).toString()
        val summary = dailyWatchRecordDao.getDaySummary(todayDate)
        val todayWatchTimeMs = summary?.totalWatchTime ?: 0L

        val thresholdMs = reminderMinutes * 60_000L
        if (todayWatchTimeMs >= thresholdMs) {
            val lastNotifiedDate = preferencesRepository.lastNotifiedDate.first()
            val lastNotifiedInterval = preferencesRepository.lastNotifiedInterval.first()

            val alreadyNotifiedForTodayAndInterval =
                (lastNotifiedDate == todayDate && lastNotifiedInterval == reminderMinutes)

            if (!alreadyNotifiedForTodayAndInterval) {
                notifier(context, reminderMinutes)
                preferencesRepository.recordReminderNotified(todayDate, reminderMinutes)
            }
        }
    }
}
