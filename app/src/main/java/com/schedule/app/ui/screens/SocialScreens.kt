package com.schedule.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.schedule.app.ui.components.*
import com.schedule.app.ui.theme.LocalTheme

// ─────────────────────────────────────────────────────────────────────────────
// DATA MODELS
// ─────────────────────────────────────────────────────────────────────────────

data class UserProfile(
    val name: String,
    val username: String,
    val avatar: String = "😊",
    val avatarType: String = "emoji",   // "emoji" | "photo"
    val avatarData: String? = null,     // base64 photo
    val accentColor: Color = Color(0xFFE87722),
    val status: String = "online",
    val bio: String = "",
    val vip: Boolean = false,
    val banner: String? = null,         // gradient css string or null
    val friendCount: Int = 0,
    val groupCount: Int = 0,
)

val PROFILE_STATUSES = listOf(
    Triple("online",  "🟢", "В сети"),
    Triple("study",   "📚", "Учусь"),
    Triple("busy",    "🔴", "Занят"),
    Triple("sleep",   "😴", "Сплю"),
    Triple("game",    "🎮", "Играю"),
    Triple("away",    "🌙", "Отошёл"),
)

// Status color map
val STATUS_COLORS = mapOf(
    "online" to Color(0xFF4CAF7D),
    "study"  to Color(0xFF60CDFF),
    "busy"   to Color(0xFFC94F4F),
    "sleep"  to Color(0xFFA78BFA),
    "game"   to Color(0xFFF5C518),
    "away"   to Color(0xFFE87722),
)

