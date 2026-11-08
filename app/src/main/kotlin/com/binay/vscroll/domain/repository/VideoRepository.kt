package com.binay.vscroll.domain.repository

import com.binay.vscroll.domain.model.FolderItem
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.model.VideoSortOrder
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun getVideos(sortOrder: VideoSortOrder = VideoSortOrder.NEWEST_FIRST): Flow<List<VideoItem>>
    fun getFolders(): Flow<List<FolderItem>>
    suspend fun refreshVideos()
}
