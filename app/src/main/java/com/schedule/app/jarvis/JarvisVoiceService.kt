package com.schedule.app.jarvis

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.schedule.app.MainActivity
import kotlinx.coroutines.*

/**
 * Foreground-сервис: всегда держит микрофон открытым и ждёт wake-word «Джарвис».
 *
 * Принцип работы (аналог Google Assistant):
 *   1. SpeechRecognizer слушает ~3-4 сек, анализирует результат
 *   2. Если нет wake-word — сразу перезапускает прослушивание
 *   3. Если слышит «джарвис» — отправляет команду через JarvisRelayClient
 *   4. Воспроизводит ответ через JarvisAudioPlayer (с AudioFocus)
 *   5. Возвращается в режим прослушивания
 *
 * Активируется один раз (через AppViewModel.activateJarvisProtocol())
 * и после этого автоматически стартует при каждой загрузке телефона.
 *
 * ВАЖНО: Требует разрешения RECORD_AUDIO и FOREGROUND_SERVICE_MICROPHONE.
 *        На Android 10+ SpeechRecognizer работает только из main thread,
 *        поэтому вся логика распознавания — на mainHandler.
 */
class JarvisVoiceService : Service() {

    companion object {
        const val TAG         = "JarvisVoice"
        const val CHANNEL_ID  = "jarvis_voice_ch"
        const val NOTIF_ID    = 7734
        const val NOTIF_REPLY = 7735
        const val WAKE_WORD   = "джарвис"

        const val ACTION_STOP = "com.schedule.app.JARVIS_STOP"

        var isRunning = false
            private set

        /** Запустить сервис */
        fun start(context: Context) {
            val intent = Intent(context, JarvisVoiceService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Остановить сервис */
        fun stop(context: Context) {
            context.startService(
                Intent(context, JarvisVoiceService::class.java).setAction(ACTION_STOP)
            )
        }
    }

    // ── Состояние ─────────────────────────────────────────────────────────────

    private val scope        = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ioScope      = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler  = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var isProcessing = false   // идёт запрос к Джарвису — не перезапускаем распознавание
    private var restartJob: Job? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Слушаю..."))
        Log.i(TAG, "Сервис запущен")
        mainHandler.post { startListening() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Остановка по команде")
            stopSelf()
            return START_NOT_STICKY
        }
        // START_STICKY — Android перезапустит сервис если убьёт его
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        restartJob?.cancel()
        scope.cancel()
        ioScope.cancel()
        recognizer?.destroy()
        recognizer = null
        Log.i(TAG, "Сервис остановлен")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Распознавание речи ────────────────────────────────────────────────────

    /** Запустить один цикл прослушивания. Должен вызываться из main thread. */
    private fun startListening() {
        if (!isRunning || isProcessing) return

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "SpeechRecognizer недоступен")
            updateNotification("Распознавание речи недоступно")
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(jarvisListener)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                     RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // Большие паузы чтобы слушать подольше
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,   3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 300L)
        }

        recognizer?.startListening(intent)
    }

    private val jarvisListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            updateNotification("Слушаю...")
        }

        override fun onBeginningOfSpeech() {
            updateNotification("Слышу речь...")
        }

        override fun onResults(results: Bundle?) {
            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?: emptyList()

            // Проверяем все варианты распознавания
            val wakeMatch = matches.firstOrNull { it.lowercase().contains(WAKE_WORD) }

            if (wakeMatch != null) {
                val full = wakeMatch.lowercase()
                val cmd  = full.substringAfter(WAKE_WORD).trim()
                    .replaceFirst(Regex("^[,.:!?\\s]+"), "")
                Log.i(TAG, "Wake-word! Команда: '$cmd'")
                onWakeWordDetected(cmd)
            } else {
                scheduleRestart(200)
            }
        }

        override fun onError(error: Int) {
            val delay = when (error) {
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY    -> 2000L
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    updateNotification("Нет разрешения на микрофон")
                    Log.e(TAG, "Нет разрешения RECORD_AUDIO")
                    5000L
                }
                SpeechRecognizer.ERROR_NO_MATCH           -> 100L   // тишина — норма
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT     -> 100L
                else -> {
                    Log.w(TAG, "Recognition error: $error")
                    500L
                }
            }
            scheduleRestart(delay)
        }

        override fun onEndOfSpeech()              {}
        override fun onRmsChanged(rmsdB: Float)   {}
        override fun onBufferReceived(buf: ByteArray?) {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun scheduleRestart(delayMs: Long) {
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(delayMs)
            if (isRunning && !isProcessing) {
                startListening()
            }
        }
    }

    // ── Обработка wake-word ───────────────────────────────────────────────────

    private fun onWakeWordDetected(command: String) {
        if (isProcessing) return
        isProcessing = true
        updateNotification("Обрабатываю...")

        ioScope.launch {
            try {
                val text = command.ifBlank { "я слушаю" }
                val (replyText, audioB64) = JarvisRelayClient.sendAndAwaitReply(
                    context = this@JarvisVoiceService,
                    text    = text
                )
                Log.i(TAG, "Ответ: '${replyText.take(80)}'")

                // Воспроизводим ответ в main thread (AudioFocus)
                withContext(Dispatchers.Main) {
                    showReplyNotification(replyText)
                    if (audioB64 != null) {
                        JarvisAudioPlayer.playBase64Wav(
                            context = this@JarvisVoiceService,
                            audioB64 = audioB64
                        ) {
                            // После воспроизведения снова слушаем
                            isProcessing = false
                            updateNotification("Слушаю...")
                            mainHandler.postDelayed({ startListening() }, 300)
                        }
                    } else {
                        isProcessing = false
                        updateNotification("Слушаю...")
                        mainHandler.postDelayed({ startListening() }, 300)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка при обработке команды: ${e.message}")
                withContext(Dispatchers.Main) {
                    updateNotification("Ошибка связи — пробую снова")
                    isProcessing = false
                    scheduleRestart(3000)
                }
            }
        }
    }

    // ── Уведомления ───────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Канал для постоянного статусного уведомления — LOW (без звука)
            val statusCh = NotificationChannel(
                CHANNEL_ID,
                "Джарвис — голосовой режим",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Постоянное прослушивание wake-word «Джарвис»"
                setShowBadge(false)
            }
            // Канал для ответов — HIGH (со звуком/всплывашкой)
            val replyCh = NotificationChannel(
                "${CHANNEL_ID}_reply",
                "Джарвис — ответы",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ответы Джарвиса на команды"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannels(listOf(statusCh, replyCh))
        }
    }

    private fun buildNotification(status: String): Notification {
        // Тап на уведомление → открываем приложение
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_jarvis", true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, JarvisVoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("⚡ Джарвис активен")
            .setContentText(status)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_delete, "Выключить", stopIntent)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(status))
    }

    private fun showReplyNotification(text: String) {
        val n = NotificationCompat.Builder(this, "${CHANNEL_ID}_reply")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Джарвис")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_REPLY, n)
    }
}
