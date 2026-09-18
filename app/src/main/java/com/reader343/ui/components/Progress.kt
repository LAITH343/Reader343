package com.reader343.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.reader343.ui.theme.appColors

val EmphasizedEasing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = MaterialTheme.appColors.acc,
    trackColor: Color = MaterialTheme.appColors.line2,
    animate: Boolean = true,
) {
    val target = progress.coerceIn(0f, 1f)
    val reduced = reducedMotion()
    val fill = remember { Animatable(if (animate && !reduced) 0f else target) }
    LaunchedEffect(target, reduced) {
        if (animate && !reduced) fill.animateTo(target, tween(FillBarMs, easing = EmphasizedEasing)) else fill.snapTo(target)
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) },
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        val width = size.width * fill.value
        if (width > 0f) {
            drawRoundRect(
                color = color,
                topLeft = Offset(if (rtl) size.width - width else 0f, 0f),
                size = Size(width, size.height),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
fun GoalRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 74.dp,
    strokeWidth: Dp = 7.dp,
    pulse: Boolean = false,
    color: Color = MaterialTheme.appColors.acc,
    trackColor: Color = MaterialTheme.appColors.line2,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val target = progress.coerceIn(0f, 1f)
    val reduced = reducedMotion()
    val sweep = remember { Animatable(if (reduced) target else 0f) }
    LaunchedEffect(target, reduced) {
        if (reduced) sweep.snapTo(target) else sweep.animateTo(target, tween(RingMs, easing = EmphasizedEasing))
    }
    val ringPulse by rememberPulse(enabled = pulse && !reduced, periodMs = RingPulseMs)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val outset = (size + PulseOutset * 2) / size
    Box(
        modifier = modifier
            .size(size)
            .semantics(mergeDescendants = true) { progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f) },
        contentAlignment = Alignment.Center,
    ) {
        if (pulse) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val scale = ringPulse.scale * outset
                        scaleX = scale
                        scaleY = scale
                        alpha = ringPulse.alpha
                    }
                    .border(1.dp, color, CircleShape),
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            val degrees = 360f * sweep.value
            if (degrees > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = if (rtl) -degrees else degrees,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

private const val FillBarMs = 700
private const val RingMs = 800
private const val RingPulseMs = 2800
private val PulseOutset = 4.dp
