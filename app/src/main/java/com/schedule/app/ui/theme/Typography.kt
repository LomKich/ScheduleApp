package com.schedule.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ScheduleTypography = Typography(

    // Крупные заголовки (название экрана — «Плеер», «Внешний вид»)
    displayLarge = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Bold,
        fontSize    = 32.sp,
        lineHeight  = 40.sp,
        letterSpacing = (-0.5).sp,
        color       = TextPrimary
    ),
    displayMedium = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Bold,
        fontSize    = 28.sp,
        lineHeight  = 36.sp,
        letterSpacing = (-0.25).sp,
        color       = TextPrimary
    ),

    // Заголовок экрана настроек / секции
    headlineLarge = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 24.sp,
        lineHeight  = 32.sp,
        color       = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.SemiBold,
        fontSize    = 20.sp,
        lineHeight  = 28.sp,
        color       = TextPrimary
    ),
    headlineSmall = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Medium,
        fontSize    = 18.sp,
        lineHeight  = 24.sp,
        color       = TextPrimary
    ),

    // Заголовок пункта настроек
    titleLarge = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Medium,
        fontSize    = 16.sp,
        lineHeight  = 24.sp,
        letterSpacing = 0.sp,
        color       = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Medium,
        fontSize    = 15.sp,
        lineHeight  = 22.sp,
        letterSpacing = 0.1.sp,
        color       = TextPrimary
    ),
    titleSmall = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Medium,
        fontSize    = 13.sp,
        lineHeight  = 20.sp,
        color       = TextSecondary
    ),

    // Тело (описание пунктов настроек — серый мелкий текст)
    bodyLarge = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Normal,
        fontSize    = 14.sp,
        lineHeight  = 20.sp,
        color       = TextSecondary
    ),
    bodyMedium = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Normal,
        fontSize    = 13.sp,
        lineHeight  = 18.sp,
        color       = TextSecondary
    ),
    bodySmall = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Normal,
        fontSize    = 12.sp,
        lineHeight  = 16.sp,
        color       = TextHint
    ),

    // Метки кнопок, табов навигации
    labelLarge = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Medium,
        fontSize    = 14.sp,
        lineHeight  = 20.sp,
        letterSpacing = 0.1.sp,
        color       = TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Medium,
        fontSize    = 12.sp,
        lineHeight  = 16.sp,
        letterSpacing = 0.5.sp,
        color       = TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily  = FontFamily.Default,
        fontWeight  = FontWeight.Medium,
        fontSize    = 11.sp,
        lineHeight  = 16.sp,
        letterSpacing = 0.5.sp,
        color       = TextHint
    ),
)
