package com.reader343.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
data class AppShapes(
    val swatch: Shape = RoundedCornerShape(4.dp),
    val cover: Shape = RoundedCornerShape(8.dp),
    val tile: Shape = RoundedCornerShape(9.dp),
    val small: Shape = RoundedCornerShape(10.dp),
    val iconTile: Shape = RoundedCornerShape(11.dp),
    val item: Shape = RoundedCornerShape(12.dp),
    val control: Shape = RoundedCornerShape(13.dp),
    val button: Shape = RoundedCornerShape(14.dp),
    val stepper: Shape = RoundedCornerShape(16.dp),
    val listCard: Shape = RoundedCornerShape(18.dp),
    val card: Shape = RoundedCornerShape(20.dp),
    val hero: Shape = RoundedCornerShape(22.dp),
    val emblem: Shape = RoundedCornerShape(26.dp),
    val sheet: Shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    val pill: Shape = CircleShape,
)

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

val LocalAppShapes = staticCompositionLocalOf { AppShapes() }

val MaterialTheme.appShapes: AppShapes
    @Composable
    @ReadOnlyComposable
    get() = LocalAppShapes.current
