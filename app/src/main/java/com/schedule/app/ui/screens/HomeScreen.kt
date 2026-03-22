package com.schedule.app.ui.screens

import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.schedule.app.ui.components.*
import com.schedule.app.ui.theme.LocalTheme
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.*

data class ScheduleFile(
    val name: String,
    val path: String,
    val size: Long = 0L,
)

// Расписание звонков (точное соответствие core.js)
object BellSchedule {
    // Понедельник
    val MONDAY = listOf(
        Triple("I",    9*60,     9*60+45),   // 09:00 – 09:45
        Triple("II",   10*60+45, 11*60+30),  // 10:45 – 11:30
        Triple("III",  12*60+50, 13*60+35),  // 12:50 – 13:35
        Triple("IV",   14*60+35, 15*60+35),  // 14:35 – 15:35
        Triple("V",    15*60+45, 16*60+45),  // 15:45 – 16:45
        Triple("VI",   16*60+55, 17*60+55),  // 16:55 – 17:55
    )
    // Вторник – пятница
    val TUESDAY_FRIDAY = listOf(
        Triple("I",    8*60+30,  9*60+15),   // 08:30 – 09:15
        Triple("II",   10*60+15, 11*60),     // 10:15 – 11:00
        Triple("III",  12*60+20, 13*60+5),   // 12:20 – 13:05
        Triple("IV",   14*60+5,  15*60+5),   // 14:05 – 15:05
        Triple("V",    15*60+15, 16*60+15),  // 15:15 – 16:15
        Triple("VI",   16*60+25, 17*60+25),  // 16:25 – 17:25
    )
    // Суббота
    val SATURDAY = listOf(
        Triple("I",    8*60+30,  9*60+30),   // 08:30 – 09:30
        Triple("II",   9*60+40,  10*60+40),  // 09:40 – 10:40
        Triple("III",  10*60+50, 11*60+50),  // 10:50 – 11:50
        Triple("IV",   12*60,    13*60),     // 12:00 – 13:00
        Triple("V",    13*60+10, 14*60+10),  // 13:10 – 14:10
        Triple("VI",   14*60+20, 15*60+20),  // 14:20 – 15:20
    )

    fun forDay(dayOfWeek: Int): List<Triple<String,Int,Int>>? {
        return when (dayOfWeek) {
            Calendar.MONDAY    -> MONDAY
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY   -> TUESDAY_FRIDAY
            Calendar.SATURDAY -> SATURDAY
            else -> null
        }
    }
}

/**
 * Информация о паре для отображения в статусной строке
 */
data class PairInfo(
    val roman: String,
    val startMin: Int,
    val endMin: Int,
    val isCurrent: Boolean,
    val leftMinutes: Int? = null,      // сколько осталось (только для текущей)
    val toStartMinutes: Int? = null,   // сколько до начала (только для будущей)
)

/**
 * Получить информацию о текущей или ближайшей паре
 */
fun getCurrentOrNextPair(): PairInfo? {
    val now = Calendar.getInstance()
    val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val dayOfWeek = now.get(Calendar.DAY_OF_WEEK)
    val schedule = BellSchedule.forDay(dayOfWeek) ?: return null

    // Ищем текущую пару
    for ((roman, start, end) in schedule) {
        if (nowMin in start..end) {
            val left = end - nowMin
            return PairInfo(roman, start, end, true, left, null)
        }
    }
    // Ищем ближайшую будущую
    for ((roman, start, _) in schedule) {
        if (start > nowMin) {
            val toStart = start - nowMin
            return PairInfo(roman, start, start, false, null, toStart)
        }
    }
    return null
}

