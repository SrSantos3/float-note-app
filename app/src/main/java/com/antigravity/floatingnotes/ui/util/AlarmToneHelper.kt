package com.antigravity.floatingnotes.ui.util

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log

object AlarmToneHelper {
    private const val TAG = "AlarmToneHelper"
    private const val PREFS_NAME = "floating_notes_prefs"
    const val KEY_DEFAULT_ALARM_TONE_URI = "default_alarm_tone_uri"
    const val KEY_DEFAULT_ALARM_TONE_NAME = "default_alarm_tone_name"

    private var currentPlayingRingtone: Ringtone? = null

    // Tonos prestablecidos populares de Android
    data class PresetTone(
        val name: String,
        val uriString: String
    )

    fun getDefaultAlarmTone(context: Context): Pair<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedUri = prefs.getString(KEY_DEFAULT_ALARM_TONE_URI, null)
        val savedName = prefs.getString(KEY_DEFAULT_ALARM_TONE_NAME, null)

        if (!savedUri.isNullOrBlank() && !savedName.isNullOrBlank()) {
            return Pair(savedUri, savedName)
        }

        // Si no hay guardado, usar el tono de alarma por defecto del sistema
        val systemAlarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val uriStr = systemAlarmUri?.toString() ?: ""
        val title = getToneTitle(context, uriStr).ifBlank { "Alarma Predeterminada del Sistema" }
        return Pair(uriStr, title)
    }

    fun setDefaultAlarmTone(context: Context, uriString: String, name: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DEFAULT_ALARM_TONE_URI, uriString)
            .putString(KEY_DEFAULT_ALARM_TONE_NAME, name)
            .apply()
        Log.d(TAG, "Tono de alarma predeterminado actualizado: $name ($uriString)")
    }

    fun getToneTitle(context: Context, uriString: String?): String {
        if (uriString.isNullOrBlank()) return "Predeterminado de la app"
        return try {
            val uri = Uri.parse(uriString)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.getTitle(context) ?: "Tono personalizado"
        } catch (e: Exception) {
            "Tono de Alarma"
        }
    }

    fun getSystemTones(context: Context): List<PresetTone> {
        val list = mutableListOf<PresetTone>()
        try {
            val manager = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_ALARM)
            }
            val cursor = manager.cursor
            var count = 0
            while (cursor.moveToNext() && count < 10) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = manager.getRingtoneUri(cursor.position)
                if (uri != null && title != null) {
                    list.add(PresetTone(title, uri.toString()))
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Si la lista de alarmas es corta, añadir tonos de notificación/llamada
        if (list.size < 3) {
            try {
                val manager = RingtoneManager(context).apply {
                    setType(RingtoneManager.TYPE_RINGTONE)
                }
                val cursor = manager.cursor
                var count = 0
                while (cursor.moveToNext() && count < 5) {
                    val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                    val uri = manager.getRingtoneUri(cursor.position)
                    if (uri != null && title != null && list.none { it.uriString == uri.toString() }) {
                        list.add(PresetTone(title, uri.toString()))
                        count++
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return list
    }

    @Synchronized
    fun playPreview(context: Context, uriString: String?, onComplete: (() -> Unit)? = null): Boolean {
        stopPreview()
        if (uriString.isNullOrBlank()) {
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            return playPreview(context, defaultUri?.toString(), onComplete)
        }

        return try {
            val uri = Uri.parse(uriString)
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
            if (ringtone != null) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                ringtone.audioAttributes = audioAttributes
                ringtone.play()
                currentPlayingRingtone = ringtone
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @Synchronized
    fun stopPreview() {
        try {
            if (currentPlayingRingtone?.isPlaying == true) {
                currentPlayingRingtone?.stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            currentPlayingRingtone = null
        }
    }

    fun isPlaying(): Boolean {
        return currentPlayingRingtone?.isPlaying == true
    }
}
