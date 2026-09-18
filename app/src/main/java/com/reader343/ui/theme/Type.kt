package com.reader343.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val Base = Typography()

private fun TextStyle.serif(): TextStyle = copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold)

private fun TextStyle.sans(): TextStyle = copy(fontFamily = FontFamily.Default)

val Typography = Typography(
    displayLarge = Base.displayLarge.serif(),
    displayMedium = Base.displayMedium.serif(),
    displaySmall = Base.displaySmall.serif(),
    headlineLarge = Base.headlineLarge.serif(),
    headlineMedium = Base.headlineMedium.serif(),
    headlineSmall = Base.headlineSmall.serif(),
    titleLarge = Base.titleLarge.serif(),
    titleMedium = Base.titleMedium.sans(),
    titleSmall = Base.titleSmall.sans(),
    bodyLarge = Base.bodyLarge.sans(),
    bodyMedium = Base.bodyMedium.sans(),
    bodySmall = Base.bodySmall.sans(),
    labelLarge = Base.labelLarge.sans(),
    labelMedium = Base.labelMedium.sans(),
    labelSmall = Base.labelSmall.sans(),
)
