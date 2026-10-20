package com.antigravity.floatingnotes.service

/**
 * @deprecated Obsoleto. Sustituido por [ForegroundAppDetector] que utiliza UsageStatsManager.
 * No requiere servicios de accesibilidad ni permisos invasivos y evita fallos en los ajustes del sistema.
 */
@Deprecated("Sustituido por ForegroundAppDetector que no requiere permisos de accesibilidad")
object AppDetectionAccessibilityService {
    var currentForegroundPackage: String? = null
    var currentForegroundLabel: String? = null
    fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean = false
}
