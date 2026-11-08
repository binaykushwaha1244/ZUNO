package com.binay.vscroll.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.binay.vscroll.data.local.VScrollDatabase
import com.binay.vscroll.data.player.PlaybackWatchTimeTracker
import com.binay.vscroll.data.player.PlayerPlaybackState
import com.binay.vscroll.data.player.VScrollPlayerManager
import com.binay.vscroll.data.preferences.ThemePreferencesRepository
import com.binay.vscroll.data.repository.MediaStoreVideoRepository
import com.binay.vscroll.data.repository.WatchHistoryRepositoryImpl
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.repository.WatchHistoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayPauseAnimState(
    val isVisible: Boolean = false,
    val isPlayingIcon: Boolean = false
)

data class GestureHudState(
    val isVisible: Boolean = false,
    val isVolume: Boolean = false, // true = volume, false = brightness
    val percent: Int = 0           // 0 to 100
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val database = VScrollDatabase.getInstance(application)
    val watchHistoryRepository: WatchHistoryRepository = WatchHistoryRepositoryImpl(
        watchHistoryDao = database.watchHistoryDao(),
        dailyWatchRecordDao = database.dailyWatchRecordDao(),
        videoRepository = MediaStoreVideoRepository(application)
    )

    private val reminderManager = com.binay.vscroll.data.notification.ViewingReminderManager(
        context = application,
        dailyWatchRecordDao = database.dailyWatchRecordDao(),
        preferencesRepository = com.binay.vscroll.data.preferences.ActivityPreferencesRepository(application)
    )

    val watchTimeTracker = PlaybackWatchTimeTracker(
        watchHistoryRepository = watchHistoryRepository,
        scope = viewModelScope,
        onPlaybackRecorded = {
            reminderManager.checkAndNotify()
        }
    )

    val playerManager = VScrollPlayerManager(
        context = application,
        scope = viewModelScope,
        watchTimeTracker = watchTimeTracker
    )
    val playbackState: StateFlow<PlayerPlaybackState> = playerManager.playbackState

    private val preferencesRepository = ThemePreferencesRepository(application)
    val isCleanViewingMode: StateFlow<Boolean> = preferencesRepository.isCleanViewingMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _animState = MutableStateFlow(PlayPauseAnimState())
    val animState: StateFlow<PlayPauseAnimState> = _animState.asStateFlow()

    private val _gestureHudState = MutableStateFlow(GestureHudState())
    val gestureHudState: StateFlow<GestureHudState> = _gestureHudState.asStateFlow()

    private var animDismissJob: Job? = null
    private var controlsAutoHideJob: Job? = null
    private var gestureHudJob: Job? = null

    fun playVideo(video: VideoItem) {
        playerManager.playVideo(video)
        scheduleControlsAutoHide()
    }

    fun togglePlayPause() {
        val nowPlaying = playerManager.togglePlayPause()
        triggerPlayPauseAnimation(isPlaying = nowPlaying)
        if (nowPlaying) {
            scheduleControlsAutoHide()
        } else {
            // Keep controls visible while paused
            playerManager.setControlsVisible(true)
            controlsAutoHideJob?.cancel()
        }
    }

    fun toggleControlsVisibility() {
        val currentlyVisible = playbackState.value.isControlsVisible
        if (currentlyVisible) {
            playerManager.setControlsVisible(false)
            controlsAutoHideJob?.cancel()
        } else {
            playerManager.setControlsVisible(true)
            scheduleControlsAutoHide()
        }
    }

    fun scheduleControlsAutoHide() {
        controlsAutoHideJob?.cancel()
        // Only auto-hide if playing; keep visible if user paused
        if (!playbackState.value.isUserPaused) {
            controlsAutoHideJob = viewModelScope.launch {
                delay(3000)
                if (!playbackState.value.isUserPaused) {
                    playerManager.setControlsVisible(false)
                }
            }
        }
    }

    fun onControlsInteracted() {
        // Reset 3s countdown on user interaction with slider, buttons, etc.
        scheduleControlsAutoHide()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
        onControlsInteracted()
    }

    fun skipForward10Seconds() {
        playerManager.skipForward10Seconds()
        onControlsInteracted()
    }

    fun skipBackward10Seconds() {
        playerManager.skipBackward10Seconds()
        onControlsInteracted()
    }

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
        onControlsInteracted()
    }

    fun toggleMute(): Boolean {
        val muted = playerManager.toggleMute()
        onControlsInteracted()
        return muted
    }

    fun setCleanViewingMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setCleanViewingMode(enabled)
        }
    }

    fun toggleCleanViewingMode() {
        val current = isCleanViewingMode.value
        setCleanViewingMode(!current)
    }

    fun showBrightnessHud(percent: Int) {
        gestureHudJob?.cancel()
        _gestureHudState.value = GestureHudState(
            isVisible = true,
            isVolume = false,
            percent = percent.coerceIn(0, 100)
        )
        gestureHudJob = viewModelScope.launch {
            delay(1200)
            _gestureHudState.value = _gestureHudState.value.copy(isVisible = false)
        }
    }

    fun showVolumeHud(percent: Int) {
        gestureHudJob?.cancel()
        _gestureHudState.value = GestureHudState(
            isVisible = true,
            isVolume = true,
            percent = percent.coerceIn(0, 100)
        )
        gestureHudJob = viewModelScope.launch {
            delay(1200)
            _gestureHudState.value = _gestureHudState.value.copy(isVisible = false)
        }
    }

    private fun triggerPlayPauseAnimation(isPlaying: Boolean) {
        animDismissJob?.cancel()
        _animState.value = PlayPauseAnimState(isVisible = true, isPlayingIcon = isPlaying)
        animDismissJob = viewModelScope.launch {
            delay(700)
            _animState.value = _animState.value.copy(isVisible = false)
        }
    }

    fun onFeedActiveChanged(isActive: Boolean) {
        playerManager.onDestinationActiveChanged(isActive)
        if (isActive) {
            playerManager.resume()
        } else {
            playerManager.pause()
        }
    }

    fun onAppForegroundChanged(inForeground: Boolean) {
        playerManager.onAppForegroundChanged(inForeground)
    }

    fun pause() {
        playerManager.pause()
    }

    fun resume() {
        playerManager.resume()
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.release()
    }
}
