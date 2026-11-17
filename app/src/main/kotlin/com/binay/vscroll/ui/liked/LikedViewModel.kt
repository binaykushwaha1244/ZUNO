package com.binay.vscroll.ui.liked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.binay.vscroll.domain.model.UserNoteItem
import com.binay.vscroll.domain.model.UserVideoItem
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.model.VideoSortOrder
import com.binay.vscroll.domain.repository.VideoInteractionRepository
import com.binay.vscroll.domain.repository.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LikedViewModel(
    private val interactionRepository: VideoInteractionRepository,
    private val videoRepository: VideoRepository
) : ViewModel() {

    val allVideosFlow = videoRepository.getVideos(VideoSortOrder.NEWEST_FIRST)

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Liked Videos (filtered by search query)
    val likedVideos: StateFlow<List<UserVideoItem>> = combine(
        interactionRepository.observeLikedVideos(allVideosFlow),
        _searchQuery
    ) { items, query ->
        if (query.isBlank()) {
            items
        } else {
            val q = query.trim()
            items.filter { it.title.contains(q, ignoreCase = true) || it.folderName.contains(q, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Saved/Bookmarked Videos (filtered by search query)
    val savedVideos: StateFlow<List<UserVideoItem>> = combine(
        interactionRepository.observeBookmarkedVideos(allVideosFlow),
        _searchQuery
    ) { items, query ->
        if (query.isBlank()) {
            items
        } else {
            val q = query.trim()
            items.filter { it.title.contains(q, ignoreCase = true) || it.folderName.contains(q, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Private Notes (filtered by search query across note text and video title)
    val notes: StateFlow<List<UserNoteItem>> = combine(
        interactionRepository.observeAllNotes(allVideosFlow),
        _searchQuery
    ) { items, query ->
        if (query.isBlank()) {
            items
        } else {
            val q = query.trim()
            items.filter {
                it.title.contains(q, ignoreCase = true) ||
                it.noteText.contains(q, ignoreCase = true) ||
                it.folderName.contains(q, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedCount: StateFlow<Int> = interactionRepository.getLikedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val savedCount: StateFlow<Int> = interactionRepository.getBookmarkedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val notesCount: StateFlow<Int> = interactionRepository.getNotesCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setSelectedTab(index: Int) {
        _selectedTab.value = index.coerceIn(0, 2)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleLike(video: VideoItem) {
        viewModelScope.launch {
            interactionRepository.toggleLike(video)
        }
    }

    fun unlike(item: UserVideoItem) {
        viewModelScope.launch {
            item.videoItem?.let {
                interactionRepository.setLiked(it, false)
            } ?: run {
                // If videoItem is unavailable, craft fallback VideoItem from cached metadata to update DB
                val fallback = VideoItem(
                    id = item.mediaStoreId,
                    title = item.title,
                    uriString = item.contentUriString,
                    durationMillis = item.durationMillis,
                    sizeBytes = item.sizeBytes,
                    folderName = item.folderName
                )
                interactionRepository.setLiked(fallback, false)
            }
        }
    }

    fun toggleBookmark(video: VideoItem) {
        viewModelScope.launch {
            interactionRepository.toggleBookmark(video)
        }
    }

    fun removeBookmark(item: UserVideoItem) {
        viewModelScope.launch {
            item.videoItem?.let {
                interactionRepository.setBookmarked(it, false)
            } ?: run {
                val fallback = VideoItem(
                    id = item.mediaStoreId,
                    title = item.title,
                    uriString = item.contentUriString,
                    durationMillis = item.durationMillis,
                    sizeBytes = item.sizeBytes,
                    folderName = item.folderName
                )
                interactionRepository.setBookmarked(fallback, false)
            }
        }
    }

    fun saveNote(video: VideoItem, noteText: String) {
        viewModelScope.launch {
            interactionRepository.saveNote(video, noteText)
        }
    }

    fun deleteNote(mediaStoreId: Long) {
        viewModelScope.launch {
            interactionRepository.deleteNote(mediaStoreId)
        }
    }
}

