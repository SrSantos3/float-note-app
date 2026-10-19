package com.antigravity.floatingnotes.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.antigravity.floatingnotes.ui.util.AppHelper

class AppDetectionAccessibilityService : AccessibilityService() {

    private var lastPackageName: String? = null
    private val launcherPackages = mutableSetOf<String>()

    companion object {
        private const val TAG = "AppDetectionAccess"

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedComponentName = ComponentName(context, AppDetectionAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)

            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                if (enabledComponent != null && enabledComponent == expectedComponentName) {
                    return true
                }
            }
            return false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Servicio de Detección de Apps Conectado con Éxito")
        loadLauncherPackages()
    }

    private fun loadLauncherPackages() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (info in resolveInfos) {
                launcherPackages.add(info.activityInfo.packageName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return

            // Ignorar nuestro propio paquete para no reaccionar a la propia app
            if (packageName == applicationContext.packageName) return

            // Ignorar teclado y UI del sistema
            if (packageName == "com.android.systemui" || 
                packageName.contains("inputmethod") || 
                packageName.contains("keyboard") || 
                packageName.contains("ime")) {
                return
            }

            // Si el usuario vuelve a la pantalla de inicio (launcher), notificar salida de la app
            if (launcherPackages.contains(packageName) || packageName.contains("launcher") || packageName.contains("home")) {
                if (lastPackageName != null) {
                    Log.d(TAG, "Usuario volvió al inicio. Saliendo de app: $lastPackageName")
                    notifyAppExited(lastPackageName!!)
                    lastPackageName = null
                }
                return
            }

            // Si el paquete no ha cambiado, no hacer nada
            if (packageName == lastPackageName) return

            val previousPackage = lastPackageName
            lastPackageName = packageName

            if (previousPackage != null) {
                notifyAppExited(previousPackage)
            }

            Log.d(TAG, "App abierta detectada: $packageName")
            notifyAppOpened(packageName)
        }
    }

    private fun notifyAppOpened(packageName: String) {
        val (appLabel, _) = AppHelper.getAppInfoForPackage(this, packageName)
        FloatingBubbleService.handleForegroundAppChanged(this, packageName, appLabel)
    }

    private fun notifyAppExited(packageName: String) {
        FloatingBubbleService.handleForegroundAppExited(this, packageName)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Servicio de Detección Interrumpido")
    }

    override fun onDestroy() {
        super.onDestroy()
        lastPackageName = null
    }
}
