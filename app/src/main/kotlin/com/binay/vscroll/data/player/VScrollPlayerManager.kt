package com.binay.vscroll.data.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.binay.vscroll.domain.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerPlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentVideo: VideoItem? = null,
    val isUserPaused: Boolean = false,
    val playbackError: String? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isMuted: Boolean = false,
    val isControlsVisible: Boolean = false
)

class VScrollPlayerManager(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private var watchTimeTracker: PlaybackWatchTimeTracker? = null
) {

    private val appContext = context.applicationContext

    private val _playbackState = MutableStateFlow(PlayerPlaybackState())
    val playbackState: StateFlow<PlayerPlaybackState> = _playbackState.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private var progressTrackingJob: Job? = null

    fun setWatchTimeTracker(tracker: PlaybackWatchTimeTracker) {
        this.watchTimeTracker = tracker
        tracker.setPositionProvider { exoPlayer?.currentPosition ?: 0L }
    }

    fun onAppForegroundChanged(inForeground: Boolean) {
        watchTimeTracker?.onAppForegroundChanged(inForeground)
    }

    fun onDestinationActiveChanged(isActive: Boolean) {
        watchTimeTracker?.onDestinationActiveChanged(isActive)
    }

    val player: ExoPlayer
        get() = exoPlayer ?: initPlayer().also { exoPlayer = it }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            watchTimeTracker?.onPlaybackStateChanged(state)
            when (state) {
                Player.STATE_BUFFERING -> {
                    _playbackState.update { it.copy(isBuffering = true, playbackError = null) }
                }
                Player.STATE_READY -> {
                    val dur = exoPlayer?.duration?.takeIf { it > 0 }
                        ?: _playbackState.value.currentVideo?.durationMillis
                        ?: 0L
                    _playbackState.update {
                        it.copy(
                            isBuffering = false,
                            playbackError = null,
                            durationMs = dur
                        )
                    }
                }
                Player.STATE_ENDED -> {
                    // With REPEAT_MODE_ONE, loop continues automatically.
                    _playbackState.update { it.copy(isBuffering = false) }
                }
                Player.STATE_IDLE -> {
                    _playbackState.update { it.copy(isBuffering = false) }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            watchTimeTracker?.onIsPlayingChanged(isPlaying, exoPlayer?.playWhenReady ?: false)
            _playbackState.update {
                it.copy(
                    isPlaying = isPlaying,
                    isUserPaused = if (isPlaying) false else it.isUserPaused
                )
            }
            if (isPlaying) {
                startProgressTracking()
            } else {
                stopProgressTracking()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val errorMsg = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "Video file not found or inaccessible"
                PlaybackException.ERROR_CODE_DECODING_FAILED -> "Codec decoding failed for this video"
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "Corrupted or unsupported video format"
                else -> error.localizedMessage ?: "Playback error encountered (${error.errorCodeName})"
            }
            _playbackState.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    playbackError = errorMsg
                )
            }
            stopProgressTracking()
        }
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return ExoPlayer.Builder(appContext)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE // Loop current video; advance only on user swipe
                playWhenReady = true
                addListener(playerListener)
                watchTimeTracker?.setPositionProvider { currentPosition }
            }
    }

    private fun startProgressTracking() {
        if (progressTrackingJob?.isActive == true) return
        progressTrackingJob = scope.launch {
            while (isActive) {
                exoPlayer?.let { p ->
                    val pos = p.currentPosition.coerceAtLeast(0L)
                    val dur = if (p.duration > 0) p.duration else _playbackState.value.currentVideo?.durationMillis ?: 0L
                    _playbackState.update {
                        it.copy(currentPositionMs = pos, durationMs = dur)
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    fun playVideo(video: VideoItem) {
        val activePlayer = player
        val current = _playbackState.value.currentVideo

        if (current?.id == video.id && activePlayer.playbackState != Player.STATE_IDLE) {
            // Same video: ensure playback resumes if not user paused
            if (!_playbackState.value.isUserPaused && !activePlayer.isPlaying) {
                activePlayer.play()
            }
            return
        }

        watchTimeTracker?.onVideoChanged(video)

        // New video selection
        _playbackState.update {
            it.copy(
                currentVideo = video,
                isBuffering = true,
                playbackError = null,
                isUserPaused = false,
                currentPositionMs = 0L,
                durationMs = video.durationMillis
            )
        }

        val mediaItem = MediaItem.fromUri(video.contentUri)
        activePlayer.stop()
        activePlayer.setMediaItem(mediaItem)
        activePlayer.prepare()
        activePlayer.playWhenReady = true
        activePlayer.play()
    }

    fun togglePlayPause(): Boolean {
        val activePlayer = exoPlayer ?: return false
        val currentlyPlaying = activePlayer.isPlaying
        if (currentlyPlaying) {
            activePlayer.pause()
            _playbackState.update { it.copy(isUserPaused = true, isPlaying = false) }
            return false
        } else {
            activePlayer.play()
            _playbackState.update { it.copy(isUserPaused = false, isPlaying = true) }
            return true
        }
    }

    fun seekTo(positionMs: Long) {
        val activePlayer = exoPlayer ?: return
        val maxDur = if (activePlayer.duration > 0) activePlayer.duration else _playbackState.value.durationMs
        val safePos = positionMs.coerceIn(0L, maxDur.coerceAtLeast(1L))
        activePlayer.seekTo(safePos)
        _playbackState.update { it.copy(currentPositionMs = safePos) }
    }

    fun skipForward10Seconds() {
        val activePlayer = exoPlayer ?: return
        val maxDur = if (activePlayer.duration > 0) activePlayer.duration else _playbackState.value.durationMs
        val target = (activePlayer.currentPosition + 10_000L).coerceAtMost(maxDur.coerceAtLeast(0L))
        seekTo(target)
    }

    fun skipBackward10Seconds() {
        val activePlayer = exoPlayer ?: return
        val target = maxOf(0L, activePlayer.currentPosition - 10_000L)
        seekTo(target)
    }

    fun setPlaybackSpeed(speed: Float) {
        val activePlayer = exoPlayer ?: return
        val safeSpeed = speed.coerceIn(0.25f, 2.0f)
        activePlayer.setPlaybackSpeed(safeSpeed)
        _playbackState.update { it.copy(playbackSpeed = safeSpeed) }
    }

    fun toggleMute(): Boolean {
        val activePlayer = exoPlayer ?: return false
        val willMute = !_playbackState.value.isMuted
        activePlayer.volume = if (willMute) 0f else 1f
        _playbackState.update { it.copy(isMuted = willMute) }
        return willMute
    }

    fun setControlsVisible(visible: Boolean) {
        _playbackState.update { it.copy(isControlsVisible = visible) }
    }

    fun pause() {
        exoPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            }
        }
        stopProgressTracking()
    }

    fun resume() {
        val activePlayer = exoPlayer ?: return
        if (!_playbackState.value.isUserPaused && activePlayer.playbackState != Player.STATE_IDLE) {
            activePlayer.play()
        }
    }

    fun release() {
        watchTimeTracker?.onPlayerReleased()
        stopProgressTracking()
        exoPlayer?.let {
            it.removeListener(playerListener)
            it.stop()
            it.clearMediaItems()
            it.release()
        }
        exoPlayer = null
        _playbackState.update {
            PlayerPlaybackState()
        }
    }
}
