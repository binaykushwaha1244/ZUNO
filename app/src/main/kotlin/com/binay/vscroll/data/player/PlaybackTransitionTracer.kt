package com.binay.vscroll.data.player

import android.os.SystemClock
import android.util.Log

object PlaybackTransitionTracer {
    private const val TAG = "ZUNO_TRACE"
    private var transitionCounter = 0L

    @Volatile
    var activeTransitionId: Long = 0L
        private set

    private val timestamps = mutableMapOf<String, Long>()

    fun startTransition(fromPage: Int, targetPage: Int): Long {
        val id = ++transitionCounter
        activeTransitionId = id
        synchronized(timestamps) {
            timestamps.clear()
            val now = SystemClock.elapsedRealtimeNanos()
            timestamps["SWIPE_DETECTED"] = now
            Log.d(TAG, "[$id] 1. USER_SWIPE_DETECTED: from page $fromPage to target $targetPage")
        }
        return id
    }

    fun markEvent(eventName: String, extra: String = "") {
        val id = activeTransitionId
        if (id == 0L) return
        val now = SystemClock.elapsedRealtimeNanos()
        val start: Long
        synchronized(timestamps) {
            timestamps[eventName] = now
            start = timestamps["SWIPE_DETECTED"] ?: now
        }
        val elapsedMs = (now - start) / 1_000_000.0
        Log.d(TAG, "[$id] $eventName (+${String.format("%.2f", elapsedMs)}ms) $extra")

        if (eventName == "PLAYBACK_ACTUAL_START" || eventName == "FIRST_VIDEO_FRAME_RENDERED") {
            logSummary(id)
        }
    }

    private fun logSummary(id: Long) {
        val start: Long
        val assigned: Long
        val stateReady: Long
        val firstFrame: Long
        val actualPlay: Long

        synchronized(timestamps) {
            start = timestamps["SWIPE_DETECTED"] ?: return
            assigned = timestamps["MEDIA_ITEM_ASSIGNED"] ?: start
            stateReady = timestamps["PLAYER_STATE_READY"] ?: start
            firstFrame = timestamps["FIRST_VIDEO_FRAME_RENDERED"] ?: start
            actualPlay = timestamps["PLAYBACK_ACTUAL_START"] ?: firstFrame
        }

        val swipeToAssignMs = (assigned - start) / 1_000_000.0
        val assignToReadyMs = (stateReady - assigned) / 1_000_000.0
        val readyToFrameMs = (firstFrame - stateReady) / 1_000_000.0
        val swipeToFrameMs = (firstFrame - start) / 1_000_000.0
        val swipeToPlayMs = (actualPlay - start) / 1_000_000.0

        Log.i(TAG, """
        ================ TRANSITION SUMMARY [$id] ================
        1. Swipe -> MediaItem assigned:    ${String.format("%.2f", swipeToAssignMs)} ms
        2. MediaItem -> STATE_READY:      ${String.format("%.2f", assignToReadyMs)} ms
        3. STATE_READY -> First Frame:     ${String.format("%.2f", readyToFrameMs)} ms
        4. TOTAL SWIPE -> FIRST FRAME:     ${String.format("%.2f", swipeToFrameMs)} ms
        5. TOTAL SWIPE -> PLAYBACK START:  ${String.format("%.2f", swipeToPlayMs)} ms
        =========================================================
        """.trimIndent())
    }
}

