package com.schedule.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.material3.Text
import com.schedule.app.ui.theme.LocalTheme

// ─────────────────────────────────────────────────────────────────────────────
// BOTTOM NAV  (.bottom-nav)
//
// position:fixed; bottom:calc(12px + safe-bot);
// left:50%; transform:translateX(-50%);
// background:surface; border:1px rgba(255,255,255,.08); border-radius:32px;
// width:calc(100%-40px); max-width:390px; padding:5px 6px; height:62px;
// box-shadow:0 8px 32px rgba(0,0,0,.4);
//
// Pill (#nav-pill):
// position:absolute; top:5px; height:52px; border-radius:26px;
// background:color-mix(accent 16%, surface2)
// transition: left/width .3s cubic-bezier(.34,1.2,.64,1)
// ─────────────────────────────────────────────────────────────────────────────

enum class NavTab(val label: String) {
    Home("Главная"),
    Bells("Звонки"),
    Messages("Чаты"),
    Profile("Профиль"),
}

@Composable
fun BottomNavBar(
    activeTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTheme.current
    val tabs = NavTab.values()
    val density = LocalDensity.current

    // Измеряем ширины кнопок для pill
    val tabWidths = remember { mutableStateMapOf<NavTab, Float>() }

    // Анимированная позиция пилюли
    val activeIndex = tabs.indexOf(activeTab)
    val pillX by animateDpAsState(
        targetValue = with(density) {
            val sum = tabs.take(activeIndex).sumOf { (tabWidths[it] ?: 0f).toDouble() }.toFloat()
            (sum + 6f).toDp()   // +6dp = paddingStart pill
        },
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = 380f,
        ),
        label = "pillX",
    )
    val pillWidth by animateDpAsState(
        targetValue = with(density) { ((tabWidths[activeTab] ?: 80f) - 12f).toDp() },
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
        label = "pillW",
    )

    // Тень под nav bar: 0 8px 32px rgba(0,0,0,.4), 0 2px 6px rgba(0,0,0,.2)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 390.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(62.dp)
                // Multi-layer shadow
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = Color.Black.copy(alpha = 0.4f),
                    spotColor = Color.Black.copy(alpha = 0.2f),
                )
                .clip(RoundedCornerShape(32.dp))
                .background(t.surface)
                .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(32.dp)),
        ) {
            // Sliding pill
            Box(
                modifier = Modifier
                    .padding(start = pillX, top = 5.dp)
                    .width(pillWidth)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        // color-mix(accent 16%, surface2)
                        lerp(t.surface2, t.accent, 0.16f)
                    ),
            )

            // Tab items
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                tabs.forEach { tab ->
                    NavTabItem(
                        tab = tab,
                        active = tab == activeTab,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onGloballyPositioned { coords ->
                                tabWidths[tab] = coords.size.width.toFloat()
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTabItem(
    tab: NavTab,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTheme.current
    val color by animateColorAsState(
        targetValue = if (active) t.accent else t.muted,
        animationSpec = tween(200),
        label = "tabColor",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (active) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "iconScale",
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp, horizontal = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(modifier = Modifier.scale(iconScale)) {
            NavIcon(tab = tab, color = color)
        }
        Spacer(modifier = Modifier.height(2.dp))
        androidx.compose.material3.Text(
            text = tab.label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
        )
    }
}

@Composable
private fun NavIcon(tab: NavTab, color: Color) {
    val tint = color
    // Рисуем SVG-эквивалентные иконки через Canvas
    Canvas(modifier = Modifier.size(24.dp)) {
        when (tab) {
            NavTab.Home     -> drawHome(tint)
            NavTab.Bells    -> drawBell(tint)
            NavTab.Messages -> drawMessages(tint)
            NavTab.Profile  -> drawProfile(tint)
        }
    }
}

// ── SVG paths эквивалент через Canvas ────────────────────────────────────────

private fun DrawScope.drawHome(color: Color) {
    val p = android.graphics.Path()
    val w = size.width; val h = size.height
    val s = w / 24f
    // M3 9.5L12 3l9 6.5V20a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V9.5z
    p.moveTo(3*s, 9.5f*s)
    p.lineTo(12*s, 3*s)
    p.lineTo(21*s, 9.5f*s)
    p.lineTo(21*s, 20*s)
    p.lineTo(4*s, 20*s)
    p.close()
    // M9 21V13h6v8
    p.moveTo(9*s, 13*s); p.lineTo(9*s, 21*s)
    p.moveTo(9*s, 13*s); p.lineTo(15*s, 13*s); p.lineTo(15*s, 21*s)

    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.7f * s
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        this.color = color.hashCode()
        alpha = (color.alpha * 255).toInt()
    }
    paint.color = android.graphics.Color.argb(
        (color.alpha * 255).toInt(),
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt(),
    )
    drawContext.canvas.nativeCanvas.drawPath(p, paint)
}

private fun DrawScope.drawBell(color: Color) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.7f * (size.width / 24f)
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        this.color = android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
        )
    }
    val s = size.width / 24f
    val p = android.graphics.Path()
    // Bell shape (simplified arc)
    p.moveTo(6*s, 8*s)
    p.cubicTo(6*s, 4.7f*s, 9f*s, 2f*s, 12*s, 2*s)
    p.cubicTo(15f*s, 2*s, 18*s, 4.7f*s, 18*s, 8*s)
    p.cubicTo(18*s, 15*s, 21*s, 17*s, 21*s, 17*s)
    p.lineTo(3*s, 17*s)
    p.cubicTo(3*s, 17*s, 6*s, 15*s, 6*s, 8*s)
    // clapper
    val p2 = android.graphics.Path()
    p2.moveTo(10.3f*s, 21*s)
    p2.cubicTo(10.3f*s, 21*s, 11*s, 22.5f*s, 12*s, 22.5f*s)
    p2.cubicTo(13*s, 22.5f*s, 13.7f*s, 21*s, 13.7f*s, 21*s)
    drawContext.canvas.nativeCanvas.drawPath(p, paint)
    drawContext.canvas.nativeCanvas.drawPath(p2, paint)
}

