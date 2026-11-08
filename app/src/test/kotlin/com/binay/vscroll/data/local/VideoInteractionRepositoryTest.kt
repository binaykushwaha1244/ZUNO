package com.binay.vscroll.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import com.binay.vscroll.data.local.dao.VideoInteractionDao
import com.binay.vscroll.data.local.dao.VideoNoteDao
import com.binay.vscroll.data.local.entity.VideoInteractionEntity
import com.binay.vscroll.data.local.entity.VideoNoteEntity
import com.binay.vscroll.data.repository.FeedQueueManager
import com.binay.vscroll.data.repository.VideoInteractionRepositoryImpl
import com.binay.vscroll.domain.model.FolderItem
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.model.VideoSortOrder
import com.binay.vscroll.domain.repository.VideoRepository
import com.binay.vscroll.ui.liked.LikedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * In-memory test implementation of VideoInteractionDao.
 */
class InMemoryVideoInteractionDao : VideoInteractionDao {
    val store = MutableStateFlow<Map<Long, VideoInteractionEntity>>(emptyMap())

    override suspend fun insertOrUpdate(interaction: VideoInteractionEntity): Long {
        store.update { it + (interaction.mediaStoreId to interaction) }
        return interaction.mediaStoreId
    }

    override fun getInteraction(mediaStoreId: Long): Flow<VideoInteractionEntity?> {
        return store.map { it[mediaStoreId] }
    }

    override suspend fun getInteractionSync(mediaStoreId: Long): VideoInteractionEntity? {
        return store.value[mediaStoreId]
    }

    override fun getAllLikedVideos(): Flow<List<VideoInteractionEntity>> {
        return store.map { map ->
            map.values.filter { it.isLiked }.sortedByDescending { it.likedAt ?: 0L }
        }
    }

    override fun getAllBookmarkedVideos(): Flow<List<VideoInteractionEntity>> {
        return store.map { map ->
            map.values.filter { it.isBookmarked }.sortedByDescending { it.bookmarkedAt ?: 0L }
        }
    }

    override suspend fun updateLikeStatus(mediaStoreId: Long, isLiked: Boolean, likedAt: Long?, updatedAt: Long): Int {
        val existing = store.value[mediaStoreId] ?: return 0
        store.update { it + (mediaStoreId to existing.copy(isLiked = isLiked, likedAt = likedAt, updatedAt = updatedAt)) }
        return 1
    }

    override suspend fun updateBookmarkStatus(mediaStoreId: Long, isBookmarked: Boolean, bookmarkedAt: Long?, updatedAt: Long): Int {
        val existing = store.value[mediaStoreId] ?: return 0
        store.update { it + (mediaStoreId to existing.copy(isBookmarked = isBookmarked, bookmarkedAt = bookmarkedAt, updatedAt = updatedAt)) }
        return 1
    }

    override suspend fun deleteInteraction(mediaStoreId: Long): Int {
        val hadKey = store.value.containsKey(mediaStoreId)
        store.update { it - mediaStoreId }
        return if (hadKey) 1 else 0
    }

    override fun getLikedCount(): Flow<Int> {
        return store.map { map -> map.values.count { it.isLiked } }
    }

    override fun getBookmarkedCount(): Flow<Int> {
        return store.map { map -> map.values.count { it.isBookmarked } }
    }

    override suspend fun getAllInteractionsSync(): List<VideoInteractionEntity> {
        return store.value.values.toList()
    }
}

/**
 * In-memory test implementation of VideoNoteDao.
 */
class InMemoryVideoNoteDao : VideoNoteDao {
    val store = MutableStateFlow<Map<Long, VideoNoteEntity>>(emptyMap())

    override suspend fun insertOrUpdate(note: VideoNoteEntity): Long {
        store.update { it + (note.mediaStoreId to note) }
        return note.mediaStoreId
    }

    override fun getNote(mediaStoreId: Long): Flow<VideoNoteEntity?> {
        return store.map { it[mediaStoreId] }
    }