// ─────────────────────────────────────────────────────────────────────────────
// LOGIN / REGISTER SCREEN  (#s-login)
// Два таба: Регистрация / Войти
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun LoginScreen(
    isLoading: Boolean = false,
    // Register
    regName: String,
    onRegNameChange: (String) -> Unit,
    regUsername: String,
    onRegUsernameChange: (String) -> Unit,
    regUsernameStatus: String,        // "" | "✓ Доступен" | "✗ Занят"
    regUsernameValid: Boolean,
    regEmoji: String,
    onRegEmojiChange: (String) -> Unit,
    onRegRandomEmoji: () -> Unit,
    regPassword: String,
    onRegPasswordChange: (String) -> Unit,
    onRegSubmit: () -> Unit,
    regError: String,
    regEnabled: Boolean,
    // Auth
    authUsername: String,
    onAuthUsernameChange: (String) -> Unit,
    authPassword: String,
    onAuthPasswordChange: (String) -> Unit,
    onAuthSubmit: () -> Unit,
    authError: String,
) {
    val t = LocalTheme.current
    var showRegister by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        // Header
        AppHeader(title = "Аккаунт")

        // Body
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))

            // Big icon
            Text("👤", fontSize = 56.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))

            // ── Tab switcher (.login-tabs) ──────────────────────────
            // background:surface2; border-radius:12px; padding:3px; width:300px max
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(t.surface2)
                    .padding(3.dp),
            ) {
                Row {
                    listOf(true to "Регистрация", false to "Войти").forEach { (isReg, label) ->
                        val selected = isReg == showRegister
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) t.accent else Color.Transparent)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { showRegister = isReg },
                                )
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = if (selected) t.btnText else t.muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Forms ──────────────────────────────────────────────
            Box(modifier = Modifier.widthIn(max = 340.dp).fillMaxWidth()) {
                if (showRegister) {
                    RegisterForm(
                        name            = regName,
                        onNameChange    = onRegNameChange,
                        username        = regUsername,
                        onUsernameChange= onRegUsernameChange,
                        usernameStatus  = regUsernameStatus,
                        usernameValid   = regUsernameValid,
                        emoji           = regEmoji,
                        onEmojiChange   = onRegEmojiChange,
                        onRandomEmoji   = onRegRandomEmoji,
                        password        = regPassword,
                        onPasswordChange= onRegPasswordChange,
                        onSubmit        = onRegSubmit,
                        error           = regError,
                        enabled         = regEnabled,
                        loading         = isLoading,
                    )
                } else {
                    AuthForm(
                        username        = authUsername,
                        onUsernameChange= onAuthUsernameChange,
                        password        = authPassword,
                        onPasswordChange= onAuthPasswordChange,
                        onSubmit        = onAuthSubmit,
                        error           = authError,
                        loading         = isLoading,
                    )
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

// ── Register form ─────────────────────────────────────────────────────────────
@Composable
private fun RegisterForm(
    name: String, onNameChange: (String) -> Unit,
    username: String, onUsernameChange: (String) -> Unit,
    usernameStatus: String, usernameValid: Boolean,
    emoji: String, onEmojiChange: (String) -> Unit, onRandomEmoji: () -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit, error: String,
    enabled: Boolean, loading: Boolean,
) {
    val t = LocalTheme.current

    // Имя
    SectionLabel("Имя")
    AppInput(
        value = name,
        onValueChange = onNameChange,
        placeholder = "Как тебя зовут?",
        modifier = Modifier.padding(bottom = 12.dp),
    )

    // Юзернейм
    SectionLabel("Юзернейм")
    AppInput(
        value = username,
        onValueChange = onUsernameChange,
        placeholder = "твой_ник",
        prefix = "@",
        modifier = Modifier.padding(bottom = 4.dp),
    )
    // Статус никнейма
    Text(
        text = usernameStatus,
        color = if (usernameValid) Color(0xFF4CAF7D) else Color(0xFFC94F4F),
        fontSize = 11.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 16.dp)
            .defaultMinSize(minHeight = 16.dp),
    )

    // Аватар
    SectionLabel("Аватар")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Emoji text input
        EmojiInput(
            value = emoji,
            onValueChange = onEmojiChange,
            placeholder = "Введи любой эмодзи 😎",
            modifier = Modifier.weight(1f),
        )
        // Random emoji button (.emoji-random-btn)
        // width:48dp; height:48dp; circle; border:2px surface3; cursor:pointer
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(t.surface2)
                .border(2.dp, t.surface3, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = false),
                    onClick = onRandomEmoji,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (emoji.isNotEmpty()) emoji else "🎲",
                fontSize = if (emoji.isNotEmpty()) 24.sp else 22.sp,
            )
        }
    }
    // Emoji error placeholder
    Spacer(Modifier.height(10.dp))

    // Пароль
    SectionLabel("Пароль")
    AppInput(
        value = password,
        onValueChange = onPasswordChange,
        placeholder = "Придумай пароль",
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Text(
        text = "Пароль нужен для входа с этого устройства",
        color = t.muted,
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 20.dp),
    )

    // Submit button
    AppButton(
        label = "Создать аккаунт",
        onClick = onSubmit,
        variant = BtnVariant.Accent,
        loading = loading,
        modifier = Modifier.fillMaxWidth(),
    )

    // Error
    if (error.isNotEmpty()) {
        Text(
            text = error,
            color = t.danger,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

// ── Auth form ─────────────────────────────────────────────────────────────────
@Composable
private fun AuthForm(
    username: String, onUsernameChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit, error: String, loading: Boolean,
) {
    val t = LocalTheme.current

    SectionLabel("Юзернейм")
    AppInput(
        value = username,
        onValueChange = onUsernameChange,
        placeholder = "твой_ник",
        prefix = "@",
        modifier = Modifier.padding(bottom = 16.dp),
    )

    SectionLabel("Пароль")
    AppInput(
        value = password,
        onValueChange = onPasswordChange,
        placeholder = "Пароль",
        modifier = Modifier.padding(bottom = 20.dp),
    )

    AppButton(
        label = "Войти",
        onClick = onSubmit,
        variant = BtnVariant.Accent,
        loading = loading,
        modifier = Modifier.fillMaxWidth(),
    )

    if (error.isNotEmpty()) {
        Text(
            text = error,
            color = t.danger,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

// ── Emoji-only input ──────────────────────────────────────────────────────────
@Composable
private fun EmojiInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalTheme.current
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) t.accent else t.surface3,
        animationSpec = tween(180),
        label = "emojiBorder",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(t.surface2)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = t.muted, fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = t.text, fontSize = 22.sp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PROFILE SCREEN  (#s-profile)
//
// Точно воспроизводит оригинальный profileRenderScreen():
//  • Banner height:140dp с radial-gradient (accentColor)
//  • Avatar 96dp at bottom of banner, -50dp overlap
//  • VIP crown overlay (если vip)
//  • Имя 24sp/800 + @username 14sp muted + status chip
//  • Bio 13sp muted
//  • 3 action buttons: Фото / Изменить / Настройки
//  • Card: Лидерборд / Друзья / Группы
//  • Card: P2P Подключение
//  • Card: Уведомления
//  • Card: Фоновое соединение
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ProfileScreen(
    profile: UserProfile?,
    p2pConnected: Boolean,
    notifEnabled: Boolean,
    bgServiceEnabled: Boolean,
    onLogin: () -> Unit,
    onPickPhoto: () -> Unit,
    onEdit: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenGroups: () -> Unit,
    onOpenGames: () -> Unit = {},
    onReconnect: () -> Unit,
    onToggleNotif: () -> Unit,
    onToggleBgService: () -> Unit,
    onLogout: () -> Unit = {},
) {
    val t = LocalTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        // Header (.hdr) – только title, без Back
        AppHeader(title = "Профиль")

        if (profile == null) {
            // Not logged in
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("👤", fontSize = 56.sp)
                Spacer(Modifier.height(20.dp))
                Text(
                    "Войди или создай аккаунт",
                    color = t.muted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
                AppButton(
                    label = "Войти / Создать аккаунт",
                    onClick = onLogin,
                    variant = BtnVariant.Accent,
                )
            }
        } else {
            val statusEntry = PROFILE_STATUSES.firstOrNull { it.first == profile.status }
                ?: PROFILE_STATUSES[0]
            val statusColor = STATUS_COLORS[profile.status] ?: t.accent

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                // ── Banner + Avatar ──────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                ) {
                    // Banner: height 140dp, gradient from accentColor
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        profile.accentColor.copy(alpha = 0.40f),
                                        profile.accentColor.copy(alpha = 0.13f),
                                    ),
                                    start = Offset(0f, 0f),
                                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                                )
                            ),
                    )

                    // Avatar centered, overlapping banner bottom by 50dp
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 50.dp),
                    ) {
                        // Avatar ring
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(t.surface2)
                                .border(3.dp, profile.accentColor, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            AvatarView(
                                avatar     = profile.avatar,
                                avatarType = profile.avatarType,
                                avatarData = profile.avatarData,
                                size       = 90.dp,
                            )
                        }
                        // VIP crown
                        if (profile.vip) {
                            Text(
                                text = "👑",
                                fontSize = 22.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .offset(y = 6.dp),
                            )
                        }
                    }
                }

                // Spacer to clear avatar overlap (50dp overlap + 48dp padding)
                Spacer(Modifier.height(62.dp))

                // ── Имя, username, статус, bio ───────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Имя [VIP badge]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 3.dp),
                    ) {
                        Text(
                            text = profile.name,
                            color = t.text,
                            fontSize = 24.sp,
                            fontWeight = FontWeight(800),
                            letterSpacing = (-0.01).em,
                        )
                        if (profile.vip) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFFF5C518).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFFF5C518).copy(alpha = 0.40f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "👑 VIP",
                                    color = Color(0xFFF5C518),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    // @username
                    Text(
                        text = "@${profile.username}",
                        color = t.muted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    // Status badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(statusColor.copy(alpha = 0.13f))
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(statusEntry.second, fontSize = 12.sp)
                            Text(
                                text = statusEntry.third,
                                color = statusColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // Bio
                    if (profile.bio.isNotEmpty()) {
                        Text(
                            text = profile.bio,
                            color = t.muted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ── 3 action buttons ─────────────────────────────────────
                // flex:1 each; padding:12px 6px 10px; background:surface2
                // border:1.5px surface3; border-radius:14px
                // icon circle 34px + label 12px 500
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProfileActionBtn(
                        icon = "📷",
                        label = "Выбрать\nфото",
                        onClick = onPickPhoto,
                        modifier = Modifier.weight(1f),
                    )
                    ProfileActionBtn(
                        icon = "✏️",
                        label = "Изменить",
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                    )
                    ProfileActionBtn(
                        icon = "⚙️",
                        label = "Настройки",
                        onClick = onOpenSettings,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ── Card: Лидерборд / Друзья / Группы ───────────────────
                // background:surface2; border-radius:16px; border:1.5px surface3
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(t.surface2)
                        .border(1.5.dp, t.surface3, RoundedCornerShape(16.dp)),
                ) {
                    ProfileListRow(
                        icon = "🎮",
                        title = "Игры",
                        subtitle = "15 мини-игр · секретный режим",
                        onClick = onOpenGames,
                        showDivider = true,
                    )
                    ProfileListRow(
                        icon = "🏆",
                        title = "Таблица лидеров",
                        subtitle = "Рекорды в играх",
                        onClick = onOpenLeaderboard,
                        showDivider = true,
                    )
                    ProfileListRow(
                        icon = "👥",
                        title = "Друзья",
                        subtitle = "${profile.friendCount} ${friendWord(profile.friendCount)}",
                        onClick = onOpenFriends,
                        showDivider = true,
                    )
                    ProfileListRow(
                        icon = "💬",
                        title = "Группы",
                        subtitle = "${profile.groupCount} ${groupWord(profile.groupCount)}",
                        onClick = onOpenGroups,
                        showDivider = false,
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ── Card: P2P Подключение ─────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(t.surface2)
                        .border(1.5.dp, t.surface3, RoundedCornerShape(16.dp)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Icon circle
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(t.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("📡", fontSize = 14.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Подключение",
                                color = t.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (p2pConnected) "🟢 Подключено" else "🔴 Отключено",
                                color = t.muted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                        // Reconnect button
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(bounded = false),
                                    onClick = onReconnect,
                                )
                                .padding(4.dp),
                        ) {
                            Text("↻", color = t.muted, fontSize = 22.sp)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Card: Уведомления ─────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(t.surface2)
                        .border(1.5.dp, t.surface3, RoundedCornerShape(16.dp)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(t.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🔔", fontSize = 14.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Уведомления",
                                color = t.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (notifEnabled) "🟢 Включены" else "🔴 Отключены",
                                color = t.muted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(t.surface3)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(),
                                    onClick = onToggleNotif,
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = if (notifEnabled) "Отключить" else "Включить",
                                color = t.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Card: Фоновое соединение ──────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(t.surface2)
                        .border(1.5.dp, t.surface3, RoundedCornerShape(16.dp))
                        .padding(bottom = 0.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(t.accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("📶", fontSize = 14.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Фоновое соединение",
                                color = t.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = if (bgServiceEnabled) "🟢 Активно" else "🔴 Выключено",
                                color = t.muted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 1.dp),
                            )
                        }
                        ToggleSwitch(
                            checked = bgServiceEnabled,
                            onCheckedChange = { onToggleBgService() },
                        )
                    }
                    // Description text
                    Text(
                        text = "Получайте сообщения даже когда приложение закрыто. Работает как в Telegram.",
                        color = t.muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }

                // ── Кнопка выхода ────────────────────────────────────────
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(t.surface2)
                        .border(1.5.dp, t.surface3, RoundedCornerShape(16.dp)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication        = rememberRipple(),
                                onClick           = onLogout,
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(t.danger.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) { Text("🚪", fontSize = 14.sp) }
                        Text(
                            "Выйти из аккаунта",
                            color = t.danger,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text("›", color = t.muted, fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

// ── Profile Action Button (.profile-action-btn) ───────────────────────────────
// flex-col; padding:12px 6px 10px; background:surface2; border:1.5px surface3
// border-radius:14px; icon circle 34px; label 12px 500
@Composable
private fun ProfileActionBtn(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTheme.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(t.surface2)
            .border(1.5.dp, t.surface3, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(),
                onClick = onClick,
            )
            .padding(top = 12.dp, bottom = 10.dp, start = 6.dp, end = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // Icon circle: 34dp, rgba(255,255,255,.08) bg
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(0x14FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = 18.sp)
        }
        Text(
            text = label,
            color = t.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp,
        )
    }
}

// ── Profile list row (inside a card) ─────────────────────────────────────────
// padding:14px 16px; gap:12px; border-bottom if showDivider
@Composable
private fun ProfileListRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean,
) {
    val t = LocalTheme.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(),
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Icon 28dp circle
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(t.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, fontSize = 14.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = t.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = t.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 1.dp))
            }
            Text("›", color = t.muted, fontSize = 18.sp)
        }
        if (showDivider) {
            androidx.compose.material3.Divider(
                color = t.surface3,
                thickness = 1.dp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PROFILE EDIT SCREEN  (#s-profile-edit)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ProfileEditScreen(
    profile: UserProfile,
    editName: String,
    onNameChange: (String) -> Unit,
    editBio: String,
    onBioChange: (String) -> Unit,
    editEmoji: String,
    onEmojiChange: (String) -> Unit,
    onRandomEmoji: () -> Unit,
    editStatus: String,
    onStatusChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onPickPhoto: () -> Unit = {},
) {
    val t = LocalTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        AppHeader(
            title = "Редактировать профиль",
            onBack = onBack,
            actions = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(t.accent)
                        .clickable(onClick = onSave)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        "Сохранить",
                        color = t.btnText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(20.dp))

            // Avatar preview + edit
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Avatar circle
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onPickPhoto),
                    ) {
                        AvatarView(
                            avatar     = editEmoji.ifEmpty { profile.avatar },
                            avatarType = profile.avatarType,
                            avatarData = profile.avatarData,
                            size       = 80.dp,
                            borderColor= profile.accentColor,
                            borderWidth= 3.dp,
                        )
                        // Camera overlay hint
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("📷", fontSize = 22.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    // Emoji edit row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth(),
                    ) {
                        EmojiInput(
                            value = editEmoji,
                            onValueChange = onEmojiChange,
                            placeholder = "Сменить эмодзи",
                            modifier = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(t.surface2)
                                .border(2.dp, t.surface3, CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(bounded = false),
                                    onClick = onRandomEmoji,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🎲", fontSize = 20.sp)
                        }
                    }
                }
            }

            // Имя
            SectionLabel("Имя")
            AppInput(
                value = editName,
                onValueChange = onNameChange,
                placeholder = "Твоё имя",
                modifier = Modifier.padding(bottom = 14.dp),
            )

            // Биография
            SectionLabel("О себе")
            AppInput(
                value = editBio,
                onValueChange = onBioChange,
                placeholder = "Кратко о себе...",
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.padding(bottom = 14.dp),
            )

            // Статус
            SectionLabel("Статус")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(t.surface2)
                    .border(1.5.dp, t.surface3, RoundedCornerShape(12.dp))
                    .padding(bottom = 0.dp),
            ) {
                PROFILE_STATUSES.forEachIndexed { idx, (statusId, emoji, label) ->
                    val selected = statusId == editStatus
                    val statusColor = STATUS_COLORS[statusId] ?: t.accent
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selected) statusColor.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(),
                                onClick = { onStatusChange(statusId) },
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(emoji, fontSize = 16.sp)
                        Text(
                            label,
                            color = if (selected) statusColor else t.text,
                            fontSize = 14.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Text("✓", color = statusColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (idx < PROFILE_STATUSES.lastIndex) {
                        androidx.compose.material3.Divider(
                            color = t.surface3,
                            thickness = 1.dp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FRIENDS / ONLINE SCREEN  (#s-online)
// ─────────────────────────────────────────────────────────────────────────────
data class FriendEntry(
    val username: String,
    val name: String,
    val avatar: String,
    val status: String,
    val isOnline: Boolean,
)

@Composable
fun FriendsScreen(
    friends: List<FriendEntry>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onFriendClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalTheme.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        AppHeader(
            title = "Друзья",
            onBack = onBack,
            actions = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(bounded = false),
                            onClick = onRefresh,
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Text(
                        "↻ Обновить",
                        color = t.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // Search input
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(t.surface2)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🔍", fontSize = 16.sp)
                Box(modifier = Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            "@юзернейм или поиск по имени",
                            color = t.muted,
                            fontSize = 14.sp,
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        textStyle = TextStyle(color = t.text, fontSize = 14.sp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Hint
            Text(
                text = "Введи @никнейм чтобы найти пользователя",
                color = t.muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )

            SectionLabel("Все друзья")

            if (friends.isEmpty()) {
                EmptyState(
                    icon = "👥",
                    title = "Нет друзей",
                    subtitle = "Найди пользователей по @никнейму",
                )
            } else {
                val filtered = if (searchQuery.isEmpty()) friends
                else friends.filter {
                    it.username.contains(searchQuery.removePrefix("@"), ignoreCase = true) ||
                            it.name.contains(searchQuery, ignoreCase = true)
                }
                filtered.forEach { friend ->
                    FriendRow(
                        friend = friend,
                        onClick = { onFriendClick(friend.username) },
                    )
                }
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun FriendRow(friend: FriendEntry, onClick: () -> Unit) {
    val t = LocalTheme.current
    val statusColor = STATUS_COLORS[friend.status] ?: t.muted

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(t.surface2)
            .border(1.5.dp, t.surface3, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(),
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar with online indicator
        Box(modifier = Modifier.size(44.dp)) {
            AvatarView(
                avatar     = friend.avatar,
                avatarType = "emoji",
                size       = 44.dp,
                borderColor = t.surface3,
                borderWidth = 2.dp,
            )
            // Online dot
            if (friend.isOnline) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(t.bg)
                        .padding(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(statusColor),
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                friend.name,
                color = t.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "@${friend.username}",
                    color = t.muted,
                    fontSize = 12.sp,
                )
                if (friend.isOnline) {
                    Text("·", color = t.muted, fontSize = 12.sp)
                    Text(
                        PROFILE_STATUSES.firstOrNull { it.first == friend.status }?.third ?: "",
                        color = statusColor,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Text("›", color = t.muted, fontSize = 18.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LEADERBOARD SCREEN  (#s-leaderboard)
// ─────────────────────────────────────────────────────────────────────────────
data class LeaderboardEntry(
    val rank: Int,
    val username: String,
    val name: String,
    val avatar: String,
    val score: Int,
    val game: String,
    val isMe: Boolean = false,
)