// ==========================================================================================
// HOME SCREEN
// ==========================================================================================
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
    lastGroup: String? = null,
    onModeChange: (Boolean) -> Unit,
    onFileClick: (ScheduleFile) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHomework: () -> Unit = {},
    onContinue: () -> Unit = {},
    onSecretTap: () -> Unit = {},
) {
    val t = LocalTheme.current
    var pairInfo by remember { mutableStateOf(getCurrentOrNextPair()) }

    // Обновляем каждую минуту
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            pairInfo = getCurrentOrNextPair()
        }
    }

    // Текст статусной строки (пара + ДЗ)
    val pairStatusText = when {
        pairInfo != null && pairInfo!!.isCurrent -> "урок ${pairInfo!!.roman} · ${pairInfo!!.leftMinutes} мин"
        pairInfo != null -> "до пары ${pairInfo!!.roman} · ${pairInfo!!.toStartMinutes} мин"
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg)
    ) {
        // Топ‑бар с кнопкой настроек
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(t.bg)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(t.surface2)
                    .border(1.dp, t.surface3, RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 20.dp),
                        onClick = onOpenSettings
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("⚙️", fontSize = 20.sp)
            }
        }

        // Основной контент
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Hero‑карточка с секретным тапом
            HomeHero(
                isTeacher = isTeacher,
                onSecretTap = onSecretTap
            )

            // Статусная строка (пара + ДЗ)
            HomeStatusLine(
                pairText = pairStatusText,
                hwActiveCount = hwActiveCount,
                onOpenHomework = onOpenHomework
            )

            Spacer(Modifier.height(8.dp))

            // Переключатель «Студенты / Педагоги»
            ModeTogglePill(
                isTeacher = isTeacher,
                onModeChange = onModeChange,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )

            // Статус и прогресс‑бар
            if (statusText.isNotEmpty()) {
                StatusText(text = statusText)
            } else {
                Spacer(Modifier.height(18.dp))
            }
            AppProgressBar(
                progress = loadProgress,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            // Секция файлов
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
                        subtitle = "Проверь подключение к интернету"
                    )
                    AppButton(
                        label = "🔄 Повторить",
                        onClick = onRetry,
                        variant = BtnVariant.Surface
                    )
                }
                else -> {
                    SectionLabel("Файлы")
                    files.forEach { file ->
                        PressableListItemRow(
                            name = file.name,
                            sub = if (file.size > 0) "${file.size / 1024} КБ" else null,
                            selected = file.name == selectedFile?.name,
                            onClick = { onFileClick(file) }
                        )
                    }
                }
            }

            // Кнопка «Продолжить» (только студент, есть группа)
            if (!isTeacher && lastGroup != null && lastGroup.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                ContinueButton(
                    groupName = lastGroup,
                    onClick = onContinue
                )
            }

            Spacer(Modifier.height(100.dp)) // отступ под навигационную панель
        }
    }
}

// ==========================================================================================
// Hero‑карточка (точное соответствие веб‑стилю)
// ==========================================================================================
@Composable
private fun HomeHero(
    isTeacher: Boolean,
    onSecretTap: () -> Unit
) {
    val t = LocalTheme.current
    var tapCount by remember { mutableStateOf(0) }
    var lastTapTime by remember { mutableStateOf(0L) }
    val title = if (isTeacher) "Расписание\nПедагогам" else "Расписание\nСтудентам"

    // Радиальный градиент за карточкой (как в вебе)
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(240.dp)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(t.accent.copy(alpha = 0.12f), Color.Transparent)
                )
            )
        }

        // Сама карточка
        Box(
            modifier = Modifier
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp), clip = false)
                .clip(RoundedCornerShape(24.dp))
                .background(t.surface2)
                .border(1.5.dp, t.surface3, RoundedCornerShape(24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime > 500) {
                            tapCount = 1
                        } else {
                            tapCount++
                        }
                        lastTapTime = now
                        if (tapCount >= 7) {
                            tapCount = 0
                            onSecretTap()
                        }
                    }
                )
                .padding(horizontal = 32.dp, vertical = 18.dp)
        ) {
            // Текст с многослойной тенью (как в вебе)
            DrawTextWithShadows(
                text = title,
                color = t.accent,
                fontSize = 38.sp,
                fontWeight = FontWeight(800),
                lineHeight = 42.sp,
                letterSpacing = (-0.03).em,
                textAlign = TextAlign.Center,
                shadows = listOf(
                    Shadow(color = t.accent.copy(alpha = 0.55f), blurRadius = 20f, offset = Offset.Zero),
                    Shadow(color = t.accent.copy(alpha = 0.22f), blurRadius = 60f, offset = Offset.Zero),
                    Shadow(color = t.accent.copy(alpha = 0.10f), blurRadius = 120f, offset = Offset.Zero)
                )
            )
        }
    }
}

/**
 * Рисует текст с несколькими тенями (как text‑shadow в CSS).
 */
@Composable
private fun DrawTextWithShadows(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    lineHeight: TextUnit,
    letterSpacing: TextUnit,
    textAlign: TextAlign,
    shadows: List<Shadow>
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        textAlign = textAlign
    )
    val layoutResult = remember(text, textStyle) {
        textMeasurer.measure(text, textStyle)
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(with(density) { layoutResult.size.height.toDp() })) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val layoutWidth = layoutResult.size.width
        val layoutHeight = layoutResult.size.height

        // Вычисляем позицию (центрируем)
        val x = (canvasWidth - layoutWidth) / 2
        val y = (canvasHeight - layoutHeight) / 2

        // Рисуем тени в порядке от дальней к ближней
        shadows.reversed().forEach { shadow ->
            drawIntoCanvas { canvas ->
                canvas.save()
                canvas.translate(shadow.offset.x, shadow.offset.y)
                canvas.nativeCanvas.drawTextLayout(
                    layoutResult,
                    x + shadow.offset.x,
                    y + shadow.offset.y,
                    android.graphics.Paint().apply {
                        color = shadow.color.toArgb()
                        setShadowLayer(shadow.blurRadius, 0f, 0f, shadow.color.toArgb())
                    }
                )
                canvas.restore()
            }
        }
        // Рисуем основной текст
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawTextLayout(layoutResult, x, y, android.graphics.Paint())
        }
    }
}

