package com.schedule.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Цвета по умолчанию (тема «Колледж») ─────────────────────────────────────
// Точное соответствие CSS :root переменным из index.html

object AppColors {
    // Базовые
    val Bg        = Color(0xFF0D0D0D)
    val Surface   = Color(0xFF161616)
    val Surface2  = Color(0xFF1F1F1F)
    val Surface3  = Color(0xFF2A2A2A)
    val Accent    = Color(0xFFE87722)
    val Accent2   = Color(0xFFC45F0A)
    val Text      = Color(0xFFF0EDE8)
    val Muted     = Color(0xFF6B6762)
    val Success   = Color(0xFF4A9E5C)
    val Danger    = Color(0xFFC94F4F)

    // Утилиты
    val Border       = Color(0x0FFFFFFF)   // rgba(255,255,255,0.06)
    val BorderLight  = Color(0x14FFFFFF)   // rgba(255,255,255,0.08)
    val White10      = Color(0x1AFFFFFF)
    val Black40      = Color(0x66000000)
    val Black60      = Color(0x99000000)

    // Темы — sw (swatch) цвета
    data class ThemeDef(
        val id: String,
        val name: String,
        val icon: String,
        val bg: Color,
        val surface: Color,
        val surface2: Color,
        val surface3: Color,
        val accent: Color,
        val accent2: Color,
        val text: Color,
        val muted: Color,
        val btnText: Color = Color.White,
    )

    val themes = listOf(
        ThemeDef("orange","Колледж","🟠",
            Color(0xFF0D0D0D),Color(0xFF161616),Color(0xFF1F1F1F),Color(0xFF2A2A2A),
            Color(0xFFE87722),Color(0xFFC45F0A),Color(0xFFF0EDE8),Color(0xFF6B6762)),
        ThemeDef("amoled","AMOLED","⚫",
            Color(0xFF000000),Color(0xFF080808),Color(0xFF111111),Color(0xFF1A1A1A),
            Color(0xFF00E5FF),Color(0xFF00ACC1),Color(0xFFFFFFFF),Color(0xFF505050),
            btnText = Color.Black),
        ThemeDef("win11","Win 11","🔵",
            Color(0xFF1A1A1A),Color(0xFF222222),Color(0xFF2D2D2D),Color(0xFF383838),
            Color(0xFF60CDFF),Color(0xFF0067C0),Color(0xFFFFFFFF),Color(0xFF888888),
            btnText = Color.Black),
        ThemeDef("pixel","Material","🟣",
            Color(0xFF191C1E),Color(0xFF22272A),Color(0xFF2C3237),Color(0xFF373D44),
            Color(0xFF7FCFFF),Color(0xFF2F71D4),Color(0xFFE3E5E8),Color(0xFF8E9099),
            btnText = Color.Black),
        ThemeDef("forest","Лес","🌿",
            Color(0xFF0B1A10),Color(0xFF111F15),Color(0xFF182A1D),Color(0xFF213627),
            Color(0xFF4CAF7D),Color(0xFF2D8653),Color(0xFFE0F0E8),Color(0xFF6A9076)),
        ThemeDef("rose","Розовый","🌸",
            Color(0xFF1A0D12),Color(0xFF24111A),Color(0xFF2E1622),Color(0xFF3A1E2D),
            Color(0xFFF472B6),Color(0xFFDB2777),Color(0xFFFCE7F3),Color(0xFF9D6B80)),
        ThemeDef("gold","Золото","✨",
            Color(0xFF100E00),Color(0xFF1A1700),Color(0xFF222000),Color(0xFF2E2A00),
            Color(0xFFF5C518),Color(0xFFC9A000),Color(0xFFFFF9E6),Color(0xFF7A7050),
            btnText = Color.Black),
        ThemeDef("purple","Фиолет","🫐",
            Color(0xFF0E0B1A),Color(0xFF151025),Color(0xFF1C1630),Color(0xFF251E3D),
            Color(0xFFA78BFA),Color(0xFF7C3AED),Color(0xFFEDE9FE),Color(0xFF7060A0)),
        ThemeDef("sunset","Закат","🌅",
            Color(0xFF0F0A00),Color(0xFF1A1000),Color(0xFF241800),Color(0xFF302200),
            Color(0xFFFF6B35),Color(0xFFC94A10),Color(0xFFFFF3EE),Color(0xFF7A5040)),
        ThemeDef("bw","Ч/Б","⬜",
            Color(0xFF000000),Color(0xFF111111),Color(0xFF1C1C1C),Color(0xFF2A2A2A),
            Color(0xFFFFFFFF),Color(0xFFCCCCCC),Color(0xFFFFFFFF),Color(0xFF666666),
            btnText = Color.Black),
        ThemeDef("aero","Aero","💎",
            Color(0xFF152030),Color(0xFF1A2D44),Color(0xFF1F3755),Color(0xFF274466),
            Color(0xFF7FD7FF),Color(0xFF00A8E8),Color(0xFFE8F4FF),Color(0xFF7A9AB8),
            btnText = Color.Black),
        ThemeDef("glass","Liquid Glass","🫧",
            Color(0xFF0A0F1E),Color(0x12FFFFFF),Color(0x1CFFFFFF),Color(0x2EFFFFFF),
            Color(0xFFA0C4FF),Color(0xFF7B9FFF),Color(0xFFF0F4FF),Color(0xB4B4DCFF),
            btnText = Color.Black),
        ThemeDef("ocean","Океан","🌊",
            Color(0xFF000D1A),Color(0xFF001628),Color(0xFF002038),Color(0xFF002D4F),
            Color(0xFF00C8FF),Color(0xFF0099CC),Color(0xFFD0F4FF),Color(0xFF446677),
            btnText = Color.Black),
        ThemeDef("candy","Candy","🍬",
            Color(0xFF0D0018),Color(0xFF150024),Color(0xFF1E0033),Color(0xFF2A0044),
            Color(0xFFFF88CC),Color(0xFFDD44AA),Color(0xFFFFE0F8),Color(0xFF8855AA),
            btnText = Color.Black),
        ThemeDef("samek","Самек","🔥",
            Color(0xFF120008),Color(0xFF1E0010),Color(0xFF2A001A),Color(0xFF380024),
            Color(0xFFFF3366),Color(0xFFCC0044),Color(0xFFFFE0E8),Color(0xFF882244)),
        ThemeDef("light","Светлая","☀️",
            Color(0xFFF4F4F4),Color(0xFFFFFFFF),Color(0xFFEBEBEB),Color(0xFFD8D8D8),
            Color(0xFFE87722),Color(0xFFC45F0A),Color(0xFF111111),Color(0xFF888888)),
    )

