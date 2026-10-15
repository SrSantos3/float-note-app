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
    val priority: Int = 1
)
