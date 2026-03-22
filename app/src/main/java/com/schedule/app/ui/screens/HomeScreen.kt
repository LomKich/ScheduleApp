package com.schedule.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.schedule.app.ui.components.*
import com.schedule.app.ui.theme.LocalTheme
import java.util.Calendar

data class ScheduleFile(
    val name: String,
    val path: String,
    val size: Long = 0L,
)

// ─────────────────────────────────────────────────────────────────────────────
// HOME SCREEN  (#s-home)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    files: List<ScheduleFile>,
    selectedFile: ScheduleFile?,
    isLoading: Boolean,
    loadProgress: Float,
    statusText: String,
    yandexUrl: String,
    isTeacher: Boolean,
    hwActiveCount: Int = 0,
    onModeChange: (Boolean) -> Unit,
    onFileClick: (ScheduleFile) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHomework: () -> Unit = {},
) {
    val t = LocalTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        // ── Топ-бар с кнопкой настроек ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(t.bg)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(t.surface2)
                    .border(1.dp, t.surface3, RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Text("⚙️", fontSize = 20.sp)
            }
        }

        // Контент
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 0.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Hero section (.home-hero) ──
            HomeHero(onSecretTap = {})

            // ── Статус-строка (урок / ДЗ) ──
            HomeStatusLine(hwActiveCount = hwActiveCount, onOpenHomework = onOpenHomework)

            // ── Прогресс недели ──
            WeekProgressWidget()

            Spacer(Modifier.height(8.dp))

            // ── Mode toggle pill ──
            ModeTogglePill(
                isTeacher = isTeacher,
                onModeChange = onModeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )

            // ── Status + progress bar ──
            if (statusText.isNotEmpty()) {
                StatusText(text = statusText)
            } else {
                Spacer(Modifier.height(18.dp))
            }
            AppProgressBar(
                progress = loadProgress,
                modifier = Modifier.padding(vertical = 6.dp),
            )

            // ── Файлы ──
            when {
                yandexUrl.isEmpty() -> {
                    // .no-url-hint
                    NoUrlHint(onOpenSettings = onOpenSettings)
                }
                isLoading && files.isEmpty() -> {
                    // Скелетоны
                    SectionLabel("Файлы")
                    repeat(4) { SkeletonItem() }
                }
                files.isEmpty() && !isLoading -> {
                    // Ошибка / пустой список
                    EmptyState(
                        icon = "🔌",
                        title = "Не удалось загрузить файлы",
                        subtitle = "Проверь подключение к интернету",
                    )
                    AppButton(
                        label = "🔄 Повторить",
                        onClick = onRetry,
                        variant = BtnVariant.Surface,
                    )
                }
                else -> {
                    // Список файлов
                    SectionLabel("Файлы")
                    files.forEach { file ->
                        ListItemRow(
                            name = file.name,
                            sub = if (file.size > 0) "${file.size / 1024} КБ" else null,
                            selected = file.name == selectedFile?.name,
                            onClick = { onFileClick(file) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(100.dp)) // отступ под nav bar
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HERO CARD  (.home-hero / .hero-card / .hero-title)
//
// Плашка «Расписание Студентам» с radial-gradient glow сверху
// и accent text-shadow
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HomeHero(onSecretTap: () -> Unit = {}) {
    val t = LocalTheme.current
    var tapCount by remember { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Radial gradient glow (::before pseudo-element)
        Canvas(
            modifier = Modifier
                .size(240.dp)
                .align(Alignment.TopCenter),
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        t.accent.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                ),
            )
        }

        // Hero card
        Box(
            modifier = Modifier
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(24.dp),
                    ambientColor = Color.Black.copy(alpha = 0.32f),
                )
                .clip(RoundedCornerShape(24.dp))
                .background(t.surface2)
                .border(1.5.dp, t.surface3, RoundedCornerShape(24.dp))
                .clickable {
                    tapCount++
                    if (tapCount >= 7) { tapCount = 0; onSecretTap() }
                }
                .padding(horizontal = 32.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Расписание\nСтудентам",
                color = t.accent,
                fontSize = 38.sp,
                fontWeight = FontWeight(800),
                lineHeight = 42.sp,
                letterSpacing = (-0.03).em,
                textAlign = TextAlign.Center,
                // text-shadow: glow — через drawBehind не делается точно,
                // но достигаем похожего эффекта через Shadow
                style = androidx.compose.ui.text.TextStyle(
                    shadow = Shadow(
                        color = t.accent.copy(alpha = 0.55f),
                        offset = Offset(0f, 0f),
                        blurRadius = 20f,
                    ),
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NO URL HINT  (#no-url-hint)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun NoUrlHint(onOpenSettings: () -> Unit) {
    val t = LocalTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("⚙️", fontSize = 38.sp)
        Text(
            text = "Укажи ссылку на Яндекс Диск\nв ",
            color = t.muted,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        AppButton(
            label = "Открыть настройки →",
            onClick = onOpenSettings,
            variant = BtnVariant.Accent,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HOME STATUS LINE  (#home-status-line)
// Показывает: «урок X · N мин» и «N заданий ДЗ»
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HomeStatusLine(
    hwActiveCount: Int,
    onOpenHomework: () -> Unit,
) {
    val t = LocalTheme.current

    // Вычисляем ближайшую пару в реальном времени
    val statusText = remember {
        val now = Calendar.getInstance()
        val h = now.get(Calendar.HOUR_OF_DAY)
        val m = now.get(Calendar.MINUTE)
        val nowMin = h * 60 + m
        val dow = now.get(Calendar.DAY_OF_WEEK)

        // Расписание пар: пн = 2, вт-пт = 3-6, сб = 7
        val schedule = when {
            dow == 2 -> listOf(
                Triple("I",   9*60,    9*60+45),
                Triple("II",  10*60+45, 11*60+30),
                Triple("III", 12*60+50, 13*60+35),
                Triple("IV",  14*60+35, 15*60+35),
            )
            dow in 3..6 -> listOf(
                Triple("I",   8*60+30, 9*60+15),
                Triple("II",  10*60+15, 11*60),
                Triple("III", 12*60+20, 13*60+5),
                Triple("IV",  14*60+5,  15*60+5),
            )
            else -> emptyList()
        }

        val parts = mutableListOf<String>()

        // Текущая пара?
        val current = schedule.firstOrNull { nowMin in it.second..it.third }
        if (current != null) {
            val left = current.third - nowMin
            parts.add("урок ${current.first} · $left мин")
        } else {
            // Следующая пара
            val next = schedule.firstOrNull { it.second > nowMin }
            if (next != null) {
                val toStart = next.second - nowMin
                parts.add("до пары ${next.first} · $toStart мин")
            }
        }

        parts.joinToString("  ·  ")
    }

    if (statusText.isEmpty() && hwActiveCount == 0) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                color = t.muted,
                fontSize = 12.sp,
            )
        }
        if (statusText.isNotEmpty() && hwActiveCount > 0) {
            Text("  ·  ", color = t.muted, fontSize = 12.sp)
        }
        if (hwActiveCount > 0) {
            Text(
                text = "$hwActiveCount задан${
                    when {
                        hwActiveCount == 1 -> "ие"
                        hwActiveCount in 2..4 -> "ия"
                        else -> "ий"
                    }
                } ДЗ",
                color = t.accent,
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onOpenHomework),
            )
        }
    }
}