    // ── Шрифты ─────────────────────────────────────────────────────────────────
    data class FontDef(val id: String, val name: String, val sub: String, val isSerif: Boolean = false)

    val fonts = listOf(
        FontDef("default",       "По умолчанию",    "Geologica"),
        FontDef("nunito",        "Nunito",           "Мягкий, скруглённый"),
        FontDef("rubik",         "Rubik",            "Современный, геометричный"),
        FontDef("manrope",       "Manrope",          "Строгий, технологичный"),
        FontDef("inter",         "Inter",            "Читаемый, нейтральный"),
        FontDef("montserrat",    "Montserrat",       "Стильный, акцентный"),
        FontDef("unbounded",     "Unbounded",        "Широкий, брутальный"),
        FontDef("space_grotesk", "Space Grotesk",    "Космический, техно"),
        FontDef("comfortaa",     "Comfortaa",        "Дружелюбный, округлый"),
        FontDef("raleway",       "Raleway",          "Элегантный, тонкий"),
        FontDef("oswald",        "Oswald",           "Узкий, газетный"),
        FontDef("caveat",        "Caveat",           "Рукописный, живой"),
        FontDef("fira_code",     "Fira Code",        "Моно, программистский"),
        FontDef("russo_one",     "Russo One",        "Жёсткий, русский гротеск"),
        FontDef("jetbrains_mono","JetBrains Mono",   "Моношрифт, программистский"),
        FontDef("pt_sans",       "PT Sans",          "Классический русский гротеск"),
        FontDef("pt_serif",      "PT Serif",         "Классическая антиква", isSerif = true),
        FontDef("lora",          "Lora",             "Элегантная книжная антиква", isSerif = true),
        FontDef("golos_text",    "Golos Text",       "Современный русский дизайн"),
        FontDef("ubuntu",        "Ubuntu",           "Открытый, Linux-стиль"),
    )

}
