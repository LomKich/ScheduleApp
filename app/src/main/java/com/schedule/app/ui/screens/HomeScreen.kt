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
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.schedule.app.ui.components.*
import com.schedule.app.ui.theme.LocalTheme
import java.util.Calendar
import kotlin.math.abs

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
    onOpenConsole: () -> Unit = {},
) {
    val t = LocalTheme.current

    // Свайп вверх → консоль
    // Отслеживаем жест на всём экране
    var swipeStartY by remember { mutableStateOf(0f) }
    var swipeStartX by remember { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        swipeStartY = down.position.y
                        swipeStartX = down.position.x

                        var lastY = swipeStartY
                        var consumed = false

                        do {
                            val event = awaitPointerEvent()
                            val pos = event.changes.firstOrNull()?.position ?: break
                            val dy = swipeStartY - pos.y   // положительное = вверх
                            val dx = abs(pos.x - swipeStartX)

                            // Свайп вверх: вертикаль доминирует, прошли > 80dp
                            if (!consumed && dy > 80.dp.toPx() && dy > dx * 2f) {
                                onOpenConsole()
                                consumed = true
                                event.changes.forEach { it.consume() }
                            }
                            lastY = pos.y
                        } while (event.changes.any { it.pressed })
                    }
                }
            },
    ) {
        // Контент (убрана кнопка ⚙️ — она была только для дебага)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 0.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Hero section (.home-hero) ──
            HomeHero(onSecretTap = {})

            // ── Статус-строка (урок / ДЗ) — обновляется в реальном времени ──
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
// MODE TOGGLE PILL  (.mode-toggle-pill)
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
// HERO CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HomeHero(onSecretTap: () -> Unit = {}) {
    val t = LocalTheme.current
    var tapCount by remember { mutableStateOf(0) }
    val glowColor = t.accent.copy(alpha = 0.55f)

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
                        if (tapCount >= 7) { tapCount = 0; onSecretTap() }
                    },
                )
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
                style = TextStyle(
                    shadow = Shadow(
                        color = glowColor,
                        offset = Offset.Zero,
                        blurRadius = 20f,
                    ),
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NO URL HINT
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
            text = "Укажи ссылку на Яндекс Диск\nв Настройках",
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
// HOME STATUS LINE  (урок · ДЗ)  — обновляется каждую минуту в реальном времени
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HomeStatusLine(
    hwActiveCount: Int,
    onOpenHomework: () -> Unit,
) {
    val t = LocalTheme.current

    // Обновляем каждые 30 секунд чтобы статус был актуальным
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            tick++
        }
    }

    val statusText by remember(tick) {
        derivedStateOf {
            val now = Calendar.getInstance()
            val h = now.get(Calendar.HOUR_OF_DAY)
            val m = now.get(Calendar.MINUTE)
            val nowMin = h * 60 + m
            val dow = now.get(Calendar.DAY_OF_WEEK)

            val schedule = when {
                dow == 2 -> listOf(
                    Triple("I",   9*60,     9*60+45),
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

            val current = schedule.firstOrNull { nowMin in it.second..it.third }
            if (current != null) {
                val left = current.third - nowMin
                parts.add("урок ${current.first} · $left мин")
            } else {
                val next = schedule.firstOrNull { it.second > nowMin }
                if (next != null) {
                    val toStart = next.second - nowMin
                    parts.add("до пары ${next.first} · $toStart мин")
                }
            }

            parts.joinToString("  ·  ")
        }
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
