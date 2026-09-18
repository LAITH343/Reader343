package com.reader343.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.reader343.R
import java.util.Locale

private val Base = Typography()

private val ArabicFamily = FontFamily(
    Font(R.font.noto_naskh_arabic_ui_regular, FontWeight.Normal),
    Font(R.font.noto_naskh_arabic_ui_bold, FontWeight.Bold),
)

private fun TextStyle.display(family: FontFamily): TextStyle = copy(fontFamily = family, fontWeight = FontWeight.SemiBold)

private fun TextStyle.body(family: FontFamily): TextStyle = copy(fontFamily = family)

private fun typography(display: FontFamily, body: FontFamily) = Typography(
    displayLarge = Base.displayLarge.display(display),
    displayMedium = Base.displayMedium.display(display),
    displaySmall = Base.displaySmall.display(display),
    headlineLarge = Base.headlineLarge.display(display),
    headlineMedium = Base.headlineMedium.display(display),
    headlineSmall = Base.headlineSmall.display(display),
    titleLarge = Base.titleLarge.display(display),
    titleMedium = Base.titleMedium.body(body),
    titleSmall = Base.titleSmall.body(body),
    bodyLarge = Base.bodyLarge.body(body),
    bodyMedium = Base.bodyMedium.body(body),
    bodySmall = Base.bodySmall.body(body),
    labelLarge = Base.labelLarge.body(body),
    labelMedium = Base.labelMedium.body(body),
    labelSmall = Base.labelSmall.body(body),
)

private val LatinTypography = typography(display = FontFamily.Serif, body = FontFamily.Default)

private val ArabicTypography = typography(display = ArabicFamily, body = ArabicFamily)

fun typographyFor(locale: Locale): Typography =
    if (locale.language == "ar") ArabicTypography else LatinTypography
