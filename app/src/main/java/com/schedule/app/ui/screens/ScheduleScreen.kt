package com.schedule.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.schedule.app.ui.components.*
import com.schedule.app.ui.theme.LocalTheme

// Модель пары
data class Pair(
    val num: String,          // "I", "II" ...
    val timeStart: String,    // "08:30"
    val timeEnd: String,      // "09:15"
    val breakStart: String? = null,
    val breakEnd: String? = null,
    val subject: String,
    val teacher: String? = null,
    val room: String? = null,
    val cabinet: String? = null,
    val isNow: Boolean = false,
    val isNext: Boolean = false,
    val isWindow: Boolean = false,   // «Окно»
    val remainText: String? = null,  // "осталось 43 мин"
    val progressPct: Float = 0f,
)

data class ScheduleDay(
    val header: String,   // "ПОНЕДЕЛЬНИК 17.03.2025 (2-я неделя)"
    val pairs: List<Pair>,
)

// ─────────────────────────────────────────────────────────────────────────────
// SCHEDULE SCREEN  (#s-schedule)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ScheduleScreen(
    groupName: String,
    dateText: String,
    days: List<ScheduleDay>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
) {
    val t = LocalTheme.current

    // Текущая пара для live-bar
    val currentPair = days.flatMap { it.pairs }.firstOrNull { it.isNow }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        // ── Sched header (.sched-header) ──────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(t.surface),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 0.dp)
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BackButton(onClick = onBack)

                Column(modifier = Modifier.weight(1f).padding(vertical = 16.dp)) {
                    // .sched-group: 24px bold accent
                    Text(
                        text = groupName,
                        color = t.accent,
                        fontSize = 24.sp,
                        fontWeight = FontWeight(800),
                        letterSpacing = (-0.02).em,
                    )
                    // .sched-date: 11px muted
                    Text(
                        text = dateText,
                        color = t.muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }

                // Search icon
                Text(
                    text = "🔍",
                    fontSize = 20.sp,
                    modifier = Modifier
                        .clickable(onClick = onSearch)
                        .padding(8.dp),
                )
                // Share icon
                Text(
                    text = "⬆",
                    fontSize = 20.sp,
                    color = t.muted,
                    modifier = Modifier
                        .clickable(onClick = onShare)
                        .padding(8.dp),
                )
            }

            HorizontalDivider(color = Color(0x0FFFFFFF))

            // ── Live progress bar ──────────────────────────────────────
            if (currentPair != null) {
                LiveProgressBar(pair = currentPair)
                HorizontalDivider(color = Color(0x0FFFFFFF))
            }
        }

        // ── Расписание ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (isLoading) {
                Spacer(Modifier.height(16.dp))
                repeat(5) { i ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 4.dp)
                            .height(80.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(t.surface2),
                    )
                }
            } else if (days.isEmpty()) {
                EmptyState(icon = "📭", title = "Расписание не найдено")
            } else {
                days.forEach { day ->
                    // Day header
                    DayHeaderRow(text = day.header)
                    day.pairs.forEach { pair ->
                        PairCard(pair = pair)
                    }
                }
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LIVE PROGRESS BAR  (#sched-live-bar)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun LiveProgressBar(pair: Pair) {
    val t = LocalTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "▶ ${pair.subject}",
                color = t.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = pair.remainText ?: "",
                color = t.muted,
                fontSize = 11.sp,
            )
        }
        // height:3px bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(t.surface3),
        ) {
            val anim by animateFloatAsState(
                targetValue = pair.progressPct / 100f,
                animationSpec = tween(30_000, easing = LinearEasing),
                label = "liveProgress",
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(anim)
                    .clip(RoundedCornerShape(2.dp))
                    .background(t.accent),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DAY HEADER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DayHeaderRow(text: String) {
    val t = LocalTheme.current
    // Форматируем: "ПОНЕДЕЛЬНИК 17.03.2025"
    val formatted = text
    Text(
        text = formatted,
        color = t.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.04.em,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .padding(top = 6.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// PAIR CARD  (.pair-card)
//
// background:surface2; border-radius:16px; margin-bottom:9px
// border:1.5px solid surface3; border-left:3px solid
//
// .has-subject → border-left: accent2
// .is-now      → border-left: accent; bg: accent 10%; border: accent 30%
// .is-next     → border-left: success
//
// .pair-top: padding 13px 14px 8px; gap 10px
// .pair-num: JetBrains Mono 11px 600 muted uppercase
// .pair-subject: 14px 700 letter-spacing -.01em
// .pair-details: 12.5px muted padding 0 14px 12px 50px
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PairCard(pair: Pair) {
    val t = LocalTheme.current

    val leftBorderColor = when {
        pair.isNow     -> t.accent
        pair.isNext    -> t.success
        pair.isWindow  -> t.surface3
        else           -> t.accent2
    }
    val bgColor = when {
        pair.isNow -> t.accent.copy(alpha = 0.10f)
        else       -> t.surface2
    }
    val cardBorderColor = when {
        pair.isNow -> t.accent.copy(alpha = 0.30f)
        else       -> t.surface3
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 9.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(bgColor)
                .border(1.5.dp, cardBorderColor, RoundedCornerShape(16.dp)),
        ) {
            // Left accent border (border-left: 3px)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(leftBorderColor),
            )

            Column(modifier = Modifier.weight(1f)) {
                // .pair-top
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // .pair-num
                    Text(
                        text = pair.num,
                        color = t.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.06.em,
                        modifier = Modifier
                            .width(26.dp)
                            .padding(top = 2.dp),
                    )

                    // .pair-subject-wrap
                    Column(modifier = Modifier.weight(1f)) {
                        // Бейдж СЕЙЧАС / СЛЕДУЮЩАЯ
                        if (pair.isNow || pair.isNext) {
                            val badgeColor = if (pair.isNow) t.accent else t.success
                            val badgeText = if (pair.isNow) "▶ СЕЙЧАС" else "СЛЕДУЮЩАЯ"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(badgeColor)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = badgeText,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.04.em,
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                        }

                        // .pair-subject
                        Text(
                            text = pair.subject,
                            color = if (pair.isWindow) t.muted else t.text,
                            fontSize = 14.sp,
                            fontWeight = if (pair.isWindow) FontWeight.Normal else FontWeight.Bold,
                            fontStyle = if (pair.isWindow) FontStyle.Italic else FontStyle.Normal,
                            lineHeight = 19.sp,
                            letterSpacing = (-0.01).em,
                        )
                    }

                    // .pair-remain
                    if (pair.remainText != null) {
                        Text(
                            text = pair.remainText,
                            color = t.muted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                }

                // .pair-times — время начала/конца пары
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 50.dp, end = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    // Пара
                    PairTimeRow(
                        time = "${pair.timeStart}–${pair.timeEnd}",
                        tag = "ПАРА",
                    )
                    // Перемена
                    if (pair.breakStart != null && pair.breakEnd != null) {
                        PairTimeRow(
                            time = "${pair.breakStart}–${pair.breakEnd}",
                            tag = "ПЕРЕМ",
                            timeColor = t.muted,
                        )
                    }
                }

                // .pair-details — учитель, кабинет
                if (!pair.teacher.isNullOrEmpty() || !pair.room.isNullOrEmpty()) {
                    Text(
                        text = buildString {
                            if (!pair.teacher.isNullOrEmpty()) append(pair.teacher)
                            if (!pair.room.isNullOrEmpty()) {
                                if (!pair.teacher.isNullOrEmpty()) append(" · ")
                                append(pair.room)
                            }
                            if (!pair.cabinet.isNullOrEmpty()) append(" · ${pair.cabinet}")
                        },
                        color = t.muted,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 50.dp, end = 14.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PairTimeRow(
    time: String,
    tag: String,
    timeColor: Color = LocalTheme.current.text,
) {
    val t = LocalTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = time,
            color = timeColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(t.surface3)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = tag,
                color = t.muted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em,
            )
        }
    }
}
