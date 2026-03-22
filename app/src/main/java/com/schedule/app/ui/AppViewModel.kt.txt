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

// 鈹�鈹� 袦芯写械谢褜 褋芯芯斜褖械薪懈褟 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
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

    // 鈹�鈹� 孝械屑邪 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
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

    // 鈹�鈹� 楔褉懈褎褌 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
    var currentFontId by mutableStateOf(prefs.getString("font_id", "default") ?: "default")

    fun setFont(id: String) {
        currentFontId = id
        prefs.edit().putString("font_id", id).apply()
        showToast("馃敜 楔褉懈褎褌: ${com.schedule.app.ui.theme.AppColors.fonts.firstOrNull { it.id == id }?.name ?: id}")
    }

    // 鈹�鈹� Liquid Glass optimization 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
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
        hotPatchStatus = "馃攧 袩褉芯胁械褉褟褞 芯斜薪芯胁谢械薪懈褟..."
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            hotPatchStatus = "鉁� 袨斜薪芯胁谢械薪懈泄 薪械褌"
            kotlinx.coroutines.delay(3000)
            hotPatchStatus = ""
        }
    }

    // 鈹�鈹� 袧邪褋褌褉芯泄泻懈 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
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

    // 鈹�鈹� 肖邪泄谢褘 / 褉邪褋锌懈褋邪薪懈械 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
    var files         by mutableStateOf<List<ScheduleFile>>(emptyList())
    var selectedFile  by mutableStateOf<ScheduleFile?>(null)
    var isLoading     by mutableStateOf(false)
    var loadProgress  by mutableStateOf(0f)
    var statusText    by mutableStateOf("")

    fun loadFiles() {
        if (yandexUrl.isEmpty()) return
        viewModelScope.launch {
            isLoading = true; statusText = "袟邪谐褉褍卸邪褞 褎邪泄谢褘..."; loadProgress = 0.3f
            try {
                files = withContext(Dispatchers.IO) { fetchYandexFiles(yandexUrl) }
                loadProgress = 1f; statusText = "袧邪泄写械薪芯: ${files.size} 褎邪泄谢芯胁"
                delay(1200); loadProgress = 0f; statusText = ""
            } catch (e: Exception) {
                loadProgress = 0f; statusText = "鉂� ${e.message}"
            } finally { isLoading = false }
        }
    }

    // 鈹�鈹� 袚褉褍锌锌褘 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
    var groups        by mutableStateOf<List<String>>(emptyList())
    var selectedGroup by mutableStateOf<String?>(prefs.getString("last_group", null))

    fun selectGroup(group: String) {
        selectedGroup = group
        prefs.edit().putString("last_group", group).apply()
        loadSchedule(group)
    }

    // 鈹�鈹� 袩褉械锌芯写邪胁邪褌械谢懈 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
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
                    header = "袪邪褋锌懈褋邪薪懈械: $teacher",
                    pairs  = listOf(
                        Pair("I",  "08:30","09:15","09:20","10:05","袚褉褍锌锌邪 101","","邪褍写. 201"),
                        Pair("II", "10:15","11:00","11:05","11:50","袚褉褍锌锌邪 204","","邪褍写. 201"),
                        Pair("III","12:20","13:05","13:10","13:55","袚褉褍锌锌邪 302","","邪褍写. 315"),
                    )
                )
            )
            isLoading = false
        }
    }

    fun selectFile(file: ScheduleFile) {
        selectedFile = file
        // Demo 鈥� real impl would parse .docx for both groups and teachers
        groups = listOf(
            "袦袩袛-2-24","袦袩袛-1-24","袩袣-3-23","袩袣-2-23","袠小-1-24",
            "袠小-2-23","孝协-1-24","孝协-2-23","袘袛-1-24","小袗-3-22",
        )
        teachers = listOf(
            "袠胁邪薪芯胁邪 袗.袙.","袩械褌褉芯胁 袠.小.","小懈写芯褉芯胁邪 袝.袦.",
            "袣芯蟹谢芯胁 袛.袗.","袧懈泻芯谢邪械胁邪 袨.袩.","肖械写芯褉芯胁 袙.袚.",
            "小屑懈褉薪芯胁邪 袥.袧.","袣褍蟹薪械褑芯胁 袗.袪.","袩芯锌芯胁 袦.小.",
        )
    }

    // 鈹�鈹� 袪邪褋锌懈褋邪薪懈械 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
    var scheduleDays by mutableStateOf<List<ScheduleDay>>(emptyList())

    private fun loadSchedule(group: String) {
        viewModelScope.launch {
            isLoading = true
            delay(400)
            scheduleDays = buildDemoSchedule(group)
            isLoading = false
        }
    }

    // 鈹�鈹� 袟胁芯薪泻懈 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
    val bellSchedules: List<BellSchedule> by lazy {
        val now = java.util.Calendar.getInstance()
        val dow = now.get(java.util.Calendar.DAY_OF_WEEK)
        listOf(
            BellSchedule("袩芯薪械写械谢褜薪懈泻", dow == 2, listOf(
                BellPeriod("I",   "09:00","09:45","09:50","10:35", isNow = dow==2 && isNowPair(9*60, 10*60+35)),
                BellPeriod("II",  "10:45","11:30","11:35","12:20"),
                BellPeriod("III", "12:50","13:35","13:40","14:25"),
                BellPeriod("IV",  "14:35","15:35",null,null),
                BellPeriod("V",   "15:45","16:45",null,null),
                BellPeriod("VI",  "16:55","17:55",null,null),
            )),
            BellSchedule("袙褌芯褉薪懈泻 鈥� 袩褟褌薪懈褑邪", dow in 3..6, listOf(
                BellPeriod("I",   "08:30","09:15","09:20","10:05", isNow = dow in 3..6 && isNowPair(8*60+30, 10*60+5)),
                BellPeriod("II",  "10:15","11:00","11:05","11:50"),
                BellPeriod("III", "12:20","13:05","13:10","13:55"),
                BellPeriod("IV",  "14:05","15:05",null,null),
                BellPeriod("V",   "15:15","16:15",null,null),
                BellPeriod("VI",  "16:25","17:25",null,null),
            )),
            BellSchedule("小褍斜斜芯褌邪", dow == 7, listOf(
                BellPeriod("I",   "08:30","09:30",null,null),
                BellPeriod("II",  "09:40","10:40",null,null),
                BellPeriod("III", "10:50","11:50",null,null),
                BellPeriod("IV",  "12:00","13:00",null,null),
                BellPeriod("V",   "13:10","14:10",null,null),
                BellPeriod("VI",  "14:20","15:20",null,null),
            )),
        )
    }

    // 鈹�鈹� 袛芯屑邪褕薪械械 蟹邪写邪薪懈械 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
    var hwItems by mutableStateOf<List<HomeworkItem>>(emptyList())
        private set

    init {
        loadHw()
        // 袙芯褋褋褌邪薪邪胁谢懈胁邪械屑 褋械褋褋懈褞 械褋谢懈 斜褘谢 蟹邪谢芯谐懈薪械薪
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

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    // SUPABASE 鈥� 袗袙孝袨袪袠袟袗笑袠携
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

    var userProfile      by mutableStateOf<UserProfile?>(null)
    var authLoading      by mutableStateOf(false)
    var authError        by mutableStateOf("")
    var regError         by mutableStateOf("")

    // 袩芯谢褟 褉械谐懈褋褌褉邪褑懈懈
    var regName          by mutableStateOf("")
    var regUsername      by mutableStateOf("")
    var regUsernameStatus by mutableStateOf("")
    var regUsernameValid  by mutableStateOf(false)
    var regEmoji         by mutableStateOf("馃槉")
    var regPassword      by mutableStateOf("")
    var regEnabled       by mutableStateOf(false)

    // 袩芯谢褟 胁褏芯写邪
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
            v.length < 3 -> "袦懈薪懈屑褍屑 3 褋懈屑胁芯谢邪"
            else         -> "鉁� 袛芯褋褌褍锌械薪"
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
     * 袪械谐懈褋褌褉邪褑懈褟: 锌褉芯胁械褉褟械屑 褔褌芯 username 薪械 蟹邪薪褟褌 鈫� 褋芯蟹写邪褢屑 蟹邪锌懈褋褜 胁 users.
     * 袗胁褌芯褉懈蟹邪褑懈褟 褔械褉械蟹 email: username@sapp.local (Supabase 褌褉械斜褍械褌 email).
     */


    /**
     * 袙褏芯写: email = username@sapp.local + 锌邪褉芯谢褜.
     * 袩芯褋谢械 胁褏芯写邪 蟹邪谐褉褍卸邪械屑 锌褉芯褎懈谢褜 懈蟹 褌邪斜谢懈褑褘 users.
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
                        avatar     = row.optString("avatar",   "馃槉"),
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
            // 袩芯褋谢械 蟹邪谐褉褍蟹泻懈 锌褉芯褎懈谢褟 鈥� 谐褉褍蟹懈屑 写褉褍蟹械泄 懈 谢懈写械褉斜芯褉写
            loadFriendsFromSupabase(username)
            loadLeaderboardFromSupabase()
        } catch (e: Exception) {
            log.e("AppViewModel", "loadProfile error: ${e.message}")
        }
    }

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    // SUPABASE 鈥� PRESENCE (芯薪谢邪泄薪-褋褌邪褌褍褋)
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

    var onlineUsers by mutableStateOf<List<String>>(emptyList())
    private var presenceJob: Job? = null

    private fun startPresencePoller() {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            while (isActive) {
                updatePresence()
                delay(30_000) // 泻邪卸写褘械 30 褋械泻
            }
        }
    }

    private suspend fun updatePresence() {
        val p = userProfile ?: return
        try {
            // 袨斜薪芯胁谢褟械屑 褋胁芯泄 presence
            val now = System.currentTimeMillis()
            val presenceJson = JSONObject().apply {
                put("username", p.username)
                put("status",   p.status)
                put("ts",       now)
            }.toString()
            withContext(Dispatchers.IO) { sb.upsert("presence", presenceJson) }

            // 袩芯谢褍褔邪械屑 泻褌芯 芯薪谢邪泄薪 (邪泻褌懈胁械薪 锌芯褋谢械写薪懈械 2 屑懈薪褍褌褘)
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

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    // SUPABASE 鈥� 袛袪校袟鞋携
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

    var friends          by mutableStateOf<List<FriendEntry>>(emptyList())
    var friendsLoading   by mutableStateOf(false)
    var friendsSearchQuery by mutableStateOf("")

    private suspend fun loadFriendsFromSupabase(username: String) {
        try {
            friendsLoading = true
            // 袩芯谢褍褔邪械屑 褋锌懈褋芯泻 写褉褍蟹械泄 懈蟹 褌邪斜谢懈褑褘 users
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

            // 袟邪谐褉褍卸邪械屑 锌褉芯褎懈谢懈 写褉褍蟹械泄
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
                    avatar   = u.optString("avatar", "馃槉"),
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

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    // SUPABASE 鈥� 袥袠袛袝袪袘袨袪袛
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

    var leaderboard      by mutableStateOf<List<LeaderboardEntry>>(emptyList())
    var leaderboardGame by mutableStateOf("snake")
    private set
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
                    avatar   = row.optString("avatar", "馃槉"),
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

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    // SUPABASE 鈥� 袪袝袛袗袣孝袠袪袨袙袗袧袠袝 袩袪袨肖袠袥携
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

    var editName     by mutableStateOf("")
    var editBio      by mutableStateOf("")
    var editEmoji    by mutableStateOf("馃槉")
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

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    // SUPABASE 鈥� 小袨袨袘些袝袧袠携
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

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
                // 袨锌褌懈屑懈褋褌懈褔薪芯 写芯斜邪胁谢褟械屑 胁 褋锌懈褋芯泻
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
                messageInput = text // 胁械褉薪褍褌褜 褌械泻褋褌 械褋谢懈 芯褕懈斜泻邪
            } finally {
                sendingMessage = false
            }
        }
    }

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    // UI 小袨小孝袨携袧袠携
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

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

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    // 校孝袠袥袠孝蝎
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

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
                header = "袪袗小袩袠小袗袧袠袝: $group",
                pairs  = listOf(
                    Pair("I",  "08:30","09:15","09:20","10:05","袦邪褌械屑邪褌懈泻邪","袠胁邪薪芯胁邪 袗.袙.","邪褍写. 204",  isNow = isNow(8*60+30, 10*60+5)),
                    Pair("II", "10:15","11:00","11:05","11:50","袠薪褎芯褉屑邪褌懈泻邪","袩械褌褉芯胁 袠.小.","邪褍写. 301 (锌泻)"),
                    Pair("III","12:20","13:05","13:10","13:55","袠褋褌芯褉懈褟","小懈写芯褉芯胁邪 袝.袦.","邪褍写. 115"),
                    Pair("IV", "14:05","15:05",null,null,"袨泻薪芯", isWindow = true),
                    Pair("V",  "15:15","16:15",null,null,"肖懈蟹泻褍谢褜褌褍褉邪","袣芯蟹谢芯胁 袛.袗.","褋锌芯褉褌蟹邪谢"),
                )
            )
        )
    }


    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    // SUPABASE 鈥� 袦袝小小袝袧袛袞袝袪 (褋锌懈褋芯泻 褔邪褌芯胁)
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

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
            // 袩芯谢褍褔邪械屑 锌芯褋谢械写薪懈械 褋芯芯斜褖械薪懈褟 懈蟹 胁褋械褏 褔邪褌芯胁 谐写械 褟 褍褔邪褋褌薪懈泻
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

            // 袟邪谐褉褍卸邪械屑 锌褉芯褎懈谢懈 褋芯斜械褋械写薪懈泻芯胁
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

            // 袛谢褟 泻邪卸写芯谐芯 褔邪褌邪 锌芯谢褍褔邪械屑 锌芯褋谢械写薪械械 褋芯芯斜褖械薪懈械
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
                    avatar         = peer?.optString("avatar", "馃槉") ?: "馃槉",
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

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    // SUPABASE 鈥� 袨袧袥袗袡袧 袩袨袥鞋袟袨袙袗孝袝袥袠
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

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
                        avatar     = row.optString("avatar","馃槉"),
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

    // 鈹�鈹� Peer profile 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
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
                            avatar     = row.optString("avatar","馃槉"),
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
                // 袛芯斜邪胁谢褟械屑 胁 屑邪褋褋懈胁 friends 胁 褌邪斜谢懈褑械 users
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

    // 鈹�鈹� 袚褉褍锌锌 褔邪褌褘 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
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
                            name        = row.optString("name","袚褉褍锌锌邪"),
                            avatar      = row.optString("avatar","馃懃"),
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


    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    // FUN FEATURES & EASTER EGGS
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

    // 鈹�鈹� Dev Console 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
    var showConsole       by mutableStateOf(false)
    var consoleInput      by mutableStateOf("")
    var consoleEntries    by mutableStateOf(listOf(
        com.schedule.app.ui.screens.ConsoleEntry("info", "鈿� ScheduleApp Dev Console"),
        com.schedule.app.ui.screens.ConsoleEntry("muted", "袙胁械写懈 /help 写谢褟 褋锌懈褋泻邪 泻芯屑邪薪写"),
        com.schedule.app.ui.screens.ConsoleEntry("muted", "鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�"),
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
                cmdPrint("info", "袛芯褋褌褍锌薪褘械 泻芯屑邪薪写褘:")
                cmdPrint("ok",   "  /help 鈥� 褝褌芯褌 褋锌懈褋芯泻")
                cmdPrint("ok",   "  /fact 鈥� 褋谢褍褔邪泄薪褘泄 褎邪泻褌 馃挕")
                cmdPrint("ok",   "  /haiku 鈥� 褏邪泄泻褍 锌褉芯 褍褔褢斜褍 馃尭")
                cmdPrint("ok",   "  /excuse 鈥� 芯褌屑邪蟹泻邪 写谢褟 锌褉芯锌褍褋泻邪 馃摑")
                cmdPrint("ok",   "  /quiz 鈥� 褌械褋褌 锌芯 褉邪褋锌懈褋邪薪懈褞 馃")
                cmdPrint("ok",   "  /bpmtap 鈥� BPM 褌邪锌邪谢泻邪 馃")
                cmdPrint("ok",   "  /taunt 鈥� 褉械锌谢懈泻邪 褍褔懈褌械谢褟 馃懇鈥嶐煆�")
                cmdPrint("ok",   "  /stats 鈥� 褋褌邪褌懈褋褌懈泻邪 锌褉懈谢芯卸械薪懈褟 馃搳")
                cmdPrint("ok",   "  /motivation 鈥� 屑芯褌懈胁邪褑懈褟 薪邪 褋械谐芯写薪褟 馃挭")
                cmdPrint("ok",   "  /greeting 鈥� 锌褉邪蟹写薪懈褔薪芯械 锌褉懈胁械褌褋褌胁懈械 馃帀")
                cmdPrint("ok",   "  /focus 鈥� 褉械卸懈屑 褎芯泻褍褋邪 馃幆")
                cmdPrint("ok",   "  /clear 鈥� 芯褔懈褋褌懈褌褜 泻芯薪褋芯谢褜")
                cmdPrint("ok",   "  /version 鈥� 胁械褉褋懈褟 锌褉懈谢芯卸械薪懈褟")
                cmdPrint("ok",   "  /vip <袣袨袛> 鈥� 邪泻褌懈胁懈褉芯胁邪褌褜 VIP 馃憫")
                cmdPrint("ok",   "  /logout 鈥� 胁褘泄褌懈 懈蟹 邪泻泻邪褍薪褌邪")
                cmdPrint("ok",   "  /theme <id> 鈥� 褋屑械薪懈褌褜 褌械屑褍")
                cmdPrint("ok",   "  /group 鈥� 褋谢褍褔邪泄薪邪褟 谐褉褍锌锌邪 馃幉")
            }
            "fact" -> {
                activeFunOverlay = com.schedule.app.ui.FunOverlay.None
                cmdPrint("ok", "馃挕 ${com.schedule.app.ui.screens.RANDOM_FACTS.random()}")
            }
            "haiku" -> {
                activeFunOverlay = com.schedule.app.ui.FunOverlay.Haiku
                cmdPrint("ok", "馃尭 袨褌泻褉褘胁邪褞 褏邪泄泻褍...")
            }
            "excuse" -> {
                activeFunOverlay = com.schedule.app.ui.FunOverlay.Excuse
                cmdPrint("ok", "馃摑 袚械薪械褉懈褉褍褞 芯褌屑邪蟹泻褍...")
            }
            "quiz" -> {
                activeFunOverlay = com.schedule.app.ui.FunOverlay.Quiz
                cmdPrint("ok", "馃 袟邪锌褍褋泻邪褞 褌械褋褌...")
            }
            "bpmtap" -> {
                activeFunOverlay = com.schedule.app.ui.FunOverlay.BpmTapper
                cmdPrint("ok", "馃 BPM 褌邪锌邪谢泻邪 蟹邪锌褍褖械薪邪")
            }
            "taunt" -> {
                cmdPrint("ok", com.schedule.app.ui.screens.TEACHER_TAUNTS.random())
            }
            "stats" -> {
                activeFunOverlay = com.schedule.app.ui.FunOverlay.Stats
                cmdPrint("ok", "馃搳 袨褌泻褉褘胁邪褞 褋褌邪褌懈褋褌懈泻褍...")
            }
            "motivation" -> {
                cmdPrint("ok", "馃挭 ${com.schedule.app.ui.screens.getDayMotivation()}")
            }
            "greeting" -> {
                val g = com.schedule.app.ui.screens.getSpecialDateGreeting()
                if (g != null) {
                    activeFunOverlay = com.schedule.app.ui.FunOverlay.Greeting
                    showGreeting = true
                    cmdPrint("ok", "${g.second} ${g.first} 鈥� ${g.third}")
                } else {
                    cmdPrint("warn", "小械谐芯写薪褟 薪械褌 锌褉邪蟹写薪懈泻邪 馃槩")
                }
            }
            "focus" -> {
                focusMode = !focusMode
                cmdPrint("ok", if (focusMode) "馃幆 袪械卸懈屑 褎芯泻褍褋邪 胁泻谢褞褔褢薪" else "馃幆 袪械卸懈屑 褎芯泻褍褋邪 胁褘泻谢褞褔械薪")
            }
            "clear" -> {
                consoleEntries = listOf(
                    com.schedule.app.ui.screens.ConsoleEntry("muted", "鈹�鈹� 泻芯薪褋芯谢褜 芯褔懈褖械薪邪 鈹�鈹�")
                )
            }
            "version" -> {
                cmdPrint("ok", "馃摝 ScheduleApp v4.8.7 (Native Kotlin/Compose)")
                cmdPrint("ok", "  Android ${android.os.Build.VERSION.RELEASE} 路 ${android.os.Build.MODEL}")
            }
            "vip" -> {
                val vipCodes = listOf("SAPP_VIP_2024", "LOMKICHVIP", "袣袨袛袙袠袩")
                if (arg.isEmpty()) {
                    cmdPrint("info", "馃憫 袠褋锌芯谢褜蟹芯胁邪薪懈械: /vip <袣袨袛>")
                } else if (vipCodes.contains(arg.uppercase())) {
                    val p = userProfile
                    if (p != null) {
                        userProfile = p.copy(vip = true)
                        viewModelScope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                sb.update("users", "username=eq.${p.username}", """{"vip":true}""")
                            }
                        }
                        cmdPrint("ok", "馃憫 VIP 邪泻褌懈胁懈褉芯胁邪薪! 袛芯褋褌褍锌薪褘: 褎芯褌芯-邪胁邪褌邪褉, 褉邪屑泻懈, 蟹薪邪褔泻懈, 斜邪薪薪械褉")
                    } else {
                        cmdPrint("err", "鉂� 袧褍卸薪芯 胁芯泄褌懈 胁 邪泻泻邪褍薪褌")
                    }
                } else {
                    cmdPrint("err", "鉂� 袧械胁械褉薪褘泄 泻芯写 VIP")
                }
            }
            "logout" -> {
                doLogout()
                cmdPrint("ok", "馃憢 袙褘褏芯写 胁褘锌芯谢薪械薪")
            }
            "theme" -> {
                if (arg.isEmpty()) {
                    cmdPrint("info", "孝械屑褘: orange, amoled, win11, pixel, forest, rose, gold, purple, sunset, bw, ocean, candy, samek, light")
                } else {
                    setTheme(arg)
                    cmdPrint("ok", "馃帹 孝械屑邪 懈蟹屑械薪械薪邪: $arg")
                }
            }
            "group" -> {
                if (groups.isNotEmpty()) {
                    val g = groups.random()
                    cmdPrint("ok", "馃幉 小谢褍褔邪泄薪邪褟 谐褉褍锌锌邪: $g")
                    selectGroup(g)
                } else {
                    cmdPrint("warn", "小薪邪褔邪谢邪 蟹邪谐褉褍蟹懈 褎邪泄谢 褉邪褋锌懈褋邪薪懈褟")
                }
            }
            "deleteaccount" -> {
                cmdPrint("warn", "鈿狅笍 袙胁械写懈 /deleteaccount 袩袨袛孝袙袝袪袞袛袗挟 写谢褟 褍写邪谢械薪懈褟")
                if (arg == "袩袨袛孝袙袝袪袞袛袗挟") {
                    val p = userProfile
                    if (p != null) {
                        viewModelScope.launch {
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                sb.delete("users", "username=eq.${p.username}")
                            }
                            doLogout()
                        }
                        cmdPrint("err", "馃拃 袗泻泻邪褍薪褌 褍写邪谢褢薪")
                    }
                }
            }
            else -> {
                cmdPrint("err", "鉂� 袧械懈蟹胁械褋褌薪邪褟 泻芯屑邪薪写邪: /$cmd  (胁胁械写懈 /help)")
            }
        }
        consoleInput = ""
    }

    // 鈹�鈹� Fun Overlays 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
    var activeFunOverlay  by mutableStateOf(com.schedule.app.ui.FunOverlay.None)
    var showGreeting      by mutableStateOf(false)

    // 鈹�鈹� Focus mode 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
    var focusMode         by mutableStateOf(false)

    // 鈹�鈹� Schedule search 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
    var schedSearchVisible by mutableStateOf(false)
    var schedSearchQuery   by mutableStateOf("")

    // 鈹�鈹� Starred pairs 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
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

    // 鈹�鈹� Pair notes 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
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

    // 鈹�鈹� Visit streak 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
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
                    showToast("馃敟 $streak 写薪械泄 锌芯写褉褟写! 孝邪泻 写械褉卸邪褌褜!")
                }
            }
        } catch (e: Exception) {}
    }

    // 鈹�鈹� Toast 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
    // 鈹�鈹� Games launcher 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
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

    // 鈹�鈹� Share schedule 鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�鈹�
    var pendingShareText by mutableStateOf<String?>(null)

    fun buildShareText(): String {
        val group = selectedGroup ?: return ""
        val lines = mutableListOf("馃搮 $group", "")
        scheduleDays.firstOrNull()?.pairs?.forEach { pair ->
            if (!pair.isWindow) {
                lines.add("${pair.num}. ${pair.subject}${if (pair.timeStart.isNotEmpty()) " 路 ${pair.timeStart}鈥�${pair.timeEnd}" else ""}")
            }
        }
        lines.add(""); lines.add("馃摬 ScheduleApp")
        return lines.joinToString("\n")
    }

    companion object {
        private val EMOJI_POOL = listOf(
            "馃槉","馃槑","馃","馃コ","馃槒","馃ぉ","馃","馃惡","馃","馃惛",
            "馃惐","馃惗","馃","馃惣","馃","馃悏","馃","馃幁","馃","馃懟",
            "馃拃","馃巸","鈿�","馃敟","馃拵","馃寵","猸�","馃寛","馃幍","馃幃","馃弳"
        )
    }



    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    // UI 袛袠袗袥袨袚袠
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

    var showAddHwDialog by mutableStateOf(false)
    fun showAddHwDialog() { showAddHwDialog = true }

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    // 肖袨孝袨-袗袙袗孝袗袪
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

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

            // 袨斜薪芯胁谢褟械屑 谢芯泻邪谢褜薪芯
            userProfile = p.copy(avatarType = "photo", avatarData = dataUri)

            // 小芯褏褉邪薪褟械屑 胁 Supabase
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

    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲
    // CALLBACKS 小 袧袗袙袠袚袗笑袠袝袡 (锌褉懈薪懈屑邪褞褌 谢褟屑斜写褍 onSuccess)
    // 鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲鈺愨晲

    fun doRegister(onSuccess: () -> Unit) {
    if (!regEnabled) return
    val username = regUsername.trim().lowercase()
    val name     = regName.trim()
    val emoji    = regEmoji.ifEmpty { "馃槉" }
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
                if (arr.length() > 0) { regError = "协褌芯褌 薪懈泻薪械泄屑 褍卸械 蟹邪薪褟褌"; return@launch }
            }

            val email = "$username@sapp.local"
            val signUpResult = withContext(Dispatchers.IO) { sb.signUp(email, password) }
            val signUpJson = JSONObject(signUpResult)
            if (!signUpJson.optBoolean("ok")) {
                val bodyStr = signUpJson.optString("body", "{}")
                regError = try { JSONObject(bodyStr).optString("msg", "袨褕懈斜泻邪 褉械谐懈褋褌褉邪褑懈懈") }
                           catch (e: Exception) { "袨褕懈斜泻邪 褉械谐懈褋褌褉邪褑懈懈" }
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
            regError = "鉂� ${e.message}"
        } finally { authLoading = false }
    }
}


    fun doLogin(onSuccess: () -> Unit) {
    val username = authUsername.trim().lowercase()
    val password = authPassword
    if (username.isEmpty() || password.isEmpty()) { authError = "袟邪锌芯谢薪懈 锌芯谢褟"; return }

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
                    b.optString("error_description", b.optString("msg", "袧械胁械褉薪褘泄 谢芯谐懈薪 懈谢懈 锌邪褉芯谢褜"))
                } catch (e: Exception) { "袧械胁械褉薪褘泄 谢芯谐懈薪 懈谢懈 锌邪褉芯谢褜" }
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
            authError = "鉂� ${e.message}"
        } finally { authLoading = false }
    }
}
}
