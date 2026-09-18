package com.reader343.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    compact: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val colors = MaterialTheme.appColors
    ButtonFrame(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        contentPadding = contentPadding,
        container = if (compact) colors.accTint12 else colors.accLtTint12,
        border = BorderStroke(1.dp, if (compact) colors.acc else colors.accLt),
        contentColor = colors.ink,
        minHeight = if (compact) CompactHeight else ButtonHeight,
        textStyle = if (compact) MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp) else MaterialTheme.typography.labelLarge,
    ) {
        ButtonContent(text = text, icon = icon, iconSize = if (compact) 16.dp else ButtonIconSize)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.appColors
    ButtonFrame(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        container = Color.Transparent,
        border = BorderStroke(1.dp, colors.line2),
        contentColor = colors.ink2,
        minHeight = ButtonHeight,
        textStyle = MaterialTheme.typography.labelLarge,
    ) {
        ButtonContent(text = text, icon = icon, iconSize = ButtonIconSize)
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.appColors
    ButtonFrame(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        container = Color.Transparent,
        border = null,
        contentColor = if (destructive) colors.danger else colors.accLt,
        minHeight = GhostHeight,
        textStyle = MaterialTheme.typography.labelMedium,
        shape = MaterialTheme.appShapes.small,
        contentPadding = PaddingValues(horizontal = 10.dp),
    ) {
        ButtonContent(text = text, icon = icon, iconSize = 16.dp)
    }
}

@Composable
fun IconTextButton(
    @DrawableRes icon: Int,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    GhostButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        destructive = destructive,
    )
}

@Composable
fun DestructiveTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GhostButton(text = text, onClick = onClick, modifier = modifier, destructive = true)
}

enum class IconButtonTone { Neutral, Accent, Plain }

@Composable
fun IconBadgeButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: IconButtonTone = IconButtonTone.Neutral,
    badge: Boolean = false,
    badgePulse: Boolean = true,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.button
    val (container, border, content) = when (tone) {
        IconButtonTone.Neutral -> Triple(colors.surf, colors.line2, colors.ink)
        IconButtonTone.Accent -> Triple(colors.accTint16, colors.accLine, colors.accTx)
        IconButtonTone.Plain -> Triple(Color.Transparent, Color.Transparent, colors.ink)
    }
    Box(
        modifier = modifier
            .size(IconButtonSize)
            .disabledAlpha(enabled)
            .background(container, shape)
            .border(1.dp, border, shape)
            .appClickable(shape = shape, enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp),
        )
        if (badge) {
            BadgeDot(
                pulse = badgePulse,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 7.dp),
            )
        }
    }
}

@Composable
fun BadgeDot(modifier: Modifier = Modifier, pulse: Boolean = true) {
    val colors = MaterialTheme.appColors
    val animated by rememberPulse(enabled = pulse && !reducedMotion(), periodMs = BadgePulseMs)
    Box(
        modifier = modifier
            .size(BadgeSize)
            .graphicsLayer {
                scaleX = animated.scale
                scaleY = animated.scale
                alpha = if (pulse) animated.alpha else 1f
            }
            .background(colors.bg, CircleShape)
            .padding(2.dp)
            .background(colors.acc, CircleShape),
    )
}

@Composable
private fun ButtonFrame(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    container: Color,
    border: BorderStroke?,
    contentColor: Color,
    minHeight: Dp,
    textStyle: TextStyle,
    shape: Shape = MaterialTheme.appShapes.button,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = minHeight)
            .disabledAlpha(enabled)
            .background(container, shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .appClickable(shape = shape, enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(textStyle.copy(color = contentColor)) {
                content()
            }
        }
    }
}

@Composable
private fun ButtonContent(text: String, @DrawableRes icon: Int?, iconSize: Dp) {
    if (icon != null) {
        Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(iconSize))
    }
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private val ButtonHeight = 48.dp
private val CompactHeight = 44.dp
private val GhostHeight = 32.dp
private val ButtonIconSize = 18.dp
private val IconButtonSize = 44.dp
private val BadgeSize = 9.dp
private const val BadgePulseMs = 2400
