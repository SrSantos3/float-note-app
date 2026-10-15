package com.antigravity.floatingnotes.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.antigravity.floatingnotes.MainActivity
import com.antigravity.floatingnotes.R
import com.antigravity.floatingnotes.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "channel_floating_notes_reminders"
        const val ACTION_COMPLETE = "com.antigravity.floatingnotes.ACTION_COMPLETE_NOTE"
        const val ACTION_SNOOZE = "com.antigravity.floatingnotes.ACTION_SNOOZE_NOTE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, -1L)
        if (noteId == -1L) return

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (intent.action) {
            ACTION_COMPLETE -> {
                // El usuario pulsó "Marcar Hecha" en la notificación
                notificationManager.cancel(noteId.toInt())
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(context)
                    db.noteDao().setCompletionStatus(noteId, true)
                    ReminderScheduler.cancelReminder(context, noteId)
                }
            }
            ACTION_SNOOZE -> {
                // El usuario pulsó "Posponer 15 min"
                notificationManager.cancel(noteId.toInt())
                val title = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_TITLE) ?: "Nota"
                val content = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_CONTENT) ?: ""
                val repeatMinutes = intent.getIntExtra(ReminderScheduler.EXTRA_REPEAT_MINUTES, 0)
                
                // Reprogramar en 15 minutos
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(context)
                    val note = db.noteDao().getNoteById(noteId)
                    if (note != null && !note.isCompleted) {
                        val snoozedNote = note.copy(
                            targetTime = System.currentTimeMillis() + (15 * 60 * 1000L)
                        )
                        ReminderScheduler.scheduleNoteReminder(context, snoozedNote)
                    }
                }
            }
            else -> {
                // Es el disparo de la alarma programada o periódica
                val title = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_TITLE) ?: "Recordatorio de Nota"
                val content = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_CONTENT) ?: ""
                val repeatMinutes = intent.getIntExtra(ReminderScheduler.EXTRA_REPEAT_MINUTES, 0)

                // Verificar si la nota no fue completada antes de lanzar la notificación
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(context)
                    val note = db.noteDao().getNoteById(noteId)
                    if (note != null && !note.isCompleted) {
                        showNotification(context, notificationManager, noteId, title, content, repeatMinutes)

                        // Si tiene intervalo periódico (cada X minutos), reprogramar la siguiente notificación
                        if (repeatMinutes > 0) {
                            ReminderScheduler.scheduleNoteReminder(context, note)
                        }
                    }
                }
            }
        }
    }

    private fun showNotification(
        context: Context,
        notificationManager: NotificationManager,
        noteId: Long,
        title: String,
        content: String,
        repeatMinutes: Int
    ) {
        createNotificationChannel(context, notificationManager)

        // Intent al tocar la notificación para abrir la app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            noteId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Acción: Marcar Hecha
        val completeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_COMPLETE
            putExtra(ReminderScheduler.EXTRA_NOTE_ID, noteId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            (noteId * 10 + 1).toInt(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Acción: Posponer 15 min
        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(ReminderScheduler.EXTRA_NOTE_ID, noteId)
            putExtra(ReminderScheduler.EXTRA_NOTE_TITLE, title)
            putExtra(ReminderScheduler.EXTRA_NOTE_CONTENT, content)
            putExtra(ReminderScheduler.EXTRA_REPEAT_MINUTES, repeatMinutes)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (noteId * 10 + 2).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalInfo = if (repeatMinutes > 0) " (Repite cada $repeatMinutes min)" else ""

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle(title)
            .setContentText(content.ifBlank { "Tienes esta tarea pendiente$intervalInfo" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                if (content.isNotBlank()) "$content\n$intervalInfo" else "Tienes esta tarea pendiente$intervalInfo"
            ))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_check, "Completar", completePendingIntent)
            .addAction(R.drawable.ic_close, "Posponer 15m", snoozePendingIntent)
            .build()

        notificationManager.notify(noteId.toInt(), notification)
    }

    private fun createNotificationChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_reminders_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_reminders_desc)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
