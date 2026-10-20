package com.antigravity.floatingnotes.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        var currentForegroundPackage: String? = null
            private set
        var currentForegroundLabel: String? = null
            private set

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            return try {
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
                false
            } catch (e: Throwable) {
                false
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                notificationTimeout = 100
                flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            }
            this.serviceInfo = info
            Log.d(TAG, "Servicio de Detección de Apps Conectado con Éxito")
            loadLauncherPackages()
        } catch (e: Throwable) {
            Log.e(TAG, "Error en onServiceConnected", e)
        }
    }

    private fun loadLauncherPackages() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            for (info in resolveInfos) {
                val pkg = info.activityInfo?.packageName
                if (!pkg.isNullOrBlank()) {
                    launcherPackages.add(pkg)
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun isSystemOrTransientPackage(packageName: String): Boolean {
        if (packageName.equals(applicationContext.packageName, ignoreCase = true)) return true
        if (packageName == "android" ||
            packageName == "com.android.systemui" ||
            packageName == "com.google.android.gms" ||
            packageName.contains("inputmethod") ||
            packageName.contains("keyboard") ||
            packageName.contains("ime") ||
            packageName.contains("permissioncontroller") ||
            packageName.contains("packageinstaller") ||
            packageName == "com.miui.securityadd" ||
            packageName == "com.miui.guardprovider" ||
            packageName == "com.samsung.android.app.cocktailbarservice"
        ) {
            return true
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val eventPackage = event.packageName?.toString() ?: return

                // Si es nuestra propia app (FloatNote), limpiar estados de apps externas y no interferir
                if (eventPackage.equals(applicationContext.packageName, ignoreCase = true)) {
                    if (lastPackageName != null) {
                        notifyAppExited(lastPackageName!!)
                        lastPackageName = null
                        currentForegroundPackage = null
                        currentForegroundLabel = null
                    }
                    return
                }

                // Ignorar teclado, UI del sistema y paquetes transitorios
                if (isSystemOrTransientPackage(eventPackage)) return

                // Si el usuario vuelve a la pantalla de inicio (launcher), notificar salida de la app
                if (launcherPackages.contains(eventPackage) || eventPackage.contains("launcher") || eventPackage.contains("home")) {
                    if (lastPackageName != null) {
                        Log.d(TAG, "Usuario volvió al inicio. Saliendo de app: $lastPackageName")
                        notifyAppExited(lastPackageName!!)
                        lastPackageName = null
                        currentForegroundPackage = null
                        currentForegroundLabel = null
                    }
                    return
                }

                // Si el paquete no ha cambiado, no hacer nada
                if (eventPackage.equals(lastPackageName, ignoreCase = true)) return

                val previousPackage = lastPackageName
                lastPackageName = eventPackage
                currentForegroundPackage = eventPackage

                val (appLabel, _) = AppHelper.getAppInfoForPackage(this, eventPackage)
                currentForegroundLabel = appLabel

                if (previousPackage != null) {
                    notifyAppExited(previousPackage)
                }

                Log.d(TAG, "App abierta detectada: $eventPackage ($appLabel)")
                notifyAppOpened(eventPackage, appLabel)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error en onAccessibilityEvent", e)
        }
    }

    private fun notifyAppOpened(packageName: String, appLabel: String) {
        try {
            FloatingBubbleService.handleForegroundAppChanged(this, packageName, appLabel)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun notifyAppExited(packageName: String) {
        try {
            FloatingBubbleService.handleForegroundAppExited(this, packageName)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Servicio de Detección Interrumpido")
    }

    override fun onDestroy() {
        super.onDestroy()
        lastPackageName = null
        currentForegroundPackage = null
        currentForegroundLabel = null
    }
}
