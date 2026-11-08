package com.binay.vscroll.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.activityDataStore: DataStore<Preferences> by preferencesDataStore(name = "vscroll_activity_settings")

interface ReminderPreferencesDataSource {
    val viewingReminderMinutes: Flow<Int?>
    val lastNotifiedDate: Flow<String?>
    val lastNotifiedInterval: Flow<Int?>
    suspend fun recordReminderNotified(date: String, intervalMinutes: Int)
}

class ActivityPreferencesRepository(private val context: Context) : ReminderPreferencesDataSource {

    private val viewingReminderKey = intPreferencesKey("viewing_reminder_minutes")
    private val legacyDailyGoalKey = intPreferencesKey("daily_watch_goal_minutes")
    private val lastNotifiedDateKey = stringPreferencesKey("reminder_last_notified_date")
    private val lastNotifiedIntervalKey = intPreferencesKey("reminder_last_notified_interval")

    override val viewingReminderMinutes: Flow<Int?> = context.activityDataStore.data.map { preferences ->
        // Check new key first, fallback to legacy key
        val raw = preferences[viewingReminderKey] ?: preferences[legacyDailyGoalKey] ?: 0
        if (raw > 0) raw else null
    }

    // Backward compatibility property for existing callers/tests
    val dailyWatchGoalMinutes: Flow<Int?> get() = viewingReminderMinutes

    suspend fun setViewingReminderMinutes(minutes: Int?) {
        context.activityDataStore.edit { preferences ->
            val value = if (minutes != null && minutes > 0) minutes else 0
            preferences[viewingReminderKey] = value
            preferences[legacyDailyGoalKey] = value // Keep legacy key in sync
        }
    }

    // Backward compatibility function for existing callers/tests
    suspend fun setDailyWatchGoalMinutes(minutes: Int?) = setViewingReminderMinutes(minutes)

    override val lastNotifiedDate: Flow<String?> = context.activityDataStore.data.map { preferences ->
        preferences[lastNotifiedDateKey]
    }

    override val lastNotifiedInterval: Flow<Int?> = context.activityDataStore.data.map { preferences ->
        preferences[lastNotifiedIntervalKey]
    }

    override suspend fun recordReminderNotified(date: String, intervalMinutes: Int) {
        context.activityDataStore.edit { preferences ->
            preferences[lastNotifiedDateKey] = date
            preferences[lastNotifiedIntervalKey] = intervalMinutes
        }
    }
}
