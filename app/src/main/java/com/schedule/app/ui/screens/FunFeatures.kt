package com.schedule.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.schedule.app.ui.components.*
import com.schedule.app.ui.theme.LocalTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// DATA
// ─────────────────────────────────────────────────────────────────────────────

val RANDOM_FACTS = listOf(
    "Самая длинная лекция в истории длилась 54 часа. Студент не сдался.",
    "Средний студент пьёт 3 чашки кофе в день. В сессию — 7.",
    "Слово «дедлайн» изначально означало черту в тюрьме, за которой стреляли.",
    "Исследования: 80% конспектов не перечитывается никогда.",
    "Мозг запоминает лучше, если учиться перед сном. Не в 3 ночи перед экзаменом.",
    "Первый в мире университет — Болонский, основан в 1088 году.",
    "Средняя скорость записи — 30 слов в минуту. Преподаватели говорят быстрее.",
    "Синдром самозванца испытывают 70% студентов. Ты не один.",
    "Учёные доказали: перерывы каждые 25 минут повышают продуктивность на 40%.",
    "Первая оценка в истории — поставлена в 1792 году в Кембридже.",
    "В среднем студент теряет 3 ручки в семестр. Они попадают в параллельное измерение.",
    "Если учиться 10000 часов — станешь экспертом. Это ~417 дней без сна.",
)

val EXCUSES = listOf(
    "У меня внезапно разрядился телефон, а будильник стоял именно на нём.",
    "Автобус уехал ровно тогда, когда я к нему подбежал.",
    "Я был, но сидел так тихо, что меня не заметили.",
    "Медицинские показания. Врач сказал «отдыхайте». Я послушался.",
    "Готовился к следующей паре — она важнее.",
    "Интернет подтвердил, что пара отменена. Интернет врал.",
    "Застрял в лифте. В здании без лифта.",
    "Бабушка. Всегда бабушка.",
    "Я был там духовно.",
    "Форс-мажор международного масштаба.",
)

val HAIKU_LIST = listOf(
    listOf("Звонок прозвенел", "Тетрадь тихо открыта", "Сон не отпускает"),
    listOf("Домашнее есть", "«Кто выполнил?» — тишина", "Все смотрят в окно"),
    listOf("Урок математик", "x не находит себя", "Я — тоже"),
    listOf("Доска исписана", "Мел крошится и падёт", "Пятница близко"),
    listOf("Звонок. Конец пар", "Рюкзак собран за секунд", "Свобода пришла"),
    listOf("Список предметов", "Такой длинный, как страданье", "Выходной — мечта"),
    listOf("Контрольная — враг", "Случайные варианты", "Я знал эти все"),
)

val TEACHER_TAUNTS = listOf(
    "👩‍🏫 «Ещё раз опоздаешь — в журнал!»",
    "👨‍🏫 «Кто не понял — подойдёт после урока»",
    "👩‍🏫 «Это будет на контрольной. Записывайте.»",
    "👨‍🏫 «Тишина в классе! Я слышу жвачку.»",
    "👩‍🏫 «Убери телефон, иначе до родителей»",
    "👨‍🏫 «Повторите дома. Всё. Прямо всё.»",
    "👩‍🏫 «Это проходили в прошлом году. Как вы не знаете?»",
    "👨‍🏫 «Опять двоечники вышли к доске первыми...»",
    "👩‍🏫 «Лучший ответ — тот, которого не было.»",
    "👨‍🏫 «Мы опаздываем на 20 минут, поэтому задание на дом двойное.»",
)

val MOTIVATIONS = mapOf(
    1 to listOf("Понедельник — день тяжёлый, но ты справишься 💪", "Неделя только началась — всё впереди!", "С понедельника начинается новая версия тебя 🚀"),
    2 to listOf("Вторник — уже не понедельник. Прогресс!", "Ты пережил понедельник. Вторник — пустяки."),
    3 to listOf("Среда — экватор недели! Половина позади 🏆", "В середине пути самое важное — не останавливаться."),
    4 to listOf("Четверг — почти пятница. Почти.", "Один день до финиша. Держись!"),
    5 to listOf("ПЯТНИЦА! 🎉 Ты дожил!", "Последний рывок — и выходные твои!"),
    6 to listOf("Суббота. Даже учёба в выходной — это сила.", "Пока другие отдыхают, ты растёшь 📈"),
    0 to listOf("Воскресенье. Заряжайся перед новой неделей 🔋", "Отдохни сегодня — завтра в бой!"),
)

