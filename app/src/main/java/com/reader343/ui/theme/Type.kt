package com.reader343.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.reader343.R
import java.util.Locale

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val NewsreaderFamily = FontFamily(
    Font(R.font.newsreader_regular, FontWeight.Normal),
    Font(R.font.newsreader_medium, FontWeight.Medium),
)

private val ArabicFamily = FontFamily(
    Font(R.font.noto_naskh_arabic_ui_regular, FontWeight.Normal),
    Font(R.font.noto_naskh_arabic_ui_bold, FontWeight.Bold),
)

@Immutable
data class AppTypography(
    val screenTitle: TextStyle,
    val subScreenTitle: TextStyle,
    val quote: TextStyle,
    val sectionLabel: TextStyle,
    val kicker: TextStyle,
    val caption: TextStyle,
)

private class Fonts(val serif: FontFamily, val sans: FontFamily, val lineHeight: Float)

private val Latin = Fonts(serif = NewsreaderFamily, sans = InterFamily, lineHeight = 1.3f)

private val Arabic = Fonts(serif = ArabicFamily, sans = ArabicFamily, lineHeight = 1.6f)

private fun Fonts.style(size: Int, weight: FontWeight, serif: Boolean = false, tight: Boolean = false) = TextStyle(
    fontFamily = if (serif) this.serif else sans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * if (tight) lineHeight - 0.15f else lineHeight).sp,
    letterSpacing = 0.sp,
)

private fun Fonts.typography() = Typography(
    displayLarge = style(40, FontWeight.Bold, tight = true),
    displayMedium = style(26, FontWeight.Bold, tight = true),
    displaySmall = style(25, FontWeight.Medium, serif = true, tight = true),
    headlineLarge = style(25, FontWeight.Medium, serif = true, tight = true),
    headlineMedium = style(21, FontWeight.Medium, serif = true, tight = true),
    headlineSmall = style(20, FontWeight.SemiBold, tight = true),
    titleLarge = style(18, FontWeight.SemiBold),
    titleMedium = style(17, FontWeight.SemiBold),
    titleSmall = style(15, FontWeight.SemiBold),
    bodyLarge = style(15, FontWeight.Normal),
    bodyMedium = style(14, FontWeight.Normal),
    bodySmall = style(13, FontWeight.Normal),
    labelLarge = style(15, FontWeight.SemiBold),
    labelMedium = style(13, FontWeight.SemiBold),
    labelSmall = style(11, FontWeight.SemiBold),
)

private fun Fonts.appTypography() = AppTypography(
    screenTitle = style(25, FontWeight.Medium, serif = true, tight = true),
    subScreenTitle = style(21, FontWeight.Medium, serif = true, tight = true),
    quote = style(14, FontWeight.Normal, serif = true).copy(lineHeight = (14 * (lineHeight + 0.15f)).sp),
    sectionLabel = style(12, FontWeight.SemiBold).copy(letterSpacing = 0.12.em),
    kicker = style(11, FontWeight.Normal).copy(letterSpacing = 0.14.em),
    caption = style(12, FontWeight.Normal),
)

private val LatinTypography = Latin.typography()
private val ArabicTypography = Arabic.typography()
private val LatinAppTypography = Latin.appTypography()
private val ArabicAppTypography = Arabic.appTypography()

private fun Locale.isArabic() = language == "ar"

fun typographyFor(locale: Locale): Typography = if (locale.isArabic()) ArabicTypography else LatinTypography

fun appTypographyFor(locale: Locale): AppTypography = if (locale.isArabic()) ArabicAppTypography else LatinAppTypography

val LocalAppTypography = staticCompositionLocalOf { LatinAppTypography }

val MaterialTheme.appType: AppTypography
    @Composable
    @ReadOnlyComposable
    get() = LocalAppTypography.current
