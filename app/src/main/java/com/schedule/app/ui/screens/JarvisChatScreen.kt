package com.schedule.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.schedule.app.ui.ChatMessage
import com.schedule.app.ui.theme.LocalTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Экран чата с Джарвисом.
 *
 * Отличия от обычного ChatScreen:
 *  - Кнопка "Протокол Астра" для включения/выключения голосового режима
 *  - Пульсирующий статус (ПК онлайн / офлайн)
 *  - Нет кнопки прикрепить файл (не нужна)
 *  - Индикатор "печатает..." пока Джарвис думает
 *  - Сообщения хранятся в jarvisMessages, не в messages
 */
@Composable
fun JarvisChatScreen(
    messages:      List<ChatMessage>,
    isSending:     Boolean,
    isOnline:      Boolean,
    voiceActive:   Boolean,
    onSend:        (String) -> Unit,
    onToggleVoice: () -> Unit,
    onBack:        () -> Unit,
) {
    val t         = LocalTheme.current
    val listState = rememberLazyListState()
    var input     by remember { mutableStateOf("") }

    // Автоскролл вниз при новых сообщениях
    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(
                if (isSending) messages.size else messages.size - 1
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(t.bg)) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(t.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Назад
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", color = t.accent, fontSize = 28.sp, fontWeight = FontWeight.Light)
                }

                // Аватар Джарвиса — пульсирует если онлайн
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOnline) t.accent.copy(alpha = 0.25f)
                            else t.surface2
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🤖", fontSize = 20.sp)
                }

                // Онлайн-индикатор
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .offset(x = (-10).dp, y = 10.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Джарвис",
                        color = t.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (isOnline) "ПК онлайн" else "ПК офлайн",
                        color = if (isOnline) Color(0xFF4CAF50) else t.muted,
                        fontSize = 11.sp,
                    )
                }

                // Кнопка Протокол Астра / голос
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (voiceActive) t.accent.copy(alpha = 0.2f)
                            else t.surface2
                        )
                        .border(
                            1.dp,
                            if (voiceActive) t.accent else t.surface3,
                            RoundedCornerShape(16.dp),
                        )
                        .clickable(onClick = onToggleVoice)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (voiceActive) "🎙 Вкл" else "🎙 Выкл",
                        color    = if (voiceActive) t.accent else t.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Spacer(Modifier.width(8.dp))
            }

            // ── Список сообщений ──────────────────────────────────────────
            LazyColumn(
                state    = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                // Приветственная плашка если чат пустой
                if (messages.isEmpty() && !isSending) {
                    item {
                        JarvisWelcomeBanner(voiceActive = voiceActive, t = t)
                    }
                }

                items(messages, key = { it.id }) { msg ->
                    JarvisBubble(
                        msg       = msg,
                        fromMe    = msg.fromUser != "__jarvis__",
                        t         = t,
                    )
                }

                // "Печатает..." пока ждём ответа
                if (isSending) {
                    item {
                        JarvisTypingBubble(t = t)
                    }
                }
            }

            // ── Поле ввода ────────────────────────────────────────────────
            JarvisInputBar(
                value     = input,
                isSending = isSending,
                onChanged = { input = it },
                onSend    = {
                    val text = input.trim()
                    if (text.isNotEmpty() && !isSending) {
                        onSend(text)
                        input = ""
                    }
                },
            )
        }
    }
}

// ── Пузырь сообщения ─────────────────────────────────────────────────────────

@Composable
private fun JarvisBubble(
    msg:    ChatMessage,
    fromMe: Boolean,
    t: com.schedule.app.ui.theme.AppTheme,
) {
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = timeFmt.format(Date(msg.ts))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromMe) Arrangement.End else Arrangement.Start,
    ) {
        // Аватар Джарвиса слева
        if (!fromMe) {
            Box(
                modifier = Modifier
                    .padding(end = 6.dp, top = 2.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(t.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🤖", fontSize = 14.sp)
            }
        }

        // Пузырь
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart    = if (fromMe) 18.dp else 4.dp,
                        topEnd      = if (fromMe) 4.dp else 18.dp,
                        bottomStart = 18.dp,
                        bottomEnd   = 18.dp,
                    )
                )
                .background(if (fromMe) t.accent else t.surface)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Column {
                Text(
                    text     = msg.text,
                    color    = if (fromMe) t.btnText else t.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = timeStr,
                    color    = if (fromMe) t.btnText.copy(alpha = 0.6f) else t.muted,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

// ── "Джарвис печатает..." ────────────────────────────────────────────────────

@Composable
private fun JarvisTypingBubble(t: com.schedule.app.ui.theme.AppTheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 6.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(t.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Text("🤖", fontSize = 14.sp) }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp,
                    bottomStart = 18.dp, bottomEnd = 18.dp))
                .background(t.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier  = Modifier.size(12.dp),
                    strokeWidth = 2.dp,
                    color     = t.accent,
                )
                Text("думаю...", color = t.muted, fontSize = 12.sp)
            }
        }
    }
}

// ── Приветственный баннер ────────────────────────────────────────────────────

@Composable
private fun JarvisWelcomeBanner(
    voiceActive: Boolean,
    t: com.schedule.app.ui.theme.AppTheme,
) {
    Column(
        modifier            = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("🤖", fontSize = 56.sp)
        Text(
            "Джарвис",
            color = t.text, fontSize = 22.sp, fontWeight = FontWeight.Bold,
        )
        Text(
            "AI-ассистент с голосом XTTS\nна базе Ollama",
            color = t.muted, fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(t.surface)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                if (voiceActive)
                    "🎙 Голосовой режим активен\nСкажи «Джарвис, [команда]»"
                else
                    "Нажми 🎙 Выкл чтобы активировать\nголосовой режим (Протокол Астра)",
                color     = if (voiceActive) t.accent else t.muted,
                fontSize  = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 18.sp,
            )
        }
    }
}

// ── Поле ввода ───────────────────────────────────────────────────────────────

@Composable
private fun JarvisInputBar(
    value:     String,
    isSending: Boolean,
    onChanged: (String) -> Unit,
    onSend:    () -> Unit,
) {
    val t = LocalTheme.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(t.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalAlignment     = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Поле ввода
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(t.surface2)
                .border(1.5.dp, t.surface3, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (value.isEmpty()) {
                Text("Напиши Джарвису...", color = t.muted, fontSize = 14.sp)
            }
            BasicTextField(
                value         = value,
                onValueChange = onChanged,
                textStyle     = androidx.compose.ui.text.TextStyle(
                    color = t.text, fontSize = 14.sp,
                ),
                maxLines      = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction      = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier      = Modifier.fillMaxWidth(),
                enabled       = !isSending,
            )
        }

        // Кнопка отправки
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isSending                -> t.accent.copy(alpha = 0.4f)
                        value.trim().isNotEmpty() -> t.accent
                        else                     -> t.surface2
                    }
                )
                .clickable(
                    enabled = value.trim().isNotEmpty() && !isSending,
                    onClick = onSend,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSending) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    color       = t.btnText,
                )
            } else {
                Text(
                    "➤",
                    color    = if (value.trim().isNotEmpty()) t.btnText else t.muted,
                    fontSize = 18.sp,
                )
            }
        }
    }
}
