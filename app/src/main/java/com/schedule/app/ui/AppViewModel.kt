package com.schedule.app.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.schedule.app.AppLogger
import com.schedule.app.SupabaseClient
import com.schedule.app.ui.screens.*
import com.schedule.app.ui.theme.AppColors
import com.schedule.app.ui.theme.ThemeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

// ── Модель сообщения ──────────────────────────────────────────────────────────
data class ChatMessage(
    val id: String,
    val chatKey: String,
    val fromUser: String,
    val toUser: String,
    val text: String,
    val ts: Long,
    val type: String = "text",
    val fileUrl: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("sapp_prefs", Context.MODE_PRIVATE)
    private val log   = AppLogger.get(app)
    val sb: SupabaseClient = SupabaseClient.get(app, log)

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

    // ── Шрифт ─────────────────────────────────────────────────────────────────
    var currentFontId by mutableStateOf(prefs.getString("font_id", "default") ?: "default")

    fun setFont(id: String) {
        currentFontId = id
        prefs.edit().putString("font_id", id).apply()
        showToast("🔤 Шрифт: ${com.schedule.app.ui.theme.AppColors.fonts.firstOrNull { it.id == id }?.name ?: id}")
    }

    // ── Liquid Glass optimization ──────────────────────────────────────────────
    var isGlassOptMode by mutableStateOf(prefs.getBoolean("glass_opt", false))

    fun toggleGlassOpt() {
        isGlassOptMode = !isGlassOptMode
        prefs.edit().putBoolean("glass_opt", isGlassOptMode).apply()
    }

    fun toggleGlass() {
        isGlassMode = !isGlassMode
        prefs.edit().putBoolean("glass_mode", isGlassMode).apply()
    }

    fun toggleBgBlur() {
        isBgBlurEnabled = !isBgBlurEnabled
        prefs.edit().putBoolean("bg_blur", isBgBlurEnabled).apply()
    }

    fun toggleIphoneEmoji() {
        isIphoneEmoji = !isIphoneEmoji
        prefs.edit().putBoolean("iphone_emoji", isIphoneEmoji).apply()
    }

    fun onBgImagePicked(uri: android.net.Uri, cr: android.content.ContentResolver) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    cr.openInputStream(uri)?.readBytes()
                } ?: return@launch
                val mime = cr.getType(uri) ?: "image/jpeg"
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val dataUri = "data:$mime;base64,$b64"
                bgImageData = dataUri
                hasBgImage = true
                prefs.edit().putString("bg_image_data", dataUri).apply()
            } catch (e: Exception) {
                log.e("AppViewModel", "onBgImagePicked error: ${e.message}")
            }
        }
    }

    fun removeBgImage() {
        bgImageData = null
        hasBgImage = false
        prefs.edit().remove("bg_image_data").apply()
    }

    fun checkHotPatch() {
        hotPatchStatus = "🔄 Проверяю обновления..."
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            hotPatchStatus = "✅ Обновлений нет"
            kotlinx.coroutines.delay(3000)
            hotPatchStatus = ""
        }
    }

    // ── Настройки ─────────────────────────────────────────────────────────────
    var yandexUrl by mutableStateOf(
        prefs.getString("yandex_url", "https://disk.yandex.ru/d/mjhoc7kysmQEuQ") ?: ""
    )
    var isMuted       by mutableStateOf(prefs.getBoolean("muted", true))
    var isTeacher     by mutableStateOf(prefs.getBoolean("is_teacher", false))
    var isGlassMode        by mutableStateOf(prefs.getBoolean("glass_mode", false))
    var hasBgImage         by mutableStateOf(prefs.getString("bg_image_data", null) != null)
    var bgImageData        by mutableStateOf(prefs.getString("bg_image_data", null))
    var isBgBlurEnabled    by mutableStateOf(prefs.getBoolean("bg_blur", true))
    var isIphoneEmoji      by mutableStateOf(prefs.getBoolean("iphone_emoji", false))
    var hotPatchStatus     by mutableStateOf("")

    fun setUrl(url: String)          { yandexUrl = url; prefs.edit().putString("yandex_url", url).apply() }
    fun toggleMute()                 { isMuted = !isMuted; prefs.edit().putBoolean("muted", isMuted).apply() }
    fun setMode(teacher: Boolean)    { isTeacher = teacher; prefs.edit().putBoolean("is_teacher", teacher).apply() }

    // ── Файлы / расписание ────────────────────────────────────────────────────
    var files         by mutableStateOf<List<ScheduleFile>>(emptyList())
    var selectedFile  by mutableStateOf<ScheduleFile?>(null)
    var isLoading     by mutableStateOf(false)
    var loadProgress  by mutableStateOf(0f)
    var statusText    by mutableStateOf("")

    fun loadFiles() {
        if (yandexUrl.isEmpty()) return
        viewModelScope.launch {
            isLoading = true; statusText = "Загружаю файлы..."; loadProgress = 0.3f
            try {
                files = withContext(Dispatchers.IO) { fetchYandexFiles(yandexUrl) }
                loadProgress = 1f; statusText = "Найдено: ${files.size} файлов"
                delay(1200); loadProgress = 0f; statusText = ""
            } catch (e: Exception) {
                loadProgress = 0f; statusText = "❌ ${e.message}"
            } finally { isLoading = false }
        }
    }

    // ── Группы ────────────────────────────────────────────────────────────────
    var groups        by mutableStateOf<List<String>>(emptyList())
    var selectedGroup by mutableStateOf<String?>(prefs.getString("last_group", null))

    fun selectGroup(group: String) {
        selectedGroup = group
        prefs.edit().putString("last_group", group).apply()
        loadSchedule(group)
    }

    // ── Преподаватели ──────────────────────────────────────────────────────────
    var teachers         by mutableStateOf<List<String>>(emptyList())
    var selectedTeacher  by mutableStateOf<String?>(prefs.getString("last_teacher", null))
    var teacherSearchQuery by mutableStateOf("")

    fun selectTeacher(teacher: String) {
        selectedTeacher = teacher
        prefs.edit().putString("last_teacher", teacher).apply()
        loadScheduleForTeacher(teacher)
    }

    private fun loadScheduleForTeacher(teacher: String) {
        viewModelScope.launch {
            isLoading = true
            delay(400)
            scheduleDays = listOf(
                ScheduleDay(
                    header = "Расписание: $teacher",
                    pairs  = listOf(
                        Pair("I",  "08:30","09:15","09:20","10:05","Группа 101","","ауд. 201"),
                        Pair("II", "10:15","11:00","11:05","11:50","Группа 204","","ауд. 201"),
                        Pair("III","12:20","13:05","13:10","13:55","Группа 302","","ауд. 315"),
                    )
                )
            )
            isLoading = false
        }
    }

    fun selectFile(file: ScheduleFile) {
        selectedFile = file
        // Demo — real impl would parse .docx for both groups and teachers
        groups = listOf(
            "МПД-2-24","МПД-1-24","ПК-3-23","ПК-2-23","ИС-1-24",
            "ИС-2-23","ТЭ-1-24","ТЭ-2-23","БД-1-24","СА-3-22",
        )
        teachers = listOf(
            "Иванова А.В.","Петров И.С.","Сидорова Е.М.",
            "Козлов Д.А.","Николаева О.П.","Федоров В.Г.",
            "Смирнова Л.Н.","Кузнецов А.Р.","Попов М.С.",
        )
    }

    // ── Расписание ────────────────────────────────────────────────────────────
    var scheduleDays by mutableStateOf<List<ScheduleDay>>(emptyList())

    private fun loadSchedule(group: String) {
        viewModelScope.launch {
            isLoading = true
            delay(400)
            scheduleDays = buildDemoSchedule(group)
            isLoading = false
        }
    }

    // ── Звонки ────────────────────────────────────────────────────────────────
    val bellSchedules: List<BellSchedule> by lazy {
        val now = java.util.Calendar.getInstance()
        val dow = now.get(java.util.Calendar.DAY_OF_WEEK)
        listOf(
            BellSchedule("Понедельник", dow == 2, listOf(
                BellPeriod("I",   "09:00","09:45","09:50","10:35", isNow = dow==2 && isNowPair(9*60, 10*60+35)),
                BellPeriod("II",  "10:45","11:30","11:35","12:20"),
                BellPeriod("III", "12:50","13:35","13:40","14:25"),
                BellPeriod("IV",  "14:35","15:35",null,null),
                BellPeriod("V",   "15:45","16:45",null,null),
                BellPeriod("VI",  "16:55","17:55",null,null),
            )),
            BellSchedule("Вторник – Пятница", dow in 3..6, listOf(
                BellPeriod("I",   "08:30","09:15","09:20","10:05", isNow = dow in 3..6 && isNowPair(8*60+30, 10*60+5)),
                BellPeriod("II",  "10:15","11:00","11:05","11:50"),
                BellPeriod("III", "12:20","13:05","13:10","13:55"),
                BellPeriod("IV",  "14:05","15:05",null,null),
                BellPeriod("V",   "15:15","16:15",null,null),
                BellPeriod("VI",  "16:25","17:25",null,null),
            )),
            BellSchedule("Суббота", dow == 7, listOf(
                BellPeriod("I",   "08:30","09:30",null,null),
                BellPeriod("II",  "09:40","10:40",null,null),
                BellPeriod("III", "10:50","11:50",null,null),
                BellPeriod("IV",  "12:00","13:00",null,null),
                BellPeriod("V",   "13:10","14:10",null,null),
                BellPeriod("VI",  "14:20","15:20",null,null),
            )),
        )
    }

    // ── Домашнее задание ──────────────────────────────────────────────────────
    var hwItems by mutableStateOf<List<HomeworkItem>>(emptyList())
        private set

    init {
        loadHw()
        // Восстанавливаем сессию если был залогинен
        val savedUsername = prefs.getString("sb_username", null)
        val savedToken    = prefs.getString("sb_token", null)
        if (savedUsername != null && savedToken != null) {
            sb.setAuthToken(savedToken)
            viewModelScope.launch {
                loadProfileFromSupabase(savedUsername)
                startPresencePoller()
                startMessengerPoller()
            }
        }
        updateVisitStreak()
        // Show greeting for special dates
        if (com.schedule.app.ui.screens.getSpecialDateGreeting() != null) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(1000)
                showGreeting = true
            }
        }
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
                put("id", h.id); put("subject", h.subject); put("task", h.task)
                put("deadline", h.deadline); put("urgent", h.urgent); put("done", h.done)
            })
        }
        prefs.edit().putString("hw_v2", arr.toString()).apply()
        hwItems = items
    }

    fun toggleHwDone(id: Long) = saveHw(hwItems.map { if (it.id == id) it.copy(done = !it.done) else it })
    fun deleteHw(id: Long)     = saveHw(hwItems.filter { it.id != id })
    fun addHw(subject: String, task: String, deadline: String, urgent: Boolean) {
        saveHw(listOf(HomeworkItem(System.currentTimeMillis(), subject, task, deadline, urgent, false)) + hwItems)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SUPABASE — АВТОРИЗАЦИЯ
    // ══════════════════════════════════════════════════════════════════════════

    var userProfile      by mutableStateOf<UserProfile?>(null)
    var authLoading      by mutableStateOf(false)
    var authError        by mutableStateOf("")
    var regError         by mutableStateOf("")

    // Поля регистрации
    var regName          by mutableStateOf("")
    var regUsername      by mutableStateOf("")
    var regUsernameStatus by mutableStateOf("")
    var regUsernameValid  by mutableStateOf(false)
    var regEmoji         by mutableStateOf("😊")
    var regPassword      by mutableStateOf("")
    var regEnabled       by mutableStateOf(false)

    // Поля входа
    var authUsername     by mutableStateOf("")
    var authPassword     by mutableStateOf("")

    fun onRegNameChange(v: String)     { regName = v; checkRegEnabled() }
    fun onRegEmojiChange(v: String)    { regEmoji = v }
    fun onRegPasswordChange(v: String) { regPassword = v; checkRegEnabled() }
    fun onRegRandomEmoji() { regEmoji = EMOJI_POOL.random() }
    fun onEditRandomEmoji() { editEmoji = EMOJI_POOL.random() }

    fun onRegUsernameChange(v: String) {
        regUsername = v.trim()
        regUsernameValid  = v.length >= 3
        regUsernameStatus = when {
            v.isEmpty()  -> ""
            v.length < 3 -> "Минимум 3 символа"
            else         -> "✓ Доступен"
        }
        checkRegEnabled()
    }

    private fun checkRegEnabled() {
        regEnabled = regName.trim().isNotEmpty() &&
                regUsername.trim().length >= 3 &&
                regPassword.length >= 4
    }

    fun onAuthUsernameChange(v: String) { authUsername = v }
    fun onAuthPasswordChange(v: String) { authPassword = v }

    /**
     * Регистрация: проверяем что username не занят → создаём запись в users.
     * Авторизация через email: username@sapp.local (Supabase требует email).
     */


    /**
     * Вход: email = username@sapp.local + пароль.
     * После входа загружаем профиль из таблицы users.
     */


    fun doLogout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { sb.signOut() } }
            presenceJob?.cancel()
            userProfile = null
            friends = emptyList()
            leaderboard = emptyList()
            prefs.edit().remove("sb_username").remove("sb_token").apply()
            sb.setAuthToken(null)
        }
    }

    private suspend fun loadProfileFromSupabase(username: String) {
        try {
            val result = withContext(Dispatchers.IO) {
                sb.select("users",
                    "select=username,name,avatar,avatar_type,avatar_data,status,bio,vip,banner,color" +
                    "&username=eq.$username&limit=1")
            }
            val json = JSONObject(result)
            if (json.optBoolean("ok")) {
                val arr = JSONArray(json.optString("body", "[]"))
                if (arr.length() > 0) {
                    val row = arr.getJSONObject(0)
                    val colorHex = row.optString("color", "")
                    val accent = if (colorHex.isNotEmpty()) {
                        try { Color(android.graphics.Color.parseColor(colorHex)) }
                        catch (e: Exception) { Color(0xFFE87722) }
                    } else Color(0xFFE87722)

                    userProfile = UserProfile(
                        name       = row.optString("name",     username),
                        username   = row.optString("username", username),
                        avatar     = row.optString("avatar",   "😊"),
                        avatarType = row.optString("avatar_type", "emoji"),
                        avatarData = row.optString("avatar_data","").ifEmpty { null },
                        accentColor= accent,
                        status     = row.optString("status",   "online"),
                        bio        = row.optString("bio",      ""),
                        vip        = row.optBoolean("vip",     false),
                        banner     = row.optString("banner",   "").ifEmpty { null },
                    )
                }
            }
            // После загрузки профиля — грузим друзей и лидерборд
            loadFriendsFromSupabase(username)
            loadLeaderboardFromSupabase()
        } catch (e: Exception) {
            log.e("AppViewModel", "loadProfile error: ${e.message}")
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SUPABASE — PRESENCE (онлайн-статус)
    // ══════════════════════════════════════════════════════════════════════════

    var onlineUsers by mutableStateOf<List<String>>(emptyList())
    private var presenceJob: Job? = null

    private fun startPresencePoller() {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            while (isActive) {
                updatePresence()
                delay(30_000) // каждые 30 сек
            }
        }
    }

    private suspend fun updatePresence() {
        val p = userProfile ?: return
        try {
            // Обновляем свой presence
            val now = System.currentTimeMillis()
            val presenceJson = JSONObject().apply {
                put("username", p.username)
                put("status",   p.status)
                put("ts",       now)
            }.toString()
            withContext(Dispatchers.IO) { sb.upsert("presence", presenceJson) }

            // Получаем кто онлайн (активен последние 2 минуты)
            val since = now - 2 * 60 * 1000
            val result = withContext(Dispatchers.IO) {
                sb.select("presence", "select=username,status&ts=gte.$since&order=ts.desc&limit=200")
            }
            val json = JSONObject(result)
            if (json.optBoolean("ok")) {
                val arr = JSONArray(json.optString("body", "[]"))
                onlineUsers = (0 until arr.length()).map { arr.getJSONObject(it).optString("username") }
            }
        } catch (e: Exception) {
            log.e("AppViewModel", "presence error: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        presenceJob?.cancel()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SUPABASE — ДРУЗЬЯ
    // ══════════════════════════════════════════════════════════════════════════

    var friends          by mutableStateOf<List<FriendEntry>>(emptyList())
    var friendsLoading   by mutableStateOf(false)
    var friendsSearchQuery by mutableStateOf("")

    private suspend fun loadFriendsFromSupabase(username: String) {
        try {
            friendsLoading = true
            // Получаем список друзей из таблицы users
            val result = withContext(Dispatchers.IO) {
                sb.select("users", "select=friends&username=eq.$username&limit=1")
            }
            val json = JSONObject(result)
            if (!json.optBoolean("ok")) return
            val arr = JSONArray(json.optString("body", "[]"))
            if (arr.length() == 0) return

            val row = arr.getJSONObject(0)
            val friendsRaw = row.optString("friends", "[]")
            val friendsArr = try { JSONArray(friendsRaw) } catch (e: Exception) { JSONArray() }
            if (friendsArr.length() == 0) { friends = emptyList(); return }

            // Загружаем профили друзей
            val usernames = (0 until friendsArr.length()).joinToString(",") {
                "\"${friendsArr.getString(it)}\""
            }
            val profilesResult = withContext(Dispatchers.IO) {
                sb.select("users",
                    "select=username,name,avatar,avatar_type,avatar_data,status,vip,color" +
                    "&username=in.($usernames)&limit=100")
            }
            val profilesJson = JSONObject(profilesResult)
            if (!profilesJson.optBoolean("ok")) return
            val profilesArr = JSONArray(profilesJson.optString("body", "[]"))

            friends = (0 until profilesArr.length()).map { i ->
                val u = profilesArr.getJSONObject(i)
                FriendEntry(
                    username = u.optString("username"),
                    name     = u.optString("name"),
                    avatar   = u.optString("avatar", "😊"),
                    status   = u.optString("status", "online"),
                    isOnline = onlineUsers.contains(u.optString("username")),
                )
            }
        } catch (e: Exception) {
            log.e("AppViewModel", "loadFriends error: ${e.message}")
        } finally {
            friendsLoading = false
        }
    }

    fun refreshFriends() {
        val username = userProfile?.username ?: return
        viewModelScope.launch { loadFriendsFromSupabase(username) }
    }

    fun onFriendClick(username: String) {
        openChat(username)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SUPABASE — ЛИДЕРБОРД
    // ══════════════════════════════════════════════════════════════════════════

    var leaderboard      by mutableStateOf<List<LeaderboardEntry>>(emptyList())
    var leaderboardGame  by mutableStateOf("snake")
    var leaderboardLoading by mutableStateOf(false)

    private suspend fun loadLeaderboardFromSupabase() {
        try {
            leaderboardLoading = true
            val result = withContext(Dispatchers.IO) {
                sb.select("leaderboard",
                    "select=username,name,avatar,score,game&game=eq.$leaderboardGame" +
                    "&order=score.desc&limit=50")
            }
            val json = JSONObject(result)
            if (!json.optBoolean("ok")) return
            val arr = JSONArray(json.optString("body", "[]"))
            leaderboard = (0 until arr.length()).mapIndexed { index, i ->
                val row = arr.getJSONObject(i)
                LeaderboardEntry(
                    rank     = index + 1,
                    username = row.optString("username"),
                    name     = row.optString("name", row.optString("username")),
                    avatar   = row.optString("avatar", "😊"),
                    score    = row.optInt("score", 0),
                    game     = row.optString("game", leaderboardGame),
                )
            }
        } catch (e: Exception) {
            log.e("AppViewModel", "loadLeaderboard error: ${e.message}")
        } finally {
            leaderboardLoading = false
        }
    }

    fun setLeaderboardGame(game: String) {
        leaderboardGame = game
        viewModelScope.launch { loadLeaderboardFromSupabase() }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SUPABASE — РЕДАКТИРОВАНИЕ ПРОФИЛЯ
    // ══════════════════════════════════════════════════════════════════════════

    var editName     by mutableStateOf("")
    var editBio      by mutableStateOf("")
    var editEmoji    by mutableStateOf("😊")
    var editStatus   by mutableStateOf("online")
    var editUsername by mutableStateOf("")
    var editColor    by mutableStateOf("#E87722")
    var editLoading  by mutableStateOf(false)

    fun startEditProfile() {
        val p = userProfile ?: return
        editName     = p.name
        editBio      = p.bio
        editEmoji    = p.avatar
        editStatus   = p.status
        editUsername = p.username
        // Convert accentColor back to hex
        val argb = p.accentColor.value.toLong()
        editColor = "#%06X".format(argb and 0xFFFFFF)
    }

    fun onEditNameChange(v: String)     { editName = v }
    fun onEditBioChange(v: String)      { editBio = v }
    fun onEditEmojiChange(v: String)    { editEmoji = v }
    fun onEditStatusChange(v: String)   { editStatus = v }
    fun onEditUsernameChange(v: String) { editUsername = v.trim().lowercase() }
    fun onEditColorChange(v: String)    { editColor = v }

    fun saveEditProfile() {
        val p = userProfile ?: return
        val oldUsername = p.username
        val newUsername = editUsername.trim().lowercase().ifEmpty { oldUsername }
        viewModelScope.launch {
            editLoading = true
            try {
                val accent = try {
                    Color(android.graphics.Color.parseColor(editColor))
                } catch (e: Exception) { p.accentColor }

                val updateJson = JSONObject().apply {
                    put("name",   editName.trim().ifEmpty { p.name })
                    put("bio",    editBio.trim())
                    put("avatar", editEmoji.ifEmpty { "\uD83D\uDE0A" })
                    put("status", editStatus)
                    put("color",  editColor)
                    if (newUsername != oldUsername) put("username", newUsername)
                }.toString()
                withContext(Dispatchers.IO) {
                    sb.update("users", "username=eq.$oldUsername", updateJson)
                }
                if (newUsername != oldUsername) {
                    prefs.edit().putString("sb_username", newUsername).apply()
                }
                userProfile = p.copy(
                    name        = editName.trim().ifEmpty { p.name },
                    bio         = editBio.trim(),
                    avatar      = editEmoji.ifEmpty { "\uD83D\uDE0A" },
                    status      = editStatus,
                    username    = newUsername,
                    accentColor = accent,
                )
            } catch (e: Exception) {
                log.e("AppViewModel", "saveEditProfile error: ${e.message}")
            } finally {
                editLoading = false
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SUPABASE — СООБЩЕНИЯ
    // ══════════════════════════════════════════════════════════════════════════

    var messages         by mutableStateOf<List<ChatMessage>>(emptyList())
    var messagesLoading  by mutableStateOf(false)
    var currentChatUser  by mutableStateOf<String?>(null)
    var messageInput     by mutableStateOf("")
    var sendingMessage   by mutableStateOf(false)
    private var messagesJob: Job? = null
    private var lastMsgTs: Long = 0

    fun openChat(withUsername: String) {
        currentChatUser = withUsername
        messages = emptyList()
        lastMsgTs = 0
        startMessagesPoller(withUsername)
    }

    fun closeChat() {
        messagesJob?.cancel()
        currentChatUser = null
        messages = emptyList()
    }

    fun openGroupChat(groupId: String) {
        // Group chat uses group_<id> as chat_key prefix
        currentChatUser = groupId
        messages = emptyList()
        lastMsgTs = 0
        startGroupMessagesPoller(groupId)
    }

    private fun startGroupMessagesPoller(groupId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            while (isActive) {
                pollGroupMessages(groupId)
                delay(2_000)
            }
        }
    }

    private suspend fun pollGroupMessages(groupId: String) {
        val chatKey = "group_$groupId"
        try {
            messagesLoading = messages.isEmpty()
            val result = withContext(Dispatchers.IO) {
                sb.select("messages", "select=*&chat_key=eq.$chatKey&ts=gt.$lastMsgTs&order=ts.asc&limit=100")
            }
            val json = JSONObject(result)
            if (!json.optBoolean("ok")) return
            val arr = JSONArray(json.optString("body","[]"))
            if (arr.length() > 0) {
                val newMsgs = (0 until arr.length()).map { i ->
                    val row = arr.getJSONObject(i)
                    ChatMessage(
                        id       = row.optString("id",""),
                        chatKey  = chatKey,
                        fromUser = row.optString("from_user"),
                        toUser   = groupId,
                        text     = row.optString("text",""),
                        ts       = row.optLong("ts",0),
                        type     = row.optString("type","text"),
                        fileUrl  = row.optString("file_url","").ifEmpty { null },
                    )
                }
                messages = (messages + newMsgs).takeLast(500)
                lastMsgTs = newMsgs.last().ts
            }
        } catch (e: Exception) { log.e("AppViewModel","pollGroupMessages error: ${e.message}") }
        finally { messagesLoading = false }
    }

    private fun startMessagesPoller(withUsername: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            while (isActive) {
                pollMessages(withUsername)
                delay(2_000)
            }
        }
    }

    private suspend fun pollMessages(withUsername: String) {
        val myUsername = userProfile?.username ?: return
        val chatKey = listOf(myUsername, withUsername).sorted().joinToString("_")
        try {
            messagesLoading = messages.isEmpty()
            val result = withContext(Dispatchers.IO) {
                sb.select("messages",
                    "select=*&chat_key=eq.$chatKey&ts=gt.$lastMsgTs&order=ts.asc&limit=100")
            }
            val json = JSONObject(result)
            if (!json.optBoolean("ok")) return
            val arr = JSONArray(json.optString("body", "[]"))
            if (arr.length() > 0) {
                val newMsgs = (0 until arr.length()).map { i ->
                    val row = arr.getJSONObject(i)
                    ChatMessage(
                        id       = row.optString("id", ""),
                        chatKey  = chatKey,
                        fromUser = row.optString("from_user"),
                        toUser   = row.optString("to_user"),
                        text     = row.optString("text", ""),
                        ts       = row.optLong("ts", 0),
                        type     = row.optString("type", "text"),
                        fileUrl  = row.optString("file_url", "").ifEmpty { null },
                    )
                }
                messages = (messages + newMsgs).takeLast(500)
                lastMsgTs = newMsgs.last().ts
            }
        } catch (e: Exception) {
            log.e("AppViewModel", "pollMessages error: ${e.message}")
        } finally {
            messagesLoading = false
        }
    }

    fun sendMessage() {
        val myUsername = userProfile?.username ?: return
        val toUsername = currentChatUser ?: return
        val text = messageInput.trim()
        if (text.isEmpty()) return
        messageInput = ""

        val chatKey = listOf(myUsername, toUsername).sorted().joinToString("_")
        val ts = System.currentTimeMillis()

        viewModelScope.launch {
            sendingMessage = true
            try {
                // Оптимистично добавляем в список
                val localMsg = ChatMessage("local_$ts", chatKey, myUsername, toUsername, text, ts)
                messages = messages + localMsg

                val msgJson = JSONObject().apply {
                    put("chat_key",  chatKey)
                    put("from_user", myUsername)
                    put("to_user",   toUsername)
                    put("text",      text)
                    put("ts",        ts)
                    put("type",      "text")
                }.toString()

                withContext(Dispatchers.IO) { sb.insert("messages", msgJson) }
            } catch (e: Exception) {
                log.e("AppViewModel", "sendMessage error: ${e.message}")
                messageInput = text // вернуть текст если ошибка
            } finally {
                sendingMessage = false
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UI СОСТОЯНИЯ
    // ══════════════════════════════════════════════════════════════════════════

    var p2pConnected     by mutableStateOf(false)
    var notifEnabled     by mutableStateOf(prefs.getBoolean("notif_enabled", false))
    var bgServiceEnabled by mutableStateOf(prefs.getBoolean("bg_service", false))

    fun toggleNotif() {
        notifEnabled = !notifEnabled
        prefs.edit().putBoolean("notif_enabled", notifEnabled).apply()
    }
    fun toggleBgService() {
        bgServiceEnabled = !bgServiceEnabled
        prefs.edit().putBoolean("bg_service", bgServiceEnabled).apply()
    }

    // Photo picking is handled by ComposeActivity via ActivityResultContracts.GetContent()
    // onPhotoPicked(uri, contentResolver) is called from there

    // ══════════════════════════════════════════════════════════════════════════
    // УТИЛИТЫ
    // ══════════════════════════════════════════════════════════════════════════

    private fun isNowPair(startMin: Int, endMin: Int): Boolean {
        val now = java.util.Calendar.getInstance()
        val nowMin = now.get(java.util.Calendar.HOUR_OF_DAY)*60 + now.get(java.util.Calendar.MINUTE)
        return nowMin in startMin until endMin
    }

    private suspend fun fetchYandexFiles(publicKey: String): List<ScheduleFile> {
        val url = "https://cloud-api.yandex.net/v1/disk/public/resources" +
                "?public_key=${URLEncoder.encode(publicKey, "UTF-8")}&limit=100"
        val conn = URL(url).openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 10_000
        val body = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(body)
        val items = json.getJSONObject("_embedded").getJSONArray("items")
        return (0 until items.length())
            .map { items.getJSONObject(it) }
            .filter { it.getString("type")=="file" &&
                    it.getString("name").matches(Regex(".*\\.(doc|docx)", RegexOption.IGNORE_CASE)) }
            .map { ScheduleFile(
                name = it.getString("name"),
                path = it.optString("path", "/"+it.getString("name")),
                size = it.optLong("size", 0)
            )}
    }

    private fun buildDemoSchedule(group: String): List<ScheduleDay> {
        val nowMin = run {
            val c = java.util.Calendar.getInstance()
            c.get(java.util.Calendar.HOUR_OF_DAY)*60 + c.get(java.util.Calendar.MINUTE)
        }
        fun isNow(s: Int, e: Int) = nowMin in s until e
        return listOf(
            ScheduleDay(
                header = "РАСПИСАНИЕ: $group",
                pairs  = listOf(
                    Pair("I",  "08:30","09:15","09:20","10:05","Математика","Иванова А.В.","ауд. 204",  isNow = isNow(8*60+30, 10*60+5)),
                    Pair("II", "10:15","11:00","11:05","11:50","Информатика","Петров И.С.","ауд. 301 (пк)"),
                    Pair("III","12:20","13:05","13:10","13:55","История","Сидорова Е.М.","ауд. 115"),
                    Pair("IV", "14:05","15:05",null,null,"Окно", isWindow = true),
                    Pair("V",  "15:15","16:15",null,null,"Физкультура","Козлов Д.А.","спортзал"),
                )
            )
        )
    }


    // ══════════════════════════════════════════════════════════════════════════
    // SUPABASE — МЕССЕНДЖЕР (список чатов)
    // ══════════════════════════════════════════════════════════════════════════

    var messengerChats     by mutableStateOf<List<com.schedule.app.ui.screens.ChatPreview>>(emptyList())
    var messengerSearch    by mutableStateOf("")
    var messengerLoading   by mutableStateOf(false)
    private var messengerJob: Job? = null

    fun startMessengerPoller() {
        messengerJob?.cancel()
        messengerJob = viewModelScope.launch {
            while (isActive) {
                refreshMessengerChats()
                delay(5_000)
            }
        }
    }

    private suspend fun refreshMessengerChats() {
        val myUsername = userProfile?.username ?: return
        try {
            messengerLoading = messengerChats.isEmpty()
            // Получаем последние сообщения из всех чатов где я участник
            val sentResult = withContext(Dispatchers.IO) {
                sb.select("messages", "select=to_user&from_user=eq.$myUsername&order=ts.desc&limit=200")
            }
            val rcvResult = withContext(Dispatchers.IO) {
                sb.select("messages", "select=from_user&to_user=eq.$myUsername&order=ts.desc&limit=200")
            }

            val sentJson = JSONObject(sentResult)
            val rcvJson  = JSONObject(rcvResult)
            val chatUsernames = mutableSetOf<String>()
            if (sentJson.optBoolean("ok")) {
                val arr = JSONArray(sentJson.optString("body","[]"))
                for (i in 0 until arr.length()) chatUsernames.add(arr.getJSONObject(i).optString("to_user"))
            }
            if (rcvJson.optBoolean("ok")) {
                val arr = JSONArray(rcvJson.optString("body","[]"))
                for (i in 0 until arr.length()) chatUsernames.add(arr.getJSONObject(i).optString("from_user"))
            }
            chatUsernames.remove(myUsername)
            if (chatUsernames.isEmpty()) { messengerLoading = false; return }

            // Загружаем профили собеседников
            val unames = chatUsernames.joinToString(",") { "\"$it\"" }
            val profilesResult = withContext(Dispatchers.IO) {
                sb.select("users", "select=username,name,avatar,avatar_type,avatar_data,status,vip&username=in.($unames)&limit=100")
            }
            val profilesJson = JSONObject(profilesResult)
            val profilesMap = mutableMapOf<String, JSONObject>()
            if (profilesJson.optBoolean("ok")) {
                val arr = JSONArray(profilesJson.optString("body","[]"))
                for (i in 0 until arr.length()) {
                    val row = arr.getJSONObject(i)
                    profilesMap[row.optString("username")] = row
                }
            }

            // Для каждого чата получаем последнее сообщение
            val previews = mutableListOf<com.schedule.app.ui.screens.ChatPreview>()
            for (username in chatUsernames) {
                val chatKey = listOf(myUsername, username).sorted().joinToString("_")
                val lastMsgResult = withContext(Dispatchers.IO) {
                    sb.select("messages", "select=text,ts,from_user&chat_key=eq.$chatKey&order=ts.desc&limit=1")
                }
                val lastMsgJson = JSONObject(lastMsgResult)
                var lastText = ""; var lastTs = 0L; var isMe = false
                if (lastMsgJson.optBoolean("ok")) {
                    val arr = JSONArray(lastMsgJson.optString("body","[]"))
                    if (arr.length() > 0) {
                        val row = arr.getJSONObject(0)
                        lastText = row.optString("text","")
                        lastTs   = row.optLong("ts",0)
                        isMe     = row.optString("from_user") == myUsername
                    }
                }
                val peer = profilesMap[username]
                previews.add(com.schedule.app.ui.screens.ChatPreview(
                    id             = username,
                    name           = peer?.optString("name", username) ?: username,
                    avatar         = peer?.optString("avatar", "😊") ?: "😊",
                    avatarType     = peer?.optString("avatar_type", "emoji") ?: "emoji",
                    avatarData     = peer?.optString("avatar_data", "")?.ifEmpty { null },
                    lastMessage    = lastText,
                    lastMessageTime= lastTs,
                    isOnline       = onlineUsers.contains(username),
                    isMe           = isMe,
                ))
            }
            messengerChats = previews.sortedByDescending { it.lastMessageTime }
        } catch (e: Exception) {
            log.e("AppViewModel", "refreshMessengerChats error: ${e.message}")
        } finally {
            messengerLoading = false
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SUPABASE — ОНЛАЙН ПОЛЬЗОВАТЕЛИ
    // ══════════════════════════════════════════════════════════════════════════

    var onlineUsersList    by mutableStateOf<List<com.schedule.app.ui.screens.OnlineUser>>(emptyList())
    var onlineSearchQuery  by mutableStateOf("")
    var onlineListLoading  by mutableStateOf(false)

    fun loadOnlineUsers() {
        viewModelScope.launch {
            onlineListLoading = true
            try {
                val since = System.currentTimeMillis() - 2 * 60 * 1000
                val presenceResult = withContext(Dispatchers.IO) {
                    sb.select("presence", "select=username,status&ts=gte.$since&order=ts.desc&limit=200")
                }
                val presenceJson = JSONObject(presenceResult)
                if (!presenceJson.optBoolean("ok")) return@launch
                val presenceArr = JSONArray(presenceJson.optString("body","[]"))
                val onlineNames = (0 until presenceArr.length()).map {
                    presenceArr.getJSONObject(it).optString("username")
                }.filter { it != (userProfile?.username ?: "") }
                if (onlineNames.isEmpty()) { onlineUsersList = emptyList(); return@launch }

                val unames = onlineNames.joinToString(",") { "\"$it\"" }
                val profilesResult = withContext(Dispatchers.IO) {
                    sb.select("users", "select=username,name,avatar,avatar_type,avatar_data,status,bio,vip&username=in.($unames)&limit=200")
                }
                val profilesJson = JSONObject(profilesResult)
                if (!profilesJson.optBoolean("ok")) return@launch
                val arr = JSONArray(profilesJson.optString("body","[]"))
                onlineUsersList = (0 until arr.length()).map { i ->
                    val row = arr.getJSONObject(i)
                    com.schedule.app.ui.screens.OnlineUser(
                        username   = row.optString("username"),
                        name       = row.optString("name", row.optString("username")),
                        avatar     = row.optString("avatar","😊"),
                        avatarType = row.optString("avatar_type","emoji"),
                        avatarData = row.optString("avatar_data","").ifEmpty { null },
                        status     = row.optString("status","online"),
                        bio        = row.optString("bio",""),
                        vip        = row.optBoolean("vip",false),
                    )
                }
            } catch (e: Exception) {
                log.e("AppViewModel","loadOnlineUsers error: ${e.message}")
            } finally {
                onlineListLoading = false
            }
        }
    }

    // ── Peer profile ──────────────────────────────────────────────────────────
    var peerProfile        by mutableStateOf<com.schedule.app.ui.screens.OnlineUser?>(null)
    var peerProfileLoading by mutableStateOf(false)

    fun loadPeerProfile(username: String) {
        viewModelScope.launch {
            peerProfileLoading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    sb.select("users", "select=username,name,avatar,avatar_type,avatar_data,status,bio,vip&username=eq.$username&limit=1")
                }
                val json = JSONObject(result)
                if (json.optBoolean("ok")) {
                    val arr = JSONArray(json.optString("body","[]"))
                    if (arr.length() > 0) {
                        val row = arr.getJSONObject(0)
                        peerProfile = com.schedule.app.ui.screens.OnlineUser(
                            username   = row.optString("username"),
                            name       = row.optString("name", username),
                            avatar     = row.optString("avatar","😊"),
                            avatarType = row.optString("avatar_type","emoji"),
                            avatarData = row.optString("avatar_data","").ifEmpty { null },
                            status     = row.optString("status","online"),
                            bio        = row.optString("bio",""),
                            vip        = row.optBoolean("vip",false),
                        )
                    }
                }
            } catch (e: Exception) {
                log.e("AppViewModel","loadPeerProfile error: ${e.message}")
            } finally {
                peerProfileLoading = false
            }
        }
    }

    fun addFriend(username: String) {
        val myUsername = userProfile?.username ?: return
        viewModelScope.launch {
            try {
                // Добавляем в массив friends в таблице users
                val currentFriends = friends.map { it.username }.toMutableList()
                if (!currentFriends.contains(username)) currentFriends.add(username)
                val friendsJson = JSONObject().apply {
                    put("friends", org.json.JSONArray(currentFriends))
                }.toString()
                withContext(Dispatchers.IO) { sb.update("users", "username=eq.$myUsername", friendsJson) }
                refreshFriends()
            } catch (e: Exception) { log.e("AppViewModel","addFriend error: ${e.message}") }
        }
    }

    fun removeFriend(username: String) {
        val myUsername = userProfile?.username ?: return
        viewModelScope.launch {
            try {
                val currentFriends = friends.map { it.username }.filter { it != username }
                val friendsJson = JSONObject().apply {
                    put("friends", org.json.JSONArray(currentFriends))
                }.toString()
                withContext(Dispatchers.IO) { sb.update("users", "username=eq.$myUsername", friendsJson) }
                friends = friends.filter { it.username != username }
            } catch (e: Exception) { log.e("AppViewModel","removeFriend error: ${e.message}") }
        }
    }

    // ── Групп чаты ────────────────────────────────────────────────────────────
    var groupChats         by mutableStateOf<List<com.schedule.app.ui.screens.GroupChat>>(emptyList())
    var groupChatsLoading  by mutableStateOf(false)
    var showCreateGroupDialog by mutableStateOf(false)

    fun loadGroupChats() {
        val myUsername = userProfile?.username ?: return
        viewModelScope.launch {
            groupChatsLoading = true
            try {
                val result = withContext(Dispatchers.IO) {
                    sb.select("groups", "select=id,name,avatar,members&members=cs.{\"$myUsername\"}&order=updated_at.desc&limit=100")
                }
                val json = JSONObject(result)
                if (json.optBoolean("ok")) {
                    val arr = JSONArray(json.optString("body","[]"))
                    groupChats = (0 until arr.length()).map { i ->
                        val row = arr.getJSONObject(i)
                        val membersArr = try { JSONArray(row.optString("members","[]")) } catch(e:Exception){ JSONArray() }
                        com.schedule.app.ui.screens.GroupChat(
                            id          = row.optString("id"),
                            name        = row.optString("name","Группа"),
                            avatar      = row.optString("avatar","👥"),
                            memberCount = membersArr.length(),
                        )
                    }
                }
            } catch (e: Exception) { log.e("AppViewModel","loadGroupChats error: ${e.message}") }
            finally { groupChatsLoading = false }
        }
    }

    fun createGroup(name: String, avatar: String) {
        val myUsername = userProfile?.username ?: return
        val groupId = "grp_${System.currentTimeMillis()}"
        viewModelScope.launch {
            try {
                val groupJson = JSONObject().apply {
                    put("id", groupId); put("name", name); put("avatar", avatar)
                    put("members", JSONArray().put(myUsername))
                    put("created_by", myUsername)
                }.toString()
                withContext(Dispatchers.IO) { sb.insert("groups", groupJson) }
                loadGroupChats()
            } catch (e: Exception) { log.e("AppViewModel","createGroup error: ${e.message}") }
        }
    }


    // ══════════════════════════════════════════════════════════════════════════
    // FUN FEATURES & EASTER EGGS
    // ══════════════════════════════════════════════════════════════════════════

    // ── Dev Console ───────────────────────────────────────────────────────────
    var showConsole       by mutableStateOf(false)
    var consoleInput      by mutableStateOf("")
    var consoleEntries    by mutableStateOf(listOf(
        com.schedule.app.ui.screens.ConsoleEntry("info", "⚡ ScheduleApp Dev Console"),
        com.schedule.app.ui.screens.ConsoleEntry("muted", "Введи /help для списка команд"),
        com.schedule.app.ui.screens.ConsoleEntry("muted", "──────────────────────────────"),
    ))

    fun cmdPrint(type: String, text: String) {
        consoleEntries = consoleEntries + com.schedule.app.ui.screens.ConsoleEntry(type, text)
    }

    fun processCommand(raw: String) {
        val parts = raw.trim().split(" ", limit = 2)
        val cmd = parts[0].lowercase().trimStart('/')
        val arg = parts.getOrNull(1)?.trim() ?: ""

        cmdPrint("out", "> $raw")

        when (cmd) {
            "help" -> {
                cmdPrint("info", "Доступные команды:")
                cmdPrint("ok",   "  /help — этот список")
                cmdPrint("ok",   "  /fact — случайный факт 💡")
                cmdPrint("ok",   "  /haiku — хайку про учёбу 🌸")
                cmdPrint("ok",   "  /excuse — отмазка для пропуска 📝")
                cmdPrint("ok",   "  /quiz — тест по расписанию 🧠")
                cmdPrint("ok",   "  /bpmtap — BPM тапалка 🥁")
                cmdPrint("ok",   "  /taunt — реплика учителя 👩‍🏫")
                cmdPrint("ok",   "  /stats — статистика приложения 📊")
                cmdPrint("ok",   "  /motivation — мотивация на сегодня 💪")
                cmdPrint("ok",   "  /greeting — праздничное приветствие 🎉")
                cmdPrint("ok",   "  /focus — режим фокуса 🎯")
                cmdPrint("ok",   "  /clear — очистить консоль")
                cmdPrint("ok",   "  /version — версия приложения")
                cmdPrint("ok",   "  /vip <КОД> — активировать VIP 👑")
                cmdPrint("ok",   "  /logout — выйти из аккаунта")
                cmdPrint("ok",   "  /theme <id> — сменить тему")
                cmdPrint("ok",   "  /group — случайная группа 🎲")
            }
            "fact" -> {
                activeFunOverlay = com.schedule.app.ui.FunOverlay.None
                cmdPrint("ok", "💡 ${com.schedule.app.ui.screens.RANDOM_FACTS.random()}")
            }
            "haiku" -> {
                activeFunOverlay = com.schedule.app.ui.FunOverlay.Haiku
                cmdPrint("ok", "🌸 Открываю хайку...")
            }
            "excuse" -> {
                activeFunOverlay = com.schedule.app.ui.FunOverlay.Excuse
                cmdPrint("ok", "📝 Генерирую отмазку...")
            }
            "quiz" -> {
                activeFunOverlay = com.schedule.app.ui.FunOverlay.Quiz
                cmdPrint("ok", "🧠 Запускаю тест...")
            }
            "bpmtap" -> {
                activeFunOverlay = com.schedule.app.ui.FunOverlay.BpmTapper
                cmdPrint("ok", "🥁 BPM тапалка запущена")
            }
            "taunt" -> {
                cmdPrint("ok", com.schedule.app.ui.screens.TEACHER_TAUNTS.random())
            }
            "stats" -> {
                activeFunOverlay = com.schedule.app.ui.FunOverlay.Stats
                cmdPrint("ok", "📊 Открываю статистику...")
            }
            "motivation" -> {
                cmdPrint("ok", "💪 ${com.schedule.app.ui.screens.getDayMotivation()}")
            }
            "greeting" -> {
                val g = com.schedule.app.ui.screens.getSpecialDateGreeting()
                if (g != null) {
                    activeFunOverlay = com.schedule.app.ui.FunOverlay.Greeting
                    showGreeting = true
                    cmdPrint("ok", "${g.second} ${g.first} — ${g.third}")
                } else {
                    cmdPrint("warn", "Сегодня нет праздника 😢")
                }
            }
            "focus" -> {
                focusMode = !focusMode
                cmdPrint("ok", if (focusMode) "🎯 Режим фокуса включён" else "🎯 Режим фокуса выключен")
            }
            "clear" -> {
                consoleEntries = listOf(
                    com.schedule.app.ui.screens.ConsoleEntry("muted", "── консоль очищена ──")
                )
            }
            "version" -> {
                cmdPrint("ok", "📦 ScheduleApp v4.8.7 (Native Kotlin/Compose)")
                cmdPrint("ok", "  Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}")
            }
            "vip" -> {
                val vipCodes = listOf("SAPP_VIP_2024", "LOMKICHVIP", "КОДВИП")
                if (arg.isEmpty()) {
                    cmdPrint("info", "👑 Использование: /vip <КОД>")
                } else if (vipCodes.contains(arg.uppercase())) {
                    val p = userProfile
                    if (p != null) {
                        userProfile = p.copy(vip = true)
                        viewModelScope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                sb.update("users", "username=eq.${p.username}", """{"vip":true}""")
                            }
                        }
                        cmdPrint("ok", "👑 VIP активирован! Доступны: фото-аватар, рамки, значки, баннер")
                    } else {
                        cmdPrint("err", "❌ Нужно войти в аккаунт")
                    }
                } else {
                    cmdPrint("err", "❌ Неверный код VIP")
                }
            }
            "logout" -> {
                doLogout()
                cmdPrint("ok", "👋 Выход выполнен")
            }
            "theme" -> {
                if (arg.isEmpty()) {
                    cmdPrint("info", "Темы: orange, amoled, win11, pixel, forest, rose, gold, purple, sunset, bw, ocean, candy, samek, light")
                } else {
                    setTheme(arg)
                    cmdPrint("ok", "🎨 Тема изменена: $arg")
                }
            }
            "group" -> {
                if (groups.isNotEmpty()) {
                    val g = groups.random()
                    cmdPrint("ok", "🎲 Случайная группа: $g")
                    selectGroup(g)
                } else {
                    cmdPrint("warn", "Сначала загрузи файл расписания")
                }
            }
            "deleteaccount" -> {
                cmdPrint("warn", "⚠️ Введи /deleteaccount ПОДТВЕРЖДАЮ для удаления")
                if (arg == "ПОДТВЕРЖДАЮ") {
                    val p = userProfile
                    if (p != null) {
                        viewModelScope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                sb.delete("users", "username=eq.${p.username}")
                            }
                            doLogout()
                        }
                        cmdPrint("err", "💀 Аккаунт удалён")
                    }
                }
            }
            else -> {
                cmdPrint("err", "❌ Неизвестная команда: /$cmd  (введи /help)")
            }
        }
        consoleInput = ""
    }

    // ── Fun Overlays ──────────────────────────────────────────────────────────
    var activeFunOverlay  by mutableStateOf(com.schedule.app.ui.FunOverlay.None)
    var showGreeting      by mutableStateOf(false)

    // ── Focus mode ────────────────────────────────────────────────────────────
    var focusMode         by mutableStateOf(false)

    // ── Schedule search ───────────────────────────────────────────────────────
    var schedSearchVisible by mutableStateOf(false)
    var schedSearchQuery   by mutableStateOf("")

    // ── Starred pairs ─────────────────────────────────────────────────────────
    var starredPairs by mutableStateOf(
        prefs.getStringSet("starred_pairs_v1", emptySet()) ?: emptySet<String>()
    )

    fun toggleStarPair(key: String) {
        starredPairs = if (starredPairs.contains(key)) {
            starredPairs - key
        } else {
            starredPairs + key
        }
        prefs.edit().putStringSet("starred_pairs_v1", starredPairs).apply()
    }

    // ── Pair notes ────────────────────────────────────────────────────────────
    var pairNotes by mutableStateOf(
        try {
            val raw = prefs.getString("pair_notes", "{}") ?: "{}"
            val json = org.json.JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }
        } catch (e: Exception) { emptyMap() }
    )
    var editingNoteKey by mutableStateOf<String?>(null)

    fun saveNote(key: String, text: String) {
        val updated = if (text.trim().isEmpty()) pairNotes - key else pairNotes + (key to text.trim())
        pairNotes = updated
        val json = org.json.JSONObject(updated)
        prefs.edit().putString("pair_notes", json.toString()).apply()
    }

    // ── Visit streak ──────────────────────────────────────────────────────────
    var totalOpens  by mutableStateOf(0)
    var visitStreak by mutableStateOf(0)

    private fun updateVisitStreak() {
        try {
            val raw = prefs.getString("visit_streak", "{}") ?: "{}"
            val data = org.json.JSONObject(raw)
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            val lastDate = data.optString("lastDate", "")
            val diffDays = if (lastDate.isEmpty()) 0 else {
                val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val last = fmt.parse(lastDate)
                val now  = fmt.parse(today)
                if (last != null && now != null)
                    ((now.time - last.time) / (1000 * 60 * 60 * 24)).toInt()
                else 1
            }
            val total  = data.optInt("total", 0) + 1
            val streak = if (diffDays == 1) (data.optInt("streak", 0) + 1) else 1

            data.put("lastDate", today)
            data.put("total", total)
            data.put("streak", streak)
            prefs.edit().putString("visit_streak", data.toString()).apply()

            totalOpens  = total
            visitStreak = streak

            if (streak > 1 && streak % 5 == 0) {
                viewModelScope.launch {
                    kotlinx.coroutines.delay(2500)
                    showToast("🔥 $streak дней подряд! Так держать!")
                }
            }
        } catch (e: Exception) {}
    }

    // ── Toast ─────────────────────────────────────────────────────────────────
    // ── Games launcher ───────────────────────────────────────────────────
    var pendingLaunchGames by mutableStateOf(false)
    fun openGames()      { pendingLaunchGames = true }
    fun onGamesLaunched() { pendingLaunchGames = false }

    var toastMessage by mutableStateOf<com.schedule.app.ui.screens.ToastState?>(null)
        private set
    private var _toastCounter = 0

    fun showToast(msg: String) {
        _toastCounter++
        toastMessage = com.schedule.app.ui.screens.ToastState(msg, _toastCounter)
        viewModelScope.launch {
            kotlinx.coroutines.delay(2500)
            toastMessage = null
        }
    }

    // ── Share schedule ────────────────────────────────────────────────────────
    var pendingShareText by mutableStateOf<String?>(null)

    fun buildShareText(): String {
        val group = selectedGroup ?: return ""
        val lines = mutableListOf("📅 $group", "")
        scheduleDays.firstOrNull()?.pairs?.forEach { pair ->
            if (!pair.isWindow) {
                lines.add("${pair.num}. ${pair.subject}${if (pair.timeStart.isNotEmpty()) " · ${pair.timeStart}–${pair.timeEnd}" else ""}")
            }
        }
        lines.add(""); lines.add("📲 ScheduleApp")
        return lines.joinToString("\n")
    }

    companion object {
        private val EMOJI_POOL = listOf(
            "😊","😎","🤓","🥳","😏","🤩","🦊","🐺","🦋","🐸",
            "🐱","🐶","🦁","🐼","🦄","🐉","🦅","🎭","🤖","👻",
            "💀","🎃","⚡","🔥","💎","🌙","⭐","🌈","🎵","🎮","🏆"
        )
    }



    // ══════════════════════════════════════════════════════════════════════════
    // UI ДИАЛОГИ
    // ══════════════════════════════════════════════════════════════════════════

    var showAddHwDialog by mutableStateOf(false)
    fun showAddHwDialog() { showAddHwDialog = true }

    // ══════════════════════════════════════════════════════════════════════════
    // ФОТО-АВАТАР
    // ══════════════════════════════════════════════════════════════════════════

    fun onPhotoPicked(uri: android.net.Uri, cr: android.content.ContentResolver) {
    val p = userProfile ?: return
    viewModelScope.launch {
        try {
            val bytes = withContext(Dispatchers.IO) {
                cr.openInputStream(uri)?.readBytes() ?: return@withContext null
            } ?: return@launch
            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            val mimeType = cr.getType(uri) ?: "image/jpeg"
            val dataUri = "data:$mimeType;base64,$base64"

            // Обновляем локально
            userProfile = p.copy(avatarType = "photo", avatarData = dataUri)

            // Сохраняем в Supabase
            val updateJson = JSONObject().apply {
                put("avatar_type", "photo")
                put("avatar_data", dataUri)
            }.toString()
            withContext(Dispatchers.IO) {
                sb.update("users", "username=eq.${p.username}", updateJson)
            }
        } catch (e: Exception) {
            log.e("AppViewModel", "onPhotoPicked error: ${e.message}")
        }
    }
    } // end onPhotoPicked

    // ══════════════════════════════════════════════════════════════════════════
    // CALLBACKS С НАВИГАЦИЕЙ (принимают лямбду onSuccess)
    // ══════════════════════════════════════════════════════════════════════════

    fun doRegister(onSuccess: () -> Unit) {
    if (!regEnabled) return
    val username = regUsername.trim().lowercase()
    val name     = regName.trim()
    val emoji    = regEmoji.ifEmpty { "😊" }
    val password = regPassword

    viewModelScope.launch {
        authLoading = true; regError = ""
        try {
            val checkResult = withContext(Dispatchers.IO) {
                sb.select("users", "select=username&username=eq.$username&limit=1")
            }
            val checkJson = JSONObject(checkResult)
            if (checkJson.optBoolean("ok")) {
                val arr = JSONArray(checkJson.optString("body", "[]"))
                if (arr.length() > 0) { regError = "Этот никнейм уже занят"; return@launch }
            }

            val email = "$username@sapp.local"
            val signUpResult = withContext(Dispatchers.IO) { sb.signUp(email, password) }
            val signUpJson = JSONObject(signUpResult)
            if (!signUpJson.optBoolean("ok")) {
                val bodyStr = signUpJson.optString("body", "{}")
                regError = try { JSONObject(bodyStr).optString("msg", "Ошибка регистрации") }
                           catch (e: Exception) { "Ошибка регистрации" }
                return@launch
            }

            val userJson = JSONObject().apply {
                put("username", username); put("name", name)
                put("avatar", emoji); put("avatar_type", "emoji")
                put("status", "online"); put("bio", ""); put("vip", false)
            }.toString()
            withContext(Dispatchers.IO) { sb.upsert("users", userJson) }

            prefs.edit()
                .putString("sb_username", username)
                .putString("sb_token", sb.getAuthToken() ?: "")
                .apply()
            userProfile = UserProfile(name = name, username = username, avatar = emoji, avatarType = "emoji", status = "online")
            regError = ""
            startPresencePoller()
            onSuccess()
        } catch (e: Exception) {
            regError = "❌ ${e.message}"
        } finally { authLoading = false }
    }
}


    fun doLogin(onSuccess: () -> Unit) {
    val username = authUsername.trim().lowercase()
    val password = authPassword
    if (username.isEmpty() || password.isEmpty()) { authError = "Заполни поля"; return }

    viewModelScope.launch {
        authLoading = true; authError = ""
        try {
            val email = "$username@sapp.local"
            val result = withContext(Dispatchers.IO) { sb.signIn(email, password) }
            val json = JSONObject(result)
            if (!json.optBoolean("ok")) {
                val bodyStr = json.optString("body", "{}")
                authError = try {
                    val b = JSONObject(bodyStr)
                    b.optString("error_description", b.optString("msg", "Неверный логин или пароль"))
                } catch (e: Exception) { "Неверный логин или пароль" }
                return@launch
            }
            prefs.edit()
                .putString("sb_username", username)
                .putString("sb_token", sb.getAuthToken() ?: "")
                .apply()
            loadProfileFromSupabase(username)
            authError = ""
            startPresencePoller()
            onSuccess()
        } catch (e: Exception) {
            authError = "❌ ${e.message}"
        } finally { authLoading = false }
    }
}
}
