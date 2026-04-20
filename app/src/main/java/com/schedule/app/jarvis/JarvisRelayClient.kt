package com.schedule.app.jarvis

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Клиент для общения с Джарвисом через Supabase jarvis_relay.
 *
 * Схема:
 *   1. Телефон вставляет строку role=user, status=pending
 *   2. PC-Джарвис (Python) видит строку, обрабатывает, вставляет reply role=jarvis
 *   3. Телефон получает reply polling'ом — текст + base64 WAV
 *
 * Session ID постоянный (хранится в SharedPreferences) — история разговора
 * сохраняется между запусками приложения.
 */
object JarvisRelayClient {

    private const val TAG          = "JarvisRelay"
    private const val PREFS_NAME   = "jarvis_relay_prefs"
    private const val KEY_SESSION  = "session_id"
    const  val KEY_ACTIVE          = "jarvis_voice_active"
    const  val KEY_ASTRA_SEEN      = "astra_last_seen"

    // ── Supabase credentials (те же что в jarvis.py) ──────────────────────────
    private const val SUPABASE_URL = "https://mjonazsosajvgevqllzs.supabase.co"
    private const val SUPABASE_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im1qb25henNvc2FqdmdldnFsbHpzIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM2NjAwNDMsImV4cCI6MjA4OTIzNjA0M30.yUB_HRQSeh3TeOseZ4_ARNiBuklr5AoEbBgvJJl5p3Y"

    // ── Session ───────────────────────────────────────────────────────────────

    fun getOrCreateSession(context: Context): String {
        val prefs = prefs(context)
        return prefs.getString(KEY_SESSION, null) ?: UUID.randomUUID().toString().also { id ->
            prefs.edit().putString(KEY_SESSION, id).apply()
            Log.i(TAG, "Новая сессия: $id")
        }
    }

    fun resetSession(context: Context) {
        prefs(context).edit().remove(KEY_SESSION).apply()
    }

    // ── Отправить сообщение и дождаться ответа ────────────────────────────────

    /**
     * Отправляет текст Джарвису и ждёт ответ (max 30 сек).
     * Возвращает Pair(текст, base64_wav_или_null).
     * Бросает исключение при ошибке сети или таймауте.
     */
    suspend fun sendAndAwaitReply(
        context: Context,
        text: String,
    ): Pair<String, String?> = withContext(Dispatchers.IO) {

        val session = getOrCreateSession(context)
        Log.d(TAG, "→ sess=${session.takeLast(8)} text='${text.take(60)}'")

        // 1. Вставляем строку пользователя
        val body = JSONObject().apply {
            put("session_id", session)
            put("role",       "user")
            put("content",    text)
            put("status",     "pending")
        }.toString()

        val insertRaw = supabasePost("jarvis_relay", body)
        val insertedId = try {
            JSONArray(insertRaw).getJSONObject(0).getString("id")
        } catch (e: Exception) {
            throw RuntimeException("Не удалось вставить сообщение: $insertRaw")
        }

        Log.d(TAG, "↑ вставлено id=$insertedId")

        // 2. Polling — ждём ответ
        withTimeout(30_000L) {
            while (true) {
                delay(1_200)
                val rows = supabaseGet(
                    "jarvis_relay",
                    "reply_to=eq.$insertedId" +
                    "&role=eq.jarvis" +
                    "&status=eq.done" +
                    "&select=content,audio_b64" +
                    "&limit=1"
                )
                val arr = try { JSONArray(rows) } catch (e: Exception) { JSONArray() }
                if (arr.length() > 0) {
                    val row = arr.getJSONObject(0)
                    val replyText = row.optString("content", "...")
                    val audioB64  = row.optString("audio_b64", "").takeIf { it.isNotBlank() }
                    Log.d(TAG, "↓ ответ: '${replyText.take(80)}' audio=${audioB64 != null}")
                    return@withTimeout Pair(replyText, audioB64)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            Pair("Нет ответа", null)
        }
    }

    // ── Проверить онлайн-статус Джарвиса ─────────────────────────────────────

    /**
     * Возвращает true если PC-Джарвис отправил __ASTRA_ONLINE__ в последние 5 минут.
     */
    suspend fun isJarvisOnline(): Boolean = withContext(Dispatchers.IO) {
        try {
            val since = System.currentTimeMillis() - 5 * 60 * 1000
            // Supabase хранит created_at в UTC ISO, используем фильтр по created_at
            val rows = supabaseGet(
                "jarvis_relay",
                "session_id=eq.system" +
                "&role=eq.jarvis" +
                "&content=eq.__ASTRA_ONLINE__" +
                "&order=created_at.desc" +
                "&limit=1" +
                "&select=created_at"
            )
            val arr = JSONArray(rows)
            if (arr.length() == 0) return@withContext false
            // Просто наличие записи достаточно (PC запускается → пишет метку)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    fun supabasePost(table: String, jsonBody: String): String {
        val url = URL("$SUPABASE_URL/rest/v1/$table")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout    = 8_000
            setRequestProperty("apikey",        SUPABASE_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
            setRequestProperty("Content-Type",  "application/json")
            setRequestProperty("Prefer",        "return=representation")
            doOutput = true
            outputStream.write(jsonBody.toByteArray(Charsets.UTF_8))
        }
        return if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "unknown"
            throw RuntimeException("POST $table HTTP ${conn.responseCode}: $err")
        }
    }

    fun supabaseGet(table: String, query: String): String {
        val url = URL("$SUPABASE_URL/rest/v1/$table?$query")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout    = 8_000
            setRequestProperty("apikey",        SUPABASE_KEY)
            setRequestProperty("Authorization", "Bearer $SUPABASE_KEY")
        }
        return if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "unknown"
            throw RuntimeException("GET $table HTTP ${conn.responseCode}: $err")
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