    override suspend fun getNoteSync(mediaStoreId: Long): VideoNoteEntity? {
        return store.value[mediaStoreId]
    }

    override fun getAllNotes(): Flow<List<VideoNoteEntity>> {
        return store.map { map ->
            map.values.sortedByDescending { it.updatedAt }
        }
    }

    override suspend fun deleteNote(mediaStoreId: Long): Int {
        val hadKey = store.value.containsKey(mediaStoreId)
        store.update { it - mediaStoreId }
        return if (hadKey) 1 else 0
    }

    override fun getNotesCount(): Flow<Int> {
        return store.map { it.size }
    }
}

/**
 * Fake VideoRepository providing controllable library videos for test scenarios.
 */
class FakeTestVideoRepository : VideoRepository {
    val videosFlow = MutableStateFlow<List<VideoItem>>(emptyList())
    val foldersFlow = MutableStateFlow<List<FolderItem>>(emptyList())

    override fun getVideos(sortOrder: VideoSortOrder): Flow<List<VideoItem>> = videosFlow
    override fun getFolders(): Flow<List<FolderItem>> = foldersFlow
    override suspend fun refreshVideos() {}
}

class VideoInteractionRepositoryTest {

    private lateinit var interactionDao: InMemoryVideoInteractionDao
    private lateinit var noteDao: InMemoryVideoNoteDao
    private lateinit var repository: VideoInteractionRepositoryImpl
    private lateinit var fakeVideoRepository: FakeTestVideoRepository
    private val testDispatcher = StandardTestDispatcher()

    private val sampleVideo1 = VideoItem(
        id = 101L,
        title = "workout_motivation.mp4",
        uriString = "content://media/external/video/media/101",
        durationMillis = 15_000L,
        sizeBytes = 12_000_000L,
        folderName = "Fitness"
    )

    private val sampleVideo2 = VideoItem(
        id = 202L,
        title = "coding_tutorial.mp4",
        uriString = "content://media/external/video/media/202",
        durationMillis = 45_000L,
        sizeBytes = 35_000_000L,
        folderName = "Dev"
    )

    @Before
    fun setUp() {
        interactionDao = InMemoryVideoInteractionDao()
        noteDao = InMemoryVideoNoteDao()
        repository = VideoInteractionRepositoryImpl(interactionDao, noteDao, testDispatcher)
        fakeVideoRepository = FakeTestVideoRepository()
        fakeVideoRepository.videosFlow.value = listOf(sampleVideo1, sampleVideo2)
    }

    // 1. Like a video and retrieve it after database reload
    @Test
    fun test1_likeVideo_retrievedAfterReload() = runTest(testDispatcher) {
        val result = repository.toggleLike(sampleVideo1)
        assertTrue(result)

        // Retrieve from repository
        val interaction = repository.getInteraction(sampleVideo1.id).first()
        assertTrue(interaction.isLiked)
        assertNotNull(interaction.likedAt)

        // Simulate new repository instance pointing to same persistent DB
        val reloadedRepo = VideoInteractionRepositoryImpl(interactionDao, noteDao, testDispatcher)
        val reloadedInteraction = reloadedRepo.getInteraction(sampleVideo1.id).first()
        assertTrue(reloadedInteraction.isLiked)
        assertEquals(interaction.likedAt, reloadedInteraction.likedAt)
    }

    // 2. Unlike a video
    @Test
    fun test2_unlikeVideo() = runTest(testDispatcher) {
        repository.setLiked(sampleVideo1, true)
        assertTrue(repository.getInteraction(sampleVideo1.id).first().isLiked)

        // Toggle to unlike
        val stillLiked = repository.toggleLike(sampleVideo1)
        assertFalse(stillLiked)
        assertFalse(repository.getInteraction(sampleVideo1.id).first().isLiked)

        // Explicit unlike
        repository.setLiked(sampleVideo1, false)
        assertFalse(repository.getInteraction(sampleVideo1.id).first().isLiked)
    }

