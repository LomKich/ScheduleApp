package com.schedule.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.schedule.app.ui.navigation.AppScreen
import com.schedule.app.ui.theme.AppTheme
import androidx.compose.runtime.LaunchedEffect

class ComposeActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private fun switchToWebView() {
        getSharedPreferences("sapp_prefs", MODE_PRIVATE)
            .edit().putBoolean("use_native_ui", false).apply()
        startActivity(
            Intent(this, com.schedule.app.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.loadFiles()

        setContent {
            AppTheme(themeState = vm.themeState, fontId = vm.currentFontId) {

                // ── Единый picker: флаг определяет — аватар или фон ──────────
                // ФИКС: два отдельных GetContent()-лаунчера путались местами —
                // Android регистрирует их в порядке объявления и иногда возвращает
                // результат не тому. Один лаунчер с флагом решает проблему.
                var pendingPickType by rememberSaveable { mutableStateOf("avatar") }

                val imageLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) {
                        if (pendingPickType == "avatar") {
                            vm.onPhotoPicked(uri, contentResolver)
                        } else {
                            vm.onBgImagePicked(uri, contentResolver)
                        }
                    }
                }

                // ── Media picker для чата (фото/видео/файлы) ─────────────────
                val mediaLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    if (uri != null) vm.sendMediaMessage(uri, contentResolver)
                }

                // Share text via Android share sheet
                val shareText = vm.pendingShareText
                if (shareText != null) {
                    LaunchedEffect(shareText) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        startActivity(Intent.createChooser(intent, "Поделиться расписанием"))
                        vm.pendingShareText = null
                    }
                }

                // Launch GamesActivity when requested
                if (vm.pendingLaunchGames) {
                    LaunchedEffect(Unit) {
                        val intent = Intent(this@ComposeActivity, GamesActivity::class.java).apply {
                            putExtra("theme_id", vm.themeState.current.id)
                            putExtra("username", vm.userProfile?.username ?: "")
                        }
                        startActivity(intent)
                        vm.onGamesLaunched()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars),
                ) {
                    AppScreen(
                        vm               = vm,
                        onSwitchToWebView= ::switchToWebView,
                        onPickPhoto      = { pendingPickType = "avatar"; imageLauncher.launch("image/*") },
                        onPickBgImage    = { pendingPickType = "bg";     imageLauncher.launch("image/*") },
                        onPickChatMedia  = { mediaLauncher.launch("*/*") },
                    )
                }
            }
        }
    }
}
