package com.antigravity.floatingnotes.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.antigravity.floatingnotes.data.NoteEntity
import java.util.Calendar

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"

    const val EXTRA_NOTE_ID = "extra_note_id"
    const val EXTRA_NOTE_TITLE = "extra_note_title"
    const val EXTRA_NOTE_CONTENT = "extra_note_content"
    const val EXTRA_REPEAT_MINUTES = "extra_repeat_minutes"
    const val EXTRA_IS_URGENT_ALARM = "extra_is_urgent_alarm"
    const val EXTRA_SNOOZE_COUNT = "extra_snooze_count"

    const val PREFS_NAME = "floating_notes_prefs"
    const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
    const val KEY_QUIET_HOURS_START = "quiet_hours_start" // default 23
    const val KEY_QUIET_HOURS_END = "quiet_hours_end"     // default 8

    const val KEY_DAILY_BRIEFING_ENABLED = "daily_briefing_enabled"
    const val KEY_DAILY_BRIEFING_HOUR = "daily_briefing_hour" // default 8
    const val KEY_DAILY_BRIEFING_MINUTE = "daily_briefing_minute" // default 30

    fun scheduleNoteReminder(context: Context, note: NoteEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val triggerAtMillis: Long = when {
            note.targetTime != null && note.targetTime > System.currentTimeMillis() -> {
                note.targetTime
            }
            note.repeatIntervalMinutes > 0 -> {
                System.currentTimeMillis() + (note.repeatIntervalMinutes * 60 * 1000L)
            }
            note.targetDate != null && note.targetDate > System.currentTimeMillis() -> {
                note.targetDate + (9 * 60 * 60 * 1000L) // 9:00 AM
            }
            note.isUrgentAlarm -> {
                System.currentTimeMillis() + (15 * 60 * 1000L)
            }
            else -> return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.antigravity.floatingnotes.ACTION_ALARM"
            putExtra(EXTRA_NOTE_ID, note.id)
            putExtra(EXTRA_NOTE_TITLE, note.title)
            putExtra(EXTRA_NOTE_CONTENT, note.content)
            putExtra(EXTRA_REPEAT_MINUTES, note.repeatIntervalMinutes)
            putExtra(EXTRA_IS_URGENT_ALARM, note.isUrgentAlarm)
            putExtra(EXTRA_SNOOZE_COUNT, note.snoozeCount)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            note.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Alarma programada para la nota #${note.id} en ${triggerAtMillis - System.currentTimeMillis()} ms")
        } catch (e: SecurityException) {
            Log.e(TAG, "No se pudo programar la alarma exacta: permiso denegado", e)
        }
    }

    fun cancelReminder(context: Context, noteId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.antigravity.floatingnotes.ACTION_ALARM"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            noteId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Alarma cancelada para la nota #$noteId")
        }
    }

    // ==========================================
    // 🌙 HORAS DE SILENCIO (QUIET HOURS)
    // ==========================================

    fun isQuietHours(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, true)
        if (!enabled) return false

        val startHour = prefs.getInt(KEY_QUIET_HOURS_START, 23)
        val endHour = prefs.getInt(KEY_QUIET_HOURS_END, 8)

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        return if (startHour > endHour) {
            // Ejemplo: 23:00 a 08:00
            currentHour >= startHour || currentHour < endHour
        } else {
            // Ejemplo: 01:00 a 07:00
            currentHour in startHour until endHour
        }
    }

    fun getNextQuietHoursEndMillis(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val endHour = prefs.getInt(KEY_QUIET_HOURS_END, 8)

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }

    // ==========================================
    // ☀️ RESUMEN MATUTINO (DAILY BRIEFING)
    // ==========================================

    fun scheduleDailyBriefing(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_DAILY_BRIEFING_ENABLED, true)
        if (!enabled) return

        val hour = prefs.getInt(KEY_DAILY_BRIEFING_HOUR, 8)
        val minute = prefs.getInt(KEY_DAILY_BRIEFING_MINUTE, 30)

        val targetCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_DAILY_BRIEFING
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            888888,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                targetCal.timeInMillis,
                pendingIntent
            )
            Log.d(TAG, "Resumen matutino programado para las ${targetCal.time}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getEveningSnoozeMillis(): Long {
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.HOUR_OF_DAY) < 18) {
            cal.set(Calendar.HOUR_OF_DAY, 18)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        } else {
            return System.currentTimeMillis() + (3 * 60 * 60 * 1000L)
        }
    }

    fun getTomorrowMorningSnoozeMillis(): Long {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
