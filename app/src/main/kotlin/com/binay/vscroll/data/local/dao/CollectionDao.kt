package com.binay.vscroll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.binay.vscroll.data.local.entity.CollectionVideoCrossRefEntity
import com.binay.vscroll.data.local.entity.VideoCollectionEntity
import kotlinx.coroutines.flow.Flow

data class CollectionWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val videoCount: Int,
    val coverVideoId: Long?
)

@Dao
interface CollectionDao {

    @Query("SELECT * FROM video_collections ORDER BY updated_at DESC")
    fun observeAllCollections(): Flow<List<VideoCollectionEntity>>

    @Query("""
        SELECT 
            c.id AS id,
            c.name AS name,
            c.created_at AS createdAt,
            c.updated_at AS updatedAt,
            COUNT(cv.media_store_id) AS videoCount,
            (SELECT cv2.media_store_id FROM collection_videos cv2 WHERE cv2.collection_id = c.id ORDER BY cv2.added_at DESC LIMIT 1) AS coverVideoId
        FROM video_collections c
        LEFT JOIN collection_videos cv ON c.id = cv.collection_id
        GROUP BY c.id
        ORDER BY c.updated_at DESC
    """)
    fun observeCollectionsWithCounts(): Flow<List<CollectionWithCount>>

    @Query("SELECT * FROM video_collections WHERE id = :id LIMIT 1")
    fun observeCollectionById(id: Long): Flow<VideoCollectionEntity?>

    @Query("SELECT * FROM video_collections WHERE id = :id LIMIT 1")
    suspend fun getCollectionByIdSync(id: Long): VideoCollectionEntity?

    @Query("SELECT * FROM video_collections WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findCollectionByNameSync(name: String): VideoCollectionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollection(collection: VideoCollectionEntity): Long

    @Update
    suspend fun updateCollection(collection: VideoCollectionEntity): Int

    @Query("UPDATE video_collections SET name = :newName, updated_at = :updatedAt WHERE id = :id")
    suspend fun renameCollection(id: Long, newName: String, updatedAt: Long): Int

    @Query("DELETE FROM video_collections WHERE id = :id")
    suspend fun deleteCollection(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: CollectionVideoCrossRefEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(crossRefs: List<CollectionVideoCrossRefEntity>): List<Long>

    @Query("DELETE FROM collection_videos WHERE collection_id = :collectionId AND media_store_id = :mediaStoreId")
    suspend fun removeVideoFromCollection(collectionId: Long, mediaStoreId: Long): Int

    @Query("DELETE FROM collection_videos WHERE collection_id = :collectionId AND media_store_id IN (:mediaStoreIds)")
    suspend fun removeVideosFromCollection(collectionId: Long, mediaStoreIds: List<Long>): Int

    @Query("SELECT media_store_id FROM collection_videos WHERE collection_id = :collectionId ORDER BY added_at DESC")
    fun observeVideoIdsForCollection(collectionId: Long): Flow<List<Long>>

    @Query("SELECT media_store_id FROM collection_videos WHERE collection_id = :collectionId ORDER BY added_at DESC")
    suspend fun getVideoIdsForCollectionSync(collectionId: Long): List<Long>

    @Query("SELECT collection_id FROM collection_videos WHERE media_store_id = :mediaStoreId")
    fun observeCollectionIdsForVideo(mediaStoreId: Long): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM video_collections")
    fun observeCollectionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM collection_videos WHERE collection_id = :collectionId")
    fun observeVideoCountForCollection(collectionId: Long): Flow<Int>
}

