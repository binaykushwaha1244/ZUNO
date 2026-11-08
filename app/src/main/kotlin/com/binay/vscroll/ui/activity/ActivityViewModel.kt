package com.binay.vscroll.ui.activity

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.binay.vscroll.data.local.VScrollDatabase
import com.binay.vscroll.data.preferences.ActivityPreferencesRepository
import com.binay.vscroll.data.repository.MediaStoreVideoRepository
import com.binay.vscroll.data.repository.WatchHistoryRepositoryImpl
import com.binay.vscroll.domain.model.ActivityDashboardData
import com.binay.vscroll.domain.model.WatchHistoryItem
import com.binay.vscroll.domain.repository.WatchHistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ActivityViewModel @JvmOverloads constructor(
    application: Application,
    private val watchHistoryRepository: WatchHistoryRepository = WatchHistoryRepositoryImpl(
        watchHistoryDao = VScrollDatabase.getInstance(application).watchHistoryDao(),
        dailyWatchRecordDao = VScrollDatabase.getInstance(application).dailyWatchRecordDao(),
        videoRepository = MediaStoreVideoRepository(application)
    ),
    private val activityPreferencesRepository: ActivityPreferencesRepository = ActivityPreferencesRepository(application)
) : AndroidViewModel(application) {

    val viewingReminderMinutes: StateFlow<Int?> = activityPreferencesRepository.viewingReminderMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Backward compatibility alias
    val dailyGoalMinutes: StateFlow<Int?> get() = viewingReminderMinutes

    @OptIn(ExperimentalCoroutinesApi::class)
    val dashboardData: StateFlow<ActivityDashboardData> = viewingReminderMinutes
        .flatMapLatest { reminder ->
            watchHistoryRepository.observeDashboardData(reminder)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ActivityDashboardData())

    val historyItems: StateFlow<List<WatchHistoryItem>> = watchHistoryRepository.observeAllHistory()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setViewingReminder(minutes: Int?) {
        viewModelScope.launch {
            activityPreferencesRepository.setViewingReminderMinutes(minutes)
        }
    }

    // Backward compatibility alias
    fun setDailyGoal(minutes: Int?) = setViewingReminder(minutes)

    fun deleteHistoryItem(mediaStoreId: Long) {
        viewModelScope.launch {
            watchHistoryRepository.deleteHistoryItem(mediaStoreId)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            watchHistoryRepository.clearAllHistory()
        }
    }
}