    // 3. Bookmark and remove bookmark
    @Test
    fun test3_bookmarkAndRemoveBookmark() = runTest(testDispatcher) {
        val isBookmarked = repository.toggleBookmark(sampleVideo1)
        assertTrue(isBookmarked)

        val interaction = repository.getInteraction(sampleVideo1.id).first()
        assertTrue(interaction.isBookmarked)
        assertNotNull(interaction.bookmarkedAt)

        // Remove bookmark
        val stillBookmarked = repository.toggleBookmark(sampleVideo1)
        assertFalse(stillBookmarked)
        assertFalse(repository.getInteraction(sampleVideo1.id).first().isBookmarked)
    }

    // 4. Saved Videos screen data
    @Test
    fun test4_savedVideosScreenData() = runTest(testDispatcher) {
        repository.setBookmarked(sampleVideo1, true)
        repository.setBookmarked(sampleVideo2, true)

        val savedItems = repository.observeBookmarkedVideos(fakeVideoRepository.videosFlow).first()
        assertEquals(2, savedItems.size)

        val item1 = savedItems.first { it.mediaStoreId == sampleVideo1.id }
        assertEquals("workout_motivation.mp4", item1.title)
        assertEquals("Fitness", item1.folderName)
        assertTrue(item1.isAvailable)
        assertNotNull(item1.videoItem)
    }

    // 5. Add / edit / delete private notes
    @Test
    fun test5_addEditDeletePrivateNotes() = runTest(testDispatcher) {
        // Add note
        repository.saveNote(sampleVideo1, "Great form check at 0:08")
        assertEquals("Great form check at 0:08", repository.getNote(sampleVideo1.id).first())

        // Edit note
        repository.saveNote(sampleVideo1, "Updated note: Remember to breathe!")
        assertEquals("Updated note: Remember to breathe!", repository.getNote(sampleVideo1.id).first())

        // Delete note
        repository.deleteNote(sampleVideo1.id)
        assertNull(repository.getNote(sampleVideo1.id).first())
    }

    // 6. Notes attached to the correct stable video ID
    @Test
    fun test6_notesAttachedToCorrectStableVideoId() = runTest(testDispatcher) {
        repository.saveNote(sampleVideo1, "Note for video 101")
        repository.saveNote(sampleVideo2, "Note for video 202")

        assertEquals("Note for video 101", repository.getNote(101L).first())
        assertEquals("Note for video 202", repository.getNote(202L).first())

        // Even if video1 is renamed in MediaStore, note is still bound to 101L
        val renamedVideo1 = sampleVideo1.copy(title = "renamed_video.mp4")
        val noteForRenamed = repository.getNote(renamedVideo1.id).first()
        assertEquals("Note for video 101", noteForRenamed)
    }

    // 7. Multiple videos with separate notes
    @Test
    fun test7_multipleVideosWithSeparateNotes() = runTest(testDispatcher) {
        repository.saveNote(sampleVideo1, "Multiline\nWorkout\nChecklist")
        repository.saveNote(sampleVideo2, "Kotlin Coroutines summary")

        val allNotes = repository.observeAllNotes(fakeVideoRepository.videosFlow).first()
        assertEquals(2, allNotes.size)

        val note1 = allNotes.first { it.mediaStoreId == 101L }
        val note2 = allNotes.first { it.mediaStoreId == 202L }

        assertEquals("Multiline\nWorkout\nChecklist", note1.noteText)
        assertEquals("Kotlin Coroutines summary", note2.noteText)
    }

    // 8. Deleted / unavailable video handling
    @Test
    fun test8_deletedUnavailableVideoHandling() = runTest(testDispatcher) {
        repository.setLiked(sampleVideo1, true)
        repository.saveNote(sampleVideo1, "Note on video to be deleted")

        // Simulate video1 being deleted from device MediaStore
        fakeVideoRepository.videosFlow.value = listOf(sampleVideo2)

        val likedList = repository.observeLikedVideos(fakeVideoRepository.videosFlow).first()
        assertEquals(1, likedList.size)
        val deletedItem = likedList[0]

        // Video item is null, isAvailable is false, but cached title, folder, and duration are preserved!
        assertEquals(101L, deletedItem.mediaStoreId)
        assertFalse(deletedItem.isAvailable)
        assertNull(deletedItem.videoItem)
        assertEquals("workout_motivation.mp4", deletedItem.title)
        assertEquals("Fitness", deletedItem.folderName)

        // Note is also preserved with isAvailable = false
        val notesList = repository.observeAllNotes(fakeVideoRepository.videosFlow).first()
        assertEquals(1, notesList.size)
        assertFalse(notesList[0].isAvailable)
        assertEquals("Note on video to be deleted", notesList[0].noteText)
    }

