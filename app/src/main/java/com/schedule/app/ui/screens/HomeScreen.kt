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
    onModeChange: (Boolean) -> Unit,
    onFileClick: (ScheduleFile) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val t = LocalTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        // Контент
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 0.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // ── Hero section (.home-hero) ──
            HomeHero()

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
private fun HomeHero() {
    val t = LocalTheme.current

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
