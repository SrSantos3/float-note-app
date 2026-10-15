package com.antigravity.floatingnotes.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.floatingnotes.data.NoteEntity
import com.antigravity.floatingnotes.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NoteItemCard(
    note: NoteEntity,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.isCompleted) DarkSurface.copy(alpha = 0.6f) else DarkSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (note.isCompleted) 1.dp else 3.dp)
    ) {
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
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (note.isCompleted) TextMuted else TextPrimary,
                        textDecoration = if (note.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

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

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
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

            // Badges y metadata
            if (note.repeatIntervalMinutes > 0 || note.targetDate != null || note.isPinnedToOverlay) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 42.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Badge de repetición periódica
                    if (note.repeatIntervalMinutes > 0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PrimaryIndigo.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = PrimaryIndigo,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Cada ${note.repeatIntervalMinutes} min",
                                    fontSize = 11.sp,
                                    color = PrimaryIndigo
                                )
                            }
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
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Today,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formattedDate,
                                    fontSize = 11.sp,
                                    color = CyanAccent
                                )
                            }
                        }
                    }

                    // Badge de burbuja flotante
                    if (note.isPinnedToOverlay) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SecondaryPink.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Burbuja",
                                fontSize = 11.sp,
                                color = SecondaryPink,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