    // 9. App restart persistence
    @Test
    fun test9_appRestartPersistence() = runTest(testDispatcher) {
        repository.setLiked(sampleVideo1, true)
        repository.setBookmarked(sampleVideo2, true)
        repository.saveNote(sampleVideo1, "Persistent offline note")

        // Create new repository and ViewModel instances, simulating app launch
        val newRepo = VideoInteractionRepositoryImpl(interactionDao, noteDao, testDispatcher)
        val viewModel = LikedViewModel(newRepo, fakeVideoRepository)
        testScheduler.advanceUntilIdle()

        assertEquals(1, newRepo.getLikedCount().first())
        assertEquals(1, newRepo.getBookmarkedCount().first())
        assertEquals(1, newRepo.getNotesCount().first())

        val loadedInteraction1 = newRepo.getInteraction(sampleVideo1.id).first()
        assertTrue(loadedInteraction1.isLiked)
        assertEquals("Persistent offline note", loadedInteraction1.note)

        val loadedInteraction2 = newRepo.getInteraction(sampleVideo2.id).first()
        assertTrue(loadedInteraction2.isBookmarked)
    }

    // 10. Shorts and Folder Playback integration (queue isolation preserved)
    @Test
    fun test10_shortsAndFolderPlaybackIntegration() = runTest(testDispatcher) {
        val feedQueueManager = FeedQueueManager(fakeVideoRepository, CoroutineScope(testDispatcher))
        testScheduler.advanceUntilIdle()

        // Start folder playback for "Fitness"
        feedQueueManager.startFolderPlayback(
            folderId = "Fitness",
            folderName = "Fitness",
            folderVideos = listOf(sampleVideo1),
            initialIndex = 0,
            sortOrder = VideoSortOrder.NEWEST_FIRST
        )

        // Like sampleVideo1 from Shorts
        repository.setLiked(sampleVideo1, true)

        // Verify folder queue remains strictly isolated
        val folderQueue = feedQueueManager.folderQueue.value
        assertNotNull(folderQueue)
        assertEquals(1, folderQueue!!.items.size)
        assertEquals(101L, folderQueue.items[0].id)

        // Global Shorts queue remains independent
        val shortsQueue = feedQueueManager.shortsQueue.first { it.items.isNotEmpty() }
        assertEquals(2, shortsQueue.items.size)
    }

    // 11. Database migration behaviour
    @Test
    fun test11_databaseMigrationBehaviour() {
        var executedSql = ""
        val mockDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql = args[0] as String
            }
            null
        } as SupportSQLiteDatabase

        DatabaseMigrations.MIGRATION_1_2.migrate(mockDb)

        // Verify safe ALTER TABLE statement executed without data destruction
        assertTrue(executedSql.contains("ALTER TABLE video_interactions ADD COLUMN tags"))
    }

    // 12. Empty Liked / Saved / Notes states
    @Test
    fun test12_emptyLikedSavedNotesStates() = runTest(testDispatcher) {
        val viewModel = LikedViewModel(repository, fakeVideoRepository)

        assertEquals(0, viewModel.likedCount.first())
        assertEquals(0, viewModel.savedCount.first())
        assertEquals(0, viewModel.notesCount.first())
        assertTrue(viewModel.likedVideos.first().isEmpty())
        assertTrue(viewModel.savedVideos.first().isEmpty())
        assertTrue(viewModel.notes.first().isEmpty())
    }
}

