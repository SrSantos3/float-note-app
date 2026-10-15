package com.antigravity.floatingnotes.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.antigravity.floatingnotes.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            // 1. Restaurar alarmas de notas activas
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getDatabase(context)
                val activeNotes = db.noteDao().getActiveRepeatingNotes()
                for (note in activeNotes) {
                    ReminderScheduler.scheduleNoteReminder(context, note)
                }
            }

            // 2. Iniciar el servicio de burbuja flotante si tiene el permiso concedido
            val prefs = context.getSharedPreferences("floating_notes_prefs", Context.MODE_PRIVATE)
            val bubbleEnabled = prefs.getBoolean("bubble_overlay_enabled", true)

            if (bubbleEnabled && Settings.canDrawOverlays(context)) {
                val serviceIntent = Intent(context, FloatingBubbleService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
