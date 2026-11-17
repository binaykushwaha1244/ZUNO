package com.binay.vscroll.data.repository

import com.binay.vscroll.domain.model.FeedOrderPreference
import com.binay.vscroll.domain.model.FeedQueueState
import com.binay.vscroll.domain.model.PlaybackContext
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.model.VideoSortOrder
import com.binay.vscroll.domain.repository.VideoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.random.Random

class FeedQueueManager(
    private val videoRepository: VideoRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    initialShuffleEnabled: Boolean = true
) {
    companion object {
        private fun logFeed(message: String) {
            try {
                android.util.Log.i("VScrollFeed", message)
            } catch (_: Throwable) {
                println("VScrollFeed: $message")
            }
        }
    }
    // Current seed for local random shuffle
    private val _shuffleSeed = MutableStateFlow(Random.nextLong())
    private val _isShuffleEnabled = MutableStateFlow(initialShuffleEnabled)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled
    private val _orderPreference = MutableStateFlow(
        if (initialShuffleEnabled) FeedOrderPreference.RANDOM else FeedOrderPreference.NEWEST_FIRST
    )
    private val _shortsCurrentIndex = MutableStateFlow(0)

    // Tracks last watched video ID to avoid immediate repeat on shuffle
    private var _lastWatchedId: Long? = null

    // Separate playback session for folder sequential playback (Mode B)
    private val _folderQueue = MutableStateFlow<FeedQueueState?>(null)
    val folderQueue: StateFlow<FeedQueueState?> = _folderQueue

    // Separate playback session for interaction sequential playback (Mode C: Liked, Saved, Notes)
    private val _interactionQueue = MutableStateFlow<FeedQueueState?>(null)
    val interactionQueue: StateFlow<FeedQueueState?> = _interactionQueue

    // Session feed items & metadata: emitted atomically so items and preference never desynchronize
    private data class SessionFeed(
        val items: List<VideoItem>,
        val seed: Long,
        val preference: FeedOrderPreference
    )

    private val _sessionFlow: Flow<SessionFeed> = combine(
        videoRepository.getVideos(VideoSortOrder.NEWEST_FIRST),
        _shuffleSeed,
        _orderPreference
    ) { allVideos, seed, preference ->
        val items = when (preference) {
            FeedOrderPreference.RANDOM -> {
                val shuffled = allVideos.shuffled(Random(seed)).toMutableList()
                // Avoid immediate repetition of the last watched video on new shuffle
                val lastId = _lastWatchedId
                if (shuffled.size > 1 && lastId != null && shuffled[0].id == lastId) {
                    val swapIndex = if (shuffled.size > 2) 1 else 1
                    val temp = shuffled[0]
                    shuffled[0] = shuffled[swapIndex]
                    shuffled[swapIndex] = temp
                }
                shuffled
            }
            FeedOrderPreference.NEWEST_FIRST -> allVideos.sortedByDescending { it.dateAdded }
            FeedOrderPreference.OLDEST_FIRST -> allVideos.sortedBy { it.dateAdded }
        }
        logFeed("SESSION_FEED preference=$preference seed=$seed count=${items.size} first10Ids=${items.take(10).map { it.id }}")
        SessionFeed(items = items, seed = seed, preference = preference)
    }

    // Global Shorts queue (Mode A): Independent, resilient, atomically synchronized
    val shortsQueue: StateFlow<FeedQueueState> = combine(
        _sessionFlow,
        _shortsCurrentIndex
    ) { session, currentIndex ->
        val items = session.items
        val safeIndex = if (items.isEmpty()) {
            0
        } else {
            currentIndex.coerceIn(0, items.size - 1)
        }

        items.getOrNull(safeIndex)?.let { current ->
            _lastWatchedId = current.id
        }
        logFeed("SHORTS_QUEUE_STATE safeIndex=$safeIndex currentVideoId=${items.getOrNull(safeIndex)?.id} totalCount=${items.size}")

        FeedQueueState(
            context = PlaybackContext.GlobalRandomFeed(
                sessionSeed = session.seed,
                orderPreference = session.preference
            ),
            items = items,
            currentIndex = safeIndex
        )
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        FeedQueueState(
            context = PlaybackContext.GlobalRandomFeed(),
            items = emptyList(),
            currentIndex = 0
        )
    )

    fun updateShortsIndex(index: Int) {
        if (index >= 0) {
            _shortsCurrentIndex.value = index
        }
    }

    fun shuffleGlobalShorts() {
        _shortsCurrentIndex.value = 0
        _shuffleSeed.value = Random.nextLong()
    }

    fun setShuffleEnabled(enabled: Boolean) {
        if (_isShuffleEnabled.value != enabled) {
            _isShuffleEnabled.value = enabled
            _orderPreference.value = if (enabled) FeedOrderPreference.RANDOM else FeedOrderPreference.NEWEST_FIRST
            _shortsCurrentIndex.value = 0
            if (enabled) {
                _shuffleSeed.value = Random.nextLong()
            }
        }
    }

    fun setOrderPreference(preference: FeedOrderPreference) {
        _orderPreference.value = preference
        _isShuffleEnabled.value = (preference == FeedOrderPreference.RANDOM)
    }

    // --- Mode B: Folder Sequential Playback ---

    fun startFolderPlayback(
        folderId: String,
        folderName: String,
        folderVideos: List<VideoItem>,
        initialIndex: Int,
        sortOrder: VideoSortOrder
    ) {
        val validIndex = initialIndex.coerceIn(0, (folderVideos.size - 1).coerceAtLeast(0))
        val initialVideoId = folderVideos.getOrNull(validIndex)?.id ?: 0L

        _folderQueue.value = FeedQueueState(
            context = PlaybackContext.FolderSequential(
                folderId = folderId,
                folderName = folderName,
                sortOrder = sortOrder,
                initialVideoId = initialVideoId,
                initialIndex = validIndex
            ),
            items = folderVideos,
            currentIndex = validIndex
        )
    }

    fun startCollectionPlayback(
        collectionId: Long,
        collectionName: String,
        collectionVideos: List<VideoItem>,
        initialIndex: Int,
        sortOrder: VideoSortOrder
    ) {
        val validIndex = initialIndex.coerceIn(0, (collectionVideos.size - 1).coerceAtLeast(0))
        val initialVideoId = collectionVideos.getOrNull(validIndex)?.id ?: 0L

        _folderQueue.value = FeedQueueState(
            context = PlaybackContext.CollectionSequential(
                collectionId = collectionId,
                collectionName = collectionName,
                sortOrder = sortOrder,
                initialVideoId = initialVideoId,
                initialIndex = validIndex
            ),
            items = collectionVideos,
            currentIndex = validIndex
        )
    }

    fun updateFolderIndex(index: Int) {
        _folderQueue.update { current ->
            if (current != null && current.items.isNotEmpty()) {
                val safeIndex = index.coerceIn(0, current.items.size - 1)
                current.copy(currentIndex = safeIndex)
            } else {
                current
            }
        }
    }

    fun closeFolderPlayback() {
        _folderQueue.value = null
    }

    // --- Mode C: Interaction Sequential Playback (Liked, Saved, Notes) ---

    fun startInteractionPlayback(
        source: com.binay.vscroll.domain.model.InteractionSource,
        videos: List<VideoItem>,
        initialIndex: Int
    ) {
        val validIndex = initialIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
        val initialVideoId = videos.getOrNull(validIndex)?.id ?: 0L

        _interactionQueue.value = FeedQueueState(
            context = PlaybackContext.InteractionSequential(
                source = source,
                initialVideoId = initialVideoId,
                initialIndex = validIndex
            ),
            items = videos,
            currentIndex = validIndex
        )
    }

    fun updateInteractionIndex(index: Int) {
        _interactionQueue.update { current ->
            if (current != null && current.items.isNotEmpty()) {
                val safeIndex = index.coerceIn(0, current.items.size - 1)
                current.copy(currentIndex = safeIndex)
            } else {
                current
            }
        }
    }

    fun closeInteractionPlayback() {
        _interactionQueue.value = null
    }
}
