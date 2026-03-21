package com.schedule.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.ripple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.schedule.app.ui.theme.LocalTheme

// ─────────────────────────────────────────────────────────────────────────────
// HEADER  (.hdr)
// padding: 14px 18px 12px; border-bottom; background:surface
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Назад",
    actions: @Composable RowScope.() -> Unit = {},
) {
    val t = LocalTheme.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(t.surface)
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack != null) {
                BackButton(label = backLabel, onClick = onBack)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = t.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.01).em,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = t.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            actions()
        }
        // border-bottom: 1px solid rgba(255,255,255,.06)
        Divider(color = Color(0x0FFFFFFF), thickness = 1.dp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BACK BUTTON  (.hdr-back)
// color:accent; font-size:16px; font-weight:600
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun BackButton(label: String = "Назад", onClick: () -> Unit) {
    val t = LocalTheme.current
    Text(
        text = label,
        color = t.accent,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.01).em,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick,
            )
            .padding(end = 4.dp, top = 8.dp, bottom = 8.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION LABEL  (.section-label)
// font-size:10px; font-weight:700; color:muted; letter-spacing:.15em; uppercase
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val t = LocalTheme.current
    Text(
        text = text.uppercase(),
        color = t.muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.15.em,
        modifier = modifier.padding(bottom = 8.dp, top = 2.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SEPARATOR  (.sep)
// height:1px; background:rgba(255,255,255,.06); margin:14px 0
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun Sep(modifier: Modifier = Modifier) {
    Divider(
        color = Color(0x0FFFFFFF),
        thickness = 1.dp,
        modifier = modifier.padding(vertical = 14.dp),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// LIST ITEM  (.list-item)
// background:surface2; border-radius:rb(10px); padding:14px 16px;
// selected: accent bg + glow + accent text
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ListItemRow(
    name: String,
    sub: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val t = LocalTheme.current
    val bgColor = if (selected)
        t.accent.copy(alpha = 0.15f)
    else
        t.surface2
    val borderColor = if (selected)
        t.accent.copy(alpha = 0.70f)
    else
        Color.Transparent
    val glowModifier = if (selected) Modifier.glowEffect(t.accent) else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 7.dp)
            .then(glowModifier)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = if (selected) t.accent else t.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp,
                    style = if (selected) LocalTextStyle.current.copy(
                        shadow = Shadow(
                            color = t.accent.copy(alpha = 0.9f),
                            blurRadius = 8f,
                        )
                    ) else LocalTextStyle.current,
                )
                if (sub != null) {
                    Text(
                        text = sub,
                        color = t.muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else {
                Text(
                    text = "›",
                    color = t.muted,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BUTTON  (.btn)
// width:100%; border-radius:rb(10px); font-size:14px; font-weight:700; padding:15px
// ─────────────────────────────────────────────────────────────────────────────
enum class BtnVariant { Accent, Accent2, Surface, Surface3, Danger, Success }

@Composable
fun AppButton(
    label: String,
    onClick: () -> Unit,
    variant: BtnVariant = BtnVariant.Accent,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    icon: String? = null,
) {
    val t = LocalTheme.current
    val bg = when (variant) {
        BtnVariant.Accent   -> t.accent
        BtnVariant.Accent2  -> t.accent2
        BtnVariant.Surface  -> t.surface2
        BtnVariant.Surface3 -> t.surface3
        BtnVariant.Danger   -> t.danger
        BtnVariant.Success  -> t.success
    }
    val textColor = when (variant) {
        BtnVariant.Surface  -> t.text
        BtnVariant.Surface3 -> t.accent
        else                -> t.btnText
    }
    val shadowColor = when (variant) {
        BtnVariant.Accent  -> t.accent.copy(alpha = 0.35f)
        BtnVariant.Danger  -> t.danger.copy(alpha = 0.25f)
        else               -> Color.Transparent
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .then(if (shadowColor != Color.Transparent) Modifier.glowEffect(shadowColor, spread = 8f) else Modifier),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = textColor,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 15.dp),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = textColor,
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        } else {
            if (icon != null) {
                Text(text = icon, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
            }
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.01.em,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TEXT INPUT  (.inp)
// background:surface2; border:1.5px surface3; border-radius:rb(10px)
// focus: border → accent + glow
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    prefix: String? = null,
) {
    val t = LocalTheme.current
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) t.accent else t.surface3,
        animationSpec = tween(180),
        label = "border",
    )

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = t.muted, fontSize = 14.sp) },
        singleLine = singleLine,
        maxLines = maxLines,
        textStyle = TextStyle(
            color = t.text,
            fontSize = 14.sp,
        ),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor   = t.surface2,
            unfocusedContainerColor = t.surface2,
            focusedBorderColor      = borderColor,
            unfocusedBorderColor    = t.surface3,
            cursorColor             = t.accent,
            focusedTextColor        = t.text,
            unfocusedTextColor      = t.text,
        ),
        leadingIcon = if (prefix != null) ({
            Text(prefix, color = t.muted, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
        }) else null,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SEARCH INPUT  (used in groups/teachers screens)
// background:surface2; border-radius:rb; 🔍 icon left
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Найти...",
    modifier: Modifier = Modifier,
) {
    val t = LocalTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(t.surface2)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🔍", fontSize = 15.sp)
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, color = t.muted, fontSize = 14.sp)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = t.text, fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PROGRESS BAR  (.progress / .progress-bar)
// height:2px; background:surface3; fill:accent; transition width .35s
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val t = LocalTheme.current
    val anim by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "progress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(t.surface3),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(anim)
                .clip(RoundedCornerShape(1.dp))
                .background(t.accent),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOGGLE SWITCH  (.toggle-switch)
// width:44px; height:26px; border-radius:100px
// off: background surface3; on: background accent
// knob: 18x18px white circle; transition left .2s
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ToggleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTheme.current
    val trackColor by animateColorAsState(
        targetValue = if (checked) t.accent else t.surface3,
        animationSpec = tween(200),
        label = "track",
    )
    val thumbX by animateDpAsState(
        targetValue = if (checked) 21.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "thumb",
    )

    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            ),
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbX, top = 3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White)
                .shadow(2.dp, CircleShape),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SETTINGS ROW  (.settings-row)
// background:surface2; border-radius:10px; padding:14px 16px; border:1.5px surface3
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val t = LocalTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(t.surface2)
            .border(1.5.dp, t.surface3, RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = t.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = t.muted, fontSize = 11.sp, lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (trailing != null) trailing()
        else Text("›", color = t.muted, fontSize = 18.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STATUS TEXT  (.status)
// font-size:12px; color:muted; text-align:center; min-height:18px
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun StatusText(text: String, modifier: Modifier = Modifier) {
    val t = LocalTheme.current
    Text(
        text = text,
        color = t.muted,
        fontSize = 12.sp,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 18.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// SKELETON LOADER  (pulse animation — same as CSS @keyframes pulse)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SkeletonItem(modifier: Modifier = Modifier) {
    val t = LocalTheme.current
    val alpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(bottom = 7.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(t.surface2.copy(alpha = alpha)),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// EMPTY STATE
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EmptyState(
    icon: String,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val t = LocalTheme.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(icon, fontSize = 38.sp)
        Text(title, color = t.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(
                subtitle,
                color = t.muted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GLOW EFFECT — Modifier extension
// Реализует box-shadow через drawBehind с blur
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// APP TEXT FIELD  — alias for AppInput, used in dialogs/forms
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = 1,
) {
    AppInput(
        value         = value,
        onValueChange = onValueChange,
        placeholder   = placeholder,
        modifier      = modifier,
        singleLine    = singleLine,
        maxLines      = maxLines,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// EMOJI INPUT  — large single-char input for emoji picker
// background:surface2; border animated to accent on focus; border-radius:12dp
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun EmojiInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "😊",
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
        androidx.compose.foundation.text.BasicTextField(
            value         = value,
            onValueChange = onValueChange,
            textStyle     = TextStyle(color = t.text, fontSize = 22.sp),
            singleLine    = true,
            modifier      = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AVATAR  — shows emoji or photo, clipped to circle
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun AvatarView(
    avatar: String,
    avatarType: String = "emoji",
    avatarData: String? = null,
    size: Dp = 40.dp,
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTheme.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(t.surface2)
            .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarType == "photo" && avatarData != null) {
            // Decode base64 image
            val bitmap = remember(avatarData) {
                try {
                    val clean = if (avatarData.contains(",")) avatarData.substringAfter(",") else avatarData
                    val bytes = android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } catch (e: Exception) { null }
            }
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap      = bitmap.asImageBitmap(),
                    contentDescription = "avatar",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier    = Modifier.fillMaxSize(),
                )
            } else {
                Text(avatar.ifEmpty { "😊" }, fontSize = (size.value * 0.5f).sp)
            }
        } else {
            Text(avatar.ifEmpty { "😊" }, fontSize = (size.value * 0.5f).sp)
        }
    }
}

fun Modifier.glowEffect(
    color: Color,
    borderRadius: Dp = 10.dp,
    spread: Float = 18f,
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.TRANSPARENT
            setShadowLayer(spread, 0f, 0f, color.copy(alpha = 0.45f).hashCode())
        }
        // Android doesn't support blur in drawBehind easily, use simple shadow workaround
        val rect = android.graphics.RectF(
            spread, spread,
            size.width - spread, size.height - spread,
        )
        canvas.nativeCanvas.drawRoundRect(
            rect,
            borderRadius.toPx(),
            borderRadius.toPx(),
            paint,
        )
    }
}
