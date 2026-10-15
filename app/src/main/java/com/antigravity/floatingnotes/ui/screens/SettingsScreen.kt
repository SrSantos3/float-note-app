package com.antigravity.floatingnotes.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.antigravity.floatingnotes.data.NoteEntity
import com.antigravity.floatingnotes.service.AlarmReceiver
import com.antigravity.floatingnotes.service.ReminderScheduler
import com.antigravity.floatingnotes.ui.theme.*
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

    val isQuietHoursEnabled by viewModel.isQuietHoursEnabled.collectAsState()
    val quietHoursStart by viewModel.quietHoursStart.collectAsState()
    val quietHoursEnd by viewModel.quietHoursEnd.collectAsState()

    val isDailyBriefingEnabled by viewModel.isDailyBriefingEnabled.collectAsState()

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    val canExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager?.canScheduleExactAlarms() == true
    } else true

    var testScheduledMessage by remember { mutableStateOf<String?>(null) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }

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
            text = "Pausa automáticamente notificaciones y alarmas periódicas de noche para que no te despierten:",
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
                            text = "Silenciar durante la noche",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isQuietHoursEnabled) "De ${quietHoursStart}:00 a ${quietHoursEnd}:00 (reanudará al despertar)" else "Desactivado (las alarmas sonarán a cualquier hora)",
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
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ==========================================
        // ☀️ RESUMEN MATUTINO DIARIO
        // ==========================================
        Text(
            text = "☀️ Resumen Matutino Diario",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Text(
            text = "Recibe un aviso matutino con tus tareas del día y las alarmas críticas pendientes:",
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
                            text = "Aviso diario matutino (08:30 AM)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isDailyBriefingEnabled) "Activo cada mañana con tu resumen" else "Desactivado",
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

                Spacer(modifier = Modifier.height(10.dp))

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

        Spacer(modifier = Modifier.height(40.dp))
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
