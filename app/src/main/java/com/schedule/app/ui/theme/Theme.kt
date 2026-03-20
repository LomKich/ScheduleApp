package com.schedule.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Состояние темы (mutable, живёт в CompositionLocal) ───────────────────────
class ThemeState(initial: AppColors.ThemeDef = AppColors.themes.first()) {
    var current by mutableStateOf(initial)
    val bg       get() = current.bg
    val surface  get() = current.surface
    val surface2 get() = current.surface2
    val surface3 get() = current.surface3
    val accent   get() = current.accent
    val accent2  get() = current.accent2
    val text     get() = current.text
    val muted    get() = current.muted
    val btnText  get() = current.btnText
    val danger   get() = AppColors.Danger
    val success  get() = AppColors.Success
    val border   get() = Color(0x0FFFFFFF)
}

val LocalTheme = staticCompositionLocalOf { ThemeState() }

@Composable
fun AppTheme(
    themeState: ThemeState = remember { ThemeState() },
    content: @Composable () -> Unit,
) {
    val t = themeState
    val colorScheme = darkColorScheme(
        background       = t.bg,
        surface          = t.surface,
        surfaceVariant   = t.surface2,
        primary          = t.accent,
        secondary        = t.accent2,
        onBackground     = t.text,
        onSurface        = t.text,
        onPrimary        = t.btnText,
        error            = t.danger,
    )
    CompositionLocalProvider(LocalTheme provides themeState) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
