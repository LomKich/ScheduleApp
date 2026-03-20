package com.schedule.app.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.schedule.app.ui.screens.*
import com.schedule.app.ui.theme.AppColors
import com.schedule.app.ui.theme.ThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("sapp_prefs", Context.MODE_PRIVATE)

    // ── Тема ──────────────────────────────────────────────────────────────────
    val themeState = ThemeState(
        AppColors.themes.firstOrNull {
            it.id == (prefs.getString("theme_id", "orange") ?: "orange")
        } ?: AppColors.themes.first()
    )

    fun setTheme(id: String) {
        val theme = AppColors.themes.firstOrNull { it.id == id } ?: return
        themeState.current = theme
        prefs.edit().putString("theme_id", id).apply()
    }

    // ── Настройки ─────────────────────────────────────────────────────────────
    var yandexUrl by mutableStateOf(
        prefs.getString("yandex_url", "https://disk.yandex.ru/d/mjhoc7kysmQEuQ") ?: ""
    )
    var isMuted by mutableStateOf(prefs.getBoolean("muted", true))
    var isTeacher by mutableStateOf(prefs.getBoolean("is_teacher", false))
    var isGlassMode by mutableStateOf(false)

    fun setUrl(url: String) {
        yandexUrl = url
        prefs.edit().putString("yandex_url", url).apply()
    }

    fun toggleMute() {
        isMuted = !isMuted
        prefs.edit().putBoolean("muted", isMuted).apply()
    }

    fun setMode(teacher: Boolean) {
        isTeacher = teacher
        prefs.edit().putBoolean("is_teacher", teacher).apply()
    }

    // ── Файлы ──────────────────────────────────────────────────────────────────
    var files by mutableStateOf<List<ScheduleFile>>(emptyList())
    var selectedFile by mutableStateOf<ScheduleFile?>(null)
    var isLoading by mutableStateOf(false)
    var loadProgress by mutableStateOf(0f)
    var statusText by mutableStateOf("")

    fun loadFiles() {
        if (yandexUrl.isEmpty()) return
        viewModelScope.launch {
            isLoading = true
            statusText = "Загружаю файлы..."
            loadProgress = 0.3f
            try {
                val result = withContext(Dispatchers.IO) {
                    fetchYandexFiles(yandexUrl)
                }
                files = result
                loadProgress = 1f
                statusText = "Найдено: ${result.size} файлов"
                kotlinx.coroutines.delay(1200)
                loadProgress = 0f
                statusText = ""
            } catch (e: Exception) {
                loadProgress = 0f
                statusText = "❌ ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // ── Группы ────────────────────────────────────────────────────────────────
    var groups by mutableStateOf<List<String>>(emptyList())
    var selectedGroup by mutableStateOf<String?>(
        prefs.getString("last_group", null)
    )

    fun selectFile(file: ScheduleFile) {
        selectedFile = file
        // Группы грузятся в реальном приложении через парсинг .docx
        // Здесь — демо
        groups = listOf(
            "МПД-2-24", "МПД-1-24", "ПК-3-23", "ПК-2-23", "ИС-1-24",
            "ИС-2-23", "ТЭ-1-24", "ТЭ-2-23", "БД-1-24", "СА-3-22",
        )
    }

    fun selectGroup(group: String) {
        selectedGroup = group
        prefs.edit().putString("last_group", group).apply()
        loadSchedule(group)
    }

    // ── Расписание ────────────────────────────────────────────────────────────
    var scheduleDays by mutableStateOf<List<ScheduleDay>>(emptyList())

    private fun loadSchedule(group: String) {
        // Демо-данные — в реальном приложении парсинг .docx через тот же код
        viewModelScope.launch {
            isLoading = true
            kotlinx.coroutines.delay(600)
            scheduleDays = buildDemoSchedule(group)
            isLoading = false
        }
    }

    // ── Звонки ────────────────────────────────────────────────────────────────
    val bellSchedules: List<BellSchedule> by lazy {
        val now = java.util.Calendar.getInstance()
        val dow = now.get(java.util.Calendar.DAY_OF_WEEK) // 1=вс, 2=пн, 7=сб

        listOf(
            BellSchedule("Понедельник", dow == 2, listOf(
                BellPeriod("I",   "09:00","09:45","09:50","10:35", isNow = dow == 2 && isNowPair(9*60, 10*60+35)),
                BellPeriod("II",  "10:45","11:30","11:35","12:20"),
                BellPeriod("III", "12:50","13:35","13:40","14:25"),
                BellPeriod("IV",  "14:35","15:35", null, null),
                BellPeriod("V",   "15:45","16:45", null, null),
                BellPeriod("VI",  "16:55","17:55", null, null),
            )),
            BellSchedule("Вторник – Пятница", dow in 3..6, listOf(
                BellPeriod("I",   "08:30","09:15","09:20","10:05", isNow = dow in 3..6 && isNowPair(8*60+30, 10*60+5)),
                BellPeriod("II",  "10:15","11:00","11:05","11:50"),
                BellPeriod("III", "12:20","13:05","13:10","13:55"),
                BellPeriod("IV",  "14:05","15:05", null, null),
                BellPeriod("V",   "15:15","16:15", null, null),
                BellPeriod("VI",  "16:25","17:25", null, null),
            )),
            BellSchedule("Суббота", dow == 7, listOf(
                BellPeriod("I",   "08:30","09:30", null, null),
                BellPeriod("II",  "09:40","10:40", null, null),
                BellPeriod("III", "10:50","11:50", null, null),
                BellPeriod("IV",  "12:00","13:00", null, null),
                BellPeriod("V",   "13:10","14:10", null, null),
                BellPeriod("VI",  "14:20","15:20", null, null),
            )),
        )
    }

    // ── Домашнее задание ──────────────────────────────────────────────────────
    var hwItems by mutableStateOf<List<HomeworkItem>>(emptyList())
        private set

    init {
        loadHw()
    }

    private fun loadHw() {
        val raw = prefs.getString("hw_v2", "[]") ?: "[]"
        hwItems = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HomeworkItem(
                    id       = o.getLong("id"),
                    subject  = o.optString("subject"),
                    task     = o.optString("task"),
                    deadline = o.optString("deadline"),
                    urgent   = o.optBoolean("urgent"),
                    done     = o.optBoolean("done"),
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun saveHw(items: List<HomeworkItem>) {
        val arr = JSONArray()
        items.forEach { h ->
            arr.put(JSONObject().apply {
                put("id", h.id)
                put("subject", h.subject)
                put("task", h.task)
                put("deadline", h.deadline)
                put("urgent", h.urgent)
                put("done", h.done)
            })
        }
        prefs.edit().putString("hw_v2", arr.toString()).apply()
        hwItems = items
    }

    fun toggleHwDone(id: Long) {
        saveHw(hwItems.map { if (it.id == id) it.copy(done = !it.done) else it })
    }

    fun deleteHw(id: Long) {
        saveHw(hwItems.filter { it.id != id })
    }

    fun addHw(subject: String, task: String, deadline: String, urgent: Boolean) {
        val newItem = HomeworkItem(
            id = System.currentTimeMillis(),
            subject = subject, task = task,
            deadline = deadline, urgent = urgent, done = false,
        )
        saveHw(listOf(newItem) + hwItems)
    }

    // ── Утилиты ───────────────────────────────────────────────────────────────

    private fun isNowPair(startMin: Int, endMin: Int): Boolean {
        val now = java.util.Calendar.getInstance()
        val nowMin = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        return nowMin in startMin until endMin
    }

    private suspend fun fetchYandexFiles(publicKey: String): List<ScheduleFile> {
        val url = "https://cloud-api.yandex.net/v1/disk/public/resources" +
                "?public_key=${java.net.URLEncoder.encode(publicKey, "UTF-8")}&limit=100"
        val conn = URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout    = 10_000
        val body = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(body)
        val items = json.getJSONObject("_embedded").getJSONArray("items")
        return (0 until items.length())
            .map { items.getJSONObject(it) }
            .filter { it.getString("type") == "file" && it.getString("name").matches(Regex(".*\\.(doc|docx)", RegexOption.IGNORE_CASE)) }
            .map { ScheduleFile(name = it.getString("name"), path = it.optString("path", "/"+it.getString("name")), size = it.optLong("size", 0)) }
    }

    private fun buildDemoSchedule(group: String): List<ScheduleDay> {
        val nowMin = run {
            val c = java.util.Calendar.getInstance()
            c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
        }
        fun isNow(s: Int, e: Int) = nowMin in s until e

        return listOf(
            ScheduleDay(
                header = "ПЯТНИЦА 21.03.2025 (2-я неделя)",
                pairs = listOf(
                    Pair("I",   "08:30","09:15", "09:20","10:05", "Математика",  "Иванова А.В.", "ауд. 204", isNow = isNow(8*60+30, 10*60+5)),
                    Pair("II",  "10:15","11:00", "11:05","11:50", "Информатика", "Петров И.С.", "ауд. 301 (пк)", isNow = isNow(10*60+15, 11*60+50), isNext = false),
                    Pair("III", "12:20","13:05", "13:10","13:55", "История",     "Сидорова Е.М.", "ауд. 115"),
                    Pair("IV",  "14:05","15:05", null, null, "Окно", isWindow = true),
                    Pair("V",   "15:15","16:15", null, null, "Физкультура", "Козлов Д.А.", "спортзал"),
                ),
            )
        )
    }
}

    // ── Социальные данные ────────────────────────────────────────────────────
    var userProfile by mutableStateOf<com.schedule.app.ui.screens.UserProfile?>(null)
    var p2pConnected by mutableStateOf(false)
    var notifEnabled by mutableStateOf(false)
    var bgServiceEnabled by mutableStateOf(false)
    var friends by mutableStateOf<List<com.schedule.app.ui.screens.FriendEntry>>(emptyList())
    var leaderboard by mutableStateOf<List<com.schedule.app.ui.screens.LeaderboardEntry>>(
        listOf(
            com.schedule.app.ui.screens.LeaderboardEntry(1, "pro_gamer", "Алексей", "🏆", 9999, "Тетрис"),
            com.schedule.app.ui.screens.LeaderboardEntry(2, "snake_king", "Мария", "🐍", 7420, "Змейка"),
            com.schedule.app.ui.screens.LeaderboardEntry(3, "pong_master", "Дмитрий", "🏓", 5800, "Понг"),
        )
    )

    // ── Поля регистрации / входа ─────────────────────────────────────────────
    var regName     by mutableStateOf("")
    var regUsername by mutableStateOf("")
    var regUsernameStatus by mutableStateOf("")
    var regUsernameValid  by mutableStateOf(false)
    var regEmoji    by mutableStateOf("😊")
    var regPassword by mutableStateOf("")
    var regError    by mutableStateOf("")
    var regEnabled  by mutableStateOf(false)
    var authUsername by mutableStateOf("")
    var authPassword by mutableStateOf("")
    var authError    by mutableStateOf("")

    // ── Поля редактирования профиля ──────────────────────────────────────────
    var editName   by mutableStateOf("")
    var editBio    by mutableStateOf("")
    var editEmoji  by mutableStateOf("😊")
    var editStatus by mutableStateOf("online")

    fun startEditProfile() {
        val p = userProfile ?: return
        editName   = p.name
        editBio    = p.bio
        editEmoji  = p.avatar
        editStatus = p.status
    }

    fun saveEditProfile() {
        userProfile = userProfile?.copy(
            name   = editName.trim().ifEmpty { userProfile?.name ?: "" },
            bio    = editBio.trim(),
            avatar = editEmoji.ifEmpty { "😊" },
            status = editStatus,
        )
    }

    fun onRegUsernameChange(v: String) {
        regUsername = v.trim()
        regUsernameValid  = v.length >= 3
        regUsernameStatus = when {
            v.isEmpty()   -> ""
            v.length < 3  -> "Минимум 3 символа"
            else          -> "✓ Доступен"
        }
        checkRegEnabled()
    }

    fun onRegNameChange(v: String)     { regName = v; checkRegEnabled() }
    fun onRegEmojiChange(v: String)    { regEmoji = v }
    fun onRegPasswordChange(v: String) { regPassword = v; checkRegEnabled() }

    private fun checkRegEnabled() {
        regEnabled = regName.trim().isNotEmpty() &&
                regUsername.trim().length >= 3 &&
                regPassword.length >= 4
    }

    fun onRegRandomEmoji() {
        val pool = listOf("😊","😎","🤓","🥳","😏","🤩","🦊","🐺","🦋","🐸",
                          "🐱","🐶","🦁","🐼","🦄","🐉","🦅","🎭","🤖","👻",
                          "💀","🎃","⚡","🔥","💎","🌙","⭐","🌈","🎵","🎮","🏆")
        regEmoji = pool.random()
    }

    fun onEditRandomEmoji() {
        val pool = listOf("😊","😎","🤓","🥳","😏","🤩","🦊","🐺","🦋","🐸",
                          "🐱","🐶","🦁","🐼","🦄","🐉","🦅","🎭","🤖","👻",
                          "💀","🎃","⚡","🔥","💎","🌙","⭐","🌈","🎵","🎮","🏆")
        editEmoji = pool.random()
    }

    fun doRegister() {
        if (!regEnabled) return
        val existingKey = "sapp_acc_${regUsername.lowercase().trim()}"
        val existing = prefs.getString(existingKey, null)
        if (existing != null) {
            regError = "Этот никнейм занят"
            return
        }
        val p = com.schedule.app.ui.screens.UserProfile(
            name     = regName.trim(),
            username = regUsername.trim().lowercase(),
            avatar   = regEmoji.ifEmpty { "😊" },
            status   = "online",
        )
        prefs.edit().putString(existingKey, regName.trim()).apply()
        prefs.edit().putString("sapp_profile", "${p.username}|${p.name}|${p.avatar}").apply()
        userProfile = p
        regError = ""
    }

    fun doLogin() {
        val key = "sapp_acc_${authUsername.lowercase().trim()}"
        val stored = prefs.getString(key, null)
        if (stored == null) {
            authError = "Пользователь не найден"
            return
        }
        userProfile = com.schedule.app.ui.screens.UserProfile(
            name     = stored,
            username = authUsername.trim().lowercase(),
            avatar   = "😊",
            status   = "online",
        )
        authError = ""
    }

    // Friends search
    var friendsSearchQuery by mutableStateOf("")

    fun toggleBgService() { bgServiceEnabled = !bgServiceEnabled }
    fun toggleNotif()     { notifEnabled = !notifEnabled }
