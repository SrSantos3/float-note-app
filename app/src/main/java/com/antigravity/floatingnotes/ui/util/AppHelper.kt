package com.antigravity.floatingnotes.ui.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.Locale

data class KnownAppInfo(
    val id: String,
    val name: String,
    val packageName: String,
    val aliases: List<String>,
    val emoji: String
)

object AppHelper {

    val POPULAR_APPS = listOf(
        KnownAppInfo("whatsapp", "WhatsApp", "com.whatsapp", listOf("whatsapp", "wasap", "wha", "wpp"), "💬"),
        KnownAppInfo("instagram", "Instagram", "com.instagram.android", listOf("instagram", "insta", "ig"), "📸"),
        KnownAppInfo("youtube", "YouTube", "com.google.android.youtube", listOf("youtube", "yt"), "▶️"),
        KnownAppInfo("tiktok", "TikTok", "com.zhiliaoapp.musically", listOf("tiktok", "tik tok"), "🎵"),
        KnownAppInfo("telegram", "Telegram", "org.telegram.messenger", listOf("telegram"), "✈️"),
        KnownAppInfo("twitter", "X (Twitter)", "com.twitter.android", listOf("twitter", "x", "tweet"), "🐦"),
        KnownAppInfo("gmail", "Gmail", "com.google.android.gm", listOf("gmail", "correo", "email"), "✉️"),
        KnownAppInfo("spotify", "Spotify", "com.spotify.music", listOf("spotify"), "🎧"),
        KnownAppInfo("facebook", "Facebook", "com.facebook.katana", listOf("facebook", "fb", "messenger"), "👥"),
        KnownAppInfo("linkedin", "LinkedIn", "com.linkedin.android", listOf("linkedin"), "💼"),
        KnownAppInfo("discord", "Discord", "com.discord", listOf("discord"), "🎮"),
        KnownAppInfo("slack", "Slack", "com.slack", listOf("slack"), "💬"),
        KnownAppInfo("chrome", "Chrome", "com.android.chrome", listOf("chrome", "navegador"), "🌐"),
        KnownAppInfo("amazon", "Amazon", "com.amazon.mShop.android.shopping", listOf("amazon"), "📦")
    )

