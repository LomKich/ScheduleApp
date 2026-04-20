package com.schedule.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.schedule.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────
//  SettingsSection  —  цветной заголовок секции (как «Воспроизведение»)
// ─────────────────────────────────────────────────────────────────
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.labelLarge.copy(
            color      = AccentPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 13.sp,
            letterSpacing = 0.5.sp
        ),
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

// ─────────────────────────────────────────────────────────────────
//  SettingsItem  —  пункт настроек с названием + описанием
// ─────────────────────────────────────────────────────────────────
@Composable
fun SettingsItem(
    title:       String,
    subtitle:    String?  = null,
    icon:        ImageVector? = null,
    onClick:     () -> Unit   = {},
    trailing:    @Composable (() -> Unit)? = null,
    modifier:    Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Иконка (если есть)
        if (icon != null) {
            Icon(
                imageVector         = icon,
                contentDescription  = null,
                tint                = TextSecondary,
                modifier            = Modifier
                    .size(22.dp)
                    .padding(end = 0.dp)
            )
            Spacer(Modifier.width(16.dp))
        }

        // Текст
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = title,
                style = MaterialTheme.typography.titleMedium
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = subtitle,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Trailing (Switch, стрелка, текст-значение)
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  SettingsToggleItem  —  пункт настроек с Switch
// ─────────────────────────────────────────────────────────────────
@Composable
fun SettingsToggleItem(
    title:    String,
    subtitle: String?   = null,
    checked:  Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier  = Modifier
) {
    SettingsItem(
        title    = title,
        subtitle = subtitle,
        onClick  = { onCheckedChange(!checked) },
        modifier = modifier,
        trailing = {
            Switch(
                checked         = checked,
                onCheckedChange = onCheckedChange,
                colors          = SwitchDefaults.colors(
                    checkedThumbColor        = White,
                    checkedTrackColor        = AccentPrimary,
                    uncheckedThumbColor      = Color(0xFFB0B0B0),
                    uncheckedTrackColor      = Color(0xFF3A3A3A),
                    uncheckedBorderColor     = Color(0xFF3A3A3A),
                )
            )
        }
    )
}

// ─────────────────────────────────────────────────────────────────
//  DarkCard  —  тёмная карточка в Echo Nightly стиле
// ─────────────────────────────────────────────────────────────────
@Composable
fun DarkCard(
    modifier:  Modifier  = Modifier,
    content:   @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = BgCard
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(content = content)
    }
}

// ─────────────────────────────────────────────────────────────────
//  FilterPill  —  кнопка-таблетка фильтра (Offline / Spotify)
// ─────────────────────────────────────────────────────────────────
@Composable
fun FilterPill(
    label:     String,
    selected:  Boolean,
    onClick:   () -> Unit,
    modifier:  Modifier = Modifier
) {
    val bgColor   = if (selected) White       else BgCard
    val textColor = if (selected) BgPrimary   else TextSecondary
    val border    = if (selected) null        else BorderStroke(1.dp, Outline)

    Surface(
        modifier  = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick),
        shape     = RoundedCornerShape(24.dp),
        color     = bgColor,
        border    = border
    ) {
        Text(
            text     = label,
            color    = textColor,
            style    = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────
//  PlayButton  —  градиентная кнопка «Воспроизвести»
// ─────────────────────────────────────────────────────────────────
@Composable
fun PlayButton(
    label:    String   = "Воспроизвести",
    onClick:  () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick  = onClick,
        modifier = modifier.height(40.dp),
        shape    = RoundedCornerShape(24.dp),
        colors   = ButtonDefaults.buttonColors(
            containerColor = AccentPrimary,
            contentColor   = White
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp)
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

// ─────────────────────────────────────────────────────────────────
//  IconCircleButton  —  круглая иконка-кнопка (shuffle, filter)
// ─────────────────────────────────────────────────────────────────
@Composable
fun IconCircleButton(
    icon:      ImageVector,
    onClick:   () -> Unit,
    tint:      Color    = TextSecondary,
    bgColor:   Color    = BgCard,
    size:      Int      = 40,
    modifier:  Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = tint,
            modifier           = Modifier.size((size * 0.5f).dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────
//  SectionHeader  —  заголовок раздела (Альбомы, Исполнители…)
// ─────────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(
    title:    String,
    onMore:   (() -> Unit)? = null,
    modifier: Modifier      = Modifier
) {
    Row(
        modifier          = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text     = title,
            style    = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f)
        )
        if (onMore != null) {
            Icon(
                imageVector        = androidx.compose.material.icons.Icons.Default.ChevronRight,
                contentDescription = "More",
                tint               = TextSecondary,
                modifier           = Modifier
                    .size(24.dp)
                    .clickable(onClick = onMore)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  EchoTopBar  —  AppBar в стиле Echo Nightly
// ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoTopBar(
    title:       String,
    onBack:      (() -> Unit)?  = null,
    actions:     @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title  = {
            Text(
                text  = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector        = androidx.compose.material.icons.Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint               = TextPrimary
                    )
                }
            }
        },
        actions = actions,
        colors  = TopAppBarDefaults.topAppBarColors(
            containerColor        = BgSurface,
            titleContentColor     = TextPrimary,
            navigationIconContentColor = TextPrimary,
            actionIconContentColor     = TextPrimary
        )
    )
}
