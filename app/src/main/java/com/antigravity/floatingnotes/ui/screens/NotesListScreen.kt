package com.antigravity.floatingnotes.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.antigravity.floatingnotes.data.NoteEntity
import com.antigravity.floatingnotes.ui.components.AddEditNoteDialog
import com.antigravity.floatingnotes.ui.components.EisenhowerMatrixView
import com.antigravity.floatingnotes.ui.components.NoteItemCard
import com.antigravity.floatingnotes.ui.theme.*
import com.antigravity.floatingnotes.ui.util.BiometricHelper
import com.antigravity.floatingnotes.ui.viewmodel.NotesViewModel

@Composable
fun NotesListScreen(
    viewModel: NotesViewModel,
    activity: FragmentActivity,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val filteredActiveNotes by viewModel.filteredActiveNotes.collectAsState()
    val activeNotes by viewModel.activeNotes.collectAsState()
    val completedNotes by viewModel.completedNotes.collectAsState()
    val allNotes by viewModel.allNotes.collectAsState()
    val isBubbleEnabled by viewModel.isBubbleServiceEnabled.collectAsState()
    val categories by viewModel.availableCategories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedAppFilter by viewModel.selectedAppFilter.collectAsState()

    var unlockedNoteIds by remember { mutableStateOf(setOf<Long>()) }

    var isEisenhowerMode by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val appsWithNotes by remember(allNotes) {
        derivedStateOf {
            allNotes.mapNotNull { note ->
                val appName = note.targetAppName
                if (!appName.isNullOrBlank()) {
                    Pair(note.targetAppPackage ?: "", appName)
                } else null
            }.distinctBy { it.second }
        }
    }

    val currentNotes = when (selectedTabIndex) {
        0 -> filteredActiveNotes
        1 -> {
            var list = if (selectedCategory == null) completedNotes else completedNotes.filter { it.category.equals(selectedCategory, true) }
            if (selectedAppFilter != null) {
                list = list.filter { it.matchesForegroundApp(selectedAppFilter!!.first, selectedAppFilter!!.second) }
            }
            list
        }
        else -> {
            var list = if (selectedCategory == null) allNotes else allNotes.filter { it.category.equals(selectedCategory, true) }
            if (selectedAppFilter != null) {
                list = list.filter { it.matchesForegroundApp(selectedAppFilter!!.first, selectedAppFilter!!.second) }
            }
            list
        }
    }

    val tabs = listOf("Pendientes (${filteredActiveNotes.size})", "Completadas (${completedNotes.size})", "Todas")

    var showAddDialog by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<NoteEntity?>(null) }

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

            // Banner si hay un filtro de aplicación activo
            if (selectedAppFilter != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = DarkSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "📱 Modo ${selectedAppFilter!!.second} (${currentNotes.size} tareas)",
                            color = CyanAccent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { viewModel.setAppFilter(null, null) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Ver todas", color = AmberWarning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Filtro por Categorías / Etiquetas y Aplicaciones (Horizontal Scroll)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedCategory == null && selectedAppFilter == null,
                    onClick = {
                        viewModel.setFilterCategory(null)
                        viewModel.setAppFilter(null, null)
                    },
                    label = { Text("Todas", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryIndigo,
                        selectedLabelColor = TextPrimary
                    )
                )

                // Chips para aplicaciones detectadas en notas
                appsWithNotes.forEach { (pkg, name) ->
                    val isAppSelected = selectedAppFilter?.second.equals(name, ignoreCase = true)
                    FilterChip(
                        selected = isAppSelected,
                        onClick = {
                            if (isAppSelected) viewModel.setAppFilter(null, null)
                            else viewModel.setAppFilter(pkg, name)
                        },
                        label = { Text("📱 $name", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldSuccess,
                            selectedLabelColor = DarkBackground
                        )
                    )
                }

                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = {
                            if (selectedCategory == cat) viewModel.setFilterCategory(null)
                            else viewModel.setFilterCategory(cat)
                        },
                        label = { Text(cat, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanAccent,
                            selectedLabelColor = DarkBackground
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Pestañas (Tabs) con Botón 1-Clic para Matriz de Eisenhower
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = DarkBackground,
                    contentColor = PrimaryIndigo,
                    divider = {},
                    modifier = Modifier.weight(1f)
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    color = if (selectedTabIndex == index) PrimaryIndigo else TextSecondary
                                )
                            }
                        )
                    }
                }

                IconButton(
                    onClick = { isEisenhowerMode = !isEisenhowerMode },
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isEisenhowerMode) PrimaryIndigo.copy(alpha = 0.25f) else DarkSurface)
                ) {
                    Icon(
                        imageVector = if (isEisenhowerMode) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Alternar Matriz de Eisenhower",
                        tint = if (isEisenhowerMode) CyanAccent else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Vista de Notas (Matriz de Eisenhower o Lista normal)
            if (isEisenhowerMode) {
                EisenhowerMatrixView(
                    notes = currentNotes,
                    onToggleComplete = { viewModel.toggleComplete(it) },
                    onEditNote = { note ->
                        if (note.isLocked && !unlockedNoteIds.contains(note.id)) {
                            BiometricHelper.showBiometricPrompt(
                                activity = activity,
                                title = "Editar Nota Protegida",
                                onSuccess = {
                                    unlockedNoteIds = unlockedNoteIds + note.id
                                    noteToEdit = note
                                    showAddDialog = true
                                }
                            )
                        } else {
                            noteToEdit = note
                            showAddDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 72.dp)
                )
            } else if (currentNotes.isEmpty()) {
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
                        val isUnlocked = unlockedNoteIds.contains(note.id)
                        NoteItemCard(
                            note = note,
                            isUnlocked = isUnlocked,
                            onToggleComplete = { viewModel.toggleComplete(note) },
                            onEdit = {
                                if (note.isLocked && !isUnlocked) {
                                    BiometricHelper.showBiometricPrompt(
                                        activity = activity,
                                        title = "Editar Nota Protegida",
                                        onSuccess = {
                                            unlockedNoteIds = unlockedNoteIds + note.id
                                            noteToEdit = note
                                            showAddDialog = true
                                        }
                                    )
                                } else {
                                    noteToEdit = note
                                    showAddDialog = true
                                }
                            },
                            onDelete = { viewModel.deleteNote(note) },
                            onUnlockRequest = {
                                BiometricHelper.showBiometricPrompt(
                                    activity = activity,
                                    title = "Desbloquear: ${note.title}",
                                    onSuccess = {
                                        unlockedNoteIds = unlockedNoteIds + note.id
                                    }
                                )
                            },
                            onToggleSubtask = { subtaskId ->
                                viewModel.toggleSubtask(note, subtaskId)
                            }
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
            onSave = { title, content, targetDate, targetTime, repeatInterval, isPinned, category, colorHex, isUrgentAlarm, snoozeCount, isLocked, subtasksJson, locationName, wifiSsid, alarmToneUri, alarmToneName, targetAppName, targetAppPackage ->
                if (noteToEdit != null) {
                    viewModel.updateNote(
                        noteToEdit!!.copy(
                            title = title,
                            content = content,
                            targetDate = targetDate,
                            targetTime = targetTime,
                            repeatIntervalMinutes = repeatInterval,
                            isPinnedToOverlay = isPinned,
                            category = category,
                            colorHex = colorHex,
                            isUrgentAlarm = isUrgentAlarm,
                            snoozeCount = snoozeCount,
                            isLocked = isLocked,
                            subtasksJson = subtasksJson,
                            locationName = locationName,
                            wifiSsid = wifiSsid,
                            alarmToneUri = alarmToneUri,
                            alarmToneName = alarmToneName,
                            targetAppName = targetAppName,
                            targetAppPackage = targetAppPackage
                        )
                    )
                } else {
                    viewModel.addNote(
                        title = title,
                        content = content,
                        targetDate = targetDate,
                        targetTime = targetTime,
                        repeatIntervalMinutes = repeatInterval,
                        isPinnedToOverlay = isPinned,
                        category = category,
                        colorHex = colorHex,
                        isUrgentAlarm = isUrgentAlarm,
                        snoozeCount = snoozeCount,
                        isLocked = isLocked,
                        subtasksJson = subtasksJson,
                        locationName = locationName,
                        wifiSsid = wifiSsid,
                        alarmToneUri = alarmToneUri,
                        alarmToneName = alarmToneName,
                        targetAppName = targetAppName,
                        targetAppPackage = targetAppPackage
                    )
                }
            }
        )
    }
}
