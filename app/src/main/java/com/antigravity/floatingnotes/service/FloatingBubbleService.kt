package com.antigravity.floatingnotes.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
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
import com.antigravity.floatingnotes.ui.util.AppHelper
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

    // Banner Inteligente de App
    private var layoutAppFilterBanner: View? = null
    private var tvAppFilterEmoji: TextView? = null
    private var tvAppFilterTitle: TextView? = null
    private var btnClearAppFilter: View? = null

    // Reconocimiento de voz en la burbuja
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // Estado del Filtro Inteligente de App
    data class CurrentAppFilter(
        val packageName: String,
        val appLabel: String,
        val emoji: String
    )
    private var activeAppFilter: CurrentAppFilter? = null
    private var cachedAllNotes: List<NoteEntity> = emptyList()

    companion object {
        const val CHANNEL_ID = "channel_floating_bubble_service"
        const val NOTIFICATION_ID = 9001
        const val ACTION_STOP_SERVICE = "com.antigravity.floatingnotes.ACTION_STOP_BUBBLE"
        const val ACTION_APP_OPENED = "com.antigravity.floatingnotes.ACTION_APP_OPENED"
        const val ACTION_APP_EXITED = "com.antigravity.floatingnotes.ACTION_APP_EXITED"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_LABEL = "extra_app_label"

        fun handleForegroundAppChanged(context: Context, packageName: String, appLabel: String) {
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                action = ACTION_APP_OPENED
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_APP_LABEL, appLabel)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun handleForegroundAppExited(context: Context, packageName: String) {
            val intent = Intent(context, FloatingBubbleService::class.java).apply {
                action = ACTION_APP_EXITED
                putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            updateScreenDimensions()

            startForegroundNotification()

            // Si el usuario no tiene permisos para dibujar sobre otras apps, detener para evitar crash
            if (!Settings.canDrawOverlays(this)) {
                stopSelf()
                return
            }

            setupFloatingBubble()
            setupFloatingPopup()
            initSpeechRecognizer()
            observeDatabaseNotes()
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun updateScreenDimensions() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    private fun startForegroundNotification() {
        try {
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
                manager?.createNotificationChannel(channel)
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

        // Banner de App
        layoutAppFilterBanner = popupView?.findViewById(R.id.layout_app_filter_banner)
        tvAppFilterEmoji = popupView?.findViewById(R.id.tv_app_filter_emoji)
        tvAppFilterTitle = popupView?.findViewById(R.id.tv_app_filter_title)
        btnClearAppFilter = popupView?.findViewById(R.id.btn_clear_app_filter)

        btnClearAppFilter?.setOnClickListener {
            activeAppFilter = null
            layoutAppFilterBanner?.visibility = View.GONE
            updateOverlayUI(cachedAllNotes)
        }

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
                    isUrgentAlarm = parsed.isUrgent,
                    targetAppName = parsed.detectedAppName ?: activeAppFilter?.appLabel,
                    targetAppPackage = parsed.detectedAppPackage ?: activeAppFilter?.packageName
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
                cachedAllNotes = notes
                updateOverlayUI(notes)
            }
        }
    }

    private fun onAppOpened(packageName: String, appLabel: String) {
        serviceScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val allPending = withContext(Dispatchers.IO) {
                db.noteDao().getActiveNotesSync()
            }

            val matchingNotes = allPending.filter { it.matchesForegroundApp(packageName, appLabel) }
            if (matchingNotes.isNotEmpty()) {
                val (_, emoji) = AppHelper.getAppInfoForPackage(applicationContext, packageName)
                activeAppFilter = CurrentAppFilter(packageName, appLabel, emoji)
                // Se abre solo automáticamente y muestra sólo las tareas de esta app
                showPopup()
                updateOverlayUI(cachedAllNotes)
            }
        }
    }

    private fun onAppExited(packageName: String) {
        if (activeAppFilter?.packageName.equals(packageName, ignoreCase = true)) {
            // El usuario salió de la app asociada: cerrar el popup y limpiar el filtro
            activeAppFilter = null
            hidePopup()
            updateOverlayUI(cachedAllNotes)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateOverlayUI(notes: List<NoteEntity>) {
        val currentFilter = activeAppFilter

        // Filtrar tareas según la aplicación actualmente en primer plano (si aplica)
        val displayNotes = if (currentFilter != null) {
            val filtered = notes.filter { it.matchesForegroundApp(currentFilter.packageName, currentFilter.appLabel) }
            if (filtered.isEmpty()) {
                // Se cumplieron todas las tareas de la app: desbloquear el filtro y celebrar
                activeAppFilter = null
                layoutAppFilterBanner?.visibility = View.GONE
                Toast.makeText(applicationContext, "¡Todas las tareas de ${currentFilter.appLabel} cumplidas! 🎉", Toast.LENGTH_SHORT).show()
                notes
            } else {
                layoutAppFilterBanner?.visibility = View.VISIBLE
                tvAppFilterEmoji?.text = currentFilter.emoji
                tvAppFilterTitle?.text = "Modo ${currentFilter.appLabel} (${filtered.size})"
                filtered
            }
        } else {
            layoutAppFilterBanner?.visibility = View.GONE
            notes
        }

        val count = displayNotes.size

        if (count > 0) {
            tvPendingBadge?.visibility = View.VISIBLE
            tvPendingBadge?.text = if (count > 99) "99+" else count.toString()
        } else {
            tvPendingBadge?.visibility = View.GONE
        }

        tvPopupCount?.text = count.toString()
        containerTodoItems?.removeAllViews()

        if (displayNotes.isEmpty()) {
            tvEmptyState?.visibility = View.VISIBLE
            tvEmptyState?.text = if (currentFilter != null) {
                "✨ No hay tareas pendientes para ${currentFilter.appLabel}"
            } else {
                "✨ No tienes tareas pendientes"
            }
        } else {
            tvEmptyState?.visibility = View.GONE
            val inflater = LayoutInflater.from(this)

            for (note in displayNotes) {
                val itemView = inflater.inflate(R.layout.item_overlay_todo, containerTodoItems, false)
                val cb = itemView.findViewById<CheckBox>(R.id.cb_todo_complete)
                val tvTitle = itemView.findViewById<TextView>(R.id.tv_todo_title)
                val tvSubtitle = itemView.findViewById<TextView>(R.id.tv_todo_subtitle)
                val tvCategoryTag = itemView.findViewById<TextView>(R.id.tv_category_tag)
                val viewIndicator = itemView.findViewById<View>(R.id.view_category_indicator)
                try {
                    val color = Color.parseColor(note.colorHex)
                    viewIndicator?.setBackgroundColor(color)
                    tvCategoryTag.setTextColor(color)
                } catch (e: Exception) {
                    viewIndicator?.setBackgroundColor(Color.parseColor("#6366F1"))
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
                    } else if (!note.targetAppName.isNullOrBlank()) {
                        tvSubtitle.visibility = View.VISIBLE
                        tvSubtitle.text = "📱 Vinculada a ${note.targetAppName}"
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
        when (intent?.action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_APP_OPENED -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                val label = intent.getStringExtra(EXTRA_APP_LABEL) ?: pkg
                if (!pkg.isNullOrBlank()) {
                    onAppOpened(pkg, label ?: pkg)
                }
            }
            ACTION_APP_EXITED -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                if (!pkg.isNullOrBlank()) {
                    onAppExited(pkg)
                }
            }
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
