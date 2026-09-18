package com.reader343.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration

private fun AppColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = acc,
        onPrimary = bg,
        primaryContainer = accTint18.compositeOver(surf),
        onPrimaryContainer = accTx,
        inversePrimary = accLt,
        secondary = ink2,
        onSecondary = bg,
        secondaryContainer = surf2,
        onSecondaryContainer = ink,
        tertiary = amber.bar,
        onTertiary = DarkAppColors.bg,
        tertiaryContainer = amber.fill.compositeOver(surf),
        onTertiaryContainer = amber.text,
        background = bg,
        onBackground = ink,
        surface = surf,
        onSurface = ink,
        surfaceVariant = surf2,
        onSurfaceVariant = ink3,
        surfaceTint = surf,
        inverseSurface = ink,
        inverseOnSurface = bg,
        error = danger,
        onError = bg,
        errorContainer = danger.copy(alpha = 0.14f).compositeOver(surf),
        onErrorContainer = danger,
        outline = line2,
        outlineVariant = line,
        scrim = scrim,
        surfaceBright = surf2,
        surfaceDim = bg,
        surfaceContainerLowest = bg,
        surfaceContainerLow = surf0,
        surfaceContainer = surf0,
        surfaceContainerHigh = surf2,
        surfaceContainerHighest = surf2,
    )
}

private val DarkScheme = DarkAppColors.toColorScheme()
private val LightScheme = LightAppColors.toColorScheme()

@Composable
fun Reader343Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkAppColors else LightAppColors
    val locale = LocalConfiguration.current.locales[0]

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalAppColors provides colors,
        LocalAppShapes provides AppShapes(),
        LocalAppTypography provides appTypographyFor(locale),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = typographyFor(locale),
            shapes = Shapes,
            content = content,
        )
    }
}

val MaterialTheme.appColors: AppColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current
