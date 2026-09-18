package com.reader343.ui.components

import android.provider.Settings
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun reducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

object Motion {
    const val SHORT_MS = 150
    const val MEDIUM_MS = 250

    fun fadeEnter(reduced: Boolean): EnterTransition =
        if (reduced) EnterTransition.None else fadeIn(tween(MEDIUM_MS))

    fun fadeExit(reduced: Boolean): ExitTransition =
        if (reduced) ExitTransition.None else fadeOut(tween(SHORT_MS))

    fun slideFromEdge(reduced: Boolean, fromTop: Boolean): EnterTransition =
        if (reduced) {
            EnterTransition.None
        } else {
            fadeIn(tween(MEDIUM_MS)) + slideInVertically(tween(MEDIUM_MS)) { if (fromTop) -it else it }
        }

    fun slideToEdge(reduced: Boolean, toTop: Boolean): ExitTransition =
        if (reduced) {
            ExitTransition.None
        } else {
            fadeOut(tween(SHORT_MS)) + slideOutVertically(tween(SHORT_MS)) { if (toTop) -it else it }
        }
}

fun Modifier.riseIn(delayMs: Int = 0): Modifier = composed {
    val reduced = reducedMotion()
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(1f, tween(RISE_MS, delayMillis = delayMs, easing = FastOutSlowInEasing))
        }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * RiseOffset.toPx()
    }
}

@Immutable
data class Pulse(val alpha: Float, val scale: Float)

@Composable
fun rememberPulse(enabled: Boolean, periodMs: Int): State<Pulse> {
    val transition = rememberInfiniteTransition(label = "pulse")
    val spec = infiniteRepeatable<Float>(tween(periodMs / 2, easing = FastOutSlowInEasing), RepeatMode.Reverse)
    val alpha = transition.animateFloat(PULSE_ALPHA_MAX, PULSE_ALPHA_MIN, spec, label = "pulseAlpha")
    val scale = transition.animateFloat(1f, PULSE_SCALE_MAX, spec, label = "pulseScale")
    val still = remember { mutableStateOf(Pulse(PULSE_ALPHA_MAX, 1f)) }
    return if (enabled) remember(alpha, scale) { derivedStateOf { Pulse(alpha.value, scale.value) } } else still
}

private const val RISE_MS = 340
private val RiseOffset = 10.dp
private const val PULSE_ALPHA_MAX = 0.55f
private const val PULSE_ALPHA_MIN = 0.15f
private const val PULSE_SCALE_MAX = 1.12f
