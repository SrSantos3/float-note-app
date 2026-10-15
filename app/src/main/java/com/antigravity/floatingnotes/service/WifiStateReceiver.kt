package com.antigravity.floatingnotes.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.antigravity.floatingnotes.MainActivity
import com.antigravity.floatingnotes.R
import com.antigravity.floatingnotes.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WifiStateReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_LOCATION_ID = "channel_floating_notes_location"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val activeNetwork = connectivityManager.activeNetwork ?: return
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val currentSsid = wifiInfo?.ssid?.replace("\"", "") ?: ""

            if (currentSsid.isNotBlank() && currentSsid != "<unknown ssid>") {
                checkLocationNotes(context, currentSsid)
            }
        }
    }

    private fun checkLocationNotes(context: Context, currentSsid: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val notes = db.noteDao().getNotesByLocationOrWifiSync(currentSsid, currentSsid)

            if (notes.isNotEmpty()) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                createLocationChannel(context, notificationManager)

                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    999111,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val count = notes.size
                val preview = notes.take(2).joinToString(", ") { it.title }

                val notification = NotificationCompat.Builder(context, CHANNEL_LOCATION_ID)
                    .setSmallIcon(R.drawable.ic_bubble)
                    .setContentTitle("📍 Has llegado a: $currentSsid")
                    .setContentText("Tienes $count tareas pendientes aquí: $preview")
                    .setStyle(NotificationCompat.BigTextStyle().bigText("Tienes $count tareas asignadas a esta red o ubicación:\n$preview"))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

                notificationManager.notify(999111, notification)
            }
        }
    }

    private fun createLocationChannel(context: Context, manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_LOCATION_ID,
                "Recordatorios por Ubicación / Wi-Fi",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones automáticas al conectarte a tu Wi-Fi de casa o trabajo."
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