// ==========================================================================================
// Статусная строка (пара + домашнее задание)
// ==========================================================================================
@Composable
private fun HomeStatusLine(
    pairText: String,
    hwActiveCount: Int,
    onOpenHomework: () -> Unit
) {
    val t = LocalTheme.current
    if (pairText.isEmpty() && hwActiveCount == 0) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (pairText.isNotEmpty()) {
            Text(
                text = pairText,
                color = t.muted,
                fontSize = 12.sp
            )
        }
        if (pairText.isNotEmpty() && hwActiveCount > 0) {
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
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenHomework
                )
            )
        }
    }
}

// ==========================================================================================
// Переключатель режима (Студенты / Педагоги)
// ==========================================================================================
@Composable
fun ModeTogglePill(
    isTeacher: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
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
        label = "modePill"
    )
    val pillWidth by animateDpAsState(
        targetValue = with(density) { (containerWidth / 2f - 6f).toDp() },
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
        label = "modePillW"
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(t.surface2)
            .border(1.5.dp, t.surface3, CircleShape)
            .padding(3.dp)
            .onGloballyPositioned { containerWidth = it.size.width.toFloat() }
    ) {
        if (containerWidth > 0f) {
            Box(
                modifier = Modifier
                    .padding(start = pillOffsetX)
                    .width(pillWidth)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(t.accent)
                    .shadow(elevation = 4.dp, shape = CircleShape, clip = false)
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
                            onClick = { onModeChange(teacher) }
                        )
                        .wrapContentSize(Alignment.Center)
                )
            }
        }
    }
}

// ==========================================================================================
// Элемент списка с анимацией масштаба (точное соответствие .list-item)
// ==========================================================================================
@Composable
private fun PressableListItemRow(
    name: String,
    sub: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val t = LocalTheme.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "listItemScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 7.dp)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) t.accent.copy(alpha = 0.15f) else t.surface2
            )
            .border(
                1.5.dp,
                if (selected) t.accent.copy(alpha = 0.70f) else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
                onPress = { pressed = true },
                onRelease = { pressed = false }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = if (selected) t.accent else t.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        color = t.muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Text(
                text = "›",
                color = t.muted,
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

// ==========================================================================================
// Кнопка «Продолжить» (аналог #last-group-btn)
// ==========================================================================================
@Composable
private fun ContinueButton(
    groupName: String,
    onClick: () -> Unit
) {
    val t = LocalTheme.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "continueBtnScale"
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
                indication = ripple(bounded = true),
                onClick = onClick,
                onPress = { pressed = true },
                onRelease = { pressed = false }
            )
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Продолжить (${groupName.take(20)})",
            color = t.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

// ==========================================================================================
// Вспомогательные компоненты (уже существующие в проекте, оставляем как есть)
// ==========================================================================================
@Composable
private fun NoUrlHint(onOpenSettings: () -> Unit) {
    val t = LocalTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("⚙️", fontSize = 38.sp)
        Text(
            text = "Укажи ссылку на Яндекс Диск\nв ",
            color = t.muted,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )
        AppButton(
            label = "Открыть настройки →",
            onClick = onOpenSettings,
            variant = BtnVariant.Accent
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    val t = LocalTheme.current
    Text(
        text = text.uppercase(),
        color = t.muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.15.em,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun StatusText(text: String) {
    val t = LocalTheme.current
    Text(
        text = text,
        color = t.muted,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun AppProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val t = LocalTheme.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(t.surface3)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(t.accent)
        )
    }
}

@Composable
private fun SkeletonItem() {
    val t = LocalTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(bottom = 7.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(t.surface2)
    )
}

@Composable
private fun EmptyState(
    icon: String,
    title: String,
    subtitle: String
) {
    val t = LocalTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(icon, fontSize = 36.sp)
        Text(
            text = title,
            color = t.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            color = t.muted,
            fontSize = 12.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AppButton(
    label: String,
    onClick: () -> Unit,
    variant: BtnVariant = BtnVariant.Accent
) {
    val t = LocalTheme.current
    val (bg, textColor) = when (variant) {
        BtnVariant.Accent -> t.accent to Color.White
        BtnVariant.Surface -> t.surface2 to t.text
        BtnVariant.Surface3 -> t.surface3 to t.accent
    }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "btnScale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
                onPress = { pressed = true },
                onRelease = { pressed = false }
            )
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

enum class BtnVariant { Accent, Surface, Surface3 }