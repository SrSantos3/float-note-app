package com.antigravity.floatingnotes.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE isCompleted = 0 ORDER BY priority DESC, createdAt DESC")
    fun getActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isCompleted = 1 ORDER BY createdAt DESC")
    fun getCompletedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isCompleted = 0 AND isPinnedToOverlay = 1 ORDER BY priority DESC, createdAt DESC")
    fun getOverlayPendingNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE targetDate >= :startOfDay AND targetDate <= :endOfDay ORDER BY targetTime ASC, priority DESC")
    fun getNotesForDateRange(startOfDay: Long, endOfDay: Long): Flow<List<NoteEntity>>

    @Query("SELECT DISTINCT targetDate FROM notes WHERE targetDate IS NOT NULL AND isCompleted = 0")
    fun getDistinctPendingDates(): Flow<List<Long>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE isCompleted = 0 AND repeatIntervalMinutes > 0")
    suspend fun getActiveRepeatingNotes(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("UPDATE notes SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompletionStatus(id: Long, completed: Boolean)
}