private fun DrawScope.drawMessages(color: Color) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.7f * (size.width / 24f)
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        this.color = android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
        )
    }
    val s = size.width / 24f
    val p = android.graphics.Path()
    // M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z
    p.moveTo(21*s, 15*s)
    p.cubicTo(21*s, 16.1f*s, 20.1f*s, 17*s, 19*s, 17*s)
    p.lineTo(7*s, 17*s)
    p.lineTo(3*s, 21*s)
    p.lineTo(3*s, 5*s)
    p.cubicTo(3*s, 3.9f*s, 3.9f*s, 3*s, 5*s, 3*s)
    p.lineTo(19*s, 3*s)
    p.cubicTo(20.1f*s, 3*s, 21*s, 3.9f*s, 21*s, 5*s)
    p.close()
    drawContext.canvas.nativeCanvas.drawPath(p, paint)
}

private fun DrawScope.drawProfile(color: Color) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1.7f * (size.width / 24f)
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        this.color = android.graphics.Color.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
        )
    }
    val s = size.width / 24f
    drawContext.canvas.nativeCanvas.drawCircle(12*s, 8*s, 4*s, paint)
    val p = android.graphics.Path()
    // M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2
    p.moveTo(20*s, 21*s)
    p.lineTo(20*s, 19*s)
    p.cubicTo(20*s, 16.8f*s, 18.2f*s, 15*s, 16*s, 15*s)
    p.lineTo(8*s, 15*s)
    p.cubicTo(5.8f*s, 15*s, 4*s, 16.8f*s, 4*s, 19*s)
    p.lineTo(4*s, 21*s)
    drawContext.canvas.nativeCanvas.drawPath(p, paint)
}

// ─────────────────────────────────────────────────────────────────────────────
// MODE TOGGLE PILL  (.mode-toggle-pill)
// Переключатель Студенты / Педагоги
// background:surface2; border:1.5px surface3; border-radius:100px; padding:3px
// sliding accent pill with shadow
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ModeTogglePill(
    isTeacher: Boolean,
    onModeChange: (isTeacher: Boolean) -> Unit,
    modifier: Modifier = Modifier,
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
        label = "modePill",
    )
    val pillWidth by animateDpAsState(
        targetValue = with(density) { (containerWidth / 2f - 6f).toDp() },
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
        label = "modePillW",
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(t.surface2)
            .border(1.5.dp, t.surface3, CircleShape)
            .padding(3.dp)
            .onGloballyPositioned { containerWidth = it.size.width.toFloat() },
    ) {
        // Sliding pill
        if (containerWidth > 0f) {
            Box(
                modifier = Modifier
                    .padding(start = pillOffsetX)
                    .width(pillWidth)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(t.accent)
                    .shadow(4.dp, CircleShape, ambientColor = t.accent.copy(alpha = 0.45f)),
            )
        }

        // Buttons
        Row {
            listOf("Студенты" to false, "Педагоги" to true).forEach { (label, teacher) ->
                androidx.compose.material3.Text(
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
                            onClick = { onModeChange(teacher) },
                        )
                        .wrapContentSize(Alignment.Center),
                )
            }
        }
    }
}
