package com.reader343.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AmberColors(
    val bar: Color,
    val fill: Color,
    val border: Color,
    val text: Color,
)

@Immutable
data class AppColors(
    val isDark: Boolean,
    val bg: Color,
    val surf: Color,
    val surf0: Color,
    val surf2: Color,
    val nav: Color,
    val line: Color,
    val line2: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val acc: Color,
    val accLt: Color,
    val accTx: Color,
    val accLine: Color,
    val accMid: Color,
    val heroA: Color,
    val heroB: Color,
    val heroLine: Color,
    val handle: Color,
    val knob: Color,
    val heat2: Color,
    val heat3: Color,
    val danger: Color,
    val amber: AmberColors,
    val scrim: Color,
) {
    val accTint10: Color get() = TintBase.copy(alpha = 0.10f)
    val accTint12: Color get() = TintBase.copy(alpha = 0.12f)
    val accTint16: Color get() = TintBase.copy(alpha = 0.16f)
    val accTint18: Color get() = TintBase.copy(alpha = 0.18f)
    val accTint22: Color get() = TintBase.copy(alpha = 0.22f)
    val accTint28: Color get() = TintBase.copy(alpha = 0.28f)
    val accLtTint12: Color get() = LightTintBase.copy(alpha = 0.12f)
    val accLtTint16: Color get() = LightTintBase.copy(alpha = 0.16f)
    val accLtTint22: Color get() = LightTintBase.copy(alpha = 0.22f)
    val accLtTint30: Color get() = LightTintBase.copy(alpha = 0.30f)
    val heatRamp: List<Color> get() = listOf(surf0, heat2, heat3, accMid, acc)
}

private val TintBase = Color(0xFF9184D9)

private val LightTintBase = Color(0xFFB9AFE8)

val DarkAppColors = AppColors(
    isDark = true,
    bg = Color(0xFF161826),
    surf = Color(0xFF1E2033),
    surf0 = Color(0xFF1B1D2F),
    surf2 = Color(0xFF262943),
    nav = Color(0xFF1A1C2E),
    line = Color(0xFF2A2D45),
    line2 = Color(0xFF2F3350),
    ink = Color(0xFFE9E9ED),
    ink2 = Color(0xFFC4C5DD),
    ink3 = Color(0xFFA8AABF),
    acc = TintBase,
    accLt = Color(0xFFB9AFE8),
    accTx = Color(0xFFCFC8F0),
    accLine = Color(0xFF3A3570),
    accMid = Color(0xFF6F63B8),
    heroA = Color(0xFF2B2758),
    heroB = Color(0xFF1F2140),
    heroLine = Color(0xFF4A438A),
    handle = Color(0xFF4A4D6E),
    knob = Color(0xFF8D90AB),
    heat2 = Color(0xFF332E5E),
    heat3 = Color(0xFF4B4189),
    danger = Color(0xFFE39AA6),
    amber = AmberColors(
        bar = Color(0xFFF6BE48),
        fill = Color(0x24F6BE48),
        border = Color(0xFF5C4A1F),
        text = Color(0xFFF0CB82),
    ),
    scrim = Color(0x99080912),
)

val LightAppColors = AppColors(
    isDark = false,
    bg = Color(0xFFF2F1F7),
    surf = Color(0xFFFBFAFF),
    surf0 = Color(0xFFECEAF4),
    surf2 = Color(0xFFE8E6F1),
    nav = Color(0xFFFBFAFF),
    line = Color(0xFFDCD9E8),
    line2 = Color(0xFFCFCBE0),
    ink = Color(0xFF1C1B24),
    ink2 = Color(0xFF45435A),
    ink3 = Color(0xFF5D5B72),
    acc = Color(0xFF6F63B8),
    accLt = Color(0xFF5A4F9C),
    accTx = Color(0xFF413A78),
    accLine = Color(0xFFC3BCE4),
    accMid = Color(0xFF8578CF),
    heroA = Color(0xFFE4DFF7),
    heroB = Color(0xFFF1EDFC),
    heroLine = Color(0xFFC3BCE4),
    handle = Color(0xFFB8B4C9),
    knob = Color(0xFFFBFAFF),
    heat2 = Color(0xFFCFC7EA),
    heat3 = Color(0xFFA99BDA),
    danger = Color(0xFFA03042),
    amber = AmberColors(
        bar = Color(0xFFD99A1E),
        fill = Color(0x2EF6BE48),
        border = Color(0xFFE2C27A),
        text = Color(0xFF6E4B00),
    ),
    scrim = Color(0x99080912),
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

object PaperSwatch {
    val Normal = Color(0xFFF3F1EA)
    val Night = Color(0xFF22242F)
    val Sepia = Color(0xFFE8D9BD)
}

object PaperInk {
    val Normal = Color(0xFF1B1B1F)
    val Night = Color(0xFFD8D9E4)
    val Sepia = Color(0xFF2F2618)
}

object PaperShell {
    val Normal = Color(0xFF20222F)
    val Sepia = Color(0xFF241F18)
}

object InkColors {
    val Yellow = Color(0xFFF6BE48)
    val Green = Color(0xFF7FC48A)
    val Blue = Color(0xFF7FA7E0)
    val Pink = Color(0xFFE392B8)
    val Orange = Color(0xFFE8A06A)
}