    /**
     * Detecta si un texto menciona el nombre o alias de alguna app conocida
     * o alguna app instalada en el dispositivo.
     */
    fun detectAppFromText(text: String, context: Context? = null): KnownAppInfo? {
        if (text.isBlank()) return null
        val lower = text.lowercase(Locale.getDefault())

        // 1. Comprobar lista de aplicaciones populares
        for (app in POPULAR_APPS) {
            for (alias in app.aliases) {
                val regex = Regex("(?i)\\b" + Regex.escape(alias) + "\\b")
                if (regex.containsMatchIn(lower)) {
                    return app
                }
            }
        }

        // 2. Si se proporciona contexto, buscar en las apps instaladas del sistema
        if (context != null) {
            try {
                val pm = context.packageManager
                val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                for (appInfo in installed) {
                    // Filtrar apps del sistema puras si se desea, o permitir cualquiera
                    val label = pm.getApplicationLabel(appInfo).toString()
                    if (label.length >= 3 && lower.contains(label.lowercase(Locale.getDefault()))) {
                        return KnownAppInfo(
                            id = appInfo.packageName,
                            name = label,
                            packageName = appInfo.packageName,
                            aliases = listOf(label.lowercase(Locale.getDefault())),
                            emoji = "📱"
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return null
    }

    /**
     * Resuelve el nombre y emoji de una app a partir de su paquete.
     */
    fun getAppInfoForPackage(context: Context, packageName: String): Pair<String, String> {
        val known = POPULAR_APPS.firstOrNull { 
            it.packageName.equals(packageName, ignoreCase = true) || 
            (it.id == "whatsapp" && packageName.contains("whatsapp")) ||
            (it.id == "twitter" && packageName.contains("twitter")) ||
            (it.id == "telegram" && packageName.contains("telegram")) ||
            (it.id == "tiktok" && (packageName.contains("musically") || packageName.contains("trill")))
        }
        if (known != null) {
            return Pair(known.name, known.emoji)
        }

        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString()
            Pair(label.ifBlank { "App" }, "📱")
        } catch (e: Exception) {
            val fallback = packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }.ifBlank { "App" }
            Pair(fallback, "📱")
        }
    }

    /**
     * Verifica si una nota coincide con la app que está abierta en primer plano.
     */
    fun isNoteMatchingApp(
        noteTitle: String,
        noteContent: String,
        noteAppName: String?,
        notePackage: String?,
        foregroundPackage: String,
        foregroundLabel: String
    ): Boolean {
        if (foregroundPackage.isBlank()) return false

        // Coincidencia exacta o contenida por paquete
        if (!notePackage.isNullOrBlank() && 
            (notePackage.equals(foregroundPackage, ignoreCase = true) || 
             foregroundPackage.equals(notePackage, ignoreCase = true) ||
             foregroundPackage.contains(notePackage, ignoreCase = true))) {
            return true
        }

        // Si es WhatsApp (WhatsApp estándar y WhatsApp Business)
        if (foregroundPackage.contains("whatsapp") && 
            (noteAppName?.contains("whatsapp", ignoreCase = true) == true || 
             notePackage?.contains("whatsapp", ignoreCase = true) == true ||
             noteTitle.contains("whatsapp", ignoreCase = true) || 
             noteTitle.contains("wasap", ignoreCase = true) ||
             noteTitle.contains("wpp", ignoreCase = true) ||
             noteContent.contains("whatsapp", ignoreCase = true))) {
            return true
        }

        // Si es Instagram
        if (foregroundPackage.contains("instagram") && 
            (noteAppName?.contains("instagram", ignoreCase = true) == true || 
             notePackage?.contains("instagram", ignoreCase = true) == true ||
             noteTitle.contains("instagram", ignoreCase = true) || 
             noteTitle.contains("insta", ignoreCase = true) ||
             noteContent.contains("instagram", ignoreCase = true))) {
            return true
        }

        // Si es Telegram
        if (foregroundPackage.contains("telegram") && 
            (noteAppName?.contains("telegram", ignoreCase = true) == true || 
             notePackage?.contains("telegram", ignoreCase = true) == true ||
             noteTitle.contains("telegram", ignoreCase = true) || 
             noteContent.contains("telegram", ignoreCase = true))) {
            return true
        }

        // Si es YouTube
        if (foregroundPackage.contains("youtube") && 
            (noteAppName?.contains("youtube", ignoreCase = true) == true || 
             notePackage?.contains("youtube", ignoreCase = true) == true ||
             noteTitle.contains("youtube", ignoreCase = true) || 
             noteContent.contains("youtube", ignoreCase = true))) {
            return true
        }

        // Coincidencia por nombre de la app indicado explícitamente en la nota
        if (!noteAppName.isNullOrBlank()) {
            val targetLower = noteAppName.lowercase(Locale.getDefault())
            val fgLower = foregroundLabel.lowercase(Locale.getDefault())
            if (fgLower.contains(targetLower) || targetLower.contains(fgLower)) {
                return true
            }
            if (foregroundPackage.lowercase(Locale.getDefault()).contains(targetLower)) {
                return true
            }
        }

        // Coincidencia por mención del nombre de la app en el título o detalles de la nota
        val titleLower = noteTitle.lowercase(Locale.getDefault())
        val contentLower = noteContent.lowercase(Locale.getDefault())
        val fgLabelLower = foregroundLabel.lowercase(Locale.getDefault())

        if (fgLabelLower.length >= 3 && (titleLower.contains(fgLabelLower) || contentLower.contains(fgLabelLower))) {
            return true
        }

        // Comprobar si la app en primer plano coincide con alguna app conocida mencionada en la nota
        for (known in POPULAR_APPS) {
            if (known.packageName.equals(foregroundPackage, ignoreCase = true) ||
                (known.id == "whatsapp" && foregroundPackage.contains("whatsapp")) ||
                (known.id == "twitter" && foregroundPackage.contains("twitter")) ||
                (known.id == "telegram" && foregroundPackage.contains("telegram")) ||
                (known.id == "tiktok" && (foregroundPackage.contains("musically") || foregroundPackage.contains("trill")))) {
                for (alias in known.aliases) {
                    val regex = Regex("(?i)\\b" + Regex.escape(alias) + "\\b")
                    if (regex.containsMatchIn(titleLower) || regex.containsMatchIn(contentLower)) {
                        return true
                    }
                }
            }
        }

        return false
    }
}
