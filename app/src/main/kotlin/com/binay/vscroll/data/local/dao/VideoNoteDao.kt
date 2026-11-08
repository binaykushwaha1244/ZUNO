package com.binay.vscroll.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.binay.vscroll.data.local.entity.VideoNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoNoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(note: VideoNoteEntity): Long

    @Query("SELECT * FROM video_notes WHERE media_store_id = :mediaStoreId LIMIT 1")
    fun getNote(mediaStoreId: Long): Flow<VideoNoteEntity?>

    @Query("SELECT * FROM video_notes WHERE media_store_id = :mediaStoreId LIMIT 1")
    suspend fun getNoteSync(mediaStoreId: Long): VideoNoteEntity?

    @Query("SELECT * FROM video_notes ORDER BY updated_at DESC")
    fun getAllNotes(): Flow<List<VideoNoteEntity>>

    @Query("DELETE FROM video_notes WHERE media_store_id = :mediaStoreId")
    suspend fun deleteNote(mediaStoreId: Long): Int

    @Query("SELECT COUNT(*) FROM video_notes")
    fun getNotesCount(): Flow<Int>
}

