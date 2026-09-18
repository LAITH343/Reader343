package com.reader343.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes

enum class PillTone { Accent, Neutral, Muted, Amber }

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.Neutral,
    @DrawableRes icon: Int? = null,
    height: Dp = PillHeight,
) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.pill
    val style = when (tone) {
        PillTone.Accent -> PillStyle(colors.accTint16, colors.accLine, colors.accTx, FontWeight.SemiBold)
        PillTone.Neutral -> PillStyle(colors.surf2, colors.line2, colors.ink2, FontWeight.Normal)
        PillTone.Muted -> PillStyle(colors.surf2, Color.Transparent, colors.ink3, FontWeight.Normal)
        PillTone.Amber -> PillStyle(colors.amber.fill, colors.amber.border, colors.amber.text, FontWeight.SemiBold)
    }
    Row(
        modifier = modifier
            .height(height)
            .background(style.container, shape)
            .border(1.dp, style.border, shape)
            .padding(horizontal = if (height < PillHeight) 9.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = style.content,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = if (height < PillHeight) 11.sp else 12.sp),
            fontWeight = style.weight,
            color = style.content,
            maxLines = 1,
        )
    }
}

@Immutable
data class ChipColors(val container: Color, val border: Color, val content: Color)

@Composable
fun selectableChipColors(selected: Boolean, plain: Boolean = false): ChipColors {
    val colors = MaterialTheme.appColors
    return if (selected) {
        ChipColors(colors.accTint18, colors.accMid, colors.ink)
    } else {
        ChipColors(if (plain) Color.Transparent else colors.surf2, colors.line2, colors.ink2)
    }
}

@Composable
fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    plain: Boolean = false,
) {
    SelectableSurface(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.appShapes.pill,
        minHeight = FilterChipHeight,
        plain = plain,
        contentPadding = PaddingValues(horizontal = 13.dp),
    ) {
        if (icon != null) {
            Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SelectableSurface(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.appShapes.control,
    minHeight: Dp = 46.dp,
    plain: Boolean = false,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val target = selectableChipColors(selected, plain)
    val reduced = reducedMotion()
    val spec = tween<Color>(if (reduced) 0 else Motion.SHORT_MS)
    val container by animateColorAsState(target.container, spec, label = "chipContainer")
    val border by animateColorAsState(target.border, spec, label = "chipBorder")
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minHeight = minHeight)
            .disabledAlpha(enabled)
            .clip(shape)
            .background(container, shape)
            .border(1.dp, border, shape)
            .focusRing(interactionSource, shape)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides target.content) {
            ProvideTextStyle(MaterialTheme.typography.labelMedium.copy(color = target.content)) {
                content()
            }
        }
    }
}

@Immutable
data class SegmentItem(val label: String, @param:DrawableRes val icon: Int? = null)

@Composable
fun SegmentedControl(
    items: List<SegmentItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.control
    val itemShape = MaterialTheme.appShapes.small
    val reduced = reducedMotion()
    Row(
        modifier = modifier
            .background(colors.bg, shape)
            .border(1.dp, colors.line, shape)
            .padding(SegmentInset)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SegmentInset),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val container by animateColorAsState(
                if (selected) colors.accTint22 else Color.Transparent,
                tween(if (reduced) 0 else Motion.SHORT_MS),
                label = "segment",
            )
            val content = if (selected) colors.ink else colors.ink3
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = SegmentHeight)
                    .clip(itemShape)
                    .background(container, itemShape)
                    .focusRing(interactionSource, itemShape)
                    .selectable(
                        selected = selected,
                        interactionSource = interactionSource,
                        indication = ripple(),
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    if (item.icon != null) {
                        Icon(
                            painter = painterResource(item.icon),
                            contentDescription = null,
                            tint = content,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Immutable
private data class PillStyle(val container: Color, val border: Color, val content: Color, val weight: FontWeight)

val PillHeight = 26.dp
val SmallPillHeight = 24.dp
private val FilterChipHeight = 34.dp
private val SegmentHeight = 44.dp
private val SegmentInset = 4.dp
