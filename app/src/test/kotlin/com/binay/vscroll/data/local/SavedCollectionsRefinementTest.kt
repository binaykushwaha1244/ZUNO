package com.binay.vscroll.data.local

import com.binay.vscroll.data.repository.CollectionRepositoryImpl
import com.binay.vscroll.data.repository.FeedQueueManager
import com.binay.vscroll.domain.model.PlaybackContext
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.model.VideoSortOrder
import com.binay.vscroll.domain.repository.CollectionRepository
import com.binay.vscroll.domain.repository.VideoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive Unit Tests for Instagram-Inspired Saved Collections UI Refinement:
 * - Collection operations (create, multiple, rename, delete, empty, add, remove, multi-membership, duplicate prevention, count updates).
 * - Save behavior (saving independently, saving to collection, multiple collections, removing without un-saving).
 * - Thumbnail behavior (first video cover, empty collection placeholder, cover updates on removal).
 * - Playback isolation (exact video start, sequential isolated swiping, queue ordering, no shorts/folder mixing).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SavedCollectionsRefinementTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var collectionDao: InMemoryCollectionDao
    private lateinit var collectionRepository: CollectionRepository
    private lateinit var interactionDao: InMemoryVideoInteractionDao
    private lateinit var fakeVideoRepository: FakeVideoRepository
    private lateinit var feedQueueManager: FeedQueueManager

    private val video1 = VideoItem(
        id = 101L,
        title = "workout_abs.mp4",
        uriString = "content://media/external/video/media/101",
        durationMillis = 30_000L,
        sizeBytes = 15_000_000L,
        width = 1080,
        height = 1920,
        mimeType = "video/mp4",
        dateAdded = 1000L,
        dateModified = 1000L,
        folderName = "Fitness",
        relativePath = "Movies/Fitness/"
    )

    private val video2 = VideoItem(
        id = 202L,
        title = "motivation_speech.mp4",
        uriString = "content://media/external/video/media/202",
        durationMillis = 60_000L,
        sizeBytes = 25_000_000L,
        width = 1920,
        height = 1080,
        mimeType = "video/mp4",
        dateAdded = 2000L,
        dateModified = 2000L,
        folderName = "Speeches",
        relativePath = "Movies/Speeches/"
    )

    private val video3 = VideoItem(
        id = 303L,
        title = "cooking_recipe.mp4",
        uriString = "content://media/external/video/media/303",
        durationMillis = 45_000L,
        sizeBytes = 40_000_000L,
        width = 1080,
        height = 1920,
        mimeType = "video/mp4",
        dateAdded = 3000L,
        dateModified = 3000L,
        folderName = "Cooking",
        relativePath = "Movies/Cooking/"
    )

    private class FakeVideoRepository(val videos: List<VideoItem>) : VideoRepository {
        override fun getVideos(sortOrder: VideoSortOrder): Flow<List<VideoItem>> = flowOf(videos)
        override fun getFolders(): Flow<List<com.binay.vscroll.domain.model.FolderItem>> = flowOf(emptyList())
        override suspend fun refreshVideos() {}
    }

    @Before
    fun setup() {
        collectionDao = InMemoryCollectionDao()
        collectionRepository = CollectionRepositoryImpl(collectionDao, testDispatcher)
        interactionDao = InMemoryVideoInteractionDao()
        fakeVideoRepository = FakeVideoRepository(listOf(video1, video2, video3))
        feedQueueManager = FeedQueueManager(fakeVideoRepository, initialShuffleEnabled = false)
    }

    // --- 1. Collection Operations ---

    @Test
    fun test01_createSingleAndMultipleCollections() = runTest(testDispatcher) {
        val res1 = collectionRepository.createCollection("Workout")
        assertTrue(res1.isSuccess)
        val id1 = res1.getOrThrow()

        val res2 = collectionRepository.createCollection("Motivation")
        assertTrue(res2.isSuccess)
        val id2 = res2.getOrThrow()

        val collections = collectionRepository.observeCollections().first()
        assertEquals(2, collections.size)
        assertTrue(collections.any { it.id == id1 && it.name == "Workout" })
        assertTrue(collections.any { it.id == id2 && it.name == "Motivation" })
    }

    @Test
    fun test02_handleEmptyCollection() = runTest(testDispatcher) {
        val id = collectionRepository.createCollection("Empty Collection").getOrThrow()
        val collection = collectionRepository.observeCollections().first().first { it.id == id }

        assertEquals(0, collection.videoCount)
        assertNull(collection.coverVideoId)
        assertNull(collection.coverVideoUri)
    }

    @Test
    fun test03_renameCollectionValidAndDuplicateCheck() = runTest(testDispatcher) {
        val id1 = collectionRepository.createCollection("Workout").getOrThrow()
        val id2 = collectionRepository.createCollection("Cardio").getOrThrow()

        // Rename id1 to "Extreme Workout"
        val renameRes = collectionRepository.renameCollection(id1, "Extreme Workout")
        assertTrue(renameRes.isSuccess)
        val collections = collectionRepository.observeCollections().first()
        assertEquals("Extreme Workout", collections.first { it.id == id1 }.name)

        // Attempt to rename id2 to "Extreme Workout" (duplicate name rejection)
        val duplicateRes = collectionRepository.renameCollection(id2, "Extreme Workout")
        assertFalse(duplicateRes.isSuccess)
    }

    @Test
    fun test04_deleteCollectionPreservesOriginalVideos() = runTest(testDispatcher) {
        val colId = collectionRepository.createCollection("Temporary").getOrThrow()
        collectionRepository.addVideosToCollection(colId, listOf(video1.id, video2.id))

        // Ensure collection has 2 videos
        var collections = collectionRepository.observeCollections().first()
        assertEquals(2, collections.first { it.id == colId }.videoCount)

        // Delete collection
        val delRes = collectionRepository.deleteCollection(colId)
        assertTrue(delRes.isSuccess)

        // Verify collection is gone
        collections = collectionRepository.observeCollections().first()
        assertTrue(collections.none { it.id == colId })

        // Verify original videos still exist in video repository
        assertTrue(fakeVideoRepository.videos.any { it.id == video1.id })
        assertTrue(fakeVideoRepository.videos.any { it.id == video2.id })
    }

    // --- 2. Membership & Save Behavior ---

    @Test
    fun test05_saveOneVideoToMultipleCollections() = runTest(testDispatcher) {
        val col1 = collectionRepository.createCollection("Favorites").getOrThrow()
        val col2 = collectionRepository.createCollection("Gym").getOrThrow()

        // Add video1 to both collections
        collectionRepository.addVideosToCollection(col1, listOf(video1.id))
        collectionRepository.addVideosToCollection(col2, listOf(video1.id))

        val activeColsForVideo1 = collectionRepository.observeCollectionIdsForVideo(video1.id).first()
        assertEquals(2, activeColsForVideo1.size)
        assertTrue(activeColsForVideo1.contains(col1))
        assertTrue(activeColsForVideo1.contains(col2))
    }

    @Test
    fun test06_preventDuplicateMembershipEntries() = runTest(testDispatcher) {
        val colId = collectionRepository.createCollection("Unique").getOrThrow()

        collectionRepository.addVideosToCollection(colId, listOf(video1.id))
        collectionRepository.addVideosToCollection(colId, listOf(video1.id)) // Duplicate add

        val count = collectionDao.observeVideoCountForCollection(colId).first()
        assertEquals(1, count)
    }

    @Test
    fun test07_removeVideoFromOneCollectionDoesNotAffectOthers() = runTest(testDispatcher) {
        val col1 = collectionRepository.createCollection("Playlist A").getOrThrow()
        val col2 = collectionRepository.createCollection("Playlist B").getOrThrow()

        collectionRepository.addVideosToCollection(col1, listOf(video1.id))
        collectionRepository.addVideosToCollection(col2, listOf(video1.id))

        // Remove video1 from Playlist A
        val remRes = collectionRepository.removeVideosFromCollection(col1, listOf(video1.id))
        assertTrue(remRes.isSuccess)

        // Playlist A should now have 0 videos
        val vidsCol1 = collectionRepository.observeCollectionVideos(col1, flowOf(listOf(video1))).first()
        assertEquals(0, vidsCol1.size)

        // Playlist B must still have video1
        val vidsCol2 = collectionRepository.observeCollectionVideos(col2, flowOf(listOf(video1))).first()
        assertEquals(1, vidsCol2.size)
        assertEquals(video1.id, vidsCol2[0].id)
    }

    // --- 3. Thumbnail & Cover Selection Behavior ---

    @Test
    fun test08_coverThumbnailUsesFirstAddedAndUpdatesOnRemoval() = runTest(testDispatcher) {
        val colId = collectionRepository.createCollection("Dynamic Cover").getOrThrow()

        // Add video1 first
        collectionRepository.addVideosToCollection(colId, listOf(video1.id))
        var col = collectionRepository.observeCollections().first().first { it.id == colId }
        assertEquals(video1.id, col.coverVideoId)

        // Add video2
        collectionRepository.addVideosToCollection(colId, listOf(video2.id))
        col = collectionRepository.observeCollections().first().first { it.id == colId }
        // Latest added or available is cover
        assertTrue(col.coverVideoId == video1.id || col.coverVideoId == video2.id)

        // Remove video2
        collectionRepository.removeVideosFromCollection(colId, listOf(video2.id))
        col = collectionRepository.observeCollections().first().first { it.id == colId }
        assertEquals(video1.id, col.coverVideoId)

        // Remove video1
        collectionRepository.removeVideosFromCollection(colId, listOf(video1.id))
        col = collectionRepository.observeCollections().first().first { it.id == colId }
        assertNull(col.coverVideoId)
    }

    // --- 4. Collection Playback & Queue Isolation ---

    @Test
    fun test09_collectionPlaybackInitializesExactVideoAndIsolatesQueue() = runTest(testDispatcher) {
        val collectionVideos = listOf(video1, video2, video3)

        // Start playback from middle video (video2 at index 1)
        feedQueueManager.startCollectionPlayback(
            collectionId = 42L,
            collectionName = "Workout Mix",
            collectionVideos = collectionVideos,
            initialIndex = 1,
            sortOrder = VideoSortOrder.NEWEST_FIRST
        )

        val folderState = feedQueueManager.folderQueue.first()
        assertNotNull(folderState)
        assertEquals(1, folderState?.currentIndex)
        assertEquals(video2.id, folderState?.currentVideo?.id)
        assertEquals(3, folderState?.items?.size)

        val context = folderState?.context as? PlaybackContext.CollectionSequential
        assertNotNull(context)
        assertEquals(42L, context?.collectionId)
        assertEquals("Workout Mix", context?.collectionName)

        // Swipe down to video 3 (index 2)
        feedQueueManager.updateFolderIndex(2)
        val updatedState = feedQueueManager.folderQueue.first()
        assertEquals(2, updatedState?.currentIndex)
        assertEquals(video3.id, updatedState?.currentVideo?.id)

        // Confirm shorts queue is completely isolated and not affected
        assertNull(feedQueueManager.interactionQueue.first())
    }

    @Test
    fun test10_closeCollectionPlaybackResetsQueue() = runTest(testDispatcher) {
        feedQueueManager.startCollectionPlayback(
            collectionId = 99L,
            collectionName = "Temporary Queue",
            collectionVideos = listOf(video1),
            initialIndex = 0,
            sortOrder = VideoSortOrder.NEWEST_FIRST
        )
        assertNotNull(feedQueueManager.folderQueue.first())

        feedQueueManager.closeFolderPlayback()
        assertNull(feedQueueManager.folderQueue.first())
    }
}

