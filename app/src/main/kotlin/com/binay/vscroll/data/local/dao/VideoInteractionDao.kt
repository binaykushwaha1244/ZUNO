package com.binay.vscroll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.binay.vscroll.data.local.entity.VideoInteractionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoInteractionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(interaction: VideoInteractionEntity): Long

    @Query("SELECT * FROM video_interactions WHERE media_store_id = :mediaStoreId LIMIT 1")
    fun getInteraction(mediaStoreId: Long): Flow<VideoInteractionEntity?>

    @Query("SELECT * FROM video_interactions WHERE media_store_id = :mediaStoreId LIMIT 1")
    suspend fun getInteractionSync(mediaStoreId: Long): VideoInteractionEntity?

    @Query("SELECT * FROM video_interactions WHERE is_liked = 1 OR reaction_type IS NOT NULL ORDER BY liked_at DESC")
    fun getAllLikedVideos(): Flow<List<VideoInteractionEntity>>

    @Query("SELECT * FROM video_interactions WHERE is_bookmarked = 1 ORDER BY bookmarked_at DESC")
    fun getAllBookmarkedVideos(): Flow<List<VideoInteractionEntity>>

    @Query("SELECT * FROM video_interactions")
    suspend fun getAllInteractionsSync(): List<VideoInteractionEntity>

    @Query("UPDATE video_interactions SET is_liked = :isLiked, liked_at = :likedAt, updated_at = :updatedAt WHERE media_store_id = :mediaStoreId")
    suspend fun updateLikeStatus(mediaStoreId: Long, isLiked: Boolean, likedAt: Long?, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE video_interactions SET is_liked = :isLiked, reaction_type = :reactionType, liked_at = :likedAt, updated_at = :updatedAt WHERE media_store_id = :mediaStoreId")
    suspend fun updateReactionStatus(mediaStoreId: Long, isLiked: Boolean, reactionType: String?, likedAt: Long?, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE video_interactions SET is_bookmarked = :isBookmarked, bookmarked_at = :bookmarkedAt, updated_at = :updatedAt WHERE media_store_id = :mediaStoreId")
    suspend fun updateBookmarkStatus(mediaStoreId: Long, isBookmarked: Boolean, bookmarkedAt: Long?, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM video_interactions WHERE media_store_id = :mediaStoreId")
    suspend fun deleteInteraction(mediaStoreId: Long): Int

    @Query("SELECT COUNT(*) FROM video_interactions WHERE is_liked = 1 OR reaction_type IS NOT NULL")
    fun getLikedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM video_interactions WHERE is_bookmarked = 1")
    fun getBookmarkedCount(): Flow<Int>
}

