package com.reader343.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.reader343.ui.theme.appColors

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.appColors
    val reduced = reducedMotion()
    val duration = if (reduced) 0 else SwitchMs
    val track by animateColorAsState(if (checked) colors.acc else colors.surf2, tween(duration), label = "track")
    val border by animateColorAsState(if (checked) colors.accMid else colors.line2, tween(duration), label = "border")
    val knob by animateColorAsState(if (checked) colors.bg else colors.knob, tween(duration), label = "knob")
    val knobStart by animateDpAsState(
        targetValue = if (checked) KnobOn else KnobOff,
        animationSpec = tween(duration, easing = EmphasizedEasing),
        label = "knobStart",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val toggle = if (onCheckedChange != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .focusRing(interactionSource, CircleShape)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(toggle)
            .disabledAlpha(enabled)
            .size(width = TrackWidth, height = TrackHeight)
            .background(track, CircleShape)
            .border(1.dp, border, CircleShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(knobStart.roundToPx(), 0) }
                .size(KnobSize)
                .background(knob, CircleShape),
        )
    }
}

private val TrackWidth = 54.dp
private val TrackHeight = 32.dp
private val KnobSize = 24.dp
private val KnobOff = 3.dp
private val KnobOn = 26.dp
private const val SwitchMs = 200
