package com.antigravity.floatingnotes.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.floatingnotes.data.AppDatabase
import com.antigravity.floatingnotes.data.NoteEntity
import com.antigravity.floatingnotes.service.FloatingBubbleService
import com.antigravity.floatingnotes.service.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val noteDao = AppDatabase.getDatabase(application).noteDao()

    val activeNotes: StateFlow<List<NoteEntity>> = noteDao.getActiveNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedNotes: StateFlow<List<NoteEntity>> = noteDao.getCompletedNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotes: StateFlow<List<NoteEntity>> = noteDao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val distinctDatesWithNotes: StateFlow<List<Long>> = noteDao.getDistinctPendingDates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Fecha seleccionada en el modo Calendario (milisegundos al inicio del día)
    private val _selectedCalendarDate = MutableStateFlow(getStartOfDay(System.currentTimeMillis()))
    val selectedCalendarDate: StateFlow<Long> = _selectedCalendarDate.asStateFlow()

    // Notas de la fecha seleccionada en el calendario
    @OptIn(ExperimentalCoroutinesApi::class)
    val notesForSelectedDate: StateFlow<List<NoteEntity>> = _selectedCalendarDate.flatMapLatest { date ->
        val startOfDay = getStartOfDay(date)
        val endOfDay = startOfDay + (24 * 60 * 60 * 1000L) - 1
        noteDao.getNotesForDateRange(startOfDay, endOfDay)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Estado del servicio de la burbuja
    private val _isBubbleServiceEnabled = MutableStateFlow(false)
    val isBubbleServiceEnabled: StateFlow<Boolean> = _isBubbleServiceEnabled.asStateFlow()

    init {
        checkBubbleServiceState()
    }

    fun checkBubbleServiceState() {
        val context = getApplication<Application>()
        val hasOverlayPermission = Settings.canDrawOverlays(context)
        val prefs = context.getSharedPreferences("floating_notes_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("bubble_overlay_enabled", false)
        _isBubbleServiceEnabled.value = hasOverlayPermission && enabled
    }

    fun setBubbleServiceEnabled(enable: Boolean) {
        val context = getApplication<Application>()
        if (enable && !Settings.canDrawOverlays(context)) {
            // Requiere permiso de superposición
            return
        }

        val prefs = context.getSharedPreferences("floating_notes_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("bubble_overlay_enabled", enable).apply()
        _isBubbleServiceEnabled.value = enable

        val serviceIntent = Intent(context, FloatingBubbleService::class.java)
        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            context.stopService(serviceIntent)
        }
    }

    fun selectCalendarDate(dateMillis: Long) {
        _selectedCalendarDate.value = getStartOfDay(dateMillis)
    }

    fun addNote(
        title: String,
        content: String = "",
        targetDate: Long? = null,
        targetTime: Long? = null,
        repeatIntervalMinutes: Int = 0,
        isPinnedToOverlay: Boolean = true,
        priority: Int = 1
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalizedDate = targetDate?.let { getStartOfDay(it) }
            val note = NoteEntity(
                title = title,
                content = content,
                targetDate = normalizedDate,
                targetTime = targetTime,
                repeatIntervalMinutes = repeatIntervalMinutes,
                isPinnedToOverlay = isPinnedToOverlay,
                priority = priority
            )
            val generatedId = noteDao.insertNote(note)
            val savedNote = note.copy(id = generatedId)

            // Programar recordatorio si aplica
            if (repeatIntervalMinutes > 0 || targetTime != null || normalizedDate != null) {
                ReminderScheduler.scheduleNoteReminder(getApplication(), savedNote)
            }
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalized = note.copy(
                targetDate = note.targetDate?.let { getStartOfDay(it) }
            )
            noteDao.updateNote(normalized)
            ReminderScheduler.cancelReminder(getApplication(), note.id)
            if (!normalized.isCompleted && (normalized.repeatIntervalMinutes > 0 || normalized.targetTime != null || normalized.targetDate != null)) {
                ReminderScheduler.scheduleNoteReminder(getApplication(), normalized)
            }
        }
    }

    fun toggleComplete(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = !note.isCompleted
            noteDao.setCompletionStatus(note.id, newStatus)
            if (newStatus) {
                ReminderScheduler.cancelReminder(getApplication(), note.id)
            } else {
                if (note.repeatIntervalMinutes > 0 || note.targetTime != null || note.targetDate != null) {
                    ReminderScheduler.scheduleNoteReminder(getApplication(), note.copy(isCompleted = false))
                }
            }
        }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            noteDao.deleteNote(note)
            ReminderScheduler.cancelReminder(getApplication(), note.id)
        }
    }

    companion object {
        fun getStartOfDay(millis: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = millis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }
    }
}
