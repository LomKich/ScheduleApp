package com.schedule.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.schedule.app.ui.components.*
import com.schedule.app.ui.theme.LocalTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

// ─────────────────────────────────────────────────────────────────────────────
// DATA MODELS
// ─────────────────────────────────────────────────────────────────────────────

data class ChatPreview(
    val id: String,           // username или grp_xxx
    val name: String,
    val avatar: String,
    val avatarType: String = "emoji",
    val avatarData: String? = null,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val isGroup: Boolean = false,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isMe: Boolean = false,   // последнее сообщение от меня
)

data class OnlineUser(
    val username: String,
    val name: String,
    val avatar: String,
    val avatarType: String = "emoji",
    val avatarData: String? = null,
    val status: String = "online",
    val bio: String = "",
    val vip: Boolean = false,
)

data class GroupChat(
    val id: String,
    val name: String,
    val avatar: String,
    val memberCount: Int = 0,
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val unreadCount: Int = 0,
)

// ─────────────────────────────────────────────────────────────────────────────
// MESSENGER SCREEN  — список диалогов (s-messenger)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun MessengerScreen(
    chats: List<ChatPreview>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onChatClick: (String) -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onRefresh: () -> Unit = {},
    onBack: () -> Unit,
) {
    val t       = LocalTheme.current
    val density = LocalDensity.current
    var showFab by remember { mutableStateOf(false) }

    // ── Pull-to-refresh ───────────────────────────────────────────────────
    var pullOffsetPx by remember { mutableStateOf(0f) }
    var pullFired    by remember { mutableStateOf(false) }
    val pullThreshPx = with(density) { 72.dp.toPx() }
    val pullAnim by animateFloatAsState(
        targetValue = if (isLoading) pullThreshPx * 0.6f else 0f,
        animationSpec = spring(stiffness = 260f),
        label = "messengerPullAnim",
    )
    val indicatorOffset = if (pullOffsetPx > 0f) pullOffsetPx.coerceAtMost(pullThreshPx * 1.2f)
                          else pullAnim
    val indicatorAlpha  = (indicatorOffset / pullThreshPx).coerceIn(0f, 1f)
    val indicatorRotate by animateFloatAsState(
        targetValue = if (isLoading) 360f else indicatorOffset / pullThreshPx * 180f,
        animationSpec = if (isLoading) infiniteRepeatable(tween(700), RepeatMode.Restart)
                        else spring(),
        label = "messengerPullRotate",
    )

    val pullModifier = Modifier.pointerInput(onRefresh) {
        var startY  = Float.NaN
        var startX  = Float.NaN
        var touchId = PointerId(0L)
        awaitPointerEventScope {
            while (true) {
                val ev = awaitPointerEvent(pass = PointerEventPass.Initial)
                for (ch in ev.changes) {
                    when {
                        !ch.previousPressed && ch.pressed -> {
                            startY = ch.position.y; startX = ch.position.x
                            touchId = ch.id; pullFired = false
                        }
                        ch.pressed && ch.id == touchId && !startY.isNaN() -> {
                            val dy = startY - ch.position.y   // < 0 = вниз
                            val dx = abs(ch.position.x - startX)
                            if (dy < 0f && abs(dy) > dx * 1.5f) {
                                pullOffsetPx = (-dy).coerceAtLeast(0f)
                                if (!pullFired && pullOffsetPx >= pullThreshPx) pullFired = true
                            }
                        }
                        !ch.pressed && ch.id == touchId -> {
                            if (pullFired && !isLoading) onRefresh()
                            startY = Float.NaN; pullOffsetPx = 0f; pullFired = false
                        }
                    }
                }
            }
        }
    }

    val filtered = if (searchQuery.isEmpty()) chats
    else chats.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.id.contains(searchQuery, ignoreCase = true)
    }

    Box(modifier = Modifier.fillMaxSize().background(t.bg).then(pullModifier)) {

        // ── Pull-to-refresh индикатор ─────────────────────────────────────
        if (indicatorAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = with(density) { (indicatorOffset * 0.5f).toDp() } + 48.dp)
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
            AppHeader(title = "💬 Сообщения", onBack = onBack)

            // Search bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(t.surface2),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("🔍", fontSize = 16.sp, color = t.muted)
                    if (searchQuery.isEmpty()) {
                        Text("Поиск", color = t.muted, fontSize = 14.sp)
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        textStyle = androidx.compose.ui.text.TextStyle(color = t.text, fontSize = 14.sp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (isLoading && chats.isEmpty()) {
                repeat(6) { SkeletonItem(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            } else if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💬", fontSize = 52.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (searchQuery.isEmpty()) "Нет сообщений" else "Ничего не найдено",
                            color = t.text, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Нажми + и начни общаться",
                            color = t.muted, fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(20.dp))
                        AppButton(
                            label = "👥 Найти собеседника",
                            onClick = onNewChat,
                            variant = BtnVariant.Accent,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 0.dp),
                ) {
                    items(filtered, key = { it.id }) { chat ->
                        ChatPreviewRow(chat = chat, onClick = { onChatClick(chat.id) })
                    }
                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
        }

        // FAB
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 96.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showFab) {
                FabMenuItem(label = "Создать группу", icon = "👥", onClick = {
                    showFab = false; onNewGroup()
                })
                FabMenuItem(label = "Новый диалог", icon = "💬", onClick = {
                    showFab = false; onNewChat()
                })
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(t.accent)
                    .clickable { showFab = !showFab },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (showFab) "✕" else "✏️", fontSize = 20.sp, color = t.btnText)
            }
        }
    }
}

