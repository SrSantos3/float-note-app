package com.antigravity.floatingnotes.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import com.antigravity.floatingnotes.MainActivity
import com.antigravity.floatingnotes.R
import com.antigravity.floatingnotes.data.AppDatabase
import com.antigravity.floatingnotes.data.NoteEntity
import com.antigravity.floatingnotes.ui.util.NlpTaskParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // Vistas flotantes
    private var bubbleView: View? = null
    private var popupView: View? = null

    // LayoutParams
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var popupParams: WindowManager.LayoutParams

    // Estados
    private var isPopupOpen = false
    private var screenWidth = 0
    private var screenHeight = 0

    // Referencias de widgets en la burbuja
    private var tvPendingBadge: TextView? = null

    // Referencias de widgets en el popup
    private var containerTodoItems: LinearLayout? = null
    private var tvEmptyState: TextView? = null
    private var tvPopupCount: TextView? = null
    private var etQuickNote: EditText? = null
    private var btnSpeechToText: ImageButton? = null

    // Reconocimiento de voz en la burbuja
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    companion object {
        const val CHANNEL_ID = "channel_floating_bubble_service"
        const val NOTIFICATION_ID = 9001
        const val ACTION_STOP_SERVICE = "com.antigravity.floatingnotes.ACTION_STOP_BUBBLE"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        updateScreenDimensions()

        startForegroundNotification()
        setupFloatingBubble()
        setupFloatingPopup()
        initSpeechRecognizer()
        observeDatabaseNotes()
    }

    private fun updateScreenDimensions() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_overlay_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_overlay_service_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bubble)
            .setContentTitle(getString(R.string.overlay_service_active))
            .setContentText(getString(R.string.overlay_service_desc))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun setupFloatingBubble() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - 180
            y = screenHeight / 3
        }

        val inflater = LayoutInflater.from(this)
        bubbleView = inflater.inflate(R.layout.layout_floating_bubble, null)
        tvPendingBadge = bubbleView?.findViewById(R.id.tv_pending_badge)

        bubbleView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var touchStartTime = 0L

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = bubbleParams.x
                        initialY = bubbleParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        touchStartTime = System.currentTimeMillis()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        bubbleParams.x = initialX + dx
                        bubbleParams.y = initialY + dy
                        windowManager.updateViewLayout(bubbleView, bubbleParams)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = abs(event.rawX - initialTouchX)
                        val diffY = abs(event.rawY - initialTouchY)
                        val duration = System.currentTimeMillis() - touchStartTime

                        if (diffX < 15 && diffY < 15 && duration < 300) {
                            togglePopup()
                        } else {
                            snapBubbleToEdge()
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager.addView(bubbleView, bubbleParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun snapBubbleToEdge() {
        val middle = screenWidth / 2
        bubbleParams.x = if (bubbleParams.x + (bubbleView?.width ?: 150) / 2 < middle) {
            10
        } else {
            screenWidth - (bubbleView?.width ?: 160) - 10
        }
        try {
            windowManager.updateViewLayout(bubbleView, bubbleParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("InflateParams")
    private fun setupFloatingPopup() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        popupParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val inflater = LayoutInflater.from(this)
        popupView = inflater.inflate(R.layout.layout_floating_popup, null)

        containerTodoItems = popupView?.findViewById(R.id.container_todo_items)
        tvEmptyState = popupView?.findViewById(R.id.tv_empty_state)
        tvPopupCount = popupView?.findViewById(R.id.tv_popup_count)
        etQuickNote = popupView?.findViewById(R.id.et_quick_note)
        btnSpeechToText = popupView?.findViewById(R.id.btn_speech_to_text)

        popupView?.findViewById<ImageButton>(R.id.btn_popup_close)?.setOnClickListener {
            hidePopup()
        }

        popupView?.findViewById<ImageButton>(R.id.btn_quick_add)?.setOnClickListener {
            submitQuickNote()
        }

        // Dictado por voz flotante
        btnSpeechToText?.setOnClickListener {
            toggleVoiceRecognition()
        }

        etQuickNote?.setOnEditorActionListener { _, _, _ ->
            submitQuickNote()
            true
        }

        popupView?.findViewById<Button>(R.id.btn_open_full_app)?.setOnClickListener {
            hidePopup()
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }

        popupView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hidePopup()
                true
            } else {
                false
            }
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    etQuickNote?.hint = "🎙️ Escuchando... habla ahora"
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                    etQuickNote?.hint = "Añadir o dictar tarea..."
                }

                override fun onError(error: Int) {
                    isListening = false
                    etQuickNote?.hint = "Añadir o dictar tarea..."
                    Toast.makeText(applicationContext, "No se capturó la voz. Toca el micro para reintentar.", Toast.LENGTH_SHORT).show()
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    etQuickNote?.hint = "Añadir o dictar tarea..."
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val spokenText = matches[0]
                        etQuickNote?.setText(spokenText)
                        // Añadir automáticamente tras dictar
                        submitQuickNote()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        etQuickNote?.setText(matches[0])
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun toggleVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "El reconocimiento de voz no está disponible en este dispositivo.", Toast.LENGTH_SHORT).show()
            return
        }

        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
            etQuickNote?.hint = "Añadir o dictar tarea..."
        } else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            try {
                speechRecognizer?.startListening(intent)
                isListening = true
                etQuickNote?.hint = "🎙️ Escuchando... habla ahora"
            } catch (e: Exception) {
                e.printStackTrace()
                isListening = false
            }
        }
    }

    private fun submitQuickNote() {
        val text = etQuickNote?.text?.toString()?.trim() ?: ""
        if (text.isNotBlank()) {
            serviceScope.launch(Dispatchers.IO) {
                val db = AppDatabase.getDatabase(applicationContext)
                val parsed = NlpTaskParser.parse(text)
                val newNote = NoteEntity(
                    title = parsed.cleanTitle.ifBlank { text },
                    targetDate = parsed.targetDate,
                    targetTime = parsed.targetTime,
                    repeatIntervalMinutes = parsed.repeatIntervalMinutes,
                    isPinnedToOverlay = true,
                    priority = if (parsed.isUrgent) 3 else 2,
                    category = parsed.detectedCategory ?: "General",
                    colorHex = if (parsed.isUrgent) "#EC4899" else "#6366F1",
                    isUrgentAlarm = parsed.isUrgent
                )
                val insertedId = db.noteDao().insertNote(newNote)
                if (newNote.repeatIntervalMinutes > 0 || newNote.targetTime != null || newNote.targetDate != null || newNote.isUrgentAlarm) {
                    ReminderScheduler.scheduleNoteReminder(applicationContext, newNote.copy(id = insertedId))
                }
            }
            etQuickNote?.setText("")
        }
    }

    private fun togglePopup() {
        if (isPopupOpen) {
            hidePopup()
        } else {
            showPopup()
        }
    }

    private fun showPopup() {
        if (!isPopupOpen && popupView != null) {
            try {
                windowManager.addView(popupView, popupParams)
                isPopupOpen = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun hidePopup() {
        if (isPopupOpen && popupView != null) {
            try {
                if (isListening) {
                    speechRecognizer?.stopListening()
                    isListening = false
                }
                windowManager.removeView(popupView)
                isPopupOpen = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun observeDatabaseNotes() {
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            db.noteDao().getOverlayPendingNotes().collectLatest { notes ->
                updateOverlayUI(notes)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateOverlayUI(notes: List<NoteEntity>) {
        val count = notes.size

        if (count > 0) {
            tvPendingBadge?.visibility = View.VISIBLE
            tvPendingBadge?.text = if (count > 99) "99+" else count.toString()
        } else {
            tvPendingBadge?.visibility = View.GONE
        }

        tvPopupCount?.text = count.toString()
        containerTodoItems?.removeAllViews()

        if (notes.isEmpty()) {
            tvEmptyState?.visibility = View.VISIBLE
        } else {
            tvEmptyState?.visibility = View.GONE
            val inflater = LayoutInflater.from(this)

            for (note in notes) {
                val itemView = inflater.inflate(R.layout.item_overlay_todo, containerTodoItems, false)
                val cb = itemView.findViewById<CheckBox>(R.id.cb_todo_complete)
                val tvTitle = itemView.findViewById<TextView>(R.id.tv_todo_title)
                val tvSubtitle = itemView.findViewById<TextView>(R.id.tv_todo_subtitle)
                val tvCategoryTag = itemView.findViewById<TextView>(R.id.tv_category_tag)
                try {
                    val color = Color.parseColor(note.colorHex)
                    viewIndicator.setBackgroundColor(color)
                    tvCategoryTag.setTextColor(color)
                } catch (e: Exception) {
                    viewIndicator.setBackgroundColor(Color.parseColor("#6366F1"))
                }
                tvCategoryTag.text = note.category

                if (note.isLocked) {
                    tvTitle.text = "🔒 Nota protegida con huella"
                    cb.visibility = View.GONE
                    tvSubtitle.visibility = View.VISIBLE
                    tvSubtitle.text = "Toca 'Abrir App' para desbloquear"
                } else {
                    tvTitle.text = note.title
                    cb.visibility = View.VISIBLE
                    cb.isChecked = note.isCompleted

                    val (done, total) = note.getSubtaskProgress()

                    if (total > 0) {
                        tvSubtitle.visibility = View.VISIBLE
                        tvSubtitle.text = "📋 $done/$total subtareas" + if (note.repeatIntervalMinutes > 0) " • ⏱ ${note.repeatIntervalMinutes}m" else ""
                    } else if (note.repeatIntervalMinutes > 0) {
                        tvSubtitle.visibility = View.VISIBLE
                        tvSubtitle.text = "⏱ Avisa cada ${note.repeatIntervalMinutes} min"
                    } else if (note.targetDate != null) {
                        tvSubtitle.visibility = View.VISIBLE
                        tvSubtitle.text = "📅 En calendario"
                    } else {
                        tvSubtitle.visibility = View.GONE
                    }
                }

                cb.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        serviceScope.launch(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(applicationContext)
                            db.noteDao().setCompletionStatus(note.id, true)
                            ReminderScheduler.cancelReminder(applicationContext, note.id)
                        }
                    }
                }

                containerTodoItems?.addView(itemView)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
        serviceScope.cancel()
        hidePopup()
        if (bubbleView != null) {
            try {
                windowManager.removeView(bubbleView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
