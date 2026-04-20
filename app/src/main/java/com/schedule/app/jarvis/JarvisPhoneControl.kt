package com.schedule.app.jarvis

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Управление телефоном через Джарвиса (ADB на ПК).
 *
 * Работает так:
 *   Телефон → relay (role=user, content="__PHONE__:screenshot")
 *   ПК-Джарвис → выполняет ADB-команду → relay (role=jarvis, content=результат)
 *
 * Требования:
 *   - Телефон подключён к ПК по USB (adb devices) ИЛИ по Wi-Fi (adb tcpip 5555)
 *   - Протокол Астра активирован (jarvis.py запущен)
 *
 * Использование:
 *   JarvisPhoneControl.sendCommand(context, "screenshot") { result -> ... }
 *   JarvisPhoneControl.sendCommand(context, "tap 540 960") { result -> ... }
 *   JarvisPhoneControl.sendCommand(context, "app com.whatsapp") { result -> ... }
 */
object JarvisPhoneControl {

    private const val TAG    = "JarvisPhone"
    private const val PREFIX = "__PHONE__:"

    // ── Доступные команды ────────────────────────────────────────────────────

    enum class Cmd(val value: String, val label: String) {
        SCREENSHOT  ("screenshot",   "📸 Скриншот"),
        HOME        ("home",         "🏠 Домой"),
        BACK        ("back",         "◀ Назад"),
        VOLUME_UP   ("volume_up",    "🔊 Громче"),
        VOLUME_DOWN ("volume_down",  "🔈 Тише"),
        BATTERY     ("battery",      "🔋 Батарея"),
    }

    /** Отправить команду управления и получить результат. */
    fun sendCommand(
        context: Context,
        scope: CoroutineScope,
        command: String,
        onResult: (String) -> Unit,
        onError: ((String) -> Unit)? = null,
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "→ cmd: $command")
                val (result, _) = JarvisRelayClient.sendAndAwaitReply(
                    context = context,
                    text    = "$PREFIX$command",
                )
                Log.d(TAG, "← result: $result")
                onResult(result)
            } catch (e: Exception) {
                Log.e(TAG, "sendCommand error: ${e.message}")
                onError?.invoke(e.message ?: "Ошибка")
            }
        }
    }

    /** Тап по координатам экрана. */
    fun tap(context: Context, scope: CoroutineScope, x: Int, y: Int,
            onResult: (String) -> Unit) =
        sendCommand(context, scope, "tap $x $y", onResult)

    /** Свайп. */
    fun swipe(context: Context, scope: CoroutineScope,
              x1: Int, y1: Int, x2: Int, y2: Int,
              onResult: (String) -> Unit) =
        sendCommand(context, scope, "swipe $x1 $y1 $x2 $y2", onResult)

    /** Открыть приложение по package name. */
    fun openApp(context: Context, scope: CoroutineScope,
                packageName: String, onResult: (String) -> Unit) =
        sendCommand(context, scope, "app $packageName", onResult)

    /** Ввести текст. */
    fun inputText(context: Context, scope: CoroutineScope,
                  text: String, onResult: (String) -> Unit) =
        sendCommand(context, scope, "text $text", onResult)
}