fun getSpecialDateGreeting(): Triple<String, String, String>? {
    val cal = Calendar.getInstance()
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    return when {
        m == 9 && d == 1                       -> Triple("С 1 сентября!", "🎒", "Новый учебный год начинается!")
        m == 6 && d <= 7                       -> Triple("Скоро каникулы!", "🏖", "Совсем чуть-чуть осталось!")
        (m == 12 && d >= 25) || (m == 1 && d <= 8) -> Triple("С Новым годом!", "🎄", "Зимние каникулы!")
        m == 3 && d == 8                       -> Triple("С 8 марта!", "🌸", "Поздравляем!")
        m == 2 && d == 23                      -> Triple("С 23 февраля!", "🎖", "С праздником!")
        else                                   -> null
    }
}

fun getDayMotivation(): String {
    val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1 // 0=вс
    val arr = MOTIVATIONS[dow] ?: MOTIVATIONS[1]!!
    return arr.random()
}

fun getWeekProgress(): String {
    val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // 1=вс, 2=пн...
    val weekDay = if (dow == 1) 0 else dow - 1 // 0=вс, 1=пн...
    if (weekDay == 0) return "Воскресенье — завтра снова в бой 🔋"
    val done = weekDay - 1
    val total = 6
    val left = total - weekDay + 1
    val pct = (done.toFloat() / total * 100).toInt()
    val filled = pct / 10
    val bar = "▰".repeat(filled) + "▱".repeat(10 - filled)
    return "$bar $pct% недели · осталось $left дн."
}

