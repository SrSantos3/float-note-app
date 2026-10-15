package com.antigravity.floatingnotes.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.floatingnotes.data.NoteEntity
import com.antigravity.floatingnotes.ui.components.AddEditNoteDialog
import com.antigravity.floatingnotes.ui.components.NoteItemCard
import com.antigravity.floatingnotes.ui.theme.*
import com.antigravity.floatingnotes.ui.viewmodel.NotesViewModel

@Composable
fun NotesListScreen(
    viewModel: NotesViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeNotes by viewModel.activeNotes.collectAsState()
    val completedNotes by viewModel.completedNotes.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState()
    val isBubbleEnabled by viewModel.isBubbleServiceEnabled.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Pendientes (${activeNotes.size})", "Completadas (${completedNotes.size})", "Todas")

    var showAddDialog by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<NoteEntity?>(null) }

    val currentNotes = when (selectedTabIndex) {
        0 -> activeNotes
        1 -> completedNotes
        else -> allNotes
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    noteToEdit = null
                    showAddDialog = true
                },
                containerColor = PrimaryIndigo,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Nueva nota"
                )
            }
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Banner de control de la Burbuja Flotante tipo Messenger
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = DarkSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(PrimaryIndigo, SecondaryPink)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BubbleChart,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Burbuja Flotante en Pantalla",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = if (isBubbleEnabled) "Activa sobre cualquier app" else "Toca el interruptor para activarla",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isBubbleEnabled) EmeraldSuccess else TextSecondary
                            )
                        }
                    }

                    Switch(
                        checked = isBubbleEnabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                if (!Settings.canDrawOverlays(context)) {
                                    // Solicitar permiso de superposición
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    viewModel.setBubbleServiceEnabled(true)
                                }
                            } else {
                                viewModel.setBubbleServiceEnabled(false)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PrimaryIndigo,
                            checkedTrackColor = PrimaryDark
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Pestañas (Tabs)
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = DarkBackground,
                contentColor = PrimaryIndigo,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                color = if (selectedTabIndex == index) PrimaryIndigo else TextSecondary
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lista de Notas
            if (currentNotes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (selectedTabIndex == 0) "🎉 ¡Estás al día!" else "No hay notas aquí",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (selectedTabIndex == 0) "Pulsa el botón + para añadir una nota o tarea." else "Las notas que marques se listarán aquí.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(currentNotes, key = { it.id }) { note ->
                        NoteItemCard(
                            note = note,
                            onToggleComplete = { viewModel.toggleComplete(note) },
                            onEdit = {
                                noteToEdit = note
                                showAddDialog = true
                            },
                            onDelete = { viewModel.deleteNote(note) }
                        )
                    }
                }
            }
        }
    }

    // Diálogo de añadir o editar
    if (showAddDialog) {
        AddEditNoteDialog(
            initialNote = noteToEdit,
            onDismiss = {
                showAddDialog = false
                noteToEdit = null
            },
            onSave = { title, content, targetDate, targetTime, repeatInterval, isPinned ->
                if (noteToEdit != null) {
                    viewModel.updateNote(
                        noteToEdit!!.copy(
                            title = title,
                            content = content,
                            targetDate = targetDate,
                            targetTime = targetTime,
                            repeatIntervalMinutes = repeatInterval,
                            isPinnedToOverlay = isPinned
                        )
                    )
                } else {
                    viewModel.addNote(
                        title = title,
                        content = content,
                        targetDate = targetDate,
                        targetTime = targetTime,
                        repeatIntervalMinutes = repeatInterval,
                        isPinnedToOverlay = isPinned
                    )
                }
            }
        )
    }
}
