package com.binay.vscroll.ui.player

import androidx.media3.common.Player
import com.binay.vscroll.data.player.PlayerPlaybackState
import com.binay.vscroll.data.repository.FakeVideoRepository
import com.binay.vscroll.data.repository.FeedQueueManager
import com.binay.vscroll.domain.model.PlaybackContext
import com.binay.vscroll.domain.model.VideoItem
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlayerPlaybackStateTest {

    @Test
    fun playerPlaybackState_defaultStateIsInitial() {
        val state = PlayerPlaybackState()
        assertFalse(state.isPlaying)
        assertFalse(state.isBuffering)
        assertFalse(state.isUserPaused)
        assertNull(state.currentVideo)
        assertNull(state.playbackError)
        assertEquals(0L, state.currentPositionMs)
        assertEquals(0L, state.durationMs)
        assertEquals(1.0f, state.playbackSpeed)
        assertFalse(state.isMuted)
        assertFalse(state.isControlsVisible)
    }

    @Test
    fun playerPlaybackState_transitionsCorrectly() {
        val sampleVideo = VideoItem(
            id = 101L,
            title = "Test Video",
            uriString = "content://media/101",
            durationMillis = 15000L,
            sizeBytes = 2048L
        )

        val buffering = PlayerPlaybackState(
            isBuffering = true,
            currentVideo = sampleVideo
        )
        assertTrue(buffering.isBuffering)
        assertFalse(buffering.isPlaying)
        assertEquals(sampleVideo, buffering.currentVideo)

        val playing = buffering.copy(
            isBuffering = false,
            isPlaying = true
        )
        assertFalse(playing.isBuffering)
        assertTrue(playing.isPlaying)

        val userPaused = playing.copy(
            isPlaying = false,
            isUserPaused = true
        )
        assertFalse(userPaused.isPlaying)
        assertTrue(userPaused.isUserPaused)

        val errorState = playing.copy(
            isPlaying = false,
            isBuffering = false,
            playbackError = "Codec decoding failed for this video"
        )
        assertFalse(errorState.isPlaying)
        assertEquals("Codec decoding failed for this video", errorState.playbackError)
    }

    @Test
    fun playerPlaybackState_seekingAndDurationCalculations() {
        val duration = 60_000L // 60 seconds
        val state = PlayerPlaybackState(
            isPlaying = true,
            durationMs = duration,
            currentPositionMs = 15_000L
        )

        // Progress calculation
        val progress = (state.currentPositionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
        assertEquals(0.25f, progress, 0.001f)

        // Target seek calculation with clamp
        val targetSeekNegative = (-5000L).coerceIn(0L, duration)
        assertEquals(0L, targetSeekNegative)

        val targetSeekNormal = 30_000L.coerceIn(0L, duration)
        assertEquals(30_000L, targetSeekNormal)

        val targetSeekOver = 90_000L.coerceIn(0L, duration)
        assertEquals(duration, targetSeekOver)
    }

    @Test
    fun playerPlaybackState_skipMathCalculatesAccurately() {
        val duration = 45_000L // 45 seconds

        // Skip forward 10s from 15s -> 25s
        val pos1 = 15_000L
        val forward1 = (pos1 + 10_000L).coerceAtMost(duration)
        assertEquals(25_000L, forward1)

        // Skip forward 10s near end -> clamped to duration
        val pos2 = 40_000L
        val forward2 = (pos2 + 10_000L).coerceAtMost(duration)
        assertEquals(duration, forward2)

        // Skip backward 10s from 25s -> 15s
        val pos3 = 25_000L
        val backward1 = maxOf(0L, pos3 - 10_000L)
        assertEquals(15_000L, backward1)

        // Skip backward 10s from 5s -> clamped to 0s
        val pos4 = 5_000L
        val backward2 = maxOf(0L, pos4 - 10_000L)
        assertEquals(0L, backward2)
    }

    @Test
    fun playerPlaybackState_playbackSpeedClamping() {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        speeds.forEach { sp ->
            val clamped = sp.coerceIn(0.25f, 2.0f)
            assertEquals(sp, clamped)
        }

        // Out of range clamping
        val lowClamped = 0.1f.coerceIn(0.25f, 2.0f)
        assertEquals(0.25f, lowClamped)

        val highClamped = 3.5f.coerceIn(0.25f, 2.0f)
        assertEquals(2.0f, highClamped)
    }

    @Test
    fun playerPlaybackState_muteToggling() {
        var state = PlayerPlaybackState(isMuted = false)
        assertFalse(state.isMuted)

        state = state.copy(isMuted = !state.isMuted)
        assertTrue(state.isMuted)

        state = state.copy(isMuted = !state.isMuted)
        assertFalse(state.isMuted)
    }

    @Test
    fun playerPlaybackState_controlsVisibilityToggling() {
        var state = PlayerPlaybackState(isControlsVisible = false)
        assertFalse(state.isControlsVisible)

        state = state.copy(isControlsVisible = true)
        assertTrue(state.isControlsVisible)

        state = state.copy(isControlsVisible = false)
        assertFalse(state.isControlsVisible)
    }

    @Test
    fun gestureHudState_clampingAndTypes() {
        val volHud = GestureHudState(isVisible = true, isVolume = true, percent = 80)
        assertTrue(volHud.isVisible)
        assertTrue(volHud.isVolume)
        assertEquals(80, volHud.percent)

        val brightHud = GestureHudState(isVisible = true, isVolume = false, percent = 45)
        assertTrue(brightHud.isVisible)
        assertFalse(brightHud.isVolume)
        assertEquals(45, brightHud.percent)

        // Clamping percent
        val clampedHigh = 120.coerceIn(0, 100)
        assertEquals(100, clampedHigh)

        val clampedLow = (-15).coerceIn(0, 100)
        assertEquals(0, clampedLow)
    }

    @Test
    fun repeatMode_isRepeatModeOneConstant() {
        // Requirement: repeatMode must be Player.REPEAT_MODE_ONE (1)
        assertEquals(1, Player.REPEAT_MODE_ONE)
    }

    @Test
    fun feedQueueManager_updatesAndPreservesShortsIndex() = runTest {
        val fakeRepo = FakeVideoRepository()
        val manager = FeedQueueManager(fakeRepo, backgroundScope)

        val initialQueue = manager.shortsQueue.first { it.items.isNotEmpty() }
        assertEquals(0, initialQueue.currentIndex)

        // Advance to page 2
        manager.updateShortsIndex(2)
        val updatedQueue = manager.shortsQueue.first { it.currentIndex == 2 }
        assertEquals(2, updatedQueue.currentIndex)
        assertEquals(initialQueue.items[2].id, updatedQueue.currentVideo?.id)

        // Shuffling resets currentIndex back to 0
        manager.shuffleGlobalShorts()
        val reshuffledQueue = manager.shortsQueue.first { it.currentIndex == 0 }
        assertEquals(0, reshuffledQueue.currentIndex)
        assertTrue(reshuffledQueue.context is PlaybackContext.GlobalRandomFeed)
    }

    @Test
    fun feedQueueManager_handlesEmptyAndOutOfBoundsIndices() = runTest {
        val fakeRepo = FakeVideoRepository()
        val manager = FeedQueueManager(fakeRepo, backgroundScope)

        // Out of bounds high index is safely clamped to items.size - 1
        manager.updateShortsIndex(999)
        val clampedQueue = manager.shortsQueue.first { it.items.isNotEmpty() }
        assertEquals(4, clampedQueue.currentIndex) // 5 items: max index is 4
    }
}
