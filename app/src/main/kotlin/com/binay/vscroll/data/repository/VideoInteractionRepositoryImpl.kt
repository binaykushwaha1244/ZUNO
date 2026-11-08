package com.binay.vscroll.data.repository

import com.binay.vscroll.data.local.dao.VideoInteractionDao
import com.binay.vscroll.data.local.dao.VideoNoteDao
import com.binay.vscroll.data.local.entity.VideoInteractionEntity
import com.binay.vscroll.data.local.entity.VideoNoteEntity
import com.binay.vscroll.domain.model.UserNoteItem
import com.binay.vscroll.domain.model.UserVideoInteraction
import com.binay.vscroll.domain.model.UserVideoItem
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.repository.VideoInteractionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class VideoInteractionRepositoryImpl(
    private val interactionDao: VideoInteractionDao,
    private val noteDao: VideoNoteDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : VideoInteractionRepository {

    override fun getInteraction(mediaStoreId: Long): Flow<UserVideoInteraction> {
        return combine(
            interactionDao.getInteraction(mediaStoreId),
            noteDao.getNote(mediaStoreId)
        ) { interaction, note ->
            UserVideoInteraction(
                mediaStoreId = mediaStoreId,
                isLiked = interaction?.isLiked ?: false,
                likedAt = interaction?.likedAt,
                isBookmarked = interaction?.isBookmarked ?: false,
                bookmarkedAt = interaction?.bookmarkedAt,
                note = note?.noteText,
                noteUpdatedAt = note?.updatedAt
            )
        }
    }

    override suspend fun toggleLike(video: VideoItem): Boolean = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val current = interactionDao.getInteractionSync(video.id)
        val newLikedState = !(current?.isLiked ?: false)

        val entityToSave = if (current != null) {
            current.copy(
                isLiked = newLikedState,
                likedAt = if (newLikedState) now else current.likedAt,
                updatedAt = now,
                // Update cached metadata in case title or folder changed
                cachedTitle = video.title,
                cachedFolderName = video.folderName,
                cachedDurationMs = video.durationMillis,
                cachedSizeBytes = video.sizeBytes,
                contentUri = video.uriString
            )
        } else {
            VideoInteractionEntity(
                mediaStoreId = video.id,
                contentUri = video.uriString,
                cachedTitle = video.title,
                cachedFolderName = video.folderName,
                cachedDurationMs = video.durationMillis,
                cachedSizeBytes = video.sizeBytes,
                cachedWidth = video.width,
                cachedHeight = video.height,
                cachedMimeType = video.mimeType,
                cachedDateAdded = video.dateAdded,
                isLiked = newLikedState,
                likedAt = now,
                isBookmarked = false,
                bookmarkedAt = null,
                updatedAt = now
            )
        }

        interactionDao.insertOrUpdate(entityToSave)
        newLikedState
    }

    override suspend fun setLiked(video: VideoItem, isLiked: Boolean) = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val current = interactionDao.getInteractionSync(video.id)

        val entityToSave = if (current != null) {
            current.copy(
                isLiked = isLiked,
                likedAt = if (isLiked) (current.likedAt ?: now) else current.likedAt,
                updatedAt = now,
                cachedTitle = video.title,
                cachedFolderName = video.folderName,
                cachedDurationMs = video.durationMillis,
                cachedSizeBytes = video.sizeBytes,
                contentUri = video.uriString
            )
        } else {
            VideoInteractionEntity(
                mediaStoreId = video.id,
                contentUri = video.uriString,
                cachedTitle = video.title,
                cachedFolderName = video.folderName,
                cachedDurationMs = video.durationMillis,
                cachedSizeBytes = video.sizeBytes,
                cachedWidth = video.width,
                cachedHeight = video.height,
                cachedMimeType = video.mimeType,
                cachedDateAdded = video.dateAdded,
                isLiked = isLiked,
                likedAt = if (isLiked) now else null,
                isBookmarked = false,
                bookmarkedAt = null,
                updatedAt = now
            )
        }

        interactionDao.insertOrUpdate(entityToSave)
        Unit
    }

    override suspend fun toggleBookmark(video: VideoItem): Boolean = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val current = interactionDao.getInteractionSync(video.id)
        val newBookmarkedState = !(current?.isBookmarked ?: false)

        val entityToSave = if (current != null) {
            current.copy(
                isBookmarked = newBookmarkedState,
                bookmarkedAt = if (newBookmarkedState) now else current.bookmarkedAt,
                updatedAt = now,
                cachedTitle = video.title,
                cachedFolderName = video.folderName,
                cachedDurationMs = video.durationMillis,
                cachedSizeBytes = video.sizeBytes,
                contentUri = video.uriString
            )
        } else {
            VideoInteractionEntity(
                mediaStoreId = video.id,
                contentUri = video.uriString,
                cachedTitle = video.title,
                cachedFolderName = video.folderName,
                cachedDurationMs = video.durationMillis,
                cachedSizeBytes = video.sizeBytes,
                cachedWidth = video.width,
                cachedHeight = video.height,
                cachedMimeType = video.mimeType,
                cachedDateAdded = video.dateAdded,
                isLiked = false,
                likedAt = null,
                isBookmarked = newBookmarkedState,
                bookmarkedAt = now,
                updatedAt = now
            )
        }

        interactionDao.insertOrUpdate(entityToSave)
        newBookmarkedState
    }

    override suspend fun setBookmarked(video: VideoItem, isBookmarked: Boolean) = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val current = interactionDao.getInteractionSync(video.id)

        val entityToSave = if (current != null) {
            current.copy(
                isBookmarked = isBookmarked,
                bookmarkedAt = if (isBookmarked) (current.bookmarkedAt ?: now) else current.bookmarkedAt,
                updatedAt = now,
                cachedTitle = video.title,
                cachedFolderName = video.folderName,
                cachedDurationMs = video.durationMillis,
                cachedSizeBytes = video.sizeBytes,
                contentUri = video.uriString
            )
        } else {
            VideoInteractionEntity(
                mediaStoreId = video.id,
                contentUri = video.uriString,
                cachedTitle = video.title,
                cachedFolderName = video.folderName,
                cachedDurationMs = video.durationMillis,
                cachedSizeBytes = video.sizeBytes,
                cachedWidth = video.width,
                cachedHeight = video.height,
                cachedMimeType = video.mimeType,
                cachedDateAdded = video.dateAdded,
                isLiked = false,
                likedAt = null,
                isBookmarked = isBookmarked,
                bookmarkedAt = if (isBookmarked) now else null,
                updatedAt = now
            )
        }

        interactionDao.insertOrUpdate(entityToSave)
        Unit
    }

    override fun getNote(mediaStoreId: Long): Flow<String?> {
        return noteDao.getNote(mediaStoreId).map { it?.noteText }
    }

    override suspend fun saveNote(video: VideoItem, noteText: String): Unit = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val trimmed = noteText.trim()
        if (trimmed.isEmpty()) {
            noteDao.deleteNote(video.id)
        } else {
            val existing = noteDao.getNoteSync(video.id)
            val noteEntity = VideoNoteEntity(
                mediaStoreId = video.id,
                noteText = trimmed,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                cachedTitle = video.title,
                cachedFolderName = video.folderName,
                cachedDurationMs = video.durationMillis,
                cachedUri = video.uriString
            )
            noteDao.insertOrUpdate(noteEntity)
        }
        Unit
    }

    override suspend fun deleteNote(mediaStoreId: Long) = withContext(dispatcher) {
        noteDao.deleteNote(mediaStoreId)
        Unit
    }

    override fun observeLikedVideos(allVideosFlow: Flow<List<VideoItem>>): Flow<List<UserVideoItem>> {
        return combine(
            interactionDao.getAllLikedVideos(),
            allVideosFlow
        ) { likedEntities, mediaStoreVideos ->
            val videosById = mediaStoreVideos.associateBy { it.id }
            likedEntities.map { entity ->
                val matching = videosById[entity.mediaStoreId]
                UserVideoItem(
                    mediaStoreId = entity.mediaStoreId,
                    title = matching?.title ?: entity.cachedTitle,
                    folderName = matching?.folderName ?: entity.cachedFolderName,
                    durationMillis = matching?.durationMillis ?: entity.cachedDurationMs,
                    sizeBytes = matching?.sizeBytes ?: entity.cachedSizeBytes,
                    contentUriString = matching?.uriString ?: entity.contentUri,
                    isAvailable = (matching != null),
                    isLiked = true,
                    likedAt = entity.likedAt,
                    isBookmarked = entity.isBookmarked,
                    bookmarkedAt = entity.bookmarkedAt,
                    videoItem = matching
                )
            }
        }
    }

    override fun observeBookmarkedVideos(allVideosFlow: Flow<List<VideoItem>>): Flow<List<UserVideoItem>> {
        return combine(
            interactionDao.getAllBookmarkedVideos(),
            allVideosFlow
        ) { bookmarkedEntities, mediaStoreVideos ->
            val videosById = mediaStoreVideos.associateBy { it.id }
            bookmarkedEntities.map { entity ->
                val matching = videosById[entity.mediaStoreId]
                UserVideoItem(
                    mediaStoreId = entity.mediaStoreId,
                    title = matching?.title ?: entity.cachedTitle,
                    folderName = matching?.folderName ?: entity.cachedFolderName,
                    durationMillis = matching?.durationMillis ?: entity.cachedDurationMs,
                    sizeBytes = matching?.sizeBytes ?: entity.cachedSizeBytes,
                    contentUriString = matching?.uriString ?: entity.contentUri,
                    isAvailable = (matching != null),
                    isLiked = entity.isLiked,
                    likedAt = entity.likedAt,
                    isBookmarked = true,
                    bookmarkedAt = entity.bookmarkedAt,
                    videoItem = matching
                )
            }
        }
    }

    override fun observeAllNotes(allVideosFlow: Flow<List<VideoItem>>): Flow<List<UserNoteItem>> {
        return combine(
            noteDao.getAllNotes(),
            allVideosFlow
        ) { noteEntities, mediaStoreVideos ->
            val videosById = mediaStoreVideos.associateBy { it.id }
            noteEntities.map { entity ->
                val matching = videosById[entity.mediaStoreId]
                UserNoteItem(
                    mediaStoreId = entity.mediaStoreId,
                    noteText = entity.noteText,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt,
                    title = matching?.title ?: entity.cachedTitle,
                    folderName = matching?.folderName ?: entity.cachedFolderName,
                    durationMillis = matching?.durationMillis ?: entity.cachedDurationMs,
                    contentUriString = matching?.uriString ?: entity.cachedUri,
                    isAvailable = (matching != null),
                    videoItem = matching
                )
            }
        }
    }

    override fun getLikedCount(): Flow<Int> = interactionDao.getLikedCount()

    override fun getBookmarkedCount(): Flow<Int> = interactionDao.getBookmarkedCount()

    override fun getNotesCount(): Flow<Int> = noteDao.getNotesCount()
}

