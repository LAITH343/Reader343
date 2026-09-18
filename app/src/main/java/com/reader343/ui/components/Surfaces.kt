package com.reader343.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import com.reader343.ui.theme.appType
import com.reader343.ui.theme.spacing
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class CardEmphasis { Default, Highlighted }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    emphasis: CardEmphasis = CardEmphasis.Default,
    shape: Shape = MaterialTheme.appShapes.listCard,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.appColors
    val fill = when (emphasis) {
        CardEmphasis.Default -> Modifier
            .background(colors.surf, shape)
            .border(1.dp, colors.line, shape)
        CardEmphasis.Highlighted -> Modifier.heroFill(shape)
    }
    val interactionSource = remember { MutableInteractionSource() }
    val interactive = if (onClick != null) {
        Modifier
            .clip(shape)
            .focusRing(interactionSource, shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClickLabel = onClickLabel,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
                onClick = onClick,
            )
    } else {
        Modifier
    }
    CompositionLocalProvider(LocalContentColor provides colors.ink) {
        Column(
            modifier = modifier
                .then(fill)
                .clip(shape)
                .then(interactive),
            content = content,
        )
    }
}

@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.appShapes.card,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.appColors.ink) {
        Column(
            modifier = modifier
                .heroFill(shape)
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

@Composable
fun Modifier.heroFill(shape: Shape): Modifier {
    val colors = MaterialTheme.appColors
    val brush = remember(colors.heroA, colors.heroB) { HeroGradient(colors.heroA, colors.heroB) }
    return this
        .background(brush, shape)
        .border(1.dp, colors.accLine, shape)
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(currentLocale()),
        style = MaterialTheme.appType.sectionLabel,
        color = MaterialTheme.appColors.acc,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.appColors.ink,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            GhostButton(text = actionLabel, onClick = onAction)
        }
        trailing?.invoke(this)
    }
}

@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.xl, vertical = MaterialTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.appColors.ink,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.appColors.ink3,
            )
        }
    }
}

@Composable
fun BookProgress(
    percent: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProgressBar(
            progress = percent,
            height = 5.dp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatPercent(percent),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.appColors.ink2,
        )
    }
}

@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    labelColor: Color = LocalContentColor.current,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            maxLines = 2,
        )
    }
}

@Composable
fun QuoteBlock(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    Row(modifier = modifier.height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(QuoteBarWidth)
                .fillMaxHeight()
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.appColors.acc),
        )
        Text(
            text = text,
            style = MaterialTheme.appType.quote,
            color = MaterialTheme.appColors.ink2,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = MaterialTheme.spacing.md),
        )
    }
}

private val QuoteBarWidth = 3.dp

private class HeroGradient(private val start: Color, private val end: Color) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val radians = Math.toRadians(HERO_ANGLE_DEG)
        val dx = sin(radians).toFloat()
        val dy = -cos(radians).toFloat()
        val half = (abs(size.width * dx) + abs(size.height * dy)) / 2f
        val center = size.center
        return LinearGradientShader(
            from = Offset(center.x - dx * half, center.y - dy * half),
            to = Offset(center.x + dx * half, center.y + dy * half),
            colors = listOf(start, end),
        )
    }

    override fun equals(other: Any?): Boolean =
        other is HeroGradient && other.start == start && other.end == end

    override fun hashCode(): Int = 31 * start.hashCode() + end.hashCode()
}

private const val HERO_ANGLE_DEG = 150.0