// ─────────────────────────────────────────────────────────────────────────────
// SPECIAL DATE GREETING OVERLAY  (показывается при запуске)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SpecialDateGreetingOverlay(onDismiss: () -> Unit) {
    val greeting = remember { getSpecialDateGreeting() } ?: return

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(800)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
        exit  = fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val t = LocalTheme.current
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(t.surface2)
                    .border(1.5.dp, t.surface3, RoundedCornerShape(24.dp))
                    .padding(32.dp)
                    .clickable(enabled = false) {},
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(greeting.second, fontSize = 56.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    greeting.first,
                    color = t.accent, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Text(
                    greeting.third,
                    color = t.muted, fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(20.dp))
                AppButton("Отлично! 🎉", onDismiss, BtnVariant.Accent)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOAST SYSTEM
// ─────────────────────────────────────────────────────────────────────────────
data class ToastState(val message: String, val id: Int = 0)

@Composable
fun AppToast(state: ToastState?, modifier: Modifier = Modifier) {
    val t = LocalTheme.current
    AnimatedVisibility(
        visible = state != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit  = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(t.surface2)
                .border(1.dp, t.surface3, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                state?.message ?: "",
                color = t.text, fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EXCUSE CARD  (отмазка для пропуска пары)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ExcuseCard(onDismiss: () -> Unit) {
    val t = LocalTheme.current
    val excuse = remember { EXCUSES.random() }
    val clipboard = LocalClipboardManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(t.surface2)
                .border(1.5.dp, t.accent.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .clickable(enabled = false) {}
                .padding(20.dp),
        ) {
            Text(
                "📝 Генератор отмазки",
                color = t.accent, fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                excuse,
                color = t.text, fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppButton(
                    label = "📋 Скопировать",
                    onClick = {
                        clipboard.setText(AnnotatedString(excuse))
                        onDismiss()
                    },
                    variant = BtnVariant.Accent,
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    label = "Закрыть",
                    onClick = onDismiss,
                    variant = BtnVariant.Surface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RANDOM FACT CARD
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun RandomFactCard(onDismiss: () -> Unit) {
    val t = LocalTheme.current
    val fact = remember { RANDOM_FACTS.random() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(t.surface2)
            .border(1.5.dp, t.surface3, RoundedCornerShape(16.dp))
            .clickable(onClick = onDismiss)
            .padding(16.dp),
    ) {
        Column {
            Text(
                "💡 СЛУЧАЙНЫЙ ФАКТ",
                color = t.accent, fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.em,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            Text(fact, color = t.text, fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SCHEDULE QUIZ  (угадай время пары)
// ─────────────────────────────────────────────────────────────────────────────
data class QuizState(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val answered: Int? = null,
)

fun buildScheduleQuiz(): QuizState {
    val bells = listOf(
        Triple("I",    "08:30", "10:05"),
        Triple("II",   "10:15", "11:50"),
        Triple("III",  "12:20", "13:55"),
        Triple("IV",   "14:05", "15:05"),
        Triple("V",    "15:15", "16:15"),
    )
    val (roman, start, end) = bells.random()
    val correct = start
    val wrongs = listOf("08:00","09:00","10:00","10:30","11:00","11:30","12:00","13:00","14:30","15:00")
        .filter { it != correct }
        .shuffled()
        .take(3)
    val options = (wrongs + correct).shuffled()
    return QuizState(
        question = "Во сколько начинается $roman пара?",
        options  = options,
        correctIndex = options.indexOf(correct),
    )
}

@Composable
fun ScheduleQuizDialog(onDismiss: () -> Unit) {
    val t = LocalTheme.current
    var quiz by remember { mutableStateOf(buildScheduleQuiz()) }
    var answered by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(t.surface)
                .clickable(enabled = false) {}
                .padding(20.dp),
        ) {
            Text(
                "🧠 Тест: знаешь расписание?",
                color = t.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Text(quiz.question, color = t.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            quiz.options.forEachIndexed { idx, opt ->
                val isCorrect = idx == quiz.correctIndex
                val isAnswered = answered != null
                val isSelected = answered == idx
                val bg = when {
                    !isAnswered            -> t.surface2
                    isCorrect              -> Color(0xFF4CAF7D).copy(alpha = 0.2f)
                    isSelected && !isCorrect -> t.danger.copy(alpha = 0.2f)
                    else                   -> t.surface2
                }
                val border = when {
                    !isAnswered  -> t.surface3
                    isCorrect    -> Color(0xFF4CAF7D)
                    isSelected   -> t.danger
                    else         -> t.surface3
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .border(1.5.dp, border, RoundedCornerShape(12.dp))
                        .clickable(enabled = !isAnswered) { answered = idx }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(opt, color = t.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (isAnswered && isCorrect) Text("✓", color = Color(0xFF4CAF7D), fontSize = 16.sp)
                    if (isAnswered && isSelected && !isCorrect) Text("✗", color = t.danger, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (answered != null) {
                Text(
                    if (answered == quiz.correctIndex) "🎉 Правильно!" else "❌ Нет! Правильный ответ: ${quiz.options[quiz.correctIndex]}",
                    color = if (answered == quiz.correctIndex) Color(0xFF4CAF7D) else t.danger,
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppButton(
                        "Ещё раз",
                        onClick = { quiz = buildScheduleQuiz(); answered = null },
                        variant = BtnVariant.Accent,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton("Закрыть", onDismiss, BtnVariant.Surface, modifier = Modifier.weight(1f))
                }
            } else {
                AppButton("Закрыть", onDismiss, BtnVariant.Surface)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECRET DEV CONSOLE  (свайп-активация)
// ─────────────────────────────────────────────────────────────────────────────
data class ConsoleEntry(val type: String, val text: String) // type: info/ok/err/warn/out/muted

@Composable
fun DevConsole(
    entries: List<ConsoleEntry>,
    input: String,
    onInputChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit,
) {
    val t = LocalTheme.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111111))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "⚡ Developer Console",
                color = Color(0xFF00FF41),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "✕",
                color = Color(0xFF666666),
                fontSize = 18.sp,
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(4.dp),
            )
        }

        // Log output
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            androidx.compose.foundation.lazy.items(entries) { entry ->
                Text(
                    text = entry.text,
                    color = when (entry.type) {
                        "ok"    -> Color(0xFF00FF41)
                        "err"   -> Color(0xFFFF4444)
                        "warn"  -> Color(0xFFFFAA00)
                        "muted" -> Color(0xFF555555)
                        "out"   -> Color(0xFFCCCCCC)
                        else    -> Color(0xFF888888)
                    },
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp,
                )
            }
        }

        // Input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111111))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(">", color = Color(0xFF00FF41), fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            Box(modifier = Modifier.weight(1f)) {
                if (input.isEmpty()) {
                    Text("команда или /help...", color = Color(0xFF444444), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color(0xFF00FF41),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "↵",
                color = if (input.trim().isNotEmpty()) Color(0xFF00FF41) else Color(0xFF333333),
                fontSize = 18.sp,
                modifier = Modifier
                    .clickable(enabled = input.trim().isNotEmpty()) {
                        onSubmit(input.trim())
                    }
                    .padding(4.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BPM TAPPER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun BpmTapper(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val t = LocalTheme.current
    var taps by remember { mutableStateOf(listOf<Long>()) }
    var bpmText by remember { mutableStateOf("Тапай!") }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(t.surface)
                .clickable(enabled = false) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🥁 BPM тапалка", color = t.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text(bpmText, color = t.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("${taps.size} тапов", color = t.muted, fontSize = 13.sp)
            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(t.accent)
                    .clickable {
                        val now = System.currentTimeMillis()
                        val newTaps = (taps + now).filter { now - it < 5000 }
                        taps = newTaps
                        if (newTaps.size >= 4) {
                            val diffs = newTaps.zipWithNext().map { (a, b) -> b - a }
                            val avg = diffs.average()
                            val bpm = (60000 / avg).toInt()
                            bpmText = "$bpm BPM"
                        } else {
                            bpmText = "•".repeat(newTaps.size) + if (newTaps.size < 4) " — ещё" else ""
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("🥁", fontSize = 40.sp)
            }
            Spacer(Modifier.height(16.dp))
            AppButton("Закрыть", onDismiss, BtnVariant.Surface)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WEEK PROGRESS WIDGET
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun WeekProgressWidget(modifier: Modifier = Modifier) {
    val t = LocalTheme.current
    val text = remember { getWeekProgress() }
    val motivation = remember { getDayMotivation() }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
        Text(
            text = text,
            color = t.muted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = motivation,
            color = t.muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FOCUS MODE — dims non-current pairs
// ─────────────────────────────────────────────────────────────────────────────
// Used in ScheduleScreen - pairs get alpha 0.25 when not current/next in focus mode
// Exposed via focusMode: Boolean param in ScheduleScreen

// ─────────────────────────────────────────────────────────────────────────────
// APP STATS SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppStatsDialog(
    totalOpens: Int,
    streak: Int,
    remainingPairsToday: Int,
    notesCount: Int,
    onDismiss: () -> Unit,
) {
    val t = LocalTheme.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(t.surface2)
                .clickable(enabled = false) {}
                .padding(20.dp),
        ) {
            Text("📊 Статистика", color = t.accent, fontSize = 16.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            StatRow("📱 Открыто всего", "$totalOpens раз")
            StatRow("🔥 Стрик", "$streak дней подряд")
            StatRow("📚 Пар осталось сегодня", "$remainingPairsToday")
            StatRow("📝 Заметок к парам", "$notesCount")
            Spacer(Modifier.height(16.dp))
            AppButton("Закрыть", onDismiss, BtnVariant.Accent)
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val t = LocalTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = t.text, fontSize = 14.sp)
        Text(value, color = t.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HAIKU DIALOG
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HaikuDialog(onDismiss: () -> Unit) {
    val t = LocalTheme.current
    val haiku = remember { HAIKU_LIST.random() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(t.surface2)
                .border(1.5.dp, t.accent.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .clickable(enabled = false) {}
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("🌸", fontSize = 36.sp)
            Spacer(Modifier.height(12.dp))
            haiku.forEach { line ->
                Text(
                    text = line,
                    color = t.text, fontSize = 16.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
            AppButton("🙏 Спасибо", onDismiss, BtnVariant.Accent)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PAIR NOTE DIALOG  — заметка к паре
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PairNoteDialog(
    pairKey: String,
    existingNote: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalTheme.current
    var text by remember { mutableStateOf(existingNote) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(t.surface)
                .clickable(enabled = false) {}
                .padding(20.dp),
        ) {
            Text(
                "📝 Заметка к паре",
                color = t.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            AppInput(
                value = text,
                onValueChange = { text = it },
                placeholder = "Домашнее задание, важная инфо...",
                singleLine = false,
                maxLines = 5,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton("Отмена", onDismiss, BtnVariant.Surface, modifier = Modifier.weight(1f))
                AppButton("Сохранить", { onSave(text) }, BtnVariant.Accent, modifier = Modifier.weight(1f))
            }
        }
    }
}
