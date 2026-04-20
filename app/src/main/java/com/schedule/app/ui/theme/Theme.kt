package com.schedule.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ─────────────────────────────────────────────────────────────────
//  Тёмная схема  —  Echo Nightly style
// ─────────────────────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    // Основной акцент
    primary              = AccentPrimary,
    onPrimary            = AccentOnPrimary,
    primaryContainer     = AccentPrimaryContainer,
    onPrimaryContainer   = AccentOnPrimary,

    // Вторичный акцент
    secondary            = AccentSecondary,
    onSecondary          = White,
    secondaryContainer   = AccentSecondaryContainer,
    onSecondaryContainer = White,

    // Третичный (пинк для shuffle / live badge)
    tertiary             = AccentPink,
    onTertiary           = White,
    tertiaryContainer    = Color(0xFF5A1A3A),
    onTertiaryContainer  = White,

    // Фон и поверхности
    background           = BgPrimary,
    onBackground         = TextPrimary,
    surface              = BgSurface,
    onSurface            = TextPrimary,
    surfaceVariant       = BgCard,
    onSurfaceVariant     = TextSecondary,

    // Контейнеры поверхностей (Material3 tokens)
    surfaceContainer             = BgCard,
    surfaceContainerLow          = BgSurface,
    surfaceContainerHigh         = BgCardElevated,
    surfaceContainerHighest      = BgDialog,

    // Обводки
    outline              = Outline,
    outlineVariant       = Divider,

    // Ошибки
    error                = ErrorColor,
    onError              = White,
    errorContainer       = ErrorContainerColor,
    onErrorContainer     = ErrorColor,

    // Инвертированные (для snackbar и т.п.)
    inverseSurface       = White,
    inverseOnSurface     = BgPrimary,
    inversePrimary       = AccentPrimaryDim,

    // Tonal surface
    surfaceTint          = AccentPrimary,
    scrim                = Color(0xCC000000),
)

// ─────────────────────────────────────────────────────────────────
//  Светлая схема  —  fallback (если система требует)
// ─────────────────────────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary              = AccentPrimary,
    onPrimary            = White,
    primaryContainer     = Color(0xFFDDDDFF),
    onPrimaryContainer   = Color(0xFF0A0A50),
    secondary            = AccentSecondary,
    onSecondary          = White,
    background           = Color(0xFFF5F5F5),
    onBackground         = Color(0xFF111111),
    surface              = White,
    onSurface            = Color(0xFF111111),
    error                = ErrorColor,
    onError              = White,
)

// ─────────────────────────────────────────────────────────────────
//  ThemeState  —  снимок текущей темы, доступный через LocalTheme
// ─────────────────────────────────────────────────────────────────
data class ThemeState(
    val isDark: Boolean = true
)

// CompositionLocal — позволяет любому Composable читать val theme = LocalTheme.current
val LocalTheme = compositionLocalOf { ThemeState() }

// ─────────────────────────────────────────────────────────────────
//  AppTheme  —  публичная точка входа (оборачивает ScheduleTheme)
//  Предоставляет LocalTheme в дерево и синхронизирует системные бары
// ─────────────────────────────────────────────────────────────────
@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalTheme provides ThemeState(isDark = darkTheme)) {
        ScheduleTheme(darkTheme = darkTheme, content = content)
    }
}

// ─────────────────────────────────────────────────────────────────
//  ScheduleTheme  —  внутренняя реализация (Material3 + системные бары)
// ─────────────────────────────────────────────────────────────────
@Composable
fun ScheduleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Синхронизируем статус-бар и навигационную панель с темой
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor     = BgPrimary.toArgb()
            window.navigationBarColor = BgPrimary.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = ScheduleTypography,
        content     = content
    )
}
