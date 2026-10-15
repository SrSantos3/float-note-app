package com.antigravity.floatingnotes.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class FloatingNotesTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        try {
            val hasOverlayPermission = Settings.canDrawOverlays(this)
            if (!hasOverlayPermission) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
                return
            }

            val prefs = getSharedPreferences("floating_notes_prefs", Context.MODE_PRIVATE)
            val currentlyEnabled = prefs.getBoolean("bubble_overlay_enabled", false)
            val newEnabled = !currentlyEnabled

            prefs.edit().putBoolean("bubble_overlay_enabled", newEnabled).apply()

            val serviceIntent = Intent(this, FloatingBubbleService::class.java)
            if (newEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } else {
                stopService(serviceIntent)
            }

            updateTileState()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences("floating_notes_prefs", Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("bubble_overlay_enabled", false)

        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (enabled) "Burbuja ON" else "Burbuja OFF"
        tile.updateTile()
    }
}
