package com.binay.vscroll.domain.repository

import com.binay.vscroll.domain.model.UserNoteItem
import com.binay.vscroll.domain.model.UserVideoInteraction
import com.binay.vscroll.domain.model.UserVideoItem
import com.binay.vscroll.domain.model.VideoItem
import kotlinx.coroutines.flow.Flow

interface VideoInteractionRepository {

    fun getInteraction(mediaStoreId: Long): Flow<UserVideoInteraction>

    suspend fun toggleLike(video: VideoItem): Boolean

    suspend fun setLiked(video: VideoItem, isLiked: Boolean)

    suspend fun toggleBookmark(video: VideoItem): Boolean

    suspend fun setBookmarked(video: VideoItem, isBookmarked: Boolean)

    fun getNote(mediaStoreId: Long): Flow<String?>

    suspend fun saveNote(video: VideoItem, noteText: String)

    suspend fun deleteNote(mediaStoreId: Long)

    fun observeLikedVideos(allVideosFlow: Flow<List<VideoItem>>): Flow<List<UserVideoItem>>

    fun observeBookmarkedVideos(allVideosFlow: Flow<List<VideoItem>>): Flow<List<UserVideoItem>>

    fun observeAllNotes(allVideosFlow: Flow<List<VideoItem>>): Flow<List<UserNoteItem>>

    fun getLikedCount(): Flow<Int>

    fun getBookmarkedCount(): Flow<Int>

    fun getNotesCount(): Flow<Int>
}

