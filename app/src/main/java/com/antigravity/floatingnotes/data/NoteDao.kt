package com.antigravity.floatingnotes.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE isCompleted = 0 ORDER BY priority DESC, isUrgentAlarm DESC, createdAt DESC")
    fun getActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isCompleted = 0 ORDER BY priority DESC, isUrgentAlarm DESC, createdAt DESC")
    suspend fun getActiveNotesSync(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE isCompleted = 1 ORDER BY createdAt DESC")
    fun getCompletedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    suspend fun getAllNotesSync(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE isCompleted = 0 AND isPinnedToOverlay = 1 ORDER BY priority DESC, isUrgentAlarm DESC, createdAt DESC")
    fun getOverlayPendingNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE targetDate >= :startOfDay AND targetDate <= :endOfDay ORDER BY targetTime ASC, isUrgentAlarm DESC, priority DESC")
    fun getNotesForDateRange(startOfDay: Long, endOfDay: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE targetDate >= :startOfDay AND targetDate <= :endOfDay AND isCompleted = 0")
    suspend fun getActiveNotesForDateRangeSync(startOfDay: Long, endOfDay: Long): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE isCompleted = 0 AND (wifiSsid = :ssid OR locationName = :locationName)")
    suspend fun getNotesByLocationOrWifiSync(locationName: String, ssid: String): List<NoteEntity>

    @Query("SELECT DISTINCT targetDate FROM notes WHERE targetDate IS NOT NULL AND isCompleted = 0")
    fun getDistinctPendingDates(): Flow<List<Long>>

    @Query("SELECT DISTINCT category FROM notes WHERE category IS NOT NULL AND category != ''")
    fun getDistinctCategories(): Flow<List<String>>

    @Query("SELECT * FROM notes WHERE category = :category AND isCompleted = 0 ORDER BY createdAt DESC")
    fun getNotesByCategory(category: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE isCompleted = 0 AND (repeatIntervalMinutes > 0 OR isUrgentAlarm = 1)")
    suspend fun getActiveRepeatingNotes(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>): List<Long>

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("UPDATE notes SET isCompleted = :completed, snoozeCount = CASE WHEN :completed = 1 THEN 0 ELSE snoozeCount END WHERE id = :id")
    suspend fun setCompletionStatus(id: Long, completed: Boolean)

    @Query("UPDATE notes SET snoozeCount = snoozeCount + 1 WHERE id = :id")
    suspend fun incrementSnoozeCount(id: Long)

    @Query("UPDATE notes SET snoozeCount = 0 WHERE id = :id")
    suspend fun resetSnoozeCount(id: Long)

    @Query("UPDATE notes SET subtasksJson = :subtasksJson WHERE id = :id")
    suspend fun updateSubtasks(id: Long, subtasksJson: String)
}
