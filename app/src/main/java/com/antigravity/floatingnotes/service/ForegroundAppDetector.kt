package com.antigravity.floatingnotes.service

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.antigravity.floatingnotes.ui.util.AppHelper

/**
 * Detector Inteligente de Aplicaciones en Primer Plano
 * Utiliza el API nativo UsageStatsManager ("Acceso a datos de uso").
 * 
 * Ventajas frente a Accesibilidad:
 * - NO bloquea ni cierra los ajustes del teléfono.
 * - Es el estándar oficial de Android para bienestar digital, bloqueadores y asistentes.
 * - No requiere permisos especiales de control total del dispositivo.
 */
object ForegroundAppDetector {

    /**
     * Comprueba si el usuario ha autorizado el "Acceso a datos de uso".
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * Abre directamente la sección de Ajustes del sistema de "Acceso a datos de uso".
     */
    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    /**
     * Detecta la aplicación actualmente en pantalla examinando los eventos recientes.
     */
    fun getForegroundPackage(context: Context): String? {
        if (!hasUsageStatsPermission(context)) return null
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
            val time = System.currentTimeMillis()
            // Consultar eventos de los últimos 10 segundos
            val events = usageStatsManager.queryEvents(time - 10_000L, time)
            val event = UsageEvents.Event()
            var latestPackage: String? = null
            var latestTimestamp = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val type = event.eventType
                if (type == UsageEvents.Event.ACTIVITY_RESUMED || type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    val pkg = event.packageName
                    if (!pkg.isNullOrBlank() && !isSystemOrIgnoredPackage(pkg, context)) {
                        if (event.timeStamp >= latestTimestamp) {
                            latestTimestamp = event.timeStamp
                            latestPackage = pkg
                        }
                    }
                }
            }
            latestPackage
        } catch (e: Throwable) {
            null
        }
    }

    private fun isSystemOrIgnoredPackage(packageName: String, context: Context): Boolean {
        if (packageName.equals(context.packageName, ignoreCase = true)) return true
        if (packageName == "android" ||
            packageName == "com.android.systemui" ||
            packageName == "com.google.android.gms" ||
            packageName.contains("inputmethod") ||
            packageName.contains("keyboard") ||
            packageName.contains("launcher") ||
            packageName.contains("home") ||
            packageName.contains("ime")
        ) {
            return true
        }
        return false
    }
}
