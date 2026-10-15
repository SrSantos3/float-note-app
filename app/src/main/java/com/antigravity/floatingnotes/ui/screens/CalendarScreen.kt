package com.antigravity.floatingnotes.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.floatingnotes.data.NoteEntity
import com.antigravity.floatingnotes.ui.components.AddEditNoteDialog
import com.antigravity.floatingnotes.ui.components.NoteItemCard
import com.antigravity.floatingnotes.ui.theme.*
import com.antigravity.floatingnotes.ui.viewmodel.NotesViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CalendarScreen(
    viewModel: NotesViewModel,
    modifier: Modifier = Modifier
) {
    val selectedDateMillis by viewModel.selectedCalendarDate.collectAsState()
    val notesForSelectedDate by viewModel.notesForSelectedDate.collectAsState()
    val distinctDatesWithNotes by viewModel.distinctDatesWithNotes.collectAsState()

    var calendarMonth by remember {
        mutableStateOf(Calendar.getInstance().apply { timeInMillis = selectedDateMillis })
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<NoteEntity?>(null) }

    val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val selectedDayFormat = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale.getDefault())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Cabecera del Mes y Navegación
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val newCal = calendarMonth.clone() as Calendar
                        newCal.add(Calendar.MONTH, -1)
                        calendarMonth = newCal
                    }) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Mes anterior",
                            tint = TextPrimary
                        )
                    }

                    Text(
                        text = monthYearFormat.format(calendarMonth.time).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = {
                        val newCal = calendarMonth.clone() as Calendar
                        newCal.add(Calendar.MONTH, 1)
                        calendarMonth = newCal
                    }) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Mes siguiente",
                            tint = TextPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Días de la semana
                val daysOfWeek = listOf("L", "M", "X", "J", "V", "S", "D")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    daysOfWeek.forEach { dayName ->
                        Text(
                            text = dayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Grid interactivo de días del mes
                CalendarMonthGrid(
                    displayedMonth = calendarMonth,
                    selectedDateMillis = selectedDateMillis,
                    datesWithNotes = distinctDatesWithNotes,
                    onDaySelected = { dateMillis ->
                        viewModel.selectCalendarDate(dateMillis)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Encabezado de la lista para el día seleccionado
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = selectedDayFormat.format(Date(selectedDateMillis)).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "${notesForSelectedDate.size} notas o tareas programadas",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            IconButton(
                onClick = {
                    noteToEdit = null
                    showAddDialog = true
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(PrimaryIndigo)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Añadir a esta fecha",
                    tint = TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Lista de notas de este día
        if (notesForSelectedDate.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "📅 Nada programado para este día",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Toca el botón + para asignar una tarea a esta fecha.",
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
                items(notesForSelectedDate, key = { it.id }) { note ->
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

    if (showAddDialog) {
        AddEditNoteDialog(
            initialNote = noteToEdit,
            defaultDate = selectedDateMillis,
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
                            targetDate = targetDate ?: selectedDateMillis,
                            targetTime = targetTime,
                            repeatIntervalMinutes = repeatInterval,
                            isPinnedToOverlay = isPinned
                        )
                    )
                } else {
                    viewModel.addNote(
                        title = title,
                        content = content,
                        targetDate = targetDate ?: selectedDateMillis,
                        targetTime = targetTime,
                        repeatIntervalMinutes = repeatInterval,
                        isPinnedToOverlay = isPinned
                    )
                }
            }
        )
    }
}

@Composable
fun CalendarMonthGrid(
    displayedMonth: Calendar,
    selectedDateMillis: Long,
    datesWithNotes: List<Long>,
    onDaySelected: (Long) -> Unit
) {
    val cal = displayedMonth.clone() as Calendar
    cal.set(Calendar.DAY_OF_MONTH, 1)

    // Ajuste para que la semana empiece en Lunes (Calendar.MONDAY = 2)
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val emptySlotsBefore = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - Calendar.MONDAY

    val maxDaysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val todayCal = Calendar.getInstance()
    val isCurrentMonth = todayCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
            todayCal.get(Calendar.MONTH) == cal.get(Calendar.MONTH)
    val todayDayNumber = if (isCurrentMonth) todayCal.get(Calendar.DAY_OF_MONTH) else -1

    val selectedCal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
    val isSelectedMonth = selectedCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
            selectedCal.get(Calendar.MONTH) == cal.get(Calendar.MONTH)
    val selectedDayNumber = if (isSelectedMonth) selectedCal.get(Calendar.DAY_OF_MONTH) else -1

    val totalCells = emptySlotsBefore + maxDaysInMonth
    val rows = (totalCells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - emptySlotsBefore + 1

                    if (dayNumber in 1..maxDaysInMonth) {
                        val dayCal = cal.clone() as Calendar
                        dayCal.set(Calendar.DAY_OF_MONTH, dayNumber)
                        val dayStartMillis = NotesViewModel.getStartOfDay(dayCal.timeInMillis)

                        val isSelected = dayNumber == selectedDayNumber
                        val isToday = dayNumber == todayDayNumber
                        val hasNotes = datesWithNotes.any {
                            NotesViewModel.getStartOfDay(it) == dayStartMillis
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> PrimaryIndigo
                                        else -> Color.Transparent
                                    }
                                )
                                .then(
                                    if (isToday && !isSelected) Modifier.border(1.dp, CyanAccent, CircleShape) else Modifier
                                )
                                .clickable {
                                    onDaySelected(dayStartMillis)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = dayNumber.toString(),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        isSelected -> TextPrimary
                                        isToday -> CyanAccent
                                        else -> TextPrimary
                                    }
                                )

                                if (hasNotes) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) TextPrimary else SecondaryPink)
                                    )
                                }
                            }
                        }
                    } else {
                        // Celda vacía
                        Spacer(modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }
}
