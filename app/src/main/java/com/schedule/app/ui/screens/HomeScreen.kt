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
// Pixel-perfect порт WebView: нет кнопки ⚙️ (debug), нет WeekProgress,
// метка секции = "СТУДЕНТАМ"/"ПЕДАГОГАМ" как в setMode()
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
    onRefresh: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenHomework: () -> Unit = {},
    onOpenConsole: () -> Unit = {},
) {
    val t = LocalTheme.current
    val density = LocalDensity.current

    // ── Pull-to-refresh состояние ─────────────────────────────────────────
    var pullOffsetPx by remember { mutableStateOf(0f) }
    var pullFired    by remember { mutableStateOf(false) }
    val pullThreshPx = with(density) { 72.dp.toPx() }
    // Анимация: плавно возвращаем индикатор на 0 после срабатывания
    val pullAnim by animateFloatAsState(
        targetValue = if (isLoading) pullThreshPx * 0.6f else 0f,
        animationSpec = spring(stiffness = 260f),
        label = "pullAnim",
    )
    val indicatorOffset = if (pullOffsetPx > 0f) pullOffsetPx.coerceAtMost(pullThreshPx * 1.2f)
                          else pullAnim
    val indicatorAlpha  = (indicatorOffset / pullThreshPx).coerceIn(0f, 1f)
    val indicatorRotate by animateFloatAsState(
        targetValue = if (isLoading) 360f else indicatorOffset / pullThreshPx * 180f,
        animationSpec = if (isLoading) infiniteRepeatable(tween(700), RepeatMode.Restart)
                        else spring(),
        label = "pullRotate",
    )

    // ── Свайп вверх → консоль / свайп вниз → обновление ─────────────────
    val swipeModifier = Modifier.pointerInput(onOpenConsole, onRefresh) {
        var startY    = Float.NaN
        var startX    = Float.NaN
        var touchId   = PointerId(0L)
        var fired     = false
        awaitPointerEventScope {
            while (true) {
                val ev = awaitPointerEvent(pass = PointerEventPass.Initial)
                for (ch in ev.changes) {
                    when {
                        !ch.previousPressed && ch.pressed -> {
                            startY = ch.position.y; startX = ch.position.x
                            touchId = ch.id; fired = false; pullFired = false
                        }
                        ch.pressed && ch.id == touchId && !startY.isNaN() -> {
                            val dy = startY - ch.position.y   // > 0 = вверх, < 0 = вниз
                            val dx = abs(ch.position.x - startX)
                            if (!fired) {
                                // Свайп ВВЕРХ → консоль
                                if (dy > with(density) { 80.dp.toPx() } && dy > dx * 2f) {
                                    fired = true; pullOffsetPx = 0f; onOpenConsole()
                                }
                                // Свайп ВНИЗ → pull-to-refresh
                                if (dy < 0f && abs(dy) > dx * 1.5f) {
                                    pullOffsetPx = (-dy).coerceAtLeast(0f)
                                    if (!pullFired && pullOffsetPx >= pullThreshPx) {
                                        pullFired = true
                                    }
                                }
                            }
                        }
                        !ch.pressed && ch.id == touchId -> {
                            if (pullFired && !isLoading) {
                                onRefresh()
                            }
                            startY = Float.NaN; fired = false
                            pullOffsetPx = 0f; pullFired = false
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg)
            .then(swipeModifier),
    ) {
        // ── Pull-to-refresh индикатор ─────────────────────────────────────
        if (indicatorAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = with(density) { (indicatorOffset * 0.5f).toDp() } + 4.dp)
                    .size(36.dp)
                    .graphicsLayer { alpha = indicatorAlpha }
                    .clip(CircleShape)
                    .background(t.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "↻",
                    color = if (pullFired || isLoading) t.accent else t.muted,
                    fontSize = 18.sp,
                    modifier = Modifier.graphicsLayer { rotationZ = indicatorRotate },
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
        // .body — единственный scrollable контейнер, padding 16px 18px как в CSS
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            // ── .home-hero ────────────────────────────────────────────────
            HomeHero(isTeacher = isTeacher, onSecretTap = {})

            // ── #home-status-line (урок / ДЗ, обновляется каждые 30 сек) ─
            HomeStatusLine(hwActiveCount = hwActiveCount, onOpenHomework = onOpenHomework)

            // ── .mode-switch-wrap / .mode-toggle-pill ─────────────────────
            ModeTogglePill(
                isTeacher = isTeacher,
                onModeChange = onModeChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )

            // ── .status / .progress ───────────────────────────────────────
            if (statusText.isNotEmpty()) {
                StatusText(text = statusText)
            }
            AppProgressBar(
                progress = loadProgress,
                modifier = Modifier.padding(vertical = 6.dp),
            )

            // ── #file-section ─────────────────────────────────────────────
            when {
                yandexUrl.isEmpty() -> {
                    NoUrlHint(onOpenSettings = onOpenSettings)
                }
                isLoading && files.isEmpty() -> {
                    // .section-label — id="file-section-label" меняется setMode()
                    SectionLabel(if (isTeacher) "Педагогам" else "Студентам")
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
                    SectionLabel(if (isTeacher) "Педагогам" else "Студентам")
                    files.forEach { file ->
                        ListItemRow(
                            name = file.name,
                            sub  = if (file.size > 0) "${file.size / 1024} КБ" else null,
                            selected = file.name == selectedFile?.name,
                            onClick  = { onFileClick(file) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(100.dp)) // под nav bar
        }
        } // Column (outer)
    } // Box
}

// ─────────────────────────────────────────────────────────────────────────────
// MODE TOGGLE PILL  (.mode-toggle-pill)
// CSS: border-radius: 9999px; background: surface2; border: 1.5px surface3;
//      ::before pill = accent; transition spring
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ModeTogglePill(
    isTeacher: Boolean,
    onModeChange: (isTeacher: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t       = LocalTheme.current
    val density = LocalDensity.current
    var containerWidth by remember { mutableStateOf(0f) }

    val pillOffsetX by animateDpAsState(
        targetValue = with(density) {
            if (!isTeacher) 3.dp
            else (containerWidth / 2f).toDp() + 3.dp - 3.dp
        },
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
        label = "modePillX",
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
        // Анимированный пилюля-индикатор (::before в CSS)
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
                    text     = label,
                    color    = if (isTeacher == teacher) t.btnText else t.muted,
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
// HERO CARD  (.home-hero → .hero-card → .hero-title)
// CSS: font-size:38px; font-weight:800; color:accent; letter-spacing:-.03em
//      text-shadow glow; card: surface2, border surface3, shadow 12dp, r 24dp
// В WebView setMode() меняет hero-title: "Студентам"/"Педагогам"
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HomeHero(
    isTeacher: Boolean,
    onSecretTap: () -> Unit = {},
) {
    val t         = LocalTheme.current
    val glowColor = t.accent.copy(alpha = 0.55f)
    var tapCount  by remember { mutableStateOf(0) }

    Box(
        modifier        = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Radial glow за карточкой (::before псевдо-элемент)
        Canvas(modifier = Modifier.size(240.dp).align(Alignment.TopCenter)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(t.accent.copy(alpha = 0.12f), Color.Transparent),
                ),
            )
        }

        Box(
            modifier = Modifier
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(24.dp), clip = false)
                .clip(RoundedCornerShape(24.dp))
                .background(t.surface2)
                .border(1.5.dp, t.surface3, RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = {
                        tapCount++
                        if (tapCount >= 7) { tapCount = 0; onSecretTap() }
                    },
                )
                .padding(horizontal = 32.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = if (isTeacher) "Расписание\nПедагогам" else "Расписание\nСтудентам",
                color      = t.accent,
                fontSize   = 38.sp,
                fontWeight = FontWeight(800),
                lineHeight = 42.sp,
                letterSpacing = (-0.03).em,
                textAlign  = TextAlign.Center,
                style = TextStyle(
                    shadow = Shadow(
                        color      = glowColor,
                        offset     = Offset.Zero,
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
            text      = "Укажи ссылку на Яндекс Диск\nв Настройках",
            color     = t.muted,
            fontSize  = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
        )
        AppButton(
            label   = "Открыть настройки →",
            onClick = onOpenSettings,
            variant = BtnVariant.Accent,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HOME STATUS LINE  (#home-status-line)
// Обновляется каждые 30 сек через LaunchedEffect + derivedStateOf
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun HomeStatusLine(
    hwActiveCount: Int,
    onOpenHomework: () -> Unit,
) {
    val t = LocalTheme.current

    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            tick++
        }
    }

    val statusText by remember(tick) {
        derivedStateOf {
            val now    = Calendar.getInstance()
            val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val dow    = now.get(Calendar.DAY_OF_WEEK)

            val schedule = when {
                dow == 2 -> listOf(
                    Triple("I",   9*60,      9*60+45),
                    Triple("II",  10*60+45, 11*60+30),
                    Triple("III", 12*60+50, 13*60+35),
                    Triple("IV",  14*60+35, 15*60+35),
                )
                dow in 3..6 -> listOf(
                    Triple("I",   8*60+30,  9*60+15),
                    Triple("II",  10*60+15, 11*60),
                    Triple("III", 12*60+20, 13*60+5),
                    Triple("IV",  14*60+5,  15*60+5),
                )
                else -> emptyList()
            }

            val current = schedule.firstOrNull { nowMin in it.second..it.third }
            if (current != null) {
                "урок ${current.first} · ${current.third - nowMin} мин"
            } else {
                val next = schedule.firstOrNull { it.second > nowMin }
                if (next != null) "до пары ${next.first} · ${next.second - nowMin} мин" else ""
            }
        }
    }

    if (statusText.isEmpty() && hwActiveCount == 0) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        if (statusText.isNotEmpty()) {
            Text(text = statusText, color = t.muted, fontSize = 12.sp)
        }
        if (statusText.isNotEmpty() && hwActiveCount > 0) {
            Text("  ·  ", color = t.muted, fontSize = 12.sp)
        }
        if (hwActiveCount > 0) {
            Text(
                text = "$hwActiveCount задан${
                    when {
                        hwActiveCount == 1    -> "ие"
                        hwActiveCount in 2..4 -> "ия"
                        else                  -> "ий"
                    }
                } ДЗ",
                color      = t.accent,
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.clickable(onClick = onOpenHomework),
            )
        }
    }
}
