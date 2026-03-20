package com.schedule.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.schedule.app.ui.navigation.AppScreen
import com.schedule.app.ui.theme.AppTheme

/**
 * ComposeActivity — нативный Kotlin/Compose интерфейс.
 *
 * Запускается независимо от оригинального MainActivity (WebView).
 * Можно добавить в AndroidManifest как отдельный экран или заменить Main.
 *
 * Дизайн: 1-в-1 с оригинальным HTML/CSS интерфейсом.
 */
class ComposeActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    /** Переключиться обратно на WebView: сбрасываем флаг и запускаем MainActivity */
    private fun switchToWebView() {
        getSharedPreferences("sapp_prefs", MODE_PRIVATE)
            .edit().putBoolean("use_native_ui", false).apply()
        startActivity(
            android.content.Intent(this, com.schedule.app.MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                          android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        vm.loadFiles()

        setContent {
            AppTheme(themeState = vm.themeState) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    AppScreen(
                        // ── Schedule ──────────────────────────────────────────────
                        files          = vm.files,
                        selectedFile   = vm.selectedFile,
                        groups         = vm.groups,
                        selectedGroup  = vm.selectedGroup,
                        scheduleDays   = vm.scheduleDays,
                        hwItems        = vm.hwItems,
                        bellSchedules  = vm.bellSchedules,
                        yandexUrl      = vm.yandexUrl,
                        isLoading      = vm.isLoading,
                        loadProgress   = vm.loadProgress,
                        statusText     = vm.statusText,
                        isMuted        = vm.isMuted,
                        isTeacher      = vm.isTeacher,
                        isGlassMode    = vm.isGlassMode,
                        currentThemeId = vm.themeState.current.id,
                        appVersion     = "4.8.7",

                        // ── Social ────────────────────────────────────────────────
                        userProfile      = vm.userProfile,
                        friends          = vm.friends,
                        leaderboard      = vm.leaderboard,
                        p2pConnected     = vm.p2pConnected,
                        notifEnabled     = vm.notifEnabled,
                        bgServiceEnabled = vm.bgServiceEnabled,

                        // ── Register fields ───────────────────────────────────────
                        regName             = vm.regName,
                        onRegNameChange     = vm::onRegNameChange,
                        regUsername         = vm.regUsername,
                        onRegUsernameChange = vm::onRegUsernameChange,
                        regUsernameStatus   = vm.regUsernameStatus,
                        regUsernameValid    = vm.regUsernameValid,
                        regEmoji            = vm.regEmoji,
                        onRegEmojiChange    = vm::onRegEmojiChange,
                        onRegRandomEmoji    = vm::onRegRandomEmoji,
                        regPassword         = vm.regPassword,
                        onRegPasswordChange = vm::onRegPasswordChange,
                        onRegSubmit         = vm::doRegister,
                        regError            = vm.regError,
                        regEnabled          = vm.regEnabled,

                        // ── Login fields ──────────────────────────────────────────
                        authUsername        = vm.authUsername,
                        onAuthUsernameChange= { vm.authUsername = it },
                        authPassword        = vm.authPassword,
                        onAuthPasswordChange= { vm.authPassword = it },
                        onAuthSubmit        = vm::doLogin,
                        authError           = vm.authError,

                        // ── Profile edit ──────────────────────────────────────────
                        editName          = vm.editName,
                        onEditNameChange  = { vm.editName = it },
                        editBio           = vm.editBio,
                        onEditBioChange   = { vm.editBio = it },
                        editEmoji         = vm.editEmoji,
                        onEditEmojiChange = { vm.editEmoji = it },
                        onEditRandomEmoji = vm::onEditRandomEmoji,
                        editStatus        = vm.editStatus,
                        onEditStatusChange= { vm.editStatus = it },
                        onEditSave        = vm::saveEditProfile,

                        // ── Actions ───────────────────────────────────────────────
                        onUrlChange      = vm::setUrl,
                        onUpdateFiles    = vm::loadFiles,
                        onFileClick      = vm::selectFile,
                        onGroupClick     = vm::selectGroup,
                        onModeChange     = vm::setMode,
                        onToggleMute     = vm::toggleMute,
                        onToggleGlass    = { vm.isGlassMode = !vm.isGlassMode },
                        onThemeSelect    = vm::setTheme,
                        onToggleHwDone   = vm::toggleHwDone,
                        onDeleteHw       = vm::deleteHw,
                        onAddHw          = { /* TODO: AddHw bottom sheet */ },
                        onRetry          = vm::loadFiles,
                        onSwitchToWebView= ::switchToWebView,
                        onReconnect      = { vm.p2pConnected = false },
                        onToggleNotif    = vm::toggleNotif,
                        onToggleBgService= vm::toggleBgService,
                        onPickPhoto      = { /* TODO: photo picker */ },
                        onFriendClick    = { /* TODO: open peer profile */ },
                        onRefreshFriends = { /* TODO: refresh peers */ },
                        friendsSearchQuery   = vm.friendsSearchQuery,
                        onFriendsSearchChange= { vm.friendsSearchQuery = it },
                    )
                }
            }
        }
    }
}
