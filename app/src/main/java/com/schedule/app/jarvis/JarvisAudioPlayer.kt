package com.schedule.app.jarvis

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Воспроизводит WAV-ответ Джарвиса.
 *
 * ПРИЧИНА БАГА (играло 1-2 фразы, потом тишина):
 *   Старый код не запрашивал AudioFocus перед воспроизведением.
 *   Android автоматически "уводил" фокус у MediaPlayer когда другое
 *   приложение (уведомление, музыка) запрашивало его — и воспроизведение
 *   обрывалось. Плюс временные файлы удалялись до окончания воспроизведения.
 *
 * ИСПРАВЛЕНИЕ:
 *   - Запрашиваем AUDIOFOCUS_GAIN_TRANSIENT перед каждым воспроизведением
 *   - Держим фокус до OnCompletion/OnError
 *   - Удаляем tmp-файл только после завершения
 *   - Явный stop() предыдущего плеера перед новым
 */
object JarvisAudioPlayer {

    private const val TAG = "JarvisAudio"

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var currentTmp: File? = null

    // ─── Публичный API ───────────────────────────────────────────────────────

    /**
     * Воспроизвести WAV из base64-строки.
     * @param audioB64  base64-закодированный WAV-файл от XTTS
     * @param onDone    вызывается когда воспроизведение закончилось (или ошибка)
     */
    fun playBase64Wav(context: Context, audioB64: String, onDone: (() -> Unit)? = null) {
        // Останавливаем предыдущее воспроизведение
        stopPlayback(context)

        // Декодируем base64
        val bytes = try {
            Base64.decode(audioB64, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "base64 decode error: ${e.message}")
            onDone?.invoke()
            return
        }

        if (bytes.size < 44) { // WAV header минимум 44 байта
            Log.w(TAG, "WAV слишком мал: ${bytes.size} байт")
            onDone?.invoke()
            return
        }

        // Сохраняем во временный файл
        val tmp = File(context.cacheDir, "jarvis_tts_${System.currentTimeMillis()}.wav")
        try {
            FileOutputStream(tmp).use { it.write(bytes) }
        } catch (e: Exception) {
            Log.e(TAG, "write tmp error: ${e.message}")
            onDone?.invoke()
            return
        }
        currentTmp = tmp

        // Запрашиваем AudioFocus
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val granted = requestAudioFocus(am)
        if (!granted) {
            Log.w(TAG, "AudioFocus не получен")
            tmp.delete()
            onDone?.invoke()
            return
        }

        // Запускаем MediaPlayer
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(tmp.absolutePath)
                prepare()   // синхронно — файл уже локальный

                setOnCompletionListener {
                    Log.d(TAG, "воспроизведение завершено (${bytes.size / 1024} KB)")
                    cleanup(context)
                    onDone?.invoke()
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    cleanup(context)
                    onDone?.invoke()
                    true
                }

                start()
                Log.d(TAG, "играем ${bytes.size / 1024} KB WAV")
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer init error: ${e.message}")
            cleanup(context)
            onDone?.invoke()
        }
    }

    /** Остановить воспроизведение немедленно */
    fun stopPlayback(context: Context) {
        player?.runCatching {
            if (isPlaying) stop()
            release()
        }
        player = null
        releaseAudioFocus(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        currentTmp?.delete()
        currentTmp = null
    }

    // ─── Внутренние методы ───────────────────────────────────────────────────

    private fun cleanup(context: Context) {
        player?.runCatching { release() }
        player = null
        releaseAudioFocus(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        currentTmp?.delete()
        currentTmp = null
    }

    private fun requestAudioFocus(am: AudioManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .setWillPauseWhenDucked(false)
                .build()
                .also { focusRequest = it }
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun releaseAudioFocus(am: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
        focusRequest = null
    }
}