@Composable
private fun FabMenuItem(label: String, icon: String, onClick: () -> Unit) {
    val t = LocalTheme.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(t.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(label, color = t.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(t.surface2)
                .border(1.5.dp, t.surface3, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(icon, fontSize = 20.sp)
        }
    }
}

@Composable
private fun ChatPreviewRow(chat: ChatPreview, onClick: () -> Unit) {
    val t = LocalTheme.current
    val timeStr = remember(chat.lastMessageTime) {
        if (chat.lastMessageTime > 0)
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(chat.lastMessageTime))
        else ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar with online dot
        Box(modifier = Modifier.size(52.dp)) {
            AvatarView(
                avatar = chat.avatar,
                avatarType = chat.avatarType,
                avatarData = chat.avatarData,
                size = 52.dp,
            )
            if (chat.isOnline && !chat.isGroup) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(t.bg)
                        .padding(2.dp),
                ) {
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape)
                            .background(Color(0xFF4CAF7D))
                    )
                }
            }
        }

        // Name + preview
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (chat.isPinned) Text("📌", fontSize = 12.sp)
                    Text(
                        chat.name,
                        color = t.text, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (timeStr.isNotEmpty()) {
                        Text(timeStr, color = t.muted, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (chat.isMe) {
                        Text("Ты: ", color = t.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        chat.lastMessage.ifEmpty { "Нет сообщений" },
                        color = t.muted, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (chat.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (chat.isMuted) t.surface3 else t.accent)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (chat.unreadCount > 99) "99+" else "${chat.unreadCount}",
                            color = if (chat.isMuted) t.muted else t.btnText,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(
        color = t.surface3.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 80.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ONLINE USERS SCREEN  — поиск пользователей (s-online)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun OnlineUsersScreen(
    users: List<OnlineUser>,
    myUsername: String,
    isLoading: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalTheme.current
    val filtered = if (searchQuery.isEmpty()) users
    else users.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.username.contains(searchQuery, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize().background(t.bg)) {
        AppHeader(
            title = "Друзья",
            onBack = onBack,
            actions = {
                Text(
                    "↻",
                    color = t.accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(onClick = onRefresh)
                        .padding(8.dp),
                )
            },
        )

        // Search
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(t.surface2)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("🔍", fontSize = 16.sp, color = t.muted)
            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) Text("@юзернейм или имя", color = t.muted, fontSize = 14.sp)
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    textStyle = androidx.compose.ui.text.TextStyle(color = t.text, fontSize = 14.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (isLoading && users.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = t.accent, modifier = Modifier.size(32.dp))
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = "👥",
                    title = "Пусто",
                    subtitle = if (searchQuery.isEmpty()) "Никого нет онлайн" else "Не найдено",
                )
            }
        } else {
            SectionLabel(
                text = if (searchQuery.isEmpty()) "Онлайн: ${filtered.size}" else "Найдено: ${filtered.size}",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered.filter { it.username != myUsername }, key = { it.username }) { user ->
                    OnlineUserRow(user = user, onClick = { onUserClick(user.username) })
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }
}

@Composable
private fun OnlineUserRow(user: OnlineUser, onClick: () -> Unit) {
    val t = LocalTheme.current
    val statusColor = STATUS_COLORS[user.status] ?: t.accent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(t.surface2)
            .border(1.5.dp, t.surface3, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(),
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Avatar + online ring
        Box(modifier = Modifier.size(46.dp)) {
            AvatarView(
                avatar = user.avatar,
                avatarType = user.avatarType,
                avatarData = user.avatarData,
                size = 46.dp,
                borderColor = statusColor.copy(alpha = 0.6f),
                borderWidth = 2.dp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    user.name,
                    color = t.text, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (user.vip) Text("👑", fontSize = 12.sp)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("@${user.username}", color = t.muted, fontSize = 12.sp)
                Text("·", color = t.muted, fontSize = 12.sp)
                Text(
                    PROFILE_STATUSES.firstOrNull { it.first == user.status }?.third ?: user.status,
                    color = statusColor,
                    fontSize = 11.sp,
                )
            }
            if (user.bio.isNotEmpty()) {
                Text(
                    user.bio,
                    color = t.muted, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(t.accent.copy(alpha = 0.12f))
                .border(1.dp, t.accent.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text("Написать", color = t.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PEER PROFILE SCREEN  (s-peer-profile)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun PeerProfileScreen(
    peer: OnlineUser?,
    isLoading: Boolean,
    isFriend: Boolean,
    onMessage: () -> Unit,
    onAddFriend: () -> Unit,
    onRemoveFriend: () -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalTheme.current

    Column(modifier = Modifier.fillMaxSize().background(t.bg)) {
        AppHeader(title = peer?.name ?: "Профиль", onBack = onBack)

        if (isLoading || peer == null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = t.accent, modifier = Modifier.size(32.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                val statusColor = STATUS_COLORS[peer.status] ?: t.accent

                // Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(t.accent.copy(alpha = 0.35f), t.accent.copy(alpha = 0.10f))
                            )
                        ),
                )

                // Avatar overlapping banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(modifier = Modifier.offset(y = (-48).dp)) {
                        AvatarView(
                            avatar = peer.avatar,
                            avatarType = peer.avatarType,
                            avatarData = peer.avatarData,
                            size = 96.dp,
                            borderColor = t.bg,
                            borderWidth = 4.dp,
                        )
                        if (peer.vip) {
                            Text(
                                "👑", fontSize = 22.sp,
                                modifier = Modifier.align(Alignment.BottomCenter).offset(y = 6.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(54.dp))

                // Name, username, status
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        peer.name,
                        color = t.text, fontSize = 22.sp,
                        fontWeight = FontWeight(800),
                    )
                    Text(
                        "@${peer.username}",
                        color = t.muted, fontSize = 14.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(statusColor.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Text(
                            PROFILE_STATUSES.firstOrNull { it.first == peer.status }
                                ?.let { "${it.second} ${it.third}" } ?: peer.status,
                            color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                    if (peer.bio.isNotEmpty()) {
                        Text(
                            peer.bio,
                            color = t.muted, fontSize = 13.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppButton(
                        label = "💬 Написать",
                        onClick = onMessage,
                        variant = BtnVariant.Accent,
                        modifier = Modifier.weight(1f),
                    )
                    AppButton(
                        label = if (isFriend) "✓ Друг" else "+ Добавить",
                        onClick = if (isFriend) onRemoveFriend else onAddFriend,
                        variant = if (isFriend) BtnVariant.Surface else BtnVariant.Accent2,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TEACHERS SCREEN  (s-teachers) — аналог GroupsScreen для преподавателей
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TeachersScreen(
    title: String,
    subtitle: String,
    teachers: List<String>,
    selectedTeacher: String?,
    isLoading: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onTeacherClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalTheme.current

    Column(modifier = Modifier.fillMaxSize().background(t.bg)) {
        AppHeader(title = title, subtitle = subtitle.ifEmpty { null }, onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(t.surface)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            SearchInput(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = "Найти преподавателя...",
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            if (isLoading && teachers.isEmpty()) {
                repeat(8) { SkeletonItem() }
            } else {
                val filtered = if (searchQuery.isEmpty()) teachers
                else teachers.filter { it.contains(searchQuery, ignoreCase = true) }

                val sorted = buildList {
                    filtered.firstOrNull { it == selectedTeacher }?.let { add(it) }
                    addAll(filtered.filter { it != selectedTeacher })
                }

                if (sorted.isEmpty()) {
                    EmptyState(icon = "🔍", title = "Ничего не найдено")
                } else {
                    sorted.forEach { teacher ->
                        ListItemRow(
                            name = teacher,
                            selected = teacher == selectedTeacher,
                            onClick = { onTeacherClick(teacher) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(100.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GROUP CHATS SCREEN  (s-groups-chat)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GroupChatsScreen(
    groups: List<GroupChat>,
    isLoading: Boolean,
    onGroupClick: (String) -> Unit,
    onCreateGroup: () -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalTheme.current

    Box(modifier = Modifier.fillMaxSize().background(t.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            AppHeader(
                title = "💬 Группы",
                onBack = onBack,
                actions = {
                    Text(
                        "＋",
                        color = t.accent,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clickable(onClick = onCreateGroup)
                            .padding(8.dp),
                    )
                },
            )

            if (isLoading && groups.isEmpty()) {
                repeat(5) { SkeletonItem(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            } else if (groups.isEmpty()) {
                Box(
                    Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("👥", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Нет групп", color = t.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Создай первую группу", color = t.muted, fontSize = 13.sp)
                        Spacer(Modifier.height(20.dp))
                        AppButton(label = "Создать группу", onClick = onCreateGroup, variant = BtnVariant.Accent)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(groups, key = { it.id }) { group ->
                        GroupChatRow(group = group, onClick = { onGroupClick(group.id) })
                    }
                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
        }
    }
}

@Composable
private fun GroupChatRow(group: GroupChat, onClick: () -> Unit) {
    val t = LocalTheme.current
    val timeStr = remember(group.lastMessageTime) {
        if (group.lastMessageTime > 0)
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(group.lastMessageTime))
        else ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Group avatar
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(t.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(group.avatar, fontSize = 24.sp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    group.name,
                    color = t.text, fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (timeStr.isNotEmpty()) Text(timeStr, color = t.muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    group.lastMessage.ifEmpty { "${group.memberCount} участников" },
                    color = t.muted, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (group.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(t.accent)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "${group.unreadCount}",
                            color = t.btnText, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(color = t.surface3.copy(alpha = 0.5f), modifier = Modifier.padding(start = 80.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// CREATE GROUP DIALOG
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun CreateGroupDialog(
    onConfirm: (name: String, avatar: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalTheme.current
    var name   by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf("👥") }
    val emojiPool = listOf("👥","🎮","📚","🏆","💬","🔥","⚡","🎵","🌟","💎","🚀","🎯")

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
                "Создать группу",
                color = t.text, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            // Emoji picker row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                emojiPool.forEach { e ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (e == avatar) t.accent.copy(alpha = 0.2f) else t.surface2)
                            .border(2.dp, if (e == avatar) t.accent else Color.Transparent, CircleShape)
                            .clickable { avatar = e },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(e, fontSize = 20.sp)
                    }
                }
            }
            AppInput(
                value = name,
                onValueChange = { name = it },
                placeholder = "Название группы",
                modifier = Modifier.padding(bottom = 20.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppButton("Отмена", onDismiss, BtnVariant.Surface, modifier = Modifier.weight(1f))
                AppButton(
                    label = "Создать",
                    onClick = { if (name.trim().isNotEmpty()) onConfirm(name.trim(), avatar) },
                    variant = BtnVariant.Accent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
