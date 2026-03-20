package com.schedule.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.schedule.app.ui.components.*
import com.schedule.app.ui.theme.AppColors
import com.schedule.app.ui.theme.LocalTheme

// ═════════════════════════════════════════════════════════════════════════════
// GROUPS SCREEN  (#s-groups)
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun GroupsScreen(
    title: String,
    subtitle: String,
    groups: List<String>,
    selectedGroup: String?,
    isLoading: Boolean,
    loadProgress: Float,
    statusText: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onGroupClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        AppHeader(
            title = title,
            subtitle = subtitle.ifEmpty { null },
            onBack = onBack,
        )

        // Поиск + статус
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(t.surface)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            SearchInput(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = "Найти группу...",
            )
            Spacer(Modifier.height(4.dp))
            StatusText(text = statusText)
            AppProgressBar(
                progress = loadProgress,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        // Список групп
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            if (isLoading && groups.isEmpty()) {
                repeat(8) { SkeletonItem() }
            } else {
                val filtered = if (searchQuery.isEmpty()) groups
                else groups.filter { it.contains(searchQuery, ignoreCase = true) }

                // Последняя выбранная группа — вверх
                val sorted = buildList {
                    filtered.firstOrNull { it == selectedGroup }?.let { add(it) }
                    addAll(filtered.filter { it != selectedGroup })
                }

                if (sorted.isEmpty()) {
                    EmptyState(icon = "🔍", title = "Ничего не найдено")
                } else {
                    sorted.forEach { group ->
                        ListItemRow(
                            name = group,
                            selected = group == selectedGroup,
                            onClick = { onGroupClick(group) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// BELLS SCREEN  (#s-bells)
// ═════════════════════════════════════════════════════════════════════════════

data class BellPeriod(
    val num: String,          // "I"
    val pairStart: String,    // "08:30"
    val pairEnd: String,      // "09:15"
    val breakStart: String?,  // "09:20" or null
    val breakEnd: String?,    // "10:05" or null
    val isNow: Boolean = false,
)

data class BellSchedule(
    val dayLabel: String,
    val isToday: Boolean,
    val periods: List<BellPeriod>,
)

@Composable
fun BellsScreen(
    schedules: List<BellSchedule>,
    onBack: () -> Unit,
) {
    val t = LocalTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        AppHeader(title = "Расписание звонков", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            schedules.forEach { sched ->
                // Section label
                SectionLabel(
                    text = sched.dayLabel + if (sched.isToday) " · СЕГОДНЯ" else "",
                    modifier = Modifier.padding(top = 4.dp),
                )

                // Bell card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(t.surface)
                        .border(1.5.dp, t.surface3, RoundedCornerShape(16.dp))
                        .padding(bottom = 1.dp),
                ) {
                    sched.periods.forEach { period ->
                        BellRow(period = period, isLastInGroup = period == sched.periods.last())
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
private fun BellRow(period: BellPeriod, isLastInGroup: Boolean) {
    val t = LocalTheme.current
    val bg = if (period.isNow)
        t.accent.copy(alpha = 0.10f)
    else
        Color.Transparent

    // Длительность пары
    fun toMin(s: String): Int {
        val parts = s.split(":").map { it.toIntOrNull() ?: 0 }
        return parts[0] * 60 + (parts.getOrNull(1) ?: 0)
    }
    val pairDur = toMin(period.pairEnd) - toMin(period.pairStart)
    val breakDur = if (period.breakStart != null && period.breakEnd != null)
        toMin(period.breakEnd) - toMin(period.breakStart) else null

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bg)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Номер пары — large bold mono
            Text(
                text = period.num,
                color = if (period.isNow) t.accent else t.muted,
                fontSize = 22.sp,
                fontWeight = FontWeight(800),
                letterSpacing = (-0.04).em,
                modifier = Modifier.width(28.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                // Время пары
                Text(
                    text = "${period.pairStart} – ${period.pairEnd}${if (period.isNow) "  ▶" else ""}",
                    color = if (period.isNow) t.accent else t.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.01).em,
                )
                // Перемена
                if (period.breakStart != null) {
                    Text(
                        text = "Перемена: ${period.breakStart} – ${period.breakEnd}",
                        color = t.muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            // Длительность
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${pairDur} мин",
                    color = t.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (breakDur != null) {
                    Text(
                        text = "/ ${breakDur} мин",
                        color = t.muted,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        // Divider между рядами (кроме последнего)
        if (!isLastInGroup) {
            androidx.compose.material3.Divider(
                color = Color(0x0FFFFFFF),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 0.dp),
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// SETTINGS SCREEN  (#s-settings)
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun SettingsScreen(
    yandexUrl: String,
    onUrlChange: (String) -> Unit,
    onUpdateFiles: () -> Unit,
    proxyStatus: String,
    currentThemeName: String,
    onOpenThemes: () -> Unit,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    isGlassMode: Boolean,
    onToggleGlass: () -> Unit,
    appVersion: String,
    onBack: () -> Unit,
    onSwitchToWebView: () -> Unit = {},
) {
    val t = LocalTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        AppHeader(title = "Настройки", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Интерфейс ─────────────────────────────────────────────
            SectionLabel("Интерфейс")
            // Карточка переключения — акцентная рамка, чтобы выделялась
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(t.surface2)
                    .border(1.5.dp, t.surface3, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "🚀 Сейчас: Нативный",
                            color = t.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(t.accent.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                "Kotlin",
                                color = t.accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(
                        text = "Переключиться на HTML / WebView версию",
                        color = t.muted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(t.surface3)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = androidx.compose.material.ripple.rememberRipple(),
                            onClick = onSwitchToWebView,
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(
                        "← WebView",
                        color = t.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Sep()

            // ── Яндекс Диск ──────────────────────────────────────────
            SectionLabel("Яндекс Диск")
            AppInput(
                value = yandexUrl,
                onValueChange = onUrlChange,
                placeholder = "https://disk.yandex.ru/d/...",
                modifier = Modifier.padding(bottom = 8.dp),
            )
            AppButton(
                label = "⬇ Обновить файлы",
                onClick = onUpdateFiles,
                variant = BtnVariant.Accent2,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            if (proxyStatus.isNotEmpty()) {
                Text(
                    proxyStatus,
                    color = t.muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Sep()

            // ── Тема оформления ───────────────────────────────────────
            SectionLabel("Тема оформления")
            ListItemRow(
                name = currentThemeName,
                sub = "Нажми для выбора",
                onClick = onOpenThemes,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // ── Liquid Glass ──────────────────────────────────────────
            SettingsRow(
                title = "🫧 Liquid Glass",
                subtitle = "⚠️ Экспериментально · влияет на производительность",
                trailing = {
                    ToggleSwitch(checked = isGlassMode, onCheckedChange = { onToggleGlass() })
                },
            )
            Sep()

            // ── Звук ──────────────────────────────────────────────────
            SectionLabel("Звук")
            SettingsRow(
                title = if (isMuted) "🔇 Звук выключен" else "🔊 Звук включён",
                subtitle = "Звуки интерфейса",
                trailing = {
                    ToggleSwitch(checked = !isMuted, onCheckedChange = { onToggleMute() })
                },
            )
            Sep()

            // ── Приложение ────────────────────────────────────────────
            SectionLabel("Приложение")
            SettingsRow(
                title = "Версия",
                subtitle = "$appVersion — Android",
                trailing = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(t.surface3)
                            .clickable {}
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("🔄 Проверить", color = t.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
            )

            Spacer(Modifier.height(100.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// THEMES SCREEN  (#s-themes)
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun ThemesScreen(
    currentThemeId: String,
    onThemeSelect: (String) -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        AppHeader(
            title = "Тема оформления",
            subtitle = "Выбери цветовую схему",
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            AppColors.themes.forEach { theme ->
                val isSelected = theme.id == currentThemeId
                ThemeCard(
                    theme = theme,
                    selected = isSelected,
                    onClick = { onThemeSelect(theme.id) },
                )
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}

// ── Theme card (.theme-card) ──────────────────────────────────────────────────
// background:surface2; border-radius:10px; padding:13px 16px
// selected: accent bg 12% + border accent 70%
@Composable
private fun ThemeCard(
    theme: AppColors.ThemeDef,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalTheme.current
    val bgColor = if (selected) t.accent.copy(alpha = 0.12f) else t.surface2
    val borderColor = if (selected) t.accent.copy(alpha = 0.70f) else t.surface3

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 7.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Emoji icon
        Text(theme.icon, fontSize = 20.sp)

        // Swatches (.theme-swatch) — 3 dot colors
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(theme.bg, theme.accent, theme.accent2).forEach { color ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
        }

        // Name
        Text(
            text = theme.name,
            color = if (selected) t.accent else t.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )

        // Checkmark
        if (selected) {
            Text("✓", color = t.accent, fontSize = 16.sp)
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// HOMEWORK SCREEN  (#s-homework)
// ═════════════════════════════════════════════════════════════════════════════

data class HomeworkItem(
    val id: Long,
    val subject: String,
    val task: String,
    val deadline: String,
    val urgent: Boolean,
    val done: Boolean,
)

@Composable
fun HomeworkScreen(
    items: List<HomeworkItem>,
    activeTab: Boolean,   // true = active, false = done
    onTabChange: (Boolean) -> Unit,
    onToggleDone: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        AppHeader(
            title = "📚 Домашнее задание",
            onBack = onBack,
            actions = {
                Text(
                    text = "＋",
                    color = t.accent,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clickable(onClick = onAdd)
                        .padding(8.dp),
                )
            },
        )

        // ── Вкладки (Активные / Выполненные) ─────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(true to "Активные", false to "Выполненные").forEach { (isActive, label) ->
                val selected = isActive == activeTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) t.accent.copy(alpha = 0.15f) else Color.Transparent
                        )
                        .border(
                            1.5.dp,
                            if (selected) t.accent else t.surface3,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { onTabChange(isActive) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (selected) t.accent else t.muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // ── Список заданий ────────────────────────────────────────────
        val filtered = items.filter { if (activeTab) !it.done else it.done }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            if (filtered.isEmpty()) {
                EmptyState(
                    icon = if (activeTab) "✅" else "📭",
                    title = if (activeTab) "Нет активных заданий" else "Нет выполненных заданий",
                    subtitle = if (activeTab) "Нажми + чтобы добавить" else null,
                )
            } else {
                filtered.forEach { item ->
                    HwCard(
                        item = item,
                        onToggle = { onToggleDone(item.id) },
                        onDelete = { onDelete(item.id) },
                    )
                }
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}

// ── HW card (.rn-hw-card equivalent) ──────────────────────────────────────────
@Composable
private fun HwCard(
    item: HomeworkItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val t = LocalTheme.current
    val borderColor = when {
        item.done   -> t.surface3
        item.urgent -> t.danger.copy(alpha = 0.4f)
        else        -> t.surface3
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(t.surface2)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Checkbox круглый
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (item.done) t.accent else Color.Transparent)
                .border(2.dp, if (item.done) t.accent else t.surface3, CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (item.done) {
                Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.subject,
                color = if (item.done) t.muted else t.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textDecoration = if (item.done)
                    androidx.compose.ui.text.style.TextDecoration.LineThrough
                else null,
                modifier = Modifier.padding(bottom = 3.dp),
            )
            if (item.task.isNotEmpty()) {
                Text(
                    text = item.task,
                    color = t.muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            if (item.deadline.isNotEmpty()) {
                Text(
                    text = "📅 ${item.deadline}${if (item.urgent) " ⚠️ Срочно!" else ""}",
                    color = if (item.urgent) t.danger else t.muted,
                    fontSize = 11.sp,
                    fontWeight = if (item.urgent) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (!item.done) {
            Text(
                text = "×",
                color = t.muted,
                fontSize = 20.sp,
                modifier = Modifier
                    .clickable(onClick = onDelete)
                    .padding(2.dp),
            )
        }
    }
}

// Добавляем modifier с аннотацией в конце файла
private fun Modifier.padding(bottom: Dp): Modifier = this.padding(bottom = bottom)
