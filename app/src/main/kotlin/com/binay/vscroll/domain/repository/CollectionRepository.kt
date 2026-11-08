package com.binay.vscroll.domain.repository

import com.binay.vscroll.domain.model.VideoCollection
import com.binay.vscroll.domain.model.VideoItem
import kotlinx.coroutines.flow.Flow

interface CollectionRepository {

    fun observeCollections(): Flow<List<VideoCollection>>

    fun observeCollection(collectionId: Long): Flow<VideoCollection?>

    suspend fun createCollection(name: String): Result<Long>

    suspend fun renameCollection(collectionId: Long, newName: String): Result<Unit>

    suspend fun deleteCollection(collectionId: Long): Result<Unit>

    suspend fun addVideosToCollection(collectionId: Long, videoIds: List<Long>): Result<Int>

    suspend fun removeVideosFromCollection(collectionId: Long, videoIds: List<Long>): Result<Int>

    fun observeCollectionVideos(
        collectionId: Long,
        allVideosFlow: Flow<List<VideoItem>>
    ): Flow<List<VideoItem>>

    fun observeCollectionIdsForVideo(mediaStoreId: Long): Flow<List<Long>>
}

