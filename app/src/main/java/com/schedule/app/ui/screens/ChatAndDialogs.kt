package com.schedule.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.schedule.app.ui.ChatMessage
import com.schedule.app.ui.components.*
import com.schedule.app.ui.theme.LocalTheme
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────────────────────────────────────
// CHAT SCREEN
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ChatScreen(
    withUsername: String,
    myUsername: String,
    messages: List<ChatMessage>,
    isLoading: Boolean,
    messageInput: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalTheme.current
    val listState = rememberLazyListState()

    // Автоскролл при новых сообщениях
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg),
    ) {
        // Header
        AppHeader(
            title    = "@$withUsername",
            subtitle = "Личные сообщения",
            onBack   = onBack,
        )

        // Messages list
        if (isLoading && messages.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = t.accent, modifier = Modifier.size(32.dp))
            }
        } else if (messages.isEmpty()) {
            Box(
                Modifier.weight(1f).padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💬", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Начни диалог с @$withUsername",
                        color = t.muted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(
                        message    = msg,
                        isFromMe   = msg.fromUser == myUsername,
                    )
                }
            }
        }

        // Input bar
        ChatInputBar(
            value     = messageInput,
            onChanged = onInputChange,
            onSend    = onSend,
        )
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isFromMe: Boolean,
) {
    val t = LocalTheme.current
    val timeStr = remember(message.ts) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.ts))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (isFromMe) 18.dp else 4.dp,
                        bottomEnd   = if (isFromMe) 4.dp  else 18.dp,
                    )
                )
                .background(if (isFromMe) t.accent else t.surface2)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Column {
                Text(
                    text     = message.text,
                    color    = if (isFromMe) t.btnText else t.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Text(
                    text      = timeStr,
                    color     = if (isFromMe) t.btnText.copy(alpha = 0.65f) else t.muted,
                    fontSize  = 11.sp,
                    modifier  = Modifier.align(Alignment.End).padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onChanged: (String) -> Unit,
    onSend: () -> Unit,
) {
    val t = LocalTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Text field
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(t.surface2)
                .border(1.5.dp, t.surface3, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (value.isEmpty()) {
                Text("Сообщение...", color = t.muted, fontSize = 14.sp)
            }
            BasicTextFieldCompat(
                value     = value,
                onChanged = onChanged,
                onSend    = onSend,
            )
        }

        // Send button
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (value.trim().isNotEmpty()) t.accent else t.surface2)
                .clickable(enabled = value.trim().isNotEmpty(), onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "➤",
                color     = if (value.trim().isNotEmpty()) t.btnText else t.muted,
                fontSize  = 18.sp,
            )
        }
    }
}

// Simple inline text field for chat
@Composable
private fun BasicTextFieldCompat(
    value: String,
    onChanged: (String) -> Unit,
    onSend: () -> Unit,
) {
    val t = LocalTheme.current
    androidx.compose.foundation.text.BasicTextField(
        value         = value,
        onValueChange = onChanged,
        textStyle     = androidx.compose.ui.text.TextStyle(color = t.text, fontSize = 14.sp),
        maxLines      = 4,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Send,
        ),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ADD HOMEWORK DIALOG
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AddHwDialog(
    onConfirm: (subject: String, task: String, deadline: String, urgent: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = LocalTheme.current
    var subject  by remember { mutableStateOf("") }
    var task     by remember { mutableStateOf("") }
    var deadline by remember { mutableStateOf("") }
    var urgent   by remember { mutableStateOf(false) }

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
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
        ) {
            Text(
                "📚 Добавить задание",
                color      = t.text,
                fontSize   = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.padding(bottom = 16.dp),
            )

            AppInput(
                value         = subject,
                onValueChange = { subject = it },
                placeholder   = "Предмет (Математика...)",
                modifier      = Modifier.padding(bottom = 10.dp),
            )
            AppInput(
                value         = task,
                onValueChange = { task = it },
                placeholder   = "Задание",
                singleLine    = false,
                maxLines      = 3,
                modifier      = Modifier.padding(bottom = 10.dp),
            )
            AppInput(
                value         = deadline,
                onValueChange = { deadline = it },
                placeholder   = "Срок (напр. Пятница)",
                modifier      = Modifier.padding(bottom = 12.dp),
            )

            // Urgent toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("🔴 Срочно", color = t.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Выделить как важное", color = t.muted, fontSize = 11.sp)
                }
                ToggleSwitch(checked = urgent, onCheckedChange = { urgent = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppButton(
                    label   = "Отмена",
                    onClick = onDismiss,
                    variant = BtnVariant.Surface,
                    modifier = Modifier.weight(1f),
                )
                AppButton(
                    label    = "Добавить",
                    onClick  = {
                        if (subject.trim().isNotEmpty()) {
                            onConfirm(subject.trim(), task.trim(), deadline.trim(), urgent)
                        }
                    },
                    variant  = BtnVariant.Accent,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UPDATED LEADERBOARD SCREEN (добавлен выбор игры)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun LeaderboardScreen(
    entries: List<LeaderboardEntry>,
    selectedGame: String,
    onGameSelect: (String) -> Unit,
    isLoading: Boolean,
    onBack: () -> Unit,
) {
    val t = LocalTheme.current

    val games = listOf(
        "snake" to "🐍 Змейка",
        "tetris" to "🟦 Тетрис",
        "pong" to "🏓 Понг",
        "dino" to "🦕 Дино",
        "basket" to "🏀 Баскет",
        "geo" to "🌀 ГеоДэш",
    )

    Column(
        modifier = Modifier.fillMaxSize().background(t.bg),
    ) {
        AppHeader(title = "🏆 Лидерборд", onBack = onBack)

        // Game selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            games.forEach { (id, label) ->
                val isActive = selectedGame == id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isActive) t.accent else t.surface2)
                        .border(1.5.dp, if (isActive) t.accent else t.surface3, RoundedCornerShape(20.dp))
                        .clickable { onGameSelect(id) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        label,
                        color      = if (isActive) t.btnText else t.text,
                        fontSize   = 12.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        if (isLoading) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = t.accent, modifier = Modifier.size(32.dp))
            }
        } else if (entries.isEmpty()) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📊", fontSize = 40.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Нет результатов", color = t.muted, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(entries, key = { it.username + it.game }) { entry ->
                    LeaderboardRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LeaderboardRow(entry: LeaderboardEntry) {
    val t = LocalTheme.current
    val rankColor = when (entry.rank) {
        1 -> Color(0xFFF5C518)
        2 -> Color(0xFFB0B0B0)
        3 -> Color(0xFFCD7F32)
        else -> t.muted
    }
    val rankBg = when (entry.rank) {
        1 -> Color(0xFFF5C518).copy(alpha = 0.12f)
        2 -> Color(0xFFB0B0B0).copy(alpha = 0.08f)
        3 -> Color(0xFFCD7F32).copy(alpha = 0.10f)
        else -> t.surface2
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(rankBg)
            .border(1.5.dp, if (entry.rank <= 3) rankColor.copy(alpha = 0.3f) else t.surface3, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Rank
        Text(
            text      = when (entry.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#${entry.rank}" },
            fontSize  = if (entry.rank <= 3) 22.sp else 14.sp,
            color     = rankColor,
            fontWeight = FontWeight.Bold,
            modifier  = Modifier.width(36.dp),
        )
        // Avatar emoji
        Text(entry.avatar, fontSize = 22.sp)
        // Name + username
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.name,
                color = t.text, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                "@${entry.username}",
                color = t.muted, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        // Score
        Text(
            text      = "${entry.score}",
            color     = rankColor,
            fontSize  = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
