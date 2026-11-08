package com.binay.vscroll.data.player

import android.os.SystemClock
import androidx.media3.common.Player
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.repository.WatchHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

interface MonotonicClock {
    fun elapsedRealtime(): Long
    fun currentTimeMillis(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}

class PlaybackWatchTimeTracker(
    private val watchHistoryRepository: WatchHistoryRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val periodicFlushIntervalMs: Long = 5_000L,
    private val onPlaybackRecorded: (suspend (watchTimeMs: Long) -> Unit)? = null
) {
    private var activeVideo: VideoItem? = null
    private var isPlayerPlaying: Boolean = false
    private var isPlayerReady: Boolean = false
    private var playWhenReady: Boolean = false
    private var isAppInForeground: Boolean = true
    private var isDestinationActive: Boolean = true

    private var isTrackingActive: Boolean = false
    private var trackingStartRealtimeMs: Long = 0L
    private var trackingStartWallClockMs: Long = 0L
    private var isFirstChunkForSession: Boolean = true

    private var currentPositionProvider: () -> Long = { 0L }
    private var periodicFlushJob: Job? = null

    fun setPositionProvider(provider: () -> Long) {
        currentPositionProvider = provider
    }

    @Synchronized
    fun onVideoChanged(video: VideoItem?) {
        if (activeVideo?.id != video?.id) {
            flushActiveInterval()
            activeVideo = video
            isFirstChunkForSession = true
            evaluateTrackingState()
        }
    }

    @Synchronized
    fun onPlaybackStateChanged(playbackState: Int) {
        isPlayerReady = (playbackState == Player.STATE_READY)
        evaluateTrackingState()
    }

    @Synchronized
    fun onIsPlayingChanged(isPlaying: Boolean, playWhenReady: Boolean) {
        this.isPlayerPlaying = isPlaying
        this.playWhenReady = playWhenReady
        evaluateTrackingState()
    }

    @Synchronized
    fun onAppForegroundChanged(inForeground: Boolean) {
        if (this.isAppInForeground != inForeground) {
            this.isAppInForeground = inForeground
            evaluateTrackingState()
        }
    }

    @Synchronized
    fun onDestinationActiveChanged(isActive: Boolean) {
        if (this.isDestinationActive != isActive) {
            this.isDestinationActive = isActive
            evaluateTrackingState()
        }
    }

    @Synchronized
    fun onPlayerReleased() {
        flushActiveInterval()
        activeVideo = null
        isPlayerPlaying = false
        isPlayerReady = false
        playWhenReady = false
        stopPeriodicFlush()
    }

    private fun shouldTrack(): Boolean {
        return activeVideo != null &&
                isPlayerPlaying &&
                isPlayerReady &&
                playWhenReady &&
                isAppInForeground &&
                isDestinationActive
    }

    private fun evaluateTrackingState() {
        val needsTracking = shouldTrack()
        if (needsTracking && !isTrackingActive) {
            // Start tracking interval
            trackingStartRealtimeMs = clock.elapsedRealtime()
            trackingStartWallClockMs = clock.currentTimeMillis()
            isTrackingActive = true
            startPeriodicFlush()
        } else if (!needsTracking && isTrackingActive) {
            // Stop and flush interval
            flushActiveInterval()
            stopPeriodicFlush()
        }
    }

    @Synchronized
    private fun flushActiveInterval() {
        if (isTrackingActive) {
            val nowRealtime = clock.elapsedRealtime()
            val elapsedMs = nowRealtime - trackingStartRealtimeMs
            val video = activeVideo
            if (elapsedMs > 0 && video != null) {
                val startWall = trackingStartWallClockMs
                val endWall = startWall + elapsedMs
                val isNewSession = isFirstChunkForSession
                isFirstChunkForSession = false
                val position = currentPositionProvider()

                scope.launch {
                    watchHistoryRepository.recordPlayback(
                        video = video,
                        watchTimeMs = elapsedMs,
                        startWallClockMs = startWall,
                        endWallClockMs = endWall,
                        isNewSession = isNewSession,
                        lastPositionMs = position
                    )
                    onPlaybackRecorded?.invoke(elapsedMs)
                }
            }
            isTrackingActive = false
            trackingStartRealtimeMs = 0L
            trackingStartWallClockMs = 0L
        }
    }

    @Synchronized
    private fun periodicFlush() {
        if (isTrackingActive) {
            val nowRealtime = clock.elapsedRealtime()
            val elapsedMs = nowRealtime - trackingStartRealtimeMs
            val video = activeVideo
            if (elapsedMs > 0 && video != null) {
                val startWall = trackingStartWallClockMs
                val endWall = startWall + elapsedMs
                val isNewSession = isFirstChunkForSession
                isFirstChunkForSession = false
                val position = currentPositionProvider()

                scope.launch {
                    watchHistoryRepository.recordPlayback(
                        video = video,
                        watchTimeMs = elapsedMs,
                        startWallClockMs = startWall,
                        endWallClockMs = endWall,
                        isNewSession = isNewSession,
                        lastPositionMs = position
                    )
                    onPlaybackRecorded?.invoke(elapsedMs)
                }

                // Advance start mark seamlessly without losing a millisecond
                trackingStartRealtimeMs = nowRealtime
                trackingStartWallClockMs = endWall
            }
        }
    }

    private fun startPeriodicFlush() {
        if (periodicFlushIntervalMs <= 0) return
        if (periodicFlushJob?.isActive == true) return
        periodicFlushJob = scope.launch {
            while (isActive) {
                delay(periodicFlushIntervalMs)
                periodicFlush()
            }
        }
    }

    private fun stopPeriodicFlush() {
        periodicFlushJob?.cancel()
        periodicFlushJob = null
    }

    // Exposed for testing inspection
    fun isTracking(): Boolean = isTrackingActive
}

