package com.schedule.app.jarvis

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * Мост между jarvis_relay и обычным чатом в приложении.
 *
 * Задачи:
 *  1. Обеспечить что "Джарвис" ВСЕГДА есть в списке чатов (закреплённый, сверху)
 *  2. Когда пользователь пишет в чат Джарвиса → отправить через jarvis_relay
 *  3. Ответ Джарвиса → воспроизвести голосом + показать как пузырь
 *  4. Синхронизировать историю relay ↔ локальный список сообщений
 *
 * Использование в AppViewModel:
 *   - val bridge = JarvisChatBridge(context, viewModelScope)
 *   - bridge.injectJarvisChat(messengerChats)       // добавляет Джарвиса в список
 *   - bridge.sendMessage(text, onReply)              // отправить сообщение
 *   - bridge.startHistoryPoller(onNewMessages)       // фоновая синхронизация истории
 */
class JarvisChatBridge(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        const val TAG           = "JarvisBridge"
        const val JARVIS_ID     = "__jarvis__"   // id в списке чатов
        const val JARVIS_NAME   = "Джарвис"
        const val JARVIS_AVATAR = "🤖"
    }

    // ID последнего прочитанного сообщения из relay
    private var lastRelayTs: Long = 0
    private var pollerJob: Job? = null

    // ── ChatPreview-заглушка (всегда вверху списка) ───────────────────────────

    /**
     * Возвращает ChatPreview для Джарвиса.
     * Вставляй в начало списка messengerChats перед отображением.
     */
    fun buildJarvisPreview(
        lastMessage: String = "Скажи «Джарвис» или напиши сюда",
        lastTs: Long = 0L,
        isOnline: Boolean = false,
    ): com.schedule.app.ui.screens.ChatPreview {
        return com.schedule.app.ui.screens.ChatPreview(
            id              = JARVIS_ID,
            name            = JARVIS_NAME,
            avatar          = JARVIS_AVATAR,
            avatarType      = "emoji",
            lastMessage     = lastMessage,
            lastMessageTime = lastTs,
            isOnline        = isOnline,
            isPinned        = true,
        )
    }

    // ── Отправить сообщение Джарвису ──────────────────────────────────────────

    /**
     * Отправить текст и получить ответ.
     * @param text       сообщение пользователя
     * @param onReply    (replyText, audioB64) — вызывается когда ответ готов
     * @param onError    вызывается при ошибке сети / таймауте
     */
    fun sendMessage(
        text: String,
        playAudio: Boolean = true,
        onReply: (text: String, audioB64: String?) -> Unit,
        onError: ((String) -> Unit)? = null,
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                val (replyText, audioB64) = JarvisRelayClient.sendAndAwaitReply(
                    context = context,
                    text    = text
                )
                onReply(replyText, audioB64)

                if (playAudio && audioB64 != null) {
                    withContext(Dispatchers.Main) {
                        JarvisAudioPlayer.playBase64Wav(context, audioB64)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage error: ${e.message}")
                onError?.invoke(e.message ?: "Неизвестная ошибка")
            }
        }
    }

    // ── Polling истории relay → список сообщений ──────────────────────────────

    /**
     * Запустить фоновый поллер истории сессии.
     * @param onNewMessages  (fromJarvis: Boolean, text: String, audioB64: String?)
     *                       вызывается для каждого нового сообщения
     */
    fun startHistoryPoller(
        onNewMessages: (List<RelayMessage>) -> Unit
    ) {
        pollerJob?.cancel()
        pollerJob = scope.launch(Dispatchers.IO) {
            val session = JarvisRelayClient.getOrCreateSession(context)
            while (isActive) {
                try {
                    val rows = JarvisRelayClient.supabaseGet(
                        "jarvis_relay",
                        "session_id=eq.$session" +
                        "&status=in.(pending,processing,done)" +
                        "&order=created_at.asc" +
                        "&limit=100" +
                        "&select=id,role,content,audio_b64,created_at"
                    )
                    val arr = JSONArray(rows)
                    if (arr.length() > 0) {
                        val msgs = mutableListOf<RelayMessage>()
                        for (i in 0 until arr.length()) {
                            val row = arr.getJSONObject(i)
                            val tsStr = row.optString("created_at", "")
                            val ts = parseIsoTs(tsStr)
                            if (ts > lastRelayTs) {
                                msgs.add(RelayMessage(
                                    id        = row.optString("id"),
                                    fromJarvis= row.optString("role") == "jarvis",
                                    text      = row.optString("content"),
                                    audioB64  = row.optString("audio_b64", "").ifBlank { null },
                                    ts        = ts,
                                ))
                                if (ts > lastRelayTs) lastRelayTs = ts
                            }
                        }
                        if (msgs.isNotEmpty()) {
                            withContext(Dispatchers.Main) { onNewMessages(msgs) }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "historyPoller error: ${e.message}")
                }
                delay(3_000)
            }
        }
    }

    fun stopHistoryPoller() {
        pollerJob?.cancel()
        pollerJob = null
    }

    // ── Утилиты ───────────────────────────────────────────────────────────────

    private fun parseIsoTs(iso: String): Long {
        return try {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .parse(iso.take(19))?.time ?: 0L
        } catch (e: Exception) { 0L }
    }

    // ── Модель сообщения relay ────────────────────────────────────────────────

    data class RelayMessage(
        val id:         String,
        val fromJarvis: Boolean,
        val text:       String,
        val audioB64:   String?,
        val ts:         Long,
    )
}
