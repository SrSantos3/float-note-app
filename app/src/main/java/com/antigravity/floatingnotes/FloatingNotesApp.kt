package com.antigravity.floatingnotes

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.antigravity.floatingnotes.data.AppDatabase
import com.antigravity.floatingnotes.service.AlarmReceiver
import com.antigravity.floatingnotes.service.FloatingBubbleService

class FloatingNotesApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Canal 1: Recordatorios de notas
            val remindersChannel = NotificationChannel(
                AlarmReceiver.CHANNEL_ID,
                getString(R.string.channel_reminders_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_reminders_desc)
                enableVibration(true)
            }

            // Canal 2: Burbuja flotante en primer plano
            val overlayChannel = NotificationChannel(
                FloatingBubbleService.CHANNEL_ID,
                getString(R.string.channel_overlay_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_overlay_service_desc)
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(remindersChannel)
            notificationManager.createNotificationChannel(overlayChannel)
        }
    }
}
