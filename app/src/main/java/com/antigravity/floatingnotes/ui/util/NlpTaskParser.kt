package com.antigravity.floatingnotes.ui.util

import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

data class ParsedTaskInfo(
    val cleanTitle: String,
    val targetDate: Long? = null,
    val targetTime: Long? = null,
    val repeatIntervalMinutes: Int = 0,
    val isUrgent: Boolean = false,
    val detectedCategory: String? = null,
    val summaryPreview: String? = null,
    val detectedAppName: String? = null,
    val detectedAppPackage: String? = null
)

object NlpTaskParser {

    fun parse(rawText: String): ParsedTaskInfo {
        if (rawText.isBlank()) return ParsedTaskInfo(rawText)

        val textLower = rawText.lowercase(Locale.getDefault())
        var cleanTitle = rawText

        var targetDate: Long? = null
        var targetTime: Long? = null
        var repeatMinutes = 0
        var isUrgent = false
        var category: String? = null
        val detectedTags = mutableListOf<String>()

        val now = Calendar.getInstance()

        // 1. Detectar Urgencia
        if (textLower.contains("urgente") || textLower.contains("prioritario") || textLower.contains("importante") || textLower.contains("asap")) {
            isUrgent = true
            detectedTags.add("🚨 Urgente")
            cleanTitle = cleanTitle.replace(Regex("(?i)\\b(urgente|prioritario|importante|asap)\\b"), "").trim()
        }

        // 1.1 Detectar Aplicación Vinculada (WhatsApp, Instagram, etc.)
        val detectedApp = AppHelper.detectAppFromText(rawText)
        var appName: String? = null
        var appPackage: String? = null
        if (detectedApp != null) {
            appName = detectedApp.name
            appPackage = detectedApp.packageName
            detectedTags.add("${detectedApp.emoji} ${detectedApp.name}")
        }

        // 2. Detectar Categorías por palabras clave
        when {
            textLower.contains("comprar") || textLower.contains("compra") || textLower.contains("supermercado") || textLower.contains("farmacia") -> {
                category = "Compras"
                detectedTags.add("🛒 Compras")
            }
            textLower.contains("trabajo") || textLower.contains("reunion") || textLower.contains("reunión") || textLower.contains("cliente") || textLower.contains("informe") -> {
                category = "Trabajo"
                detectedTags.add("💼 Trabajo")
            }
            textLower.contains("idea") || textLower.contains("proyecto") -> {
                category = "Ideas"
                detectedTags.add("💡 Ideas")
            }
        }

        // 3. Detectar Fecha (hoy, mañana, pasado mañana, días de la semana)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when {
            textLower.contains("pasado mañana") -> {
                cal.add(Calendar.DAY_OF_YEAR, 2)
                targetDate = cal.timeInMillis
                detectedTags.add("📅 Pasado mañana")
                cleanTitle = cleanTitle.replace(Regex("(?i)\\bpasado mañana\\b"), "").trim()
            }
            textLower.contains("mañana") -> {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                targetDate = cal.timeInMillis
                detectedTags.add("📅 Mañana")
                cleanTitle = cleanTitle.replace(Regex("(?i)\\bmañana\\b"), "").trim()
            }
            textLower.contains("hoy") -> {
                targetDate = cal.timeInMillis
                detectedTags.add("📅 Hoy")
                cleanTitle = cleanTitle.replace(Regex("(?i)\\bhoy\\b"), "").trim()
            }
            else -> {
                val daysMap = mapOf(
                    "lunes" to Calendar.MONDAY,
                    "martes" to Calendar.TUESDAY,
                    "miercoles" to Calendar.WEDNESDAY,
                    "miércoles" to Calendar.WEDNESDAY,
                    "jueves" to Calendar.THURSDAY,
                    "viernes" to Calendar.FRIDAY,
                    "sabado" to Calendar.SATURDAY,
                    "sábado" to Calendar.SATURDAY,
                    "domingo" to Calendar.SUNDAY
                )
                for ((dayName, dayConstant) in daysMap) {
                    if (textLower.contains(dayName)) {
                        val currentDay = cal.get(Calendar.DAY_OF_WEEK)
                        var daysToAdd = (dayConstant - currentDay + 7) % 7
                        if (daysToAdd == 0) daysToAdd = 7
                        cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
                        targetDate = cal.timeInMillis
                        detectedTags.add("📅 El $dayName")
                        cleanTitle = cleanTitle.replace(Regex("(?i)\\b(el )?$dayName\\b"), "").trim()
                        break
                    }
                }
            }
        }

        // 4. Detectar Hora exacta (ej. "a las 17:00", "a las 5pm", "a las 6 de la tarde", "a las 9")
        val timeRegex = Pattern.compile("(?i)a las (\\d{1,2})(:(\\d{2}))?\\s*(am|pm|de la mañana|de la tarde|de la noche)?")
        val timeMatcher = timeRegex.matcher(textLower)
        if (timeMatcher.find()) {
            var hour = timeMatcher.group(1)?.toIntOrNull() ?: 9
            val minute = timeMatcher.group(3)?.toIntOrNull() ?: 0
            val modifier = timeMatcher.group(4)?.lowercase() ?: ""

            if ((modifier == "pm" || modifier.contains("tarde") || modifier.contains("noche")) && hour < 12) {
                hour += 12
            } else if ((modifier == "am" || modifier.contains("mañana")) && hour == 12) {
                hour = 0
            }

            val timeCal = Calendar.getInstance().apply {
                if (targetDate != null) timeInMillis = targetDate
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis && targetDate == null) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            targetTime = timeCal.timeInMillis
            detectedTags.add(String.format(Locale.getDefault(), "⏰ %02d:%02d", hour, minute))
            cleanTitle = cleanTitle.replace(timeRegex.toRegex(), "").trim()
        }

        // 5. Detectar Recurrencia / Intervalo periódico (ej. "cada 15 min", "cada hora", "cada 2 horas", "cada 30 minutos")
        val intervalRegex = Pattern.compile("(?i)cada (\\d+)?\\s*(minuto|minutos|min|m|hora|horas|h)")
        val intervalMatcher = intervalRegex.matcher(textLower)
        if (intervalMatcher.find()) {
            val amount = intervalMatcher.group(1)?.toIntOrNull() ?: 1
            val unit = intervalMatcher.group(2)?.lowercase() ?: ""

            repeatMinutes = if (unit.startsWith("h")) {
                amount * 60
            } else {
                amount
            }
            detectedTags.add("⏱ Cada $repeatMinutes min")
            cleanTitle = cleanTitle.replace(intervalRegex.toRegex(), "").trim()
        }

        // Limpiar título de palabras sueltas como "para", "a las", comas extras
        cleanTitle = cleanTitle
            .replace(Regex("(?i)\\b(para)\\s*$"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

        val summary = if (detectedTags.isNotEmpty()) detectedTags.joinToString(" • ") else null

        return ParsedTaskInfo(
            cleanTitle = cleanTitle.ifBlank { rawText },
            targetDate = targetDate,
            targetTime = targetTime,
            repeatIntervalMinutes = repeatMinutes,
            isUrgent = isUrgent,
            detectedCategory = category,
            summaryPreview = summary,
            detectedAppName = appName,
            detectedAppPackage = appPackage
        )
    }
}
