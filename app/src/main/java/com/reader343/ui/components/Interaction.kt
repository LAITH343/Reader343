package com.reader343.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.reader343.ui.theme.appColors

fun Modifier.focusRing(interactionSource: MutableInteractionSource, shape: Shape): Modifier = composed {
    val focused by interactionSource.collectIsFocusedAsState()
    if (focused) Modifier.border(FocusRingWidth, MaterialTheme.appColors.acc, shape) else Modifier
}

fun Modifier.appClickable(
    shape: Shape,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this
        .clip(shape)
        .focusRing(interactionSource, shape)
        .clickable(
            interactionSource = interactionSource,
            indication = ripple(),
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

fun Modifier.disabledAlpha(enabled: Boolean): Modifier = if (enabled) this else alpha(DisabledAlpha)

private val FocusRingWidth = 2.dp
private const val DisabledAlpha = 0.45f
