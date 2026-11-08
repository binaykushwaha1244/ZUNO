package com.binay.vscroll.data.repository

import com.binay.vscroll.domain.model.FolderItem
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.model.VideoSortOrder
import com.binay.vscroll.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeVideoRepository : VideoRepository {
    private val sampleVideos = listOf(
        VideoItem(
            id = 1L,
            title = "Cinematic Drone 4K - Mountain Sunset",
            uriString = "content://media/external/video/media/1",
            durationMillis = 245000L,
            sizeBytes = 384L * 1024 * 1024,
            width = 3840,
            height = 2160,
            folderName = "Drone Captures",
            relativePath = "DCIM/Drone/"
        ),
        VideoItem(
            id = 2L,
            title = "Cyberpunk City Nights Reel",
            uriString = "content://media/external/video/media/2",
            durationMillis = 62000L,
            sizeBytes = 85L * 1024 * 1024,
            width = 1080,
            height = 1920,
            folderName = "Shorts",
            relativePath = "Movies/Shorts/"
        ),
        VideoItem(
            id = 3L,
            title = "Deep Ocean Whales & Bioluminescence",
            uriString = "content://media/external/video/media/3",
            durationMillis = 912000L,
            sizeBytes = 1240L * 1024 * 1024,
            width = 3840,
            height = 2160,
            folderName = "Documentaries",
            relativePath = "Movies/Documentaries/"
        ),
        VideoItem(
            id = 4L,
            title = "Formula Racing - Track Day Highlights",
            uriString = "content://media/external/video/media/4",
            durationMillis = 184000L,
            sizeBytes = 290L * 1024 * 1024,
            width = 1920,
            height = 1080,
            folderName = "Motorsports",
            relativePath = "DCIM/Motorsports/"
        ),
        VideoItem(
            id = 5L,
            title = "Japan Cherry Blossom Walkthrough",
            uriString = "content://media/external/video/media/5",
            durationMillis = 540000L,
            sizeBytes = 720L * 1024 * 1024,
            width = 3840,
            height = 2160,
            folderName = "Travel",
            relativePath = "DCIM/Travel/"
        )
    )

    private val sampleFolders = listOf(
        FolderItem(id = "DCIM/Drone", name = "Drone Captures", relativePath = "DCIM/Drone/", videoCount = 8),
        FolderItem(id = "Movies/Shorts", name = "Shorts", relativePath = "Movies/Shorts/", videoCount = 24),
        FolderItem(id = "Movies/Documentaries", name = "Documentaries", relativePath = "Movies/Documentaries/", videoCount = 5),
        FolderItem(id = "DCIM/Travel", name = "Travel", relativePath = "DCIM/Travel/", videoCount = 12)
    )

    override fun getVideos(sortOrder: VideoSortOrder): Flow<List<VideoItem>> = flow {
        val sorted = when (sortOrder) {
            VideoSortOrder.NEWEST_FIRST -> sampleVideos.sortedByDescending { it.id }
            VideoSortOrder.OLDEST_FIRST -> sampleVideos.sortedBy { it.id }
            VideoSortOrder.NAME_A_TO_Z -> sampleVideos.sortedBy { it.title }
            VideoSortOrder.LARGEST_FIRST -> sampleVideos.sortedByDescending { it.sizeBytes }
            VideoSortOrder.DURATION_LONGEST -> sampleVideos.sortedByDescending { it.durationMillis }
        }
        emit(sorted)
    }

    override fun getFolders(): Flow<List<FolderItem>> = flow {
        emit(sampleFolders)
    }

    override suspend fun refreshVideos() {}
}
