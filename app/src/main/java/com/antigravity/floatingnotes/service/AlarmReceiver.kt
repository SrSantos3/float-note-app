package com.antigravity.floatingnotes.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.antigravity.floatingnotes.MainActivity
import com.antigravity.floatingnotes.R
import com.antigravity.floatingnotes.data.AppDatabase
import com.antigravity.floatingnotes.ui.util.AlarmToneHelper
import com.antigravity.floatingnotes.ui.viewmodel.NotesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "channel_floating_notes_reminders"
        const val CHANNEL_URGENT_ID = "channel_floating_notes_urgent_alarm"
        const val CHANNEL_BRIEFING_ID = "channel_floating_notes_daily_briefing"
        const val ACTION_COMPLETE = "com.antigravity.floatingnotes.ACTION_COMPLETE_NOTE"
        const val ACTION_SNOOZE_15 = "com.antigravity.floatingnotes.ACTION_SNOOZE_15"
        const val ACTION_SNOOZE_SMART = "com.antigravity.floatingnotes.ACTION_SNOOZE_SMART"
        const val ACTION_DISMISS_ALARM = "com.antigravity.floatingnotes.ACTION_DISMISS_ALARM"
        const val ACTION_DAILY_BRIEFING = "com.antigravity.floatingnotes.ACTION_DAILY_BRIEFING"
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Caso: Resumen Matutino Diario
            if (intent.action == ACTION_DAILY_BRIEFING) {
                handleDailyBriefing(context, notificationManager)
                return
            }

            val noteId = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, -1L)
            if (noteId == -1L) return

            when (intent.action) {
                ACTION_DISMISS_ALARM -> {
                    notificationManager.cancel(noteId.toInt())
                    AlarmToneHelper.stopPreview()
                }
                ACTION_COMPLETE -> {
                    notificationManager.cancel(noteId.toInt())
                    AlarmToneHelper.stopPreview()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getDatabase(context)
                            db.noteDao().setCompletionStatus(noteId, true)
                            ReminderScheduler.cancelReminder(context, noteId)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                ACTION_SNOOZE_15 -> {
                    notificationManager.cancel(noteId.toInt())
                    AlarmToneHelper.stopPreview()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getDatabase(context)
                            val note = db.noteDao().getNoteById(noteId)
                            if (note != null && !note.isCompleted) {
                                val newSnoozeCount = note.snoozeCount + 1
                                val updatedNote = note.copy(
                                    targetTime = System.currentTimeMillis() + (15 * 60 * 1000L),
                                    snoozeCount = newSnoozeCount
                                )
                                db.noteDao().updateNote(updatedNote)
                                ReminderScheduler.scheduleNoteReminder(context, updatedNote)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                ACTION_SNOOZE_SMART -> {
                    notificationManager.cancel(noteId.toInt())
                    AlarmToneHelper.stopPreview()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getDatabase(context)
                            val note = db.noteDao().getNoteById(noteId)
                            if (note != null && !note.isCompleted) {
                                val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                                val targetTime = if (currentHour < 17) {
                                    ReminderScheduler.getEveningSnoozeMillis()
                                } else {
                                    ReminderScheduler.getTomorrowMorningSnoozeMillis()
                                }
                                val newSnoozeCount = note.snoozeCount + 1
                                val updatedNote = note.copy(
                                    targetTime = targetTime,
                                    snoozeCount = newSnoozeCount
                                )
                                db.noteDao().updateNote(updatedNote)
                                ReminderScheduler.scheduleNoteReminder(context, updatedNote)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                else -> {
                    // Disparo de la alarma programada o periódica
                    val title = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_TITLE) ?: "Alarma de Nota"
                    val content = intent.getStringExtra(ReminderScheduler.EXTRA_NOTE_CONTENT) ?: ""
                    val repeatMinutes = intent.getIntExtra(ReminderScheduler.EXTRA_REPEAT_MINUTES, 0)
                    val isUrgent = intent.getBooleanExtra(ReminderScheduler.EXTRA_IS_URGENT_ALARM, false)
                    val toneUriExtra = intent.getStringExtra(ReminderScheduler.EXTRA_ALARM_TONE_URI)

                    // 🌙 VERIFICAR HORAS DE SILENCIO (MODO SUEÑO)
                    if (ReminderScheduler.isQuietHours(context)) {
                        // Durante las horas de silencio, reprogramar automáticamente para cuando termine la noche
                        val quietEndMillis = ReminderScheduler.getNextQuietHoursEndMillis(context)
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val db = AppDatabase.getDatabase(context)
                                val note = db.noteDao().getNoteById(noteId)
                                if (note != null && !note.isCompleted) {
                                    val deferredNote = note.copy(targetTime = quietEndMillis)
                                    ReminderScheduler.scheduleNoteReminder(context, deferredNote)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        return
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getDatabase(context)
                            val note = db.noteDao().getNoteById(noteId)
                            if (note != null && !note.isCompleted) {
                                val chosenToneUri = note.alarmToneUri?.ifBlank { null }
                                    ?: toneUriExtra?.ifBlank { null }
                                    ?: AlarmToneHelper.getDefaultAlarmTone(context).first

                                showAlarmNotification(
                                    context = context,
                                    notificationManager = notificationManager,
                                    noteId = noteId,
                                    title = title,
                                    content = content,
                                    repeatMinutes = repeatMinutes,
                                    isUrgent = isUrgent,
                                    snoozeCount = note.snoozeCount,
                                    alarmToneUri = chosenToneUri
                                )

                                if (repeatMinutes > 0) {
                                    val updatedNote = note.copy(snoozeCount = note.snoozeCount + 1)
                                    db.noteDao().updateNote(updatedNote)
                                    ReminderScheduler.scheduleNoteReminder(context, updatedNote)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleDailyBriefing(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val briefingChannel = NotificationChannel(
                CHANNEL_BRIEFING_ID,
                "Resumen Matutino Diario",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificación diaria con el resumen de tareas al comenzar el día."
                enableVibration(true)
            }
            manager.createNotificationChannel(briefingChannel)
        }

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val todayStart = NotesViewModel.getStartOfDay(System.currentTimeMillis())
            val todayEnd = todayStart + (24 * 60 * 60 * 1000L) - 1

            val todayNotes = db.noteDao().getActiveNotesForDateRangeSync(todayStart, todayEnd)
            val count = todayNotes.size
            val urgentCount = todayNotes.count { it.isUrgentAlarm }

            val briefingText = when {
                count == 0 -> "No tienes tareas programadas para hoy. ¡Disfruta de tu día! ✨"
                urgentCount > 0 -> "Tienes $count tareas para hoy ($urgentCount con alarma urgente). ¡A por ellas!"
                else -> "Tienes $count tareas pendientes para hoy. Toca para verlas."
            }

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                777777,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_BRIEFING_ID)
                .setSmallIcon(R.drawable.ic_calendar)
                .setContentTitle("Buenos días ☀️ Tu resumen de hoy")
                .setContentText(briefingText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(briefingText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            manager.notify(777777, notification)

            // Reprogramar para la mañana siguiente
            ReminderScheduler.scheduleDailyBriefing(context)
        }
    }

    private fun showAlarmNotification(
        context: Context,
        notificationManager: NotificationManager,
        noteId: Long,
        title: String,
        content: String,
        repeatMinutes: Int,
        isUrgent: Boolean,
        snoozeCount: Int,
        alarmToneUri: String
    ) {
        val soundUri = if (alarmToneUri.isNotBlank()) Uri.parse(alarmToneUri) else null
        val channelId = "channel_floating_alarm_${alarmToneUri.hashCode()}"

        // Crear o actualizar canal de notificación como ALARMA
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alarmas de FloatNote",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarmas de recordatorio con tono personalizado."
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 1000)
                if (soundUri != null) {
                    val audioAttributes = AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                    setSound(soundUri, audioAttributes)
                }
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            noteId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

        val snooze15Intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_SNOOZE_15
            putExtra(ReminderScheduler.EXTRA_NOTE_ID, noteId)
        }
        val snooze15PendingIntent = PendingIntent.getBroadcast(
            context,
            (noteId * 10 + 2).toInt(),
            snooze15Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_DISMISS_ALARM
            putExtra(ReminderScheduler.EXTRA_NOTE_ID, noteId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (noteId * 10 + 4).toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val antiProcrastinationMsg = when {
            snoozeCount >= 5 -> "\n🚨 ¡ALERTA! Llevas posponiendo esto $snoozeCount VECES. ¡Hazlo de una vez!"
            snoozeCount in 2..4 -> "\n👀 Pospuesta $snoozeCount veces hoy. ¡No lo dejes pasar!"
            snoozeCount == 1 -> "\n⏱️ Pospuesta 1 vez."
            else -> ""
        }

        val repeatInfo = if (repeatMinutes > 0) " (Repite cada $repeatMinutes min)" else ""
        val urgencyPrefix = if (isUrgent) "🚨 [ALARMA URGENTE] " else "⏰ [ALARMA] "
        val fullContent = if (content.isNotBlank()) "$content$repeatInfo$antiProcrastinationMsg" else "Tarea pendiente$repeatInfo$antiProcrastinationMsg"

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle("$urgencyPrefix$title")
            .setContentText(fullContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullContent))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(R.drawable.ic_check, "Completar", completePendingIntent)
            .addAction(R.drawable.ic_close, "+15 min", snooze15PendingIntent)
            .addAction(R.drawable.ic_close, "Silenciar", dismissPendingIntent)

        if (soundUri != null) {
            notificationBuilder.setSound(soundUri)
        }
        notificationBuilder.setVibrate(longArrayOf(0, 600, 300, 600, 300, 1000))

        notificationManager.notify(noteId.toInt(), notificationBuilder.build())

        // Hacer sonar el tono de alarma insistente
        AlarmToneHelper.playPreview(context, alarmToneUri)

        // Detener el sonido automáticamente tras 45 segundos si nadie responde
        CoroutineScope(Dispatchers.Main).launch {
            delay(45000L)
            AlarmToneHelper.stopPreview()
        }
    }
}
