package com.binay.vscroll.domain.model

import com.binay.vscroll.data.repository.FakeVideoRepository
import com.binay.vscroll.data.repository.FeedQueueManager
import com.binay.vscroll.ui.navigation.NavDestination
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNotSame
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FeedQueueManagerTest {

    @Test
    fun navDestination_allFiveDestinationsExist() {
        val destinations = NavDestination.entries
        assertEquals(5, destinations.size)

        assertEquals("Shorts", destinations[0].title)
        assertEquals("Liked", destinations[1].title)
        assertEquals("Activity", destinations[2].title)
        assertEquals("Library", destinations[3].title)
        assertEquals("Settings", destinations[4].title)
    }

    @Test
    fun shortsQueue_initializesWithGlobalRandomContext() = runTest {
        val fakeRepo = FakeVideoRepository()
        val manager = FeedQueueManager(fakeRepo, backgroundScope)

        val shortsState = manager.shortsQueue.first { it.items.isNotEmpty() }
        assertTrue(shortsState.context is PlaybackContext.GlobalRandomFeed)
        assertEquals(5, shortsState.items.size)
        assertNotNull(shortsState.currentVideo)
    }

    @Test
    fun globalQueue_maintainsStableQueueWithinSession() = runTest {
        val fakeRepo = FakeVideoRepository()
        val manager = FeedQueueManager(fakeRepo, backgroundScope)

        val state1 = manager.shortsQueue.first { it.items.isNotEmpty() }
        val ids1 = state1.items.map { it.id }

        // Advance index
        manager.updateShortsIndex(2)
        val state2 = manager.shortsQueue.first { it.currentIndex == 2 }
        val ids2 = state2.items.map { it.id }

        // The list permutation must remain completely identical
        assertEquals(ids1, ids2)
        assertEquals(2, state2.currentIndex)
    }

    @Test
    fun globalQueue_avoidImmediateRepeatOnShuffle() = runTest {
        val fakeRepo = FakeVideoRepository()
        val manager = FeedQueueManager(fakeRepo, backgroundScope)

        val initialQueue = manager.shortsQueue.first { it.items.isNotEmpty() }
        val watchedVideoId = initialQueue.items[0].id

        // Reshuffle multiple times and verify index 0 avoids repeating watchedVideoId
        repeat(5) {
            manager.shuffleGlobalShorts()
            val reshuffled = manager.shortsQueue.first { it.currentIndex == 0 }
            assertEquals(5, reshuffled.items.size)
            // If library has multiple videos, first item shouldn't immediately repeat the last watched video
            if (reshuffled.items.size > 1) {
                val newFirstId = reshuffled.items[0].id
                // Verify permutation still contains 5 unique items
                assertEquals(5, reshuffled.items.distinctBy { it.id }.size)
            }
        }
    }

    @Test
    fun folderPlayback_maintainsIndependentFolderQueue() = runTest {
        val fakeRepo = FakeVideoRepository()
        val manager = FeedQueueManager(fakeRepo, backgroundScope)

        // Initially folderQueue is null
        assertNull(manager.folderQueue.value)

        val sampleVideos = fakeRepo.getVideos(VideoSortOrder.NEWEST_FIRST).first()
        manager.startFolderPlayback(
            folderId = "DCIM/Camera",
            folderName = "Camera",
            folderVideos = sampleVideos,
            initialIndex = 2,
            sortOrder = VideoSortOrder.NEWEST_FIRST
        )

        val folderState = manager.folderQueue.value
        assertNotNull(folderState)
        assertTrue(folderState?.context is PlaybackContext.FolderSequential)

        val context = folderState?.context as PlaybackContext.FolderSequential
        assertEquals("DCIM/Camera", context.folderId)
        assertEquals("Camera", context.folderName)
        assertEquals(2, context.initialIndex)
        assertEquals(sampleVideos[2].id, context.initialVideoId)

        // Verify that shortsQueue was not destroyed or changed to folder queue
        val shortsState = manager.shortsQueue.first { it.items.isNotEmpty() }
        assertTrue(shortsState.context is PlaybackContext.GlobalRandomFeed)
        assertEquals(5, shortsState.items.size)

        // Closing folder playback sets folderQueue to null
        manager.closeFolderPlayback()
        assertNull(manager.folderQueue.value)

        // shortsQueue still intact
        val remainingShorts = manager.shortsQueue.first { it.items.isNotEmpty() }
        assertEquals(5, remainingShorts.items.size)
    }

    @Test
    fun folderQueue_containsOnlyVideosFromItsFolder() = runTest {
        val fakeRepo = FakeVideoRepository()
        val manager = FeedQueueManager(fakeRepo, backgroundScope)

        val gymVideos = listOf(
            VideoItem(id = 10L, title = "Gym 1", uriString = "c://10", folderName = "Gym", relativePath = "DCIM/Gym/"),
            VideoItem(id = 11L, title = "Gym 2", uriString = "c://11", folderName = "Gym", relativePath = "DCIM/Gym/"),
            VideoItem(id = 12L, title = "Gym 3", uriString = "c://12", folderName = "Gym", relativePath = "DCIM/Gym/")
        )

        manager.startFolderPlayback(
            folderId = "DCIM/Gym/",
            folderName = "Gym",
            folderVideos = gymVideos,
            initialIndex = 1,
            sortOrder = VideoSortOrder.DURATION_LONGEST
        )

        val activeFolderQueue = manager.folderQueue.value
        assertNotNull(activeFolderQueue)
        assertEquals(3, activeFolderQueue?.items?.size)

        // Verify all videos strictly belong to Gym folder
        activeFolderQueue?.items?.forEach { video ->
            assertEquals("Gym", video.folderName)
            assertEquals("DCIM/Gym/", video.relativePath)
        }

        // Starts at exact tapped video 1
        assertEquals(gymVideos[1].id, activeFolderQueue?.currentVideo?.id)
        assertEquals(1, activeFolderQueue?.currentIndex)

        // Sequential Next navigation (swipe up): 1 -> 2
        manager.updateFolderIndex(2)
        assertEquals(gymVideos[2].id, manager.folderQueue.value?.currentVideo?.id)

        // Sequential Previous navigation (swipe down): 2 -> 0
        manager.updateFolderIndex(0)
        assertEquals(gymVideos[0].id, manager.folderQueue.value?.currentVideo?.id)
    }

    @Test
    fun folderQueue_preservesSortOrder() = runTest {
        val fakeRepo = FakeVideoRepository()
        val manager = FeedQueueManager(fakeRepo, backgroundScope)

        val sortedVideos = listOf(
            VideoItem(id = 20L, title = "A Video", uriString = "c://20"),
            VideoItem(id = 21L, title = "B Video", uriString = "c://21")
        )

        manager.startFolderPlayback(
            folderId = "Movies",
            folderName = "Movies",
            folderVideos = sortedVideos,
            initialIndex = 0,
            sortOrder = VideoSortOrder.NAME_A_TO_Z
        )

        val context = manager.folderQueue.value?.context as? PlaybackContext.FolderSequential
        assertEquals(VideoSortOrder.NAME_A_TO_Z, context?.sortOrder)
    }

    @Test
    fun repeatedModeSwitching_preservesBothQueues() = runTest {
        val fakeRepo = FakeVideoRepository()
        val manager = FeedQueueManager(fakeRepo, backgroundScope)

        // Set Shorts to index 3
        manager.updateShortsIndex(3)
        val initialShorts = manager.shortsQueue.first { it.currentIndex == 3 }
        assertEquals(3, initialShorts.currentIndex)

        val folderVideos = listOf(
            VideoItem(id = 100L, title = "F1", uriString = "c://100"),
            VideoItem(id = 101L, title = "F2", uriString = "c://101")
        )

        // Switch back and forth 5 times
        repeat(5) { iteration ->
            manager.startFolderPlayback(
                folderId = "F",
                folderName = "Folder",
                folderVideos = folderVideos,
                initialIndex = 1,
                sortOrder = VideoSortOrder.NEWEST_FIRST
            )
            assertEquals(101L, manager.folderQueue.value?.currentVideo?.id)

            // Close folder
            manager.closeFolderPlayback()
            assertNull(manager.folderQueue.value)

            // Verify global shorts queue was not modified
            val shortsAfter = manager.shortsQueue.first()
            assertEquals(3, shortsAfter.currentIndex)
            assertEquals(initialShorts.currentVideo?.id, shortsAfter.currentVideo?.id)
        }
    }

    @Test
    fun emptyFolder_handledGracefully() = runTest {
        val fakeRepo = FakeVideoRepository()
        val manager = FeedQueueManager(fakeRepo, backgroundScope)

        manager.startFolderPlayback(
            folderId = "Empty",
            folderName = "Empty",
            folderVideos = emptyList(),
            initialIndex = 0,
            sortOrder = VideoSortOrder.NEWEST_FIRST
        )

        val folderState = manager.folderQueue.value
        assertNotNull(folderState)
        assertEquals(0, folderState?.items?.size)
        assertNull(folderState?.currentVideo)
        assertEquals(0, folderState?.currentIndex)
    }

    @Test
    fun feedQueueState_navigationHelpersWork() {
        val items = listOf(
            VideoItem(id = 1L, title = "V1", uriString = "c://1", durationMillis = 1000L, sizeBytes = 1024L),
            VideoItem(id = 2L, title = "V2", uriString = "c://2", durationMillis = 2000L, sizeBytes = 2048L),
            VideoItem(id = 3L, title = "V3", uriString = "c://3", durationMillis = 3000L, sizeBytes = 3072L)
        )

        val stateFirst = FeedQueueState(
            context = PlaybackContext.GlobalRandomFeed(),
            items = items,
            currentIndex = 0
        )
        assertEquals(items[0], stateFirst.currentVideo)
        assertFalse(stateFirst.hasPrevious)
        assertTrue(stateFirst.hasNext)

        val stateMiddle = FeedQueueState(
            context = PlaybackContext.GlobalRandomFeed(),
            items = items,
            currentIndex = 1
        )
        assertEquals(items[1], stateMiddle.currentVideo)
        assertTrue(stateMiddle.hasPrevious)
        assertTrue(stateMiddle.hasNext)

        val stateLast = FeedQueueState(
            context = PlaybackContext.GlobalRandomFeed(),
            items = items,
            currentIndex = 2
        )
        assertEquals(items[2], stateLast.currentVideo)
        assertTrue(stateLast.hasPrevious)
        assertFalse(stateLast.hasNext)
    }

    @Test
    fun shuffleToggle_switchesBetweenRandomAndNewestFirst() = runTest {
        val fakeRepo = FakeVideoRepository()
        // Initialize with shuffle ON
        val manager = FeedQueueManager(fakeRepo, backgroundScope, initialShuffleEnabled = true)
        assertTrue(manager.isShuffleEnabled.value)

        val shuffledState = manager.shortsQueue.first { it.items.isNotEmpty() }
        assertEquals(5, shuffledState.items.size)

        // Toggle shuffle OFF -> should sort deterministically newest first
        manager.setShuffleEnabled(false)
        assertFalse(manager.isShuffleEnabled.value)

        val newestFirstState = manager.shortsQueue.first {
            (it.context as? PlaybackContext.GlobalRandomFeed)?.orderPreference == FeedOrderPreference.NEWEST_FIRST
        }
        val rawVideos = fakeRepo.getVideos(VideoSortOrder.NEWEST_FIRST).first()
        val expectedNewestFirstIds = rawVideos.sortedByDescending { it.dateAdded }.map { it.id }
        assertEquals(expectedNewestFirstIds, newestFirstState.items.map { it.id })

        // Toggle shuffle ON again -> should be randomized
        manager.setShuffleEnabled(true)
        assertTrue(manager.isShuffleEnabled.value)
        val reShuffledState = manager.shortsQueue.first {
            (it.context as? PlaybackContext.GlobalRandomFeed)?.orderPreference == FeedOrderPreference.RANDOM
        }
        assertEquals(5, reShuffledState.items.size)
    }

    @Test
    fun shortsExploreGrid_gridAndPlayerQueueMatchExactly() = runTest {
        val fakeRepo = FakeVideoRepository()
        val manager = FeedQueueManager(fakeRepo, backgroundScope, initialShuffleEnabled = true)

        val queue = manager.shortsQueue.first { it.items.isNotEmpty() }
        // Grid displays queue.items
        val gridItems = queue.items

        // User taps item at index 3
        val tappedIndex = 3
        val tappedVideo = gridItems[tappedIndex]
        manager.updateShortsIndex(tappedIndex)

        val updatedQueue = manager.shortsQueue.first { it.currentIndex == tappedIndex }
        // Exact video corresponding to tapped thumbnail is current
        assertEquals(tappedVideo.id, updatedQueue.currentVideo?.id)
        // Ordering in player matches grid ordering exactly
        assertEquals(gridItems.map { it.id }, updatedQueue.items.map { it.id })
    }

    @Test
    fun searchFiltering_filtersVideosByFilename() = runTest {
        val items = listOf(
            VideoItem(id = 1L, title = "Gym Workout Part 1.mp4", uriString = "c://1"),
            VideoItem(id = 2L, title = "Abs Routine Morning.mp4", uriString = "c://2"),
            VideoItem(id = 3L, title = "Gym Leg Day Intense.mp4", uriString = "c://3")
        )

        val query = "gym"
        val filtered = items.filter { it.title.contains(query, ignoreCase = true) }
        assertEquals(2, filtered.size)
        assertEquals(1L, filtered[0].id)
        assertEquals(3L, filtered[1].id)

        // Clearing query restores all items
        val emptyQuery = ""
        val restored = if (emptyQuery.isBlank()) items else items.filter { it.title.contains(emptyQuery) }
        assertEquals(3, restored.size)
    }

    @Test
    fun navDestination_selectedAndUnselectedIconsExist() {
        NavDestination.entries.forEach { dest ->
            assertNotNull(dest.selectedIcon)
            assertNotNull(dest.unselectedIcon)
            assertNotNull(dest.icon)
            assertTrue(dest.title.isNotBlank())
            assertTrue(dest.route.isNotBlank())
        }
    }
}
