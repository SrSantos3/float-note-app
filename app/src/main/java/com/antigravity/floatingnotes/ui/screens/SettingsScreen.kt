package com.antigravity.floatingnotes.ui.screens

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import com.antigravity.floatingnotes.data.NoteEntity
import com.antigravity.floatingnotes.service.ReminderScheduler
import com.antigravity.floatingnotes.ui.theme.*
import com.antigravity.floatingnotes.ui.viewmodel.NotesViewModel

@Composable
fun SettingsScreen(
    viewModel: NotesViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val hasOverlayPermission = Settings.canDrawOverlays(context)
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    val canExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        alarmManager?.canScheduleExactAlarms() == true
    } else true

    var testScheduledMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Permisos del Sistema",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Text(
            text = "Para que la burbuja y las notificaciones funcionen en cualquier pantalla del teléfono:",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Card 1: Permiso de Superposición
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

        // Card 2: Permiso de Alarmas Exactas
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

        // Card 3: Ajustes generales de notificaciones
        PermissionStatusCard(
            title = "Canales de Notificación",
            description = "Configura sonido, vibración e importancia en los ajustes de Android.",
            isGranted = true,
            actionLabel = "Ajustes",
            onActionClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Pruebas y Diagnóstico",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = DarkSurface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Probar Notificación Periódica",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Lanza un recordatorio de prueba dentro de 5 segundos para verificar sonido y los botones de acción (Completar / Posponer).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val testNote = NoteEntity(
                            id = 999999,
                            title = "🔔 Notificación de Prueba",
                            content = "¡Tu sistema de recordatorios está funcionando a la perfección!",
                            targetTime = System.currentTimeMillis() + 5000L,
                            repeatIntervalMinutes = 0
                        )
                        ReminderScheduler.scheduleNoteReminder(context, testNote)
                        testScheduledMessage = "¡Recordatorio enviado! Sonará en 5 segundos..."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lanzar prueba en 5 segundos")
                }

                if (testScheduledMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = testScheduledMessage!!,
                        fontSize = 12.sp,
                        color = EmeraldSuccess
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Cómo usar la Burbuja Flotante",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "• Mueve la burbuja arrastrándola a cualquier parte de tu pantalla; al soltarla se acoplará automáticamente al borde.\n• Un toque sobre la burbuja abre tus tareas pendientes sobre cualquier aplicación (WhatsApp, YouTube, navegador, etc.).\n• Puedes marcar tareas como hechas o añadir tareas rápidas sin tener que abrir la app completa.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            lineHeight = 20.sp
        )

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
