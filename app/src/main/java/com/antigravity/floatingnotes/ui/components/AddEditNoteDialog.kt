package com.antigravity.floatingnotes.ui.components

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.antigravity.floatingnotes.data.NoteEntity
import com.antigravity.floatingnotes.data.SubTask
import com.antigravity.floatingnotes.ui.theme.*
import com.antigravity.floatingnotes.ui.util.AlarmToneHelper
import com.antigravity.floatingnotes.ui.util.AppHelper
import com.antigravity.floatingnotes.ui.util.NlpTaskParser
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditNoteDialog(
    initialNote: NoteEntity? = null,
    defaultDate: Long? = null,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        content: String,
        targetDate: Long?,
        targetTime: Long?,
        repeatInterval: Int,
        isPinned: Boolean,
        category: String,
        colorHex: String,
        isUrgentAlarm: Boolean,
        snoozeCount: Int,
        isLocked: Boolean,
        subtasksJson: String,
        locationName: String?,
        wifiSsid: String?,
        alarmToneUri: String?,
        alarmToneName: String?,
        targetAppName: String?,
        targetAppPackage: String?
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var content by remember { mutableStateOf(initialNote?.content ?: "") }
    var repeatInterval by remember { mutableIntStateOf(initialNote?.repeatIntervalMinutes ?: 0) }
    var targetDate by remember { mutableStateOf(initialNote?.targetDate ?: defaultDate) }
    var isPinned by remember { mutableStateOf(initialNote?.isPinnedToOverlay ?: true) }
    var category by remember { mutableStateOf(initialNote?.category ?: "General") }
    var colorHex by remember { mutableStateOf(initialNote?.colorHex ?: "#6366F1") }
    var isUrgentAlarm by remember { mutableStateOf(initialNote?.isUrgentAlarm ?: false) }
    var snoozeCount by remember { mutableIntStateOf(initialNote?.snoozeCount ?: 0) }
    var isLocked by remember { mutableStateOf(initialNote?.isLocked ?: false) }
    var locationName by remember { mutableStateOf(initialNote?.locationName ?: "") }
    var wifiSsid by remember { mutableStateOf(initialNote?.wifiSsid ?: "") }

    // Tono de Alarma
    var alarmToneUri by remember { mutableStateOf(initialNote?.alarmToneUri) }
    var alarmToneName by remember { mutableStateOf(initialNote?.alarmToneName) }
    var isPlayingTonePreview by remember { mutableStateOf(false) }
    var showToneSelectorDialog by remember { mutableStateOf(false) }

    // Aplicación Vinculada (WhatsApp, Instagram, etc.)
    var targetAppName by remember { mutableStateOf(initialNote?.targetAppName ?: "") }
    var targetAppPackage by remember { mutableStateOf(initialNote?.targetAppPackage ?: "") }

    // Subtareas
    var subtasks by remember { mutableStateOf(initialNote?.getSubtasks() ?: emptyList()) }
    var newSubtaskText by remember { mutableStateOf("") }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (pickedUri != null) {
                alarmToneUri = pickedUri.toString()
                alarmToneName = AlarmToneHelper.getToneTitle(context, pickedUri.toString())
            }
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                if (title.isBlank()) {
                    title = spokenText
                } else {
                    title = "$title $spokenText"
                }
            }
        }
    }

    val presetCategories = listOf("General", "Trabajo", "Personal", "Urgente", "Ideas", "Compras")
    val presetColors = listOf(
        "#6366F1" to "Índigo",
        "#EC4899" to "Rosa",
        "#10B981" to "Esmeralda",
        "#F59E0B" to "Ámbar",
        "#06B6D4" to "Cian",
        "#8B5CF6" to "Púrpura"
    )

    val intervalOptions = listOf(
        0 to "Sin repetición",
        15 to "Cada 15 min",
        30 to "Cada 30 min",
        60 to "Cada 1 hora",
        120 to "Cada 2 horas"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Cabecera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialNote == null) "Nueva Nota / Tarea" else "Editar Nota",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Campo Título con Botón de Micrófono
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título de la tarea o nota *") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Habla para dictar el título...")
                            }
                            try {
                                speechLauncher.launch(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Dictar por voz",
                                tint = PrimaryIndigo
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryIndigo,
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = PrimaryIndigo,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // 🧠 Sugerencia Inteligente NLP en Tiempo Real
                val nlpParsed = remember(title) { NlpTaskParser.parse(title) }
                if (nlpParsed.summaryPreview != null && initialNote == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryIndigo.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🧠 Detectado automáticamente:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanAccent
                                )
                                Text(
                                    text = nlpParsed.summaryPreview,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    nlpParsed.targetDate?.let { targetDate = it }
                                    if (nlpParsed.repeatIntervalMinutes > 0) repeatInterval = nlpParsed.repeatIntervalMinutes
                                    if (nlpParsed.isUrgent) isUrgentAlarm = true
                                    nlpParsed.detectedCategory?.let { category = it }
                                    nlpParsed.detectedAppName?.let {
                                        targetAppName = it
                                        targetAppPackage = nlpParsed.detectedAppPackage ?: ""
                                    }
                                    title = nlpParsed.cleanTitle
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Aplicar", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Campo Descripción / Detalles
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Detalles opcionales") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryIndigo,
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = PrimaryIndigo,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 📋 SECCIÓN SUBTAREAS / CHECKLIST
                // ==========================================
                Text(
                    text = "Subtareas / Checklist (${subtasks.size}):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Fila para añadir subtarea
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSubtaskText,
                        onValueChange = { newSubtaskText = it },
                        placeholder = { Text("Añadir paso o elemento...", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (newSubtaskText.isNotBlank()) {
                                subtasks = subtasks + SubTask(
                                    id = UUID.randomUUID().toString(),
                                    title = newSubtaskText.trim(),
                                    isDone = false
                                )
                                newSubtaskText = ""
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(PrimaryIndigo)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Añadir subtarea", tint = TextPrimary)
                    }
                }

                // Lista de subtareas añadidas
                if (subtasks.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceElevated, RoundedCornerShape(10.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        subtasks.forEachIndexed { index, subtask ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = subtask.isDone,
                                        onCheckedChange = { checked ->
                                            subtasks = subtasks.toMutableList().also {
                                                it[index] = subtask.copy(isDone = checked)
                                            }
                                        },
                                        modifier = Modifier.size(24.dp),
                                        colors = CheckboxDefaults.colors(checkedColor = EmeraldSuccess)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = subtask.title,
                                        fontSize = 12.sp,
                                        color = TextPrimary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        subtasks = subtasks.filterIndexed { i, _ -> i != index }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Eliminar",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 🔒 SEGURIDAD: Bloqueo con Huella Dactilar
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isLocked) AmberWarning.copy(alpha = 0.12f) else DarkSurfaceElevated
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = if (isLocked) AmberWarning else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "🔒 Bloqueo con Huella Dactilar",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isLocked) AmberWarning else TextPrimary
                                )
                                Text(
                                    text = "Protege el contenido para que solo tú puedas abrirlo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = isLocked,
                            onCheckedChange = { isLocked = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AmberWarning,
                                checkedTrackColor = AmberWarning.copy(alpha = 0.4f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 🚨 ANTI-PROCRASTINACIÓN: Alarma Insistente
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isUrgentAlarm) RoseAlert.copy(alpha = 0.12f) else DarkSurfaceElevated
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (isUrgentAlarm) RoseAlert else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "🚨 Alarma Anti-Procrastinación",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isUrgentAlarm) RoseAlert else TextPrimary
                                )
                                Text(
                                    text = "Tono fuerte de despertador y vibración intensa",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        Switch(
                            checked = isUrgentAlarm,
                            onCheckedChange = { isUrgentAlarm = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = RoseAlert,
                                checkedTrackColor = RoseAlert.copy(alpha = 0.4f)
                            )
                        )
                    }
                }

                if (snoozeCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AmberWarning.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "⚠️ Llevas posponiendo esto $snoozeCount veces",
                            fontSize = 12.sp,
                            color = AmberWarning
                        )
                        TextButton(onClick = { snoozeCount = 0 }) {
                            Text("Reiniciar", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Categoría
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Categoría:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetCategories.take(3).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanAccent,
                                selectedLabelColor = DarkBackground
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetCategories.drop(3).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanAccent,
                                selectedLabelColor = DarkBackground
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Selector de Color
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = SecondaryPink,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Color Temático:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    presetColors.forEach { (hex, _) ->
                        val color = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = colorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) Modifier.border(2.5.dp, Color.White, CircleShape) else Modifier
                                )
                                .clickable { colorHex = hex }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Notificación Periódica (Cada X tiempo)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Notificación periódica (cada X tiempo):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    intervalOptions.take(3).forEach { (minutes, label) ->
                        FilterChip(
                            selected = repeatInterval == minutes,
                            onClick = { repeatInterval = minutes },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryIndigo,
                                selectedLabelColor = TextPrimary
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    intervalOptions.drop(3).forEach { (minutes, label) ->
                        FilterChip(
                            selected = repeatInterval == minutes,
                            onClick = { repeatInterval = minutes },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryIndigo,
                                selectedLabelColor = TextPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Calendario
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Fecha en Calendario:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }

                    if (targetDate != null) {
                        TextButton(onClick = { targetDate = null }) {
                            Text("Quitar fecha", color = RoseAlert, fontSize = 12.sp)
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clickable {
                            val cal = Calendar.getInstance()
                            targetDate?.let { cal.timeInMillis = it }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val newCal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, y)
                                        set(Calendar.MONTH, m)
                                        set(Calendar.DAY_OF_MONTH, d)
                                        set(Calendar.HOUR_OF_DAY, 0)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    targetDate = newCal.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceElevated
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val dateText = if (targetDate != null) {
                            SimpleDateFormat("EEEE, dd 'de' MMMM yyyy", Locale.getDefault()).format(Date(targetDate!!))
                        } else {
                            "Sin fecha asignada (Toca para elegir día)"
                        }
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (targetDate != null) CyanAccent else TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ==========================================
                // 📍 RECORDATORIO POR UBICACIÓN O WI-FI
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Recordatorio por Lugar / Wi-Fi (Llegada):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = locationName,
                        onValueChange = { locationName = it },
                        label = { Text("Lugar (ej. Casa, Oficina)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberWarning,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    OutlinedTextField(
                        value = wifiSsid,
                        onValueChange = { wifiSsid = it },
                        label = { Text("Red Wi-Fi (SSID)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AmberWarning,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
                Text(
                    text = "💡 Al conectarte a ese Wi-Fi, FloatNote te notificará esta tarea al instante.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 📱 APLICACIÓN VINCULADA (AUTO-POPUP INTELIGENTE)
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "📱 Aplicación Vinculada (Auto-Popup):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("WhatsApp", "Instagram", "YouTube", "TikTok").forEach { appName ->
                        FilterChip(
                            selected = targetAppName.equals(appName, ignoreCase = true),
                            onClick = {
                                if (targetAppName.equals(appName, ignoreCase = true)) {
                                    targetAppName = ""
                                    targetAppPackage = ""
                                } else {
                                    targetAppName = appName
                                    targetAppPackage = AppHelper.POPULAR_APPS.firstOrNull { it.name.equals(appName, ignoreCase = true) }?.packageName ?: ""
                                }
                            },
                            label = { Text(appName, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldSuccess,
                                selectedLabelColor = DarkBackground
                            )
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Telegram", "Gmail", "X (Twitter)", "Spotify").forEach { appName ->
                        FilterChip(
                            selected = targetAppName.equals(appName, ignoreCase = true),
                            onClick = {
                                if (targetAppName.equals(appName, ignoreCase = true)) {
                                    targetAppName = ""
                                    targetAppPackage = ""
                                } else {
                                    targetAppName = appName
                                    targetAppPackage = AppHelper.POPULAR_APPS.firstOrNull { it.name.equals(appName, ignoreCase = true) }?.packageName ?: ""
                                }
                            },
                            label = { Text(appName, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EmeraldSuccess,
                                selectedLabelColor = DarkBackground
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = targetAppName,
                    onValueChange = { input ->
                        targetAppName = input
                        val detected = AppHelper.detectAppFromText(input)
                        if (detected != null) {
                            targetAppPackage = detected.packageName
                        }
                    },
                    label = { Text("Nombre de la aplicación (ej. WhatsApp, Instagram...)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldSuccess,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Text(
                    text = "💡 Al abrir esta app en tu móvil, el popup flotante se abrirá solo y mostrará únicamente esta tarea.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ==========================================
                // 🔔 TONO DE ALARMA PARA ESTA TAREA
                // ==========================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = SecondaryPink,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🔔 Tono de Alarma para esta Tarea:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showToneSelectorDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    color = DarkSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SecondaryPink.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val displayTone = if (!alarmToneName.isNullOrBlank()) {
                                alarmToneName!!
                            } else {
                                "Predeterminado (${AlarmToneHelper.getDefaultAlarmTone(context).second})"
                            }
                            Text(
                                text = displayTone,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Toca para elegir el tono de alarma",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryPink
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    if (isPlayingTonePreview) {
                                        AlarmToneHelper.stopPreview()
                                        isPlayingTonePreview = false
                                    } else {
                                        val targetUri = alarmToneUri ?: AlarmToneHelper.getDefaultAlarmTone(context).first
                                        isPlayingTonePreview = AlarmToneHelper.playPreview(context, targetUri)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isPlayingTonePreview) Icons.Default.Stop else Icons.Default.VolumeUp,
                                    contentDescription = "Probar tono",
                                    tint = if (isPlayingTonePreview) RoseAlert else SecondaryPink
                                )
                            }
                            if (alarmToneUri != null) {
                                IconButton(onClick = {
                                    alarmToneUri = null
                                    alarmToneName = null
                                    AlarmToneHelper.stopPreview()
                                    isPlayingTonePreview = false
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Restablecer",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Switch: Mostrar en burbuja flotante
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Mostrar en Popup Flotante",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Aparecerá en el popup rápido sobre cualquier app",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PrimaryIndigo,
                            checkedTrackColor = PrimaryDark
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        AlarmToneHelper.stopPreview()
                        onDismiss()
                    }) {
                        Text("Cancelar", color = TextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                AlarmToneHelper.stopPreview()
                                onSave(
                                    title.trim(),
                                    content.trim(),
                                    targetDate,
                                    null,
                                    repeatInterval,
                                    isPinned,
                                    category,
                                    colorHex,
                                    isUrgentAlarm,
                                    snoozeCount,
                                    isLocked,
                                    SubTask.toJsonList(subtasks),
                                    locationName.trim().ifBlank { null },
                                    wifiSsid.trim().ifBlank { null },
                                    alarmToneUri?.ifBlank { null },
                                    alarmToneName?.ifBlank { null },
                                    targetAppName.trim().ifBlank { null },
                                    targetAppPackage.trim().ifBlank { null }
                                )
                                onDismiss()
                            }
                        },
                        enabled = title.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isUrgentAlarm) RoseAlert else PrimaryIndigo
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Guardar", color = TextPrimary)
                    }
                }
            }
        }
    }

    // Diálogo de Selección de Tono de Alarma
    if (showToneSelectorDialog) {
        val systemTones = remember { AlarmToneHelper.getSystemTones(context) }
        AlertDialog(
            onDismissRequest = {
                showToneSelectorDialog = false
                AlarmToneHelper.stopPreview()
                isPlayingTonePreview = false
            },
            title = {
                Text(
                    text = "Seleccionar Tono de Alarma",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Opción: Predeterminado de la app
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                alarmToneUri = null
                                alarmToneName = null
                                showToneSelectorDialog = false
                                AlarmToneHelper.stopPreview()
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (alarmToneUri == null) PrimaryIndigo.copy(alpha = 0.2f) else DarkSurfaceElevated
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Alarm, contentDescription = null, tint = PrimaryIndigo)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Usar Predeterminado de la App", color = TextPrimary, fontSize = 13.sp)
                        }
                    }

                    // Lista de tonos detectados en el sistema
                    systemTones.forEach { tone ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    alarmToneUri = tone.uriString
                                    alarmToneName = tone.name
                                    showToneSelectorDialog = false
                                    AlarmToneHelper.stopPreview()
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (alarmToneUri == tone.uriString) SecondaryPink.copy(alpha = 0.2f) else DarkSurfaceElevated
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = SecondaryPink)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(tone.name, color = TextPrimary, fontSize = 13.sp, maxLines = 1)
                                }
                                IconButton(
                                    onClick = {
                                        AlarmToneHelper.playPreview(context, tone.uriString)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Oír", tint = SecondaryPink, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Abrir selector nativo del sistema
                    Button(
                        onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Elige un tono de alarma")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                if (alarmToneUri != null) {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(alarmToneUri))
                                }
                            }
                            showToneSelectorDialog = false
                            AlarmToneHelper.stopPreview()
                            try {
                                ringtonePickerLauncher.launch(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Explorar más tonos en el dispositivo", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showToneSelectorDialog = false
                    AlarmToneHelper.stopPreview()
                }) {
                    Text("Cerrar", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}
