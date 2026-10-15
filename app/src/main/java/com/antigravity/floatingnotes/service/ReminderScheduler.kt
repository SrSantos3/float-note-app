package com.antigravity.floatingnotes.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.antigravity.floatingnotes.data.NoteEntity

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"

    const val EXTRA_NOTE_ID = "extra_note_id"
    const val EXTRA_NOTE_TITLE = "extra_note_title"
    const val EXTRA_NOTE_CONTENT = "extra_note_content"
    const val EXTRA_REPEAT_MINUTES = "extra_repeat_minutes"

    fun scheduleNoteReminder(context: Context, note: NoteEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Determinar cuándo debe sonar la primera notificación
        val triggerAtMillis: Long = when {
            // Caso 1: Tiene hora fijada en el futuro
            note.targetTime != null && note.targetTime > System.currentTimeMillis() -> {
                note.targetTime
            }
            // Caso 2: Tiene intervalo periódico (ej. cada X minutos)
            note.repeatIntervalMinutes > 0 -> {
                System.currentTimeMillis() + (note.repeatIntervalMinutes * 60 * 1000L)
            }
            // Caso 3: Es para una fecha futura del calendario a las 09:00 AM
            note.targetDate != null && note.targetDate > System.currentTimeMillis() -> {
                note.targetDate + (9 * 60 * 60 * 1000L) // 9:00 AM
            }
            else -> return // Sin alarma aplicable
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.antigravity.floatingnotes.ACTION_ALARM"
            putExtra(EXTRA_NOTE_ID, note.id)
            putExtra(EXTRA_NOTE_TITLE, note.title)
            putExtra(EXTRA_NOTE_CONTENT, note.content)
            putExtra(EXTRA_REPEAT_MINUTES, note.repeatIntervalMinutes)
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
}
