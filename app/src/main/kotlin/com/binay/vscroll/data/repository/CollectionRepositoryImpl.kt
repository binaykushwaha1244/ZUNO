package com.binay.vscroll.data.repository

import android.content.ContentUris
import android.provider.MediaStore
import com.binay.vscroll.data.local.dao.CollectionDao
import com.binay.vscroll.data.local.entity.CollectionVideoCrossRefEntity
import com.binay.vscroll.data.local.entity.VideoCollectionEntity
import com.binay.vscroll.domain.model.VideoCollection
import com.binay.vscroll.domain.model.VideoItem
import com.binay.vscroll.domain.repository.CollectionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CollectionRepositoryImpl(
    private val collectionDao: CollectionDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CollectionRepository {

    override fun observeCollections(): Flow<List<VideoCollection>> {
        return collectionDao.observeCollectionsWithCounts().map { list ->
            list.map { item ->
                val coverUri = item.coverVideoId?.let { id ->
                    val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    if (base != null) {
                        try {
                            ContentUris.withAppendedId(base, id).toString()
                        } catch (_: Exception) {
                            "content://media/external/video/media/$id"
                        }
                    } else {
                        "content://media/external/video/media/$id"
                    }
                }
                VideoCollection(
                    id = item.id,
                    name = item.name,
                    createdAt = item.createdAt,
                    updatedAt = item.updatedAt,
                    videoCount = item.videoCount,
                    coverVideoId = item.coverVideoId,
                    coverVideoUri = coverUri
                )
            }
        }.flowOn(ioDispatcher)
    }

    override fun observeCollection(collectionId: Long): Flow<VideoCollection?> {
        return collectionDao.observeCollectionById(collectionId).map { entity ->
            entity?.let {
                VideoCollection(
                    id = it.id,
                    name = it.name,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            }
        }.flowOn(ioDispatcher)
    }

    override suspend fun createCollection(name: String): Result<Long> = withContext(ioDispatcher) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Collection name cannot be empty"))
        }

        val existing = collectionDao.findCollectionByNameSync(trimmed)
        if (existing != null) {
            return@withContext Result.failure(IllegalArgumentException("Collection '$trimmed' already exists"))
        }

        try {
            val now = System.currentTimeMillis()
            val id = collectionDao.insertCollection(
                VideoCollectionEntity(
                    name = trimmed,
                    createdAt = now,
                    updatedAt = now
                )
            )
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameCollection(collectionId: Long, newName: String): Result<Unit> = withContext(ioDispatcher) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Collection name cannot be empty"))
        }

        val existing = collectionDao.findCollectionByNameSync(trimmed)
        if (existing != null && existing.id != collectionId) {
            return@withContext Result.failure(IllegalArgumentException("Collection '$trimmed' already exists"))
        }

        try {
            val rows = collectionDao.renameCollection(
                id = collectionId,
                newName = trimmed,
                updatedAt = System.currentTimeMillis()
            )
            if (rows > 0) Result.success(Unit) else Result.failure(IllegalStateException("Collection not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteCollection(collectionId: Long): Result<Unit> = withContext(ioDispatcher) {
        try {
            val rows = collectionDao.deleteCollection(collectionId)
            if (rows > 0) Result.success(Unit) else Result.failure(IllegalStateException("Collection not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun addVideosToCollection(
        collectionId: Long,
        videoIds: List<Long>
    ): Result<Int> = withContext(ioDispatcher) {
        if (videoIds.isEmpty()) return@withContext Result.success(0)
        try {
            val now = System.currentTimeMillis()
            val refs = videoIds.distinct().map { videoId ->
                CollectionVideoCrossRefEntity(
                    collectionId = collectionId,
                    mediaStoreId = videoId,
                    addedAt = now
                )
            }
            collectionDao.insertCrossRefs(refs)
            // Update collection updatedAt timestamp
            val collection = collectionDao.getCollectionByIdSync(collectionId)
            if (collection != null) {
                collectionDao.updateCollection(collection.copy(updatedAt = now))
            }
            Result.success(refs.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeVideosFromCollection(
        collectionId: Long,
        videoIds: List<Long>
    ): Result<Int> = withContext(ioDispatcher) {
        if (videoIds.isEmpty()) return@withContext Result.success(0)
        try {
            val deletedCount = collectionDao.removeVideosFromCollection(collectionId, videoIds)
            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeCollectionVideos(
        collectionId: Long,
        allVideosFlow: Flow<List<VideoItem>>
    ): Flow<List<VideoItem>> {
        return combine(
            collectionDao.observeVideoIdsForCollection(collectionId),
            allVideosFlow
        ) { videoIds, allVideos ->
            val videosById = allVideos.associateBy { it.id }
            videoIds.mapNotNull { id -> videosById[id] }
        }.flowOn(ioDispatcher)
    }

    override fun observeCollectionIdsForVideo(mediaStoreId: Long): Flow<List<Long>> {
        return collectionDao.observeCollectionIdsForVideo(mediaStoreId).flowOn(ioDispatcher)
    }
}

