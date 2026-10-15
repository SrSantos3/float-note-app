package com.antigravity.floatingnotes

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.floatingnotes.data.AppDatabase
import com.antigravity.floatingnotes.data.NoteEntity
import com.antigravity.floatingnotes.service.ReminderScheduler
import com.antigravity.floatingnotes.ui.theme.*
import com.antigravity.floatingnotes.ui.util.NlpTaskParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShareReceiverActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: ""
        if (sharedText.isBlank()) {
            Toast.makeText(this, "No se recibió texto para guardar", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val parsed = NlpTaskParser.parse(sharedText)

        setContent {
            FloatingNotesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground.copy(alpha = 0.95f)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ShareDialogCard(
                            initialTitle = parsed.cleanTitle.take(60),
                            initialContent = sharedText,
                            nlpSummary = parsed.summaryPreview,
                            targetDate = parsed.targetDate,
                            targetTime = parsed.targetTime,
                            repeatInterval = parsed.repeatIntervalMinutes,
                            isUrgent = parsed.isUrgent,
                            category = parsed.detectedCategory ?: "General",
                            onDismiss = { finish() },
                            onSave = { title, content, cat ->
                                saveNoteAndFinish(title, content, cat, parsed)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun saveNoteAndFinish(
        title: String,
        content: String,
        category: String,
        parsed: com.antigravity.floatingnotes.ui.util.ParsedTaskInfo
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val note = NoteEntity(
                title = title,
                content = content,
                targetDate = parsed.targetDate,
                targetTime = parsed.targetTime,
                repeatIntervalMinutes = parsed.repeatIntervalMinutes,
                isUrgentAlarm = parsed.isUrgent,
                category = category,
                colorHex = if (parsed.isUrgent) "#EF4444" else "#6366F1"
            )
            val generatedId = db.noteDao().insertNote(note)
            val savedNote = note.copy(id = generatedId)

            if (savedNote.repeatIntervalMinutes > 0 || savedNote.targetTime != null || savedNote.targetDate != null || savedNote.isUrgentAlarm) {
                ReminderScheduler.scheduleNoteReminder(applicationContext, savedNote)
            }
        }
        Toast.makeText(this, "✅ Guardado en FloatNote", Toast.LENGTH_SHORT).show()
        finish()
    }
}

@Composable
fun ShareDialogCard(
    initialTitle: String,
    initialContent: String,
    nlpSummary: String?,
    targetDate: Long?,
    targetTime: Long?,
    repeatInterval: Int,
    isUrgent: Boolean,
    category: String,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, category: String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var content by remember { mutableStateOf(initialContent) }

    Card(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.BookmarkAdded, contentDescription = null, tint = PrimaryIndigo)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Guardar en FloatNote",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = TextSecondary)
                }
            }

            if (nlpSummary != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryIndigo.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "✨ Detectado: $nlpSummary",
                        fontSize = 12.sp,
                        color = PrimaryIndigo,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título de la tarea / nota") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryIndigo,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Texto o enlace compartido") },
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryIndigo,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar", color = TextSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (title.isNotBlank()) onSave(title.trim(), content.trim(), category)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Guardar Tarea", color = TextPrimary)
                }
            }
        }
    }
}
