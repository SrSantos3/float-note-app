package com.antigravity.floatingnotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.floatingnotes.data.NoteEntity
import com.antigravity.floatingnotes.ui.theme.*

@Composable
fun EisenhowerMatrixView(
    notes: List<NoteEntity>,
    onToggleComplete: (NoteEntity) -> Unit,
    onEditNote: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val q1Notes = notes.filter { it.getEisenhowerQuadrant() == 1 }
    val q2Notes = notes.filter { it.getEisenhowerQuadrant() == 2 }
    val q3Notes = notes.filter { it.getEisenhowerQuadrant() == 3 }
    val q4Notes = notes.filter { it.getEisenhowerQuadrant() == 4 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Fila 1: Cuadrante 1 (Hacer Ya) y Cuadrante 2 (Planificar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EisenhowerQuadrantCard(
                title = "HACER YA",
                subtitle = "Urgente e Importante",
                count = q1Notes.size,
                accentColor = RoseAlert,
                notes = q1Notes,
                onToggleComplete = onToggleComplete,
                onEditNote = onEditNote,
                modifier = Modifier.weight(1f)
            )

            EisenhowerQuadrantCard(
                title = "PLANIFICAR",
                subtitle = "No urgente, pero Importante",
                count = q2Notes.size,
                accentColor = PrimaryIndigo,
                notes = q2Notes,
                onToggleComplete = onToggleComplete,
                onEditNote = onEditNote,
                modifier = Modifier.weight(1f)
            )
        }

        // Fila 2: Cuadrante 3 (Rápido) y Cuadrante 4 (Secundario)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EisenhowerQuadrantCard(
                title = "RÁPIDO",
                subtitle = "Urgente, No Importante",
                count = q3Notes.size,
                accentColor = AmberWarning,
                notes = q3Notes,
                onToggleComplete = onToggleComplete,
                onEditNote = onEditNote,
                modifier = Modifier.weight(1f)
            )

            EisenhowerQuadrantCard(
                title = "SECUNDARIO",
                subtitle = "Ni urgente ni Importante",
                count = q4Notes.size,
                accentColor = EmeraldSuccess,
                notes = q4Notes,
                onToggleComplete = onToggleComplete,
                onEditNote = onEditNote,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun EisenhowerQuadrantCard(
    title: String,
    subtitle: String,
    count: Int,
    accentColor: Color,
    notes: List<NoteEntity>,
    onToggleComplete: (NoteEntity) -> Unit,
    onEditNote: (NoteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accentColor.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = count.toString(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = DarkBorder.copy(alpha = 0.6f), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(6.dp))

            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sin tareas",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceElevated, RoundedCornerShape(8.dp))
                                .clickable { onEditNote(note) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = note.isCompleted,
                                onCheckedChange = { onToggleComplete(note) },
                                modifier = Modifier.size(20.dp),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = EmeraldSuccess,
                                    uncheckedColor = TextSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = note.title,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textDecoration = if (note.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                            )
                        }
                    }
                }
            }
        }
    }
}
