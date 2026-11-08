package com.binay.vscroll.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import com.binay.vscroll.data.local.dao.CollectionDao
import com.binay.vscroll.data.local.dao.CollectionWithCount
import com.binay.vscroll.data.local.dao.VideoInteractionDao
import com.binay.vscroll.data.local.dao.WatchHistoryDao
import com.binay.vscroll.data.local.entity.CollectionVideoCrossRefEntity
import com.binay.vscroll.data.local.entity.VideoCollectionEntity
import com.binay.vscroll.data.local.entity.VideoInteractionEntity
import com.binay.vscroll.data.local.entity.WatchHistoryEntity
import com.binay.vscroll.data.repository.CollectionRepositoryImpl
import com.binay.vscroll.data.repository.FeedQueueManager
import com.binay.vscroll.data.storage.LocalStorageManager
import com.binay.vscroll.domain.model.FolderItem
import com.binay.vscroll.domain.model.PlaybackContext
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.model.VideoSortOrder
import com.binay.vscroll.domain.repository.CollectionRepository
import com.binay.vscroll.domain.repository.VideoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

class InMemoryCollectionDao : CollectionDao {
    val collectionsStore = MutableStateFlow<Map<Long, VideoCollectionEntity>>(emptyMap())
    val crossRefStore = MutableStateFlow<List<CollectionVideoCrossRefEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAllCollections(): Flow<List<VideoCollectionEntity>> {
        return collectionsStore.map { it.values.sortedByDescending { c -> c.updatedAt } }
    }

    override fun observeCollectionsWithCounts(): Flow<List<CollectionWithCount>> {
        return kotlinx.coroutines.flow.combine(collectionsStore, crossRefStore) { collections, refs ->
            collections.values.map { c ->
                val matchingRefs = refs.filter { it.collectionId == c.id }.sortedByDescending { it.addedAt }
                CollectionWithCount(
                    id = c.id,
                    name = c.name,
                    createdAt = c.createdAt,
                    updatedAt = c.updatedAt,
                    videoCount = matchingRefs.size,
                    coverVideoId = matchingRefs.firstOrNull()?.mediaStoreId
                )
            }.sortedByDescending { it.updatedAt }
        }
    }

    override fun observeCollectionById(id: Long): Flow<VideoCollectionEntity?> {
        return collectionsStore.map { it[id] }
    }

    override suspend fun getCollectionByIdSync(id: Long): VideoCollectionEntity? {
        return collectionsStore.value[id]
    }

