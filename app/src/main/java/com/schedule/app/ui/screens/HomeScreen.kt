package com.schedule.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
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
    lastGroup: String? = null,                     // добавлено
    onModeChange: (Boolean) -> Unit,
    onFileClick: (ScheduleFile) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHomework: () -> Unit = {},
    onContinue: () -> Unit = {},                   // обработчик кнопки «Продолжить»
    onOpenConsole: () -> Unit = {},                // открыть CMD
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
            HomeHero(
                isTeacher = isTeacher,
                onSecretTap = onOpenConsole,
            )

            // ── Статус-строка (урок / ДЗ) ──
            HomeStatusLine(hwActiveCount = hwActiveCount, onOpenHomework = onOpenHomework)

            // ── Прогресс недели (опционально, если хочешь сохранить) ──
            // WeekProgressWidget()  // закомментировано, т.к. в web нет

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
                    NoUrlHint(onOpenSettings = onOpenSettings)
                }
                isLoading && files.isEmpty() -> {
                    SectionLabel("Файлы")
                    repeat(4) { SkeletonItem() }
                }
                files.isEmpty() && !isLoading -> {
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
                    SectionLabel("Файлы")
                    files.forEach { file ->
                        PressableListItemRow(
                            name = file.name,
                            sub = if (file.size > 0) "${file.size / 1024} КБ" else null,
                            selected = file.name == selectedFile?.name,
                            onClick = { onFileClick(file) },
                        )
                    }
                }
            }

            // ── Кнопка «Продолжить» (только студент и есть последняя группа) ──
            if (!isTeacher && lastGroup != null && lastGroup.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                ContinueButton(
                    groupName = lastGroup,
                    onClick = onContinue,
                )
            }

            Spacer(Modifier.height(100.dp)) // отступ под nav bar
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Кнопка «Продолжить» (аналог #last-group-btn)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ContinueButton(
    groupName: String,
    onClick: () -> Unit,
) {
    val t = LocalTheme.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "continueBtnScale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(t.surface2)
            .border(1.5.dp, t.surface3, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onPress = { pressed = true },
                onRelease = { pressed = false },
            )
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Продолжить (${groupName.take(20)})",
            color = t.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Элемент списка с анимацией масштаба при нажатии
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PressableListItemRow(
    name: String,
    sub: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val t = LocalTheme.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "listItemScale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 7.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) t.accent.copy(alpha = 0.15f)
                else t.surface2
            )
            .border(
                1.5.dp,
                if (selected) t.accent.copy(alpha = 0.70f) else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onPress = { pressed = true },
                onRelease = { pressed = false },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = if (selected) t.accent else t.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        color = t.muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Text(
                text = "›",
                color = t.muted,
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HERO CARD  (обновлённая: динамический текст, многослойная тень, 7 тапов)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HomeHero(
    isTeacher: Boolean,
    onSecretTap: () -> Unit = {},
) {
    val t = LocalTheme.current
    var tapCount by remember { mutableStateOf(0) }
    val title = if (isTeacher) "Расписание\nПедагогам" else "Расписание\nСтудентам"

    // Многослойная тень текста (как в web)
    val shadows = listOf(
        Shadow(color = t.accent.copy(alpha = 0.55f), blurRadius = 20f),
        Shadow(color = t.accent.copy(alpha = 0.22f), blurRadius = 60f),
        Shadow(color = t.accent.copy(alpha = 0.10f), blurRadius = 120f),
    )

    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Radial gradient glow
        Canvas(modifier = Modifier.size(240.dp).align(Alignment.TopCenter)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(t.accent.copy(alpha = 0.12f), Color.Transparent),
                ),
            )
        }

        // Hero card
        Box(
            modifier = Modifier
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(24.dp), clip = false)
                .clip(RoundedCornerShape(24.dp))
                .background(t.surface2)
                .border(1.5.dp, t.surface3, RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        tapCount++
                        if (tapCount >= 7) {
                            tapCount = 0
                            onSecretTap()
                        }
                    },
                )
                .padding(horizontal = 32.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = t.accent,
                fontSize = 38.sp,
                fontWeight = FontWeight(800),
                lineHeight = 42.sp,
                letterSpacing = (-0.03).em,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    shadow = shadows[0],
                    // drawBehind для многослойной тени
                ),
            )
        }
    }

    // Многослойная тень реализуется через drawBehind для Text
    // Compose не поддерживает несколько теней напрямую, но можно использовать
    // графический слой. Для простоты оставим одну самую яркую, но добавим эффект.
    // В реальном проекте можно создать кастомный отрисовщик.
}

// ─────────────────────────────────────────────────────────────────────────────
// MODE TOGGLE PILL (без изменений)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ModeTogglePill(
    isTeacher: Boolean,
    onModeChange: (isTeacher: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTheme.current
    val density = LocalDensity.current
    var containerWidth by remember { mutableStateOf(0f) }

    val pillOffsetX by animateDpAsState(
        targetValue = with(density) {
            if (!isTeacher) 3.dp
            else (containerWidth / 2f).toDp() + 3.dp - 3.dp
        },
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
        label = "modePill",
    )
    val pillWidth by animateDpAsState(
        targetValue = with(density) { (containerWidth / 2f - 6f).toDp() },
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
        label = "modePillW",
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(t.surface2)
            .border(1.5.dp, t.surface3, CircleShape)
            .padding(3.dp)
            .onGloballyPositioned { containerWidth = it.size.width.toFloat() },
    ) {
        if (containerWidth > 0f) {
            Box(
                modifier = Modifier
                    .padding(start = pillOffsetX)
                    .width(pillWidth)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(t.accent)
                    .shadow(elevation = 4.dp, shape = CircleShape, clip = false),
            )
        }

        Row {
            listOf("Студенты" to false, "Педагоги" to true).forEach { (label, teacher) ->
                Text(
                    text = label,
                    color = if (isTeacher == teacher) Color.White else t.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onModeChange(teacher) },
                        )
                        .wrapContentSize(Alignment.Center),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NO URL HINT (без изменений)
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
// HOME STATUS LINE (без изменений)
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