package com.antigravity.floatingnotes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String = "",
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    
    // Fecha asignada en el calendario (milisegundos al inicio del día)
    val targetDate: Long? = null,
    
    // Hora exacta programada (milisegundos) para la primera alarma
    val targetTime: Long? = null,
    
    // Intervalo de repetición: cada X minutos (0 = sin repetición, ej. 15, 30, 60, etc.)
    val repeatIntervalMinutes: Int = 0,
    
    // Si se muestra de forma destacada en el popup flotante
    val isPinnedToOverlay: Boolean = true,
    
    // Prioridad: 1 = Baja/Normal, 2 = Media, 3 = Alta
    val priority: Int = 1,

    // Categoría / Etiqueta (ej. "General", "Trabajo", "Personal", "Urgente", "Compras", "Ideas")
    val category: String = "General",

    // Color temático en formato Hexadecimal (ej. "#6366F1", "#10B981", "#EF4444", etc.)
    val colorHex: String = "#6366F1",

    // ANTI-PROCRASTINACIÓN: Alarma insistente con tono de alarma fuerte y vibración especial
    val isUrgentAlarm: Boolean = false,

    // ANTI-PROCRASTINACIÓN: Contador de veces que se ha pospuesto o ignorado la tarea
    val snoozeCount: Int = 0,

    // SEGURIDAD: Bloqueo biométrico con huella dactilar
    val isLocked: Boolean = false,

    // SUBTAREAS: Lista de subtareas en formato JSON
    val subtasksJson: String = "[]",

    // UBICACIÓN / WI-FI: Nombre de ubicación o etiqueta (ej. "Casa", "Oficina", "Supermercado")
    val locationName: String? = null,

    // UBICACIÓN / WI-FI: Nombre de red Wi-Fi para disparar recordatorio al conectar
    val wifiSsid: String? = null,

    // TONO DE ALARMA: Tono personalizado para esta nota (URI string y nombre descriptivo)
    val alarmToneUri: String? = null,
    val alarmToneName: String? = null,

    // DETECCIÓN INTELIGENTE DE APP: Nombre y paquete de app asociada (ej. WhatsApp, Instagram)
    val targetAppName: String? = null,
    val targetAppPackage: String? = null
) {
    fun matchesForegroundApp(packageName: String, appLabel: String): Boolean {
        return com.antigravity.floatingnotes.ui.util.AppHelper.isNoteMatchingApp(
            noteTitle = title,
            noteContent = content,
            noteAppName = targetAppName,
            notePackage = targetAppPackage,
            foregroundPackage = packageName,
            foregroundLabel = appLabel
        )
    }

    fun getSubtasks(): List<SubTask> = SubTask.parseJsonList(subtasksJson)

    fun withSubtasks(list: List<SubTask>): NoteEntity = copy(subtasksJson = SubTask.toJsonList(list))

    fun getSubtaskProgress(): Pair<Int, Int> {
        val list = getSubtasks()
        if (list.isEmpty()) return Pair(0, 0)
        val done = list.count { it.isDone }
        return Pair(done, list.size)
    }

    // Matriz de Eisenhower (Cuadrante 1 a 4)
    // Q1: Urgente e Importante (Hacer YA)
    // Q2: Importante, No Urgente (Planificar)
    // Q3: Urgente, No Importante (Delegar / Rápido)
    // Q4: Ni Urgente ni Importante (Secundario)
    fun getEisenhowerQuadrant(): Int {
        val isUrgent = isUrgentAlarm || repeatIntervalMinutes > 0 || (targetDate != null && targetDate <= System.currentTimeMillis() + (24 * 60 * 60 * 1000L))
        val isImportant = priority >= 2 || category.equals("Trabajo", ignoreCase = true) || category.equals("Urgente", ignoreCase = true)

        return when {
            isUrgent && isImportant -> 1
            !isUrgent && isImportant -> 2
            isUrgent && !isImportant -> 3
            else -> 4
        }
    }
}