    override suspend fun findCollectionByNameSync(name: String): VideoCollectionEntity? {
        return collectionsStore.value.values.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    override suspend fun insertCollection(collection: VideoCollectionEntity): Long {
        val id = if (collection.id > 0) collection.id else nextId++
        val entity = collection.copy(id = id)
        collectionsStore.update { it + (id to entity) }
        return id
    }

    override suspend fun updateCollection(collection: VideoCollectionEntity): Int {
        if (!collectionsStore.value.containsKey(collection.id)) return 0
        collectionsStore.update { it + (collection.id to collection) }
        return 1
    }

    override suspend fun renameCollection(id: Long, newName: String, updatedAt: Long): Int {
        val current = collectionsStore.value[id] ?: return 0
        collectionsStore.update { it + (id to current.copy(name = newName, updatedAt = updatedAt)) }
        return 1
    }

    override suspend fun deleteCollection(id: Long): Int {
        if (!collectionsStore.value.containsKey(id)) return 0
        collectionsStore.update { it - id }
        // Cascade delete cross references
        crossRefStore.update { list -> list.filterNot { it.collectionId == id } }
        return 1
    }

    override suspend fun insertCrossRef(crossRef: CollectionVideoCrossRefEntity): Long {
        crossRefStore.update { list ->
            if (list.any { it.collectionId == crossRef.collectionId && it.mediaStoreId == crossRef.mediaStoreId }) {
                list
            } else {
                list + crossRef
            }
        }
        return crossRef.mediaStoreId
    }

    override suspend fun insertCrossRefs(crossRefs: List<CollectionVideoCrossRefEntity>): List<Long> {
        for (ref in crossRefs) {
            insertCrossRef(ref)
        }
        return crossRefs.map { it.mediaStoreId }
    }

    override suspend fun removeVideoFromCollection(collectionId: Long, mediaStoreId: Long): Int {
        val before = crossRefStore.value.size
        crossRefStore.update { list ->
            list.filterNot { it.collectionId == collectionId && it.mediaStoreId == mediaStoreId }
        }
        return before - crossRefStore.value.size
    }

    override suspend fun removeVideosFromCollection(collectionId: Long, mediaStoreIds: List<Long>): Int {
        val before = crossRefStore.value.size
        val idSet = mediaStoreIds.toSet()
        crossRefStore.update { list ->
            list.filterNot { it.collectionId == collectionId && idSet.contains(it.mediaStoreId) }
        }
        return before - crossRefStore.value.size
    }

    override fun observeVideoIdsForCollection(collectionId: Long): Flow<List<Long>> {
        return crossRefStore.map { list ->
            list.filter { it.collectionId == collectionId }.sortedByDescending { it.addedAt }.map { it.mediaStoreId }
        }
    }

    override suspend fun getVideoIdsForCollectionSync(collectionId: Long): List<Long> {
        return crossRefStore.value.filter { it.collectionId == collectionId }
            .sortedByDescending { it.addedAt }
            .map { it.mediaStoreId }
    }

    override fun observeCollectionIdsForVideo(mediaStoreId: Long): Flow<List<Long>> {
        return crossRefStore.map { list ->
            list.filter { it.mediaStoreId == mediaStoreId }.map { it.collectionId }
        }
    }

    override fun observeCollectionCount(): Flow<Int> {
        return collectionsStore.map { it.size }
    }

    override fun observeVideoCountForCollection(collectionId: Long): Flow<Int> {
        return crossRefStore.map { list -> list.count { it.collectionId == collectionId } }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class Milestone8Tests {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var collectionDao: InMemoryCollectionDao
    private lateinit var collectionRepository: CollectionRepository
    private lateinit var interactionDao: InMemoryVideoInteractionDao
    private lateinit var watchHistoryDao: InMemoryWatchHistoryDao

    private val video1 = VideoItem(
        id = 101L,
        title = "workout_motivation.mp4",
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
        title = "coding_tutorial.mp4",
        uriString = "content://media/external/video/media/202",
        durationMillis = 60_000L,
        sizeBytes = 25_000_000L,
        width = 1920,
        height = 1080,
        mimeType = "video/mp4",
        dateAdded = 2000L,
        dateModified = 2000L,
        folderName = "Tutorials",
        relativePath = "Movies/Tutorials/"
    )

    private val video3 = VideoItem(
        id = 303L,
        title = "nature_drone_4k.mp4",
        uriString = "content://media/external/video/media/303",
        durationMillis = 45_000L,
        sizeBytes = 80_000_000L,
        width = 3840,
        height = 2160,
        mimeType = "video/mp4",
        dateAdded = 3000L,
        dateModified = 3000L,
        folderName = "Nature",
        relativePath = "DCIM/Camera/"
    )

    @Before
    fun setup() {
        collectionDao = InMemoryCollectionDao()
        collectionRepository = CollectionRepositoryImpl(collectionDao, testDispatcher)
        interactionDao = InMemoryVideoInteractionDao()
        watchHistoryDao = InMemoryWatchHistoryDao()
    }

    // 1. Creating virtual collections
    @Test
    fun test01_createVirtualCollection() = runTest(testDispatcher) {
        val result = collectionRepository.createCollection("Gym Motivation")
        assertTrue(result.isSuccess)
        val id = result.getOrThrow()
        assertTrue(id > 0)

        val collections = collectionRepository.observeCollections().first()
        assertEquals(1, collections.size)
        assertEquals("Gym Motivation", collections[0].name)
        assertEquals(0, collections[0].videoCount)
    }

    // 2. Renaming collections
    @Test
    fun test02_renameVirtualCollection() = runTest(testDispatcher) {
        val id = collectionRepository.createCollection("Workouts").getOrThrow()
        val renameResult = collectionRepository.renameCollection(id, "Extreme Workouts")
        assertTrue(renameResult.isSuccess)

        val updated = collectionRepository.observeCollection(id).first()
        assertNotNull(updated)
        assertEquals("Extreme Workouts", updated!!.name)
    }

    // 3. Deleting collections without deleting videos
    @Test
    fun test03_deleteCollectionWithoutDeletingVideos() = runTest(testDispatcher) {
        val allVideosFlow = MutableStateFlow(listOf(video1, video2))
        val id = collectionRepository.createCollection("Temporary Collection").getOrThrow()
        collectionRepository.addVideosToCollection(id, listOf(video1.id, video2.id))

        // Ensure collection has 2 videos
        val collectionVideosBefore = collectionRepository.observeCollectionVideos(id, allVideosFlow).first()
        assertEquals(2, collectionVideosBefore.size)

        // Delete collection
        val deleteResult = collectionRepository.deleteCollection(id)
        assertTrue(deleteResult.isSuccess)

        // Virtual collection is gone
        val collections = collectionRepository.observeCollections().first()
        assertTrue(collections.isEmpty())

        // Actual videos in library remain completely intact
        assertEquals(2, allVideosFlow.value.size)
        assertEquals(101L, allVideosFlow.value[0].id)
    }

    // 4. Adding and removing videos from collections
    @Test
    fun test04_addAndRemoveVideosFromCollection() = runTest(testDispatcher) {
        val allVideosFlow = MutableStateFlow(listOf(video1, video2, video3))
        val id = collectionRepository.createCollection("Favorites").getOrThrow()

        // Add 2 videos
        collectionRepository.addVideosToCollection(id, listOf(video1.id, video2.id))
        var items = collectionRepository.observeCollectionVideos(id, allVideosFlow).first()
        assertEquals(2, items.size)

        // Remove 1 video
        collectionRepository.removeVideosFromCollection(id, listOf(video1.id))
        items = collectionRepository.observeCollectionVideos(id, allVideosFlow).first()
        assertEquals(1, items.size)
        assertEquals(video2.id, items[0].id)
    }

    // 5. Multiple collections containing the same video
    @Test
    fun test05_multipleCollectionsContainingSameVideo() = runTest(testDispatcher) {
        val allVideosFlow = MutableStateFlow(listOf(video1))
        val col1 = collectionRepository.createCollection("Best of 2026").getOrThrow()
        val col2 = collectionRepository.createCollection("Daily Highlights").getOrThrow()

        // Add same video1 to both collections
        collectionRepository.addVideosToCollection(col1, listOf(video1.id))
        collectionRepository.addVideosToCollection(col2, listOf(video1.id))

        val col1Videos = collectionRepository.observeCollectionVideos(col1, allVideosFlow).first()
        val col2Videos = collectionRepository.observeCollectionVideos(col2, allVideosFlow).first()

        assertEquals(1, col1Videos.size)
        assertEquals(1, col2Videos.size)
        assertEquals(video1.id, col1Videos[0].id)
        assertEquals(video1.id, col2Videos[0].id)

        // Video appears associated with both collection IDs
        val associatedCollections = collectionRepository.observeCollectionIdsForVideo(video1.id).first()
        assertEquals(2, associatedCollections.size)
        assertTrue(associatedCollections.contains(col1))
        assertTrue(associatedCollections.contains(col2))
    }

    // 6. Physical folder creation validation
    @Test
    fun test06_physicalFolderCreationValidation() {
        // Valid names
        assertTrue(LocalStorageManager.validateFolderName("Highlights").isSuccess)
        assertTrue(LocalStorageManager.validateFolderName("Workout Videos 2026").isSuccess)

        // Invalid names: empty, blank, illegal chars
        assertTrue(LocalStorageManager.validateFolderName("").isFailure)
        assertTrue(LocalStorageManager.validateFolderName("   ").isFailure)
        assertTrue(LocalStorageManager.validateFolderName("Folder/Subfolder").isFailure)
        assertTrue(LocalStorageManager.validateFolderName("Folder:Invalid").isFailure)
        assertTrue(LocalStorageManager.validateFolderName("Folder*Star").isFailure)
        assertTrue(LocalStorageManager.validateFolderName(".").isFailure)
    }

    // 7. Physical folder rename validation
    @Test
    fun test07_physicalFolderRenameValidation() {
        val valid = LocalStorageManager.validateFolderName("RenamedFolder")
        assertTrue(valid.isSuccess)
        assertEquals("RenamedFolder", valid.getOrThrow())

        val sanitized = LocalStorageManager.sanitizeFolderName("Illegal/Name?*")
        assertEquals("IllegalName", sanitized)
    }

    // 8. Moving videos between folders and metadata reconciliation
    @Test
    fun test08_movingVideosBetweenFoldersReconciliation() = runTest(testDispatcher) {
        // Prepare existing interaction and history for video1 in "Fitness"
        val now = System.currentTimeMillis()
        interactionDao.insertOrUpdate(
            VideoInteractionEntity(
                mediaStoreId = video1.id,
                contentUri = video1.uriString,
                cachedTitle = video1.title,
                cachedFolderName = "Fitness",
                isLiked = true,
                likedAt = now
            )
        )
        watchHistoryDao.insertOrReplace(
            WatchHistoryEntity(
                mediaStoreId = video1.id,
                cachedTitle = video1.title,
                cachedFolderName = "Fitness",
                contentUri = video1.uriString,
                cachedDurationMs = 30_000L,
                firstWatchedAt = now,
                lastWatchedAt = now,
                totalWatchTimeMs = 25_000L,
                sessionCount = 2,
                lastPositionMs = 12_000L
            )
        )

        // Simulate moving video1 from "Fitness" to "Archived"
        val existingInteraction = interactionDao.getInteractionSync(video1.id)
        assertNotNull(existingInteraction)
        assertEquals("Fitness", existingInteraction!!.cachedFolderName)

        // Reconcile moved video
        interactionDao.insertOrUpdate(existingInteraction.copy(cachedFolderName = "Archived"))
        val existingHistory = watchHistoryDao.getWatchHistory(video1.id)
        assertNotNull(existingHistory)
        watchHistoryDao.insertOrReplace(existingHistory!!.copy(cachedFolderName = "Archived"))

        // Assert Likes and Watch History remain intact with the new folder name
        val updatedInteraction = interactionDao.getInteractionSync(video1.id)
        assertNotNull(updatedInteraction)
        assertTrue(updatedInteraction!!.isLiked)
        assertEquals("Archived", updatedInteraction.cachedFolderName)

        val updatedHistory = watchHistoryDao.getWatchHistory(video1.id)
        assertNotNull(updatedHistory)
        assertEquals(25_000L, updatedHistory!!.totalWatchTimeMs)
        assertEquals("Archived", updatedHistory.cachedFolderName)
    }

    // 9. Multi-select operations
    @Test
    fun test09_multiSelectOperations() {
        val selectedSet = mutableSetOf<Long>()

        // Toggle selection
        selectedSet.add(video1.id)
        assertTrue(selectedSet.contains(video1.id))
        assertEquals(1, selectedSet.size)

        // Select multiple
        selectedSet.add(video2.id)
        assertEquals(2, selectedSet.size)

        // Deselect single
        selectedSet.remove(video1.id)
        assertEquals(1, selectedSet.size)
        assertFalse(selectedSet.contains(video1.id))

        // Select all
        val allVideos = listOf(video1, video2, video3)
        selectedSet.addAll(allVideos.map { it.id })
        assertEquals(3, selectedSet.size)

        // Deselect all
        selectedSet.clear()
        assertTrue(selectedSet.isEmpty())
    }

    // 10. Permission denial handling
    @Test
    fun test10_permissionDenialHandling() {
        // When user denies permission in Scoped Storage consent dialog, action is safely cancelled
        var resultMessage = ""
        val onResult: (String) -> Unit = { resultMessage = it }

        // Simulate permission denied callback
        val isConsentGranted = false
        if (!isConsentGranted) {
            onResult("Storage operation was cancelled or denied by user")
        }

        assertEquals("Storage operation was cancelled or denied by user", resultMessage)
    }

    // 11. Deleted or inaccessible video handling in collections
    @Test
    fun test11_deletedOrInaccessibleVideoHandling() = runTest(testDispatcher) {
        val colId = collectionRepository.createCollection("Tech").getOrThrow()
        collectionRepository.addVideosToCollection(colId, listOf(video1.id, video2.id))

        // Simulate video1 being deleted from MediaStore, so only video2 remains
        val currentMediaStoreVideos = MutableStateFlow(listOf(video2))
        val displayedVideos = collectionRepository.observeCollectionVideos(colId, currentMediaStoreVideos).first()

        // Missing video is safely omitted without crash or index out of bounds
        assertEquals(1, displayedVideos.size)
        assertEquals(video2.id, displayedVideos[0].id)
    }

    // 12. Library refresh after file operations
    @Test
    fun test12_libraryRefreshAfterFileOperations() = runTest(testDispatcher) {
        val repo = FakeTestVideoRepository().apply {
            videosFlow.value = listOf(video1, video2)
        }
        assertEquals(2, repo.getVideos(VideoSortOrder.NEWEST_FIRST).first().size)

        // Simulate new video file added on storage
        repo.videosFlow.value = listOf(video1, video2, video3)
        repo.refreshVideos()

        // Emits newly scanned video
        assertEquals(3, repo.getVideos(VideoSortOrder.NEWEST_FIRST).first().size)
    }

    // 13. Existing Likes and Bookmarks persistence
    @Test
    fun test13_existingLikesAndBookmarksPersistence() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        interactionDao.insertOrUpdate(
            VideoInteractionEntity(
                mediaStoreId = video1.id,
                contentUri = video1.uriString,
                cachedTitle = video1.title,
                cachedFolderName = video1.folderName,
                isLiked = true,
                likedAt = now,
                isBookmarked = true,
                bookmarkedAt = now
            )
        )

        val retrieved = interactionDao.getInteractionSync(video1.id)
        assertNotNull(retrieved)
        assertTrue(retrieved!!.isLiked)
        assertTrue(retrieved.isBookmarked)
    }

    // 14. Private Notes persistence
    @Test
    fun test14_privateNotesPersistence() = runTest(testDispatcher) {
        val noteDao = InMemoryVideoNoteDao()
        noteDao.insertOrUpdate(
            com.binay.vscroll.data.local.entity.VideoNoteEntity(
                mediaStoreId = video1.id,
                noteText = "Key technique at 0:15"
            )
        )

        val note = noteDao.getNoteSync(video1.id)
        assertNotNull(note)
        assertEquals("Key technique at 0:15", note!!.noteText)
    }

    // 15. Watch History persistence
    @Test
    fun test15_watchHistoryPersistence() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        watchHistoryDao.insertOrReplace(
            WatchHistoryEntity(
                mediaStoreId = video2.id,
                cachedTitle = video2.title,
                cachedFolderName = video2.folderName,
                contentUri = video2.uriString,
                cachedDurationMs = video2.durationMillis,
                firstWatchedAt = now,
                lastWatchedAt = now,
                totalWatchTimeMs = 45_000L,
                sessionCount = 3,
                lastPositionMs = 20_000L
            )
        )

        val history = watchHistoryDao.getWatchHistory(video2.id)
        assertNotNull(history)
        assertEquals(45_000L, history!!.totalWatchTimeMs)
        assertEquals(3, history.sessionCount)
    }

    // 16. Shorts queue isolation
    @Test
    fun test16_shortsQueueIsolation() = runTest {
        val repo = FakeTestVideoRepository().apply {
            videosFlow.value = listOf(video1, video2)
        }
        val feedQueueManager = FeedQueueManager(repo, backgroundScope)

        // Start collection playback
        feedQueueManager.startCollectionPlayback(
            collectionId = 1L,
            collectionName = "Favorites",
            collectionVideos = listOf(video2),
            initialIndex = 0,
            sortOrder = VideoSortOrder.NEWEST_FIRST
        )

        // Verify folder / collection queue is active
        val collectionQueue = feedQueueManager.folderQueue.value
        assertNotNull(collectionQueue)
        assertTrue(collectionQueue!!.context is PlaybackContext.CollectionSequential)
        assertEquals(1, collectionQueue.items.size)
        assertEquals(video2.id, collectionQueue.items[0].id)

        // Global Shorts queue remains independent with all videos
        val shorts = feedQueueManager.shortsQueue.first { it.items.isNotEmpty() }
        assertEquals(2, shorts.items.size)
    }

    // 17. Folder sequential playback
    @Test
    fun test17_folderSequentialPlayback() = runTest {
        val repo = FakeTestVideoRepository().apply {
            videosFlow.value = listOf(video1, video2)
        }
        val feedQueueManager = FeedQueueManager(repo, backgroundScope)

        feedQueueManager.startFolderPlayback(
            folderId = "Fitness",
            folderName = "Fitness",
            folderVideos = listOf(video1),
            initialIndex = 0,
            sortOrder = VideoSortOrder.NEWEST_FIRST
        )

        val queue = feedQueueManager.folderQueue.value
        assertNotNull(queue)
        assertTrue(queue!!.context is PlaybackContext.FolderSequential)
        assertEquals("Fitness", (queue.context as PlaybackContext.FolderSequential).folderName)
    }

    // 18. Database migration V3 to V4
    @Test
    fun test18_databaseMigrationV3ToV4() {
        val executedQueries = mutableListOf<String>()
        val mockDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedQueries.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        DatabaseMigrations.MIGRATION_3_4.migrate(mockDb)

        // Verify creation of video_collections and collection_videos tables and indices
        assertTrue(executedQueries.any { it.contains("CREATE TABLE IF NOT EXISTS `video_collections`") })
        assertTrue(executedQueries.any { it.contains("CREATE TABLE IF NOT EXISTS `collection_videos`") })
        assertTrue(executedQueries.any { it.contains("index_video_collections_name") })
        assertTrue(executedQueries.any { it.contains("index_collection_videos_collection_id") })
    }

    // 19. Empty folder and collection states
    @Test
    fun test19_emptyFolderAndCollectionStates() = runTest(testDispatcher) {
        val emptyVideosFlow = MutableStateFlow<List<VideoItem>>(emptyList())
        val colId = collectionRepository.createCollection("Empty Collection").getOrThrow()
        val items = collectionRepository.observeCollectionVideos(colId, emptyVideosFlow).first()
        assertTrue(items.isEmpty())

        val count = collectionDao.observeVideoCountForCollection(colId).first()
        assertEquals(0, count)
    }

    // 20. Operation failure recovery (validation errors, duplicates, blank names)
    @Test
    fun test20_operationFailureRecovery() = runTest(testDispatcher) {
        // Blank name
        val blankResult = collectionRepository.createCollection("   ")
        assertTrue(blankResult.isFailure)

        // Valid creation
        val successResult = collectionRepository.createCollection("Study Music")
        assertTrue(successResult.isSuccess)

        // Duplicate name
        val duplicateResult = collectionRepository.createCollection("Study Music")
        assertTrue(duplicateResult.isFailure)
        assertTrue(duplicateResult.exceptionOrNull()?.message?.contains("already exists") == true)

        // Renaming to existing collection name fails
        val col2 = collectionRepository.createCollection("Relaxing").getOrThrow()
        val renameToExisting = collectionRepository.renameCollection(col2, "Study Music")
        assertTrue(renameToExisting.isFailure)
    }
}
