package com.antigravity.floatingnotes.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.floatingnotes.data.AppDatabase
import com.antigravity.floatingnotes.data.NoteEntity
import com.antigravity.floatingnotes.data.SubTask
import com.antigravity.floatingnotes.service.FloatingBubbleService
import com.antigravity.floatingnotes.service.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
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

    // Categoría seleccionada para filtrar ("Todas" = null)
    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    // Lista de categorías disponibles
    val availableCategories: StateFlow<List<String>> = noteDao.getDistinctCategories()
        .map { list ->
            val defaultCategories = listOf("General", "Trabajo", "Personal", "Urgente", "Ideas", "Compras")
            (defaultCategories + list).distinct()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("General", "Trabajo", "Personal", "Urgente", "Ideas", "Compras"))

    // Notas activas filtradas por categoría
    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredActiveNotes: StateFlow<List<NoteEntity>> = combine(activeNotes, _selectedCategory) { notes, cat ->
        if (cat == null) notes else notes.filter { it.category.equals(cat, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Fecha seleccionada en el modo Calendario
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

    // Estados de Horas de Silencio y Resumen Matutino
    val prefs = application.getSharedPreferences(ReminderScheduler.PREFS_NAME, Context.MODE_PRIVATE)

    private val _isQuietHoursEnabled = MutableStateFlow(prefs.getBoolean(ReminderScheduler.KEY_QUIET_HOURS_ENABLED, true))
    val isQuietHoursEnabled: StateFlow<Boolean> = _isQuietHoursEnabled.asStateFlow()

    private val _quietHoursStart = MutableStateFlow(prefs.getInt(ReminderScheduler.KEY_QUIET_HOURS_START, 23))
    val quietHoursStart: StateFlow<Int> = _quietHoursStart.asStateFlow()

    private val _quietHoursEnd = MutableStateFlow(prefs.getInt(ReminderScheduler.KEY_QUIET_HOURS_END, 8))
    val quietHoursEnd: StateFlow<Int> = _quietHoursEnd.asStateFlow()

    private val _isDailyBriefingEnabled = MutableStateFlow(prefs.getBoolean(ReminderScheduler.KEY_DAILY_BRIEFING_ENABLED, true))
    val isDailyBriefingEnabled: StateFlow<Boolean> = _isDailyBriefingEnabled.asStateFlow()

    init {
        checkBubbleServiceState()
    }

    fun setFilterCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun checkBubbleServiceState() {
        val context = getApplication<Application>()
        val hasOverlayPermission = Settings.canDrawOverlays(context)
        val enabled = prefs.getBoolean("bubble_overlay_enabled", false)
        _isBubbleServiceEnabled.value = hasOverlayPermission && enabled
    }

    fun setBubbleServiceEnabled(enable: Boolean) {
        val context = getApplication<Application>()
        if (enable && !Settings.canDrawOverlays(context)) {
            return
        }

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

    fun setQuietHours(enabled: Boolean, startHour: Int = _quietHoursStart.value, endHour: Int = _quietHoursEnd.value) {
        prefs.edit()
            .putBoolean(ReminderScheduler.KEY_QUIET_HOURS_ENABLED, enabled)
            .putInt(ReminderScheduler.KEY_QUIET_HOURS_START, startHour)
            .putInt(ReminderScheduler.KEY_QUIET_HOURS_END, endHour)
            .apply()
        _isQuietHoursEnabled.value = enabled
        _quietHoursStart.value = startHour
        _quietHoursEnd.value = endHour
    }

    fun setDailyBriefing(enabled: Boolean) {
        prefs.edit().putBoolean(ReminderScheduler.KEY_DAILY_BRIEFING_ENABLED, enabled).apply()
        _isDailyBriefingEnabled.value = enabled
        if (enabled) {
            ReminderScheduler.scheduleDailyBriefing(getApplication())
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
        priority: Int = 1,
        category: String = "General",
        colorHex: String = "#6366F1",
        isUrgentAlarm: Boolean = false,
        snoozeCount: Int = 0,
        isLocked: Boolean = false,
        subtasksJson: String = "[]",
        locationName: String? = null,
        wifiSsid: String? = null
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
                priority = priority,
                category = category,
                colorHex = colorHex,
                isUrgentAlarm = isUrgentAlarm,
                snoozeCount = snoozeCount,
                isLocked = isLocked,
                subtasksJson = subtasksJson,
                locationName = locationName?.ifBlank { null },
                wifiSsid = wifiSsid?.ifBlank { null }
            )
            val generatedId = noteDao.insertNote(note)
            val savedNote = note.copy(id = generatedId)

            if (repeatIntervalMinutes > 0 || targetTime != null || normalizedDate != null || isUrgentAlarm) {
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
            if (!normalized.isCompleted && (normalized.repeatIntervalMinutes > 0 || normalized.targetTime != null || normalized.targetDate != null || normalized.isUrgentAlarm)) {
                ReminderScheduler.scheduleNoteReminder(getApplication(), normalized)
            }
        }
    }

    fun toggleSubtask(note: NoteEntity, subtaskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = note.getSubtasks().map {
                if (it.id == subtaskId) it.copy(isDone = !it.isDone) else it
            }
            val updatedJson = SubTask.toJsonList(list)
            noteDao.updateSubtasks(note.id, updatedJson)
        }
    }

    fun toggleComplete(note: NoteEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val newStatus = !note.isCompleted
            noteDao.setCompletionStatus(note.id, newStatus)
            if (newStatus) {
                ReminderScheduler.cancelReminder(getApplication(), note.id)
            } else {
                if (note.repeatIntervalMinutes > 0 || note.targetTime != null || note.targetDate != null || note.isUrgentAlarm) {
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

    // ==========================================
    // COPIA DE SEGURIDAD (EXPORTAR E IMPORTAR JSON)
    // ==========================================

    suspend fun exportNotesToJsonUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val notes = noteDao.getAllNotesSync()
            val jsonArray = JSONArray()

            for (note in notes) {
                val obj = JSONObject().apply {
                    put("id", note.id)
                    put("title", note.title)
                    put("content", note.content)
                    put("isCompleted", note.isCompleted)
                    put("createdAt", note.createdAt)
                    put("targetDate", note.targetDate ?: JSONObject.NULL)
                    put("targetTime", note.targetTime ?: JSONObject.NULL)
                    put("repeatIntervalMinutes", note.repeatIntervalMinutes)
                    put("isPinnedToOverlay", note.isPinnedToOverlay)
                    put("priority", note.priority)
                    put("category", note.category)
                    put("colorHex", note.colorHex)
                    put("isUrgentAlarm", note.isUrgentAlarm)
                    put("snoozeCount", note.snoozeCount)
                    put("isLocked", note.isLocked)
                    put("subtasksJson", note.subtasksJson)
                    put("locationName", note.locationName ?: JSONObject.NULL)
                    put("wifiSsid", note.wifiSsid ?: JSONObject.NULL)
                }
                jsonArray.put(obj)
            }

            val rootJson = JSONObject().apply {
                put("app", "FloatNote")
                put("version", 5)
                put("exportedAt", System.currentTimeMillis())
                put("notes", jsonArray)
            }

            val context = getApplication<Application>()
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.write(rootJson.toString(2))
                    writer.flush()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importNotesFromJsonUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            val content = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        content.append(line)
                        line = reader.readLine()
                    }
                }
            }

            val rootJson = JSONObject(content.toString())
            val notesArray = rootJson.getJSONArray("notes")
            val importedNotes = mutableListOf<NoteEntity>()

            for (i in 0 until notesArray.length()) {
                val obj = notesArray.getJSONObject(i)
                val note = NoteEntity(
                    id = 0,
                    title = obj.getString("title"),
                    content = obj.optString("content", ""),
                    isCompleted = obj.optBoolean("isCompleted", false),
                    createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                    targetDate = if (obj.isNull("targetDate")) null else obj.optLong("targetDate"),
                    targetTime = if (obj.isNull("targetTime")) null else obj.optLong("targetTime"),
                    repeatIntervalMinutes = obj.optInt("repeatIntervalMinutes", 0),
                    isPinnedToOverlay = obj.optBoolean("isPinnedToOverlay", true),
                    priority = obj.optInt("priority", 1),
                    category = obj.optString("category", "General"),
                    colorHex = obj.optString("colorHex", "#6366F1"),
                    isUrgentAlarm = obj.optBoolean("isUrgentAlarm", false),
                    snoozeCount = obj.optInt("snoozeCount", 0),
                    isLocked = obj.optBoolean("isLocked", false),
                    subtasksJson = obj.optString("subtasksJson", "[]"),
                    locationName = if (obj.isNull("locationName")) null else obj.optString("locationName"),
                    wifiSsid = if (obj.isNull("wifiSsid")) null else obj.optString("wifiSsid")
                )
                importedNotes.add(note)
            }

            if (importedNotes.isNotEmpty()) {
                noteDao.insertNotes(importedNotes)
                for (note in importedNotes) {
                    if (!note.isCompleted && (note.repeatIntervalMinutes > 0 || note.targetTime != null || note.targetDate != null || note.isUrgentAlarm)) {
                        ReminderScheduler.scheduleNoteReminder(context, note)
                    }
                }
            }
            importedNotes.size
        } catch (e: Exception) {
            e.printStackTrace()
            -1
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
