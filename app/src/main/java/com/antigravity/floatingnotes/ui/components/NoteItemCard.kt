package com.antigravity.floatingnotes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.floatingnotes.data.NoteEntity
import com.antigravity.floatingnotes.data.SubTask
import com.antigravity.floatingnotes.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NoteItemCard(
    note: NoteEntity,
    isUnlocked: Boolean = false,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUnlockRequest: () -> Unit = {},
    onToggleSubtask: (subtaskId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isSubtasksExpanded by remember { mutableStateOf(false) }
    val subtasks = note.getSubtasks()
    val (doneCount, totalCount) = note.getSubtaskProgress()

    val noteColor = try {
        Color(android.graphics.Color.parseColor(note.colorHex))
    } catch (e: Exception) {
        PrimaryIndigo
    }

    val isHidden = note.isLocked && !isUnlocked

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.isCompleted) DarkSurface.copy(alpha = 0.6f) else DarkSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (note.isCompleted) 1.dp else 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Franja lateral con el color de la categoría (rojo si es alarma urgente)
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(
                        when {
                            note.isCompleted -> noteColor.copy(alpha = 0.4f)
                            note.isUrgentAlarm -> RoseAlert
                            else -> noteColor
                        }
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = note.isCompleted,
                        onCheckedChange = { onToggleComplete() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = EmeraldSuccess,
                            uncheckedColor = TextSecondary,
                            checkmarkColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (isHidden) Modifier.clickable { onUnlockRequest() } else Modifier
                            )
                    ) {
                        if (isHidden) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Bloqueada",
                                    tint = AmberWarning,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Nota protegida con huella",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AmberWarning
                                )
                            }
                            Text(
                                text = "Toca para desbloquear y ver contenido",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (note.isUrgentAlarm && !note.isCompleted) {
                                    Text("🚨 ", fontSize = 14.sp)
                                }
                                if (note.isLocked) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = "Desbloqueada",
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = note.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (note.isCompleted) TextMuted else TextPrimary,
                                    textDecoration = if (note.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (note.content.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = note.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (note.isCompleted) TextMuted else TextSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            if (isHidden) onUnlockRequest() else onEdit()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isHidden) Icons.Default.Fingerprint else Icons.Default.Edit,
                            contentDescription = if (isHidden) "Desbloquear" else "Editar",
                            tint = if (isHidden) AmberWarning else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Eliminar",
                            tint = RoseAlert.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // ==========================================
                // 📋 SECCIÓN SUBTAREAS / CHECKLIST CON PROGRESO
                // ==========================================
                if (!isHidden && totalCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 36.dp)
                            .clickable { isSubtasksExpanded = !isSubtasksExpanded },
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurfaceElevated
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Subtareas: $doneCount/$totalCount (${if (totalCount > 0) (doneCount * 100 / totalCount) else 0}%)",
                                    fontSize = 11.sp,
                                    color = if (doneCount == totalCount) EmeraldSuccess else TextSecondary
                                )
                                Icon(
                                    imageVector = if (isSubtasksExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            LinearProgressIndicator(
                                progress = if (totalCount > 0) doneCount.toFloat() / totalCount else 0f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = if (doneCount == totalCount) EmeraldSuccess else PrimaryIndigo,
                                trackColor = DarkBorder
                            )

                            // Lista desplegable de subtareas interactivas
                            AnimatedVisibility(visible = isSubtasksExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    subtasks.forEach { subtask ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onToggleSubtask(subtask.id) },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Checkbox(
                                                checked = subtask.isDone,
                                                onCheckedChange = { onToggleSubtask(subtask.id) },
                                                modifier = Modifier.size(24.dp),
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = EmeraldSuccess,
                                                    uncheckedColor = TextSecondary
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = subtask.title,
                                                fontSize = 12.sp,
                                                color = if (subtask.isDone) TextMuted else TextPrimary,
                                                textDecoration = if (subtask.isDone) TextDecoration.LineThrough else TextDecoration.None
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Badges y metadata
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Badge de Categoría
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = noteColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = note.category,
                            fontSize = 11.sp,
                            color = noteColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    // Badge de Alarma Insistente
                    if (note.isUrgentAlarm && !note.isCompleted) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RoseAlert.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "🚨 Insistente",
                                fontSize = 11.sp,
                                color = RoseAlert,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Badge Anti-Procrastinación
                    if (note.snoozeCount > 0 && !note.isCompleted) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AmberWarning.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "👀 Pospuesta ${note.snoozeCount}x",
                                fontSize = 11.sp,
                                color = AmberWarning,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Badge de repetición periódica
                    if (note.repeatIntervalMinutes > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PrimaryIndigo.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "⏱ Cada ${note.repeatIntervalMinutes}m",
                                fontSize = 11.sp,
                                color = PrimaryIndigo,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Badge de fecha en el calendario
                    if (note.targetDate != null) {
                        val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
                        val formattedDate = dateFormat.format(Date(note.targetDate))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyanAccent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "📅 $formattedDate",
                                fontSize = 11.sp,
                                color = CyanAccent,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Badge de App Vinculada (WhatsApp, Instagram, etc.)
                    if (!note.targetAppName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = EmeraldSuccess.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "📱 ${note.targetAppName}",
                                fontSize = 11.sp,
                                color = EmeraldSuccess,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Badge de Tono de Alarma Personalizado
                    if (!note.alarmToneName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SecondaryPink.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "🔔 ${note.alarmToneName}",
                                fontSize = 11.sp,
                                color = SecondaryPink,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Badge de Ubicación / Wi-Fi
                    if (!note.locationName.isNullOrBlank() || !note.wifiSsid.isNullOrBlank()) {
                        val locLabel = listOfNotNull(note.locationName, note.wifiSsid?.let { "📶 $it" }).joinToString(" • ")
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AmberWarning.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "📍 $locLabel",
                                fontSize = 11.sp,
                                color = AmberWarning,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
