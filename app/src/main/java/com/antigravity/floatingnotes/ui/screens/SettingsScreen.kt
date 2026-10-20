package com.antigravity.floatingnotes.ui.screens

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.media.RingtoneManager
import com.antigravity.floatingnotes.data.NoteEntity
import com.antigravity.floatingnotes.service.AlarmReceiver
import com.antigravity.floatingnotes.service.AppDetectionAccessibilityService
import com.antigravity.floatingnotes.service.ReminderScheduler
import com.antigravity.floatingnotes.ui.theme.*
import com.antigravity.floatingnotes.ui.util.AlarmToneHelper
import com.antigravity.floatingnotes.ui.util.BiometricHelper
import com.antigravity.floatingnotes.ui.viewmodel.NotesViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: NotesViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val hasOverlayPermission = Settings.canDrawOverlays(context)
    val hasAudioPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    val hasBiometricHardware = BiometricHelper.canAuthenticate(context)
    val hasAccessibilityPermission = AppDetectionAccessibilityService.isAccessibilityServiceEnabled(context)

    val isQuietHoursEnabled by viewModel.isQuietHoursEnabled.collectAsState()
    val quietHoursStartHour by viewModel.quietHoursStartHour.collectAsState()
    val quietHoursStartMinute by viewModel.quietHoursStartMinute.collectAsState()
    val quietHoursEndHour by viewModel.quietHoursEndHour.collectAsState()
    val quietHoursEndMinute by viewModel.quietHoursEndMinute.collectAsState()

    val isDailyBriefingEnabled by viewModel.isDailyBriefingEnabled.collectAsState()
    val dailyBriefingHour by viewModel.dailyBriefingHour.collectAsState()
    val dailyBriefingMinute by viewModel.dailyBriefingMinute.collectAsState()

    val initialTone = remember { AlarmToneHelper.getDefaultAlarmTone(context) }
    var defaultAlarmToneUri by remember { mutableStateOf(initialTone.first) }
    var defaultAlarmToneName by remember { mutableStateOf(initialTone.second) }
    var isPlayingDefaultTone by remember { mutableStateOf(false) }
    var showTonePicker by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            AlarmToneHelper.stopPreview()
        }
    }

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    val canExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager?.canScheduleExactAlarms() == true
    } else true

    var testScheduledMessage by remember { mutableStateOf<String?>(null) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }

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
                val title = AlarmToneHelper.getToneTitle(context, pickedUri.toString())
                defaultAlarmToneUri = pickedUri.toString()
                defaultAlarmToneName = title
                AlarmToneHelper.setDefaultAlarmTone(context, pickedUri.toString(), title)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val success = viewModel.exportNotesToJsonUri(uri)
                if (success) {
                    backupStatusMessage = "✅ Copia de seguridad guardada con éxito."
                    Toast.makeText(context, "Copia de seguridad guardada", Toast.LENGTH_SHORT).show()
                } else {
                    backupStatusMessage = "❌ Error al exportar la copia de seguridad."
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val count = viewModel.importNotesFromJsonUri(uri)
                if (count >= 0) {
                    backupStatusMessage = "✅ Se han restaurado $count notas correctamente."
                    Toast.makeText(context, "$count notas restauradas", Toast.LENGTH_SHORT).show()
                } else {
                    backupStatusMessage = "❌ Error al leer el archivo de copia de seguridad."
                }
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(context, "Permiso de micrófono concedido", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // ==========================================
        // 🌙 HORAS DE SILENCIO (MODO SUEÑO)
        // ==========================================
        Text(
            text = "🌙 Horas de Silencio (Modo Sueño)",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Text(
            text = "Pausa notificaciones y alarmas a tu medida según tus horas de sueño o trabajo:",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Silenciar durante el descanso",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isQuietHoursEnabled) {
                                String.format(Locale.getDefault(), "De %02d:%02d a %02d:%02d (reanudará al despertar)", quietHoursStartHour, quietHoursStartMinute, quietHoursEndHour, quietHoursEndMinute)
                            } else "Desactivado (las alarmas sonarán a cualquier hora)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isQuietHoursEnabled) CyanAccent else TextSecondary
                        )
                    }

                    Switch(
                        checked = isQuietHoursEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setQuietHours(enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyanAccent,
                            checkedTrackColor = CyanAccent.copy(alpha = 0.4f)
                        )
                    )
                }

                if (isQuietHoursEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Configura tu horario personalizado (toca para cambiar):",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Tarjeta Inicio Silencio
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m -> viewModel.setQuietHoursStart(h, m) },
                                        quietHoursStartHour,
                                        quietHoursStartMinute,
                                        true
                                    ).show()
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = DarkSurfaceElevated,
                            border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Bedtime, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Inicio sueño", fontSize = 11.sp, color = TextSecondary)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = String.format(Locale.getDefault(), "%02d:%02d", quietHoursStartHour, quietHoursStartMinute),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text("Toca para elegir hora", fontSize = 10.sp, color = CyanAccent)
                            }
                        }

                        // Tarjeta Fin Silencio
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    TimePickerDialog(
                                        context,
                                        { _, h, m -> viewModel.setQuietHoursEnd(h, m) },
                                        quietHoursEndHour,
                                        quietHoursEndMinute,
                                        true
                                    ).show()
                                },
                            shape = RoundedCornerShape(14.dp),
                            color = DarkSurfaceElevated,
                            border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.WbSunny, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Fin sueño", fontSize = 11.sp, color = TextSecondary)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = String.format(Locale.getDefault(), "%02d:%02d", quietHoursEndHour, quietHoursEndMinute),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text("Toca para elegir hora", fontSize = 10.sp, color = AmberWarning)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ==========================================
        // ☀️ RESUMEN MATUTINO DIARIO (CONFIGURABLE)
        // ==========================================
        Text(
            text = "☀️ Resumen Matutino Diario",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Text(
            text = "Recibe un informe matutino con tus tareas del día y alarmas críticas al despertar:",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aviso diario matutino",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isDailyBriefingEnabled) {
                                String.format(Locale.getDefault(), "Programado a las %02d:%02d cada mañana", dailyBriefingHour, dailyBriefingMinute)
                            } else "Desactivado",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDailyBriefingEnabled) EmeraldSuccess else TextSecondary
                        )
                    }

                    Switch(
                        checked = isDailyBriefingEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setDailyBriefing(enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = EmeraldSuccess,
                            checkedTrackColor = EmeraldSuccess.copy(alpha = 0.4f)
                        )
                    )
                }

                if (isDailyBriefingEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                TimePickerDialog(
                                    context,
                                    { _, h, m -> viewModel.setDailyBriefingTime(h, m) },
                                    dailyBriefingHour,
                                    dailyBriefingMinute,
                                    true
                                ).show()
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = DarkSurfaceElevated,
                        border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Hora de entrega del resumen",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Toca para ajustar la hora a la que te levantas",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = EmeraldSuccess.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = String.format(Locale.getDefault(), "⏰ %02d:%02d", dailyBriefingHour, dailyBriefingMinute),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldSuccess,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val intent = Intent(context, AlarmReceiver::class.java).apply {
                            action = AlarmReceiver.ACTION_DAILY_BRIEFING
                        }
                        context.sendBroadcast(intent)
                        Toast.makeText(context, "Resumen matutino generado en tus notificaciones", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.WbSunny, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Probar Resumen Matutino Ahora", fontSize = 12.sp, color = TextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ==========================================
        // 🔔 TONO DE ALARMA PREDETERMINADO
        // ==========================================
        Text(
            text = "🔔 Tono de Alarma Predeterminado",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Text(
            text = "Todos los recordatorios de FloatNote suenan como alarmas. Elige qué melodía o tono usar por defecto:",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SecondaryPink.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = SecondaryPink,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = defaultAlarmToneName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Tono predeterminado de alarma",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecondaryPink
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (isPlayingDefaultTone) {
                                AlarmToneHelper.stopPreview()
                                isPlayingDefaultTone = false
                            } else {
                                isPlayingDefaultTone = AlarmToneHelper.playPreview(context, defaultAlarmToneUri)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isPlayingDefaultTone) Icons.Default.Stop else Icons.Default.VolumeUp,
                            contentDescription = "Probar tono",
                            tint = if (isPlayingDefaultTone) RoseAlert else SecondaryPink,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { showTonePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryPink),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cambiar Tono de Alarma", color = Color.White, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ==========================================
        // 🔒 SEGURIDAD Y BIOMETRÍA
        // ==========================================
        Text(
            text = "🔒 Seguridad y Huella Dactilar",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = null,
                    tint = if (hasBiometricHardware) EmeraldSuccess else AmberWarning,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (hasBiometricHardware) "Sensor Biométrico Disponible" else "Sensor no detectado",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = if (hasBiometricHardware) "Puedes proteger notas individuales con tu huella dactilar o PIN de pantalla." else "Configura un PIN o huella en los ajustes de tu teléfono.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ==========================================
        // 💾 COPIA DE SEGURIDAD Y DATOS
        // ==========================================
        Text(
            text = "Copia de Seguridad y Datos",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Text(
            text = "Exporta tus notas y subtareas para no perderlas nunca o restáuralas en otro teléfono:",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            exportLauncher.launch("floatnote_backup_${System.currentTimeMillis()}.json")
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Exportar", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/*"))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FileUpload, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Importar", color = DarkBackground, fontSize = 13.sp)
                    }
                }

                if (backupStatusMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = backupStatusMessage!!,
                        fontSize = 12.sp,
                        color = if (backupStatusMessage!!.startsWith("✅")) EmeraldSuccess else RoseAlert
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // PERMISOS DEL SISTEMA
        // ==========================================
        Text(
            text = "Permisos del Sistema",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionStatusCard(
            title = "Mostrar sobre otras aplicaciones",
            description = "Obligatorio para que la burbuja flotante y su popup estén siempre accesibles.",
            isGranted = hasOverlayPermission,
            onActionClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionStatusCard(
            title = "Micrófono para Dictado por Voz",
            description = "Permite dictar notas hablando directamente desde la burbuja flotante.",
            isGranted = hasAudioPermission,
            onActionClick = {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionStatusCard(
                title = "Alarmas y recordatorios exactos",
                description = "Permite que la app te avise con precisión matemática cada X tiempo.",
                isGranted = canExactAlarm,
                onActionClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        PermissionStatusCard(
            title = "Detección Inteligente de Apps (Opcional)",
            description = "Abre automáticamente la lista de tareas al entrar en WhatsApp, Instagram u otras apps vinculadas. Si tu teléfono tiene ajustes restringidos o prefieres no activarlo, la burbuja y los filtros funcionan al 100% manualmente sin este permiso.",
            isGranted = hasAccessibilityPermission,
            actionLabel = if (hasAccessibilityPermission) "Activo" else "Activar (Opcional)",
            onActionClick = {
                try {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "No se pudo abrir Ajustes de Accesibilidad", Toast.LENGTH_SHORT).show()
                }
            }
        )

        Spacer(modifier = Modifier.height(40.dp))
    }

    if (showTonePicker) {
        val systemTones = remember { AlarmToneHelper.getSystemTones(context) }
        AlertDialog(
            onDismissRequest = {
                showTonePicker = false
                AlarmToneHelper.stopPreview()
                isPlayingDefaultTone = false
            },
            title = {
                Text("Elegir Tono de Alarma Predeterminado", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    systemTones.forEach { tone ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    defaultAlarmToneUri = tone.uriString
                                    defaultAlarmToneName = tone.name
                                    AlarmToneHelper.setDefaultAlarmTone(context, tone.uriString, tone.name)
                                    showTonePicker = false
                                    AlarmToneHelper.stopPreview()
                                    isPlayingDefaultTone = false
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (defaultAlarmToneUri == tone.uriString) SecondaryPink.copy(alpha = 0.2f) else DarkSurfaceElevated
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = SecondaryPink)
                                    Spacer(modifier = Modifier.width(8.dp))
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

                    Button(
                        onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Elige un tono de alarma")
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                if (defaultAlarmToneUri.isNotBlank()) {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(defaultAlarmToneUri))
                                }
                            }
                            showTonePicker = false
                            AlarmToneHelper.stopPreview()
                            isPlayingDefaultTone = false
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
                    showTonePicker = false
                    AlarmToneHelper.stopPreview()
                    isPlayingDefaultTone = false
                }) {
                    Text("Cerrar", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun PermissionStatusCard(
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String = if (isGranted) "Concedido" else "Conceder Permiso",
    onActionClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = DarkSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isGranted) EmeraldSuccess else AmberWarning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isGranted) "Permiso Activo" else "Permiso Pendiente",
                        fontSize = 12.sp,
                        color = if (isGranted) EmeraldSuccess else AmberWarning
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) DarkSurfaceElevated else PrimaryIndigo
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = actionLabel,
                    color = TextPrimary,
                    fontSize = 12.sp
                )
            }
        }
    }
}
