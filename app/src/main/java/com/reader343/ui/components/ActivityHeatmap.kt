package com.reader343.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.reader343.ui.theme.spacing
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun ActivityHeatmap(
    values: Map<LocalDate, Int>,
    today: LocalDate,
    weeks: Int,
    modifier: Modifier = Modifier,
    cellSize: Dp = HeatmapDefaults.CellSize,
    cellGap: Dp = HeatmapDefaults.CellGap,
    showLabels: Boolean = true,
    tooltip: (@Composable (date: LocalDate, value: Int) -> Unit)? = null,
) {
    val locale = currentLocale()
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val start = remember(today, weeks, firstDayOfWeek) {
        today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).minusWeeks((weeks - 1).toLong())
    }
    val palette = heatmapPalette()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()

    val cellPx = with(density) { cellSize.toPx() }
    val gapPx = with(density) { cellGap.toPx() }
    val stepPx = cellPx + gapPx
    val labelBandPx = if (showLabels) measurer.measure("M", labelStyle).size.height + gapPx * 2 else 0f
    val gridWidthPx = weeks * stepPx - gapPx
    val gridHeightPx = labelBandPx + DAYS_PER_WEEK * stepPx - gapPx

    val max = remember(values, start, today) {
        values.filterKeys { !it.isBefore(start) && !it.isAfter(today) }.values.maxOrNull() ?: 0
    }

    var selected by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState.maxValue) { scrollState.scrollTo(scrollState.maxValue) }
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) selected = null
    }

    fun columnLeft(column: Int): Float {
        val left = column * stepPx
        return if (rtl) gridWidthPx - left - cellPx else left
    }

    fun cellAt(offset: Offset): LocalDate? {
        val x = if (rtl) gridWidthPx - offset.x else offset.x
        val y = offset.y - labelBandPx
        if (x < 0f || y < 0f) return null
        val column = (x / stepPx).toInt()
        val row = (y / stepPx).toInt()
        if (column >= weeks || row >= DAYS_PER_WEEK) return null
        if (x - column * stepPx > cellPx || y - row * stepPx > cellPx) return null
        val date = start.plusDays(column * DAYS_PER_WEEK.toLong() + row)
        return if (date.isAfter(today)) null else date
    }

    Row(modifier = modifier) {
        if (showLabels) {
            val labels = remember(locale, firstDayOfWeek) {
                (0 until DAYS_PER_WEEK).map { firstDayOfWeek.plus(it.toLong()).getDisplayName(DateTextStyle.SHORT, locale) }
            }
            val measured = labels.map { measurer.measure(it, labelStyle) }
            val labelWidthPx = measured.maxOf { it.size.width }
            Canvas(
                modifier = Modifier
                    .padding(end = cellGap * 2)
                    .size(
                        width = with(density) { labelWidthPx.toDp() },
                        height = with(density) { gridHeightPx.toDp() },
                    ),
            ) {
                measured.forEachIndexed { row, layout ->
                    if (row % 2 == 0) return@forEachIndexed
                    val top = labelBandPx + row * stepPx + (cellPx - layout.size.height) / 2f
                    val left = if (rtl) size.width - layout.size.width else 0f
                    drawText(layout, topLeft = Offset(left, top))
                }
            }
        }
        Box(modifier = Modifier.horizontalScroll(scrollState)) {
            Canvas(
                modifier = Modifier
                    .size(
                        width = with(density) { gridWidthPx.toDp() },
                        height = with(density) { gridHeightPx.toDp() },
                    )
                    .then(
                        if (tooltip != null) {
                            Modifier.pointerInput(start, weeks, rtl, stepPx, labelBandPx) {
                                detectTapGestures { offset ->
                                    val date = cellAt(offset)
                                    selected = if (date == null || date == selected) null else date
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                val radius = CornerRadius(cellPx * CORNER_FRACTION, cellPx * CORNER_FRACTION)
                if (showLabels) {
                    var lastLabelColumn = -MIN_LABEL_GAP
                    for (column in 0 until weeks) {
                        val weekStart = start.plusWeeks(column.toLong())
                        val previous = weekStart.minusWeeks(1)
                        if (column > 0 && weekStart.month == previous.month) continue
                        if (column - lastLabelColumn < MIN_LABEL_GAP) continue
                        val layout = measurer.measure(weekStart.month.getDisplayName(DateTextStyle.SHORT, locale), labelStyle)
                        val left = if (rtl) columnLeft(column) + cellPx - layout.size.width else columnLeft(column)
                        drawText(layout, topLeft = Offset(left.coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f)), 0f))
                        lastLabelColumn = column
                    }
                }
                for (column in 0 until weeks) {
                    for (row in 0 until DAYS_PER_WEEK) {
                        val date = start.plusDays(column * DAYS_PER_WEEK.toLong() + row)
                        if (date.isAfter(today)) break
                        val value = values[date] ?: 0
                        val topLeft = Offset(columnLeft(column), labelBandPx + row * stepPx)
                        drawRoundRect(
                            color = palette.colorFor(value, max),
                            topLeft = topLeft,
                            size = Size(cellPx, cellPx),
                            cornerRadius = radius,
                        )
                        if (date == selected) {
                            val stroke = SELECTION_STROKE.dp.toPx()
                            drawRoundRect(
                                color = palette.selection,
                                topLeft = topLeft + Offset(stroke / 2f, stroke / 2f),
                                size = Size(cellPx - stroke, cellPx - stroke),
                                cornerRadius = radius,
                                style = Stroke(width = stroke),
                            )
                        }
                    }
                }
            }
            val date = selected
            if (tooltip != null && date != null && !date.isBefore(start) && !date.isAfter(today)) {
                val column = ChronoUnit.DAYS.between(start, date).toInt() / DAYS_PER_WEEK
                val row = ChronoUnit.DAYS.between(start, date).toInt() % DAYS_PER_WEEK
                val left = columnLeft(column).roundToInt()
                val top = (labelBandPx + row * stepPx).roundToInt()
                val cell = IntRect(left, top, left + cellPx.roundToInt(), top + cellPx.roundToInt())
                val margin = with(density) { cellGap.roundToPx() * 2 }
                Popup(
                    popupPositionProvider = remember(cell, margin) { CellTooltipPosition(cell, margin) },
                    onDismissRequest = { selected = null },
                    properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
                ) {
                    tooltip(date, values[date] ?: 0)
                }
            }
        }
    }
}

@Composable
fun HeatmapTooltip(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.sm, vertical = MaterialTheme.spacing.xs),
        ) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(text = body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun HeatmapLegend(
    lessLabel: String,
    moreLabel: String,
    modifier: Modifier = Modifier,
    cellSize: Dp = HeatmapDefaults.CellSize,
    cellGap: Dp = HeatmapDefaults.CellGap,
) {
    val palette = heatmapPalette()
    Row(
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(cellGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val labelStyle = MaterialTheme.typography.labelSmall
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        Text(text = lessLabel, style = labelStyle, color = labelColor)
        Spacer(Modifier.size(cellGap))
        palette.levels.forEach { color ->
            Box(
                modifier = Modifier
                    .size(cellSize)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(color),
            )
        }
        Spacer(Modifier.size(cellGap))
        Text(text = moreLabel, style = labelStyle, color = labelColor)
    }
}

object HeatmapDefaults {
    val CellSize = 14.dp
    val CellGap = 3.dp
}

private class HeatmapPalette(
    val levels: List<Color>,
    val selection: Color,
) {
    fun colorFor(value: Int, max: Int): Color {
        if (value <= 0 || max <= 0) return levels.first()
        val steps = levels.lastIndex
        val level = ceil(value.toFloat() / max * steps).toInt().coerceIn(1, steps)
        return levels[level]
    }
}

@Composable
private fun heatmapPalette(): HeatmapPalette {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme.primary, scheme.surfaceContainerHighest, scheme.surfaceContainerLow, scheme.onSurface) {
        HeatmapPalette(
            levels = listOf(scheme.surfaceContainerHighest) +
                LEVEL_ALPHAS.map { scheme.primary.copy(alpha = it).compositeOver(scheme.surfaceContainerLow) },
            selection = scheme.onSurface,
        )
    }
}

private class CellTooltipPosition(
    private val cell: IntRect,
    private val margin: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val centerX = anchorBounds.left + cell.center.x
        val minX = margin * 2
        val maxX = (windowSize.width - popupContentSize.width - margin * 2).coerceAtLeast(minX)
        val x = (centerX - popupContentSize.width / 2).coerceIn(minX, maxX)
        val above = anchorBounds.top + cell.top - margin - popupContentSize.height
        val y = if (above >= 0) above else anchorBounds.top + cell.bottom + margin
        return IntOffset(x, y)
    }
}

private const val DAYS_PER_WEEK = 7
private const val MIN_LABEL_GAP = 3
private const val CORNER_FRACTION = 0.2f
private const val SELECTION_STROKE = 1.5f
private val LEVEL_ALPHAS = listOf(0.3f, 0.55f, 0.8f, 1f)
