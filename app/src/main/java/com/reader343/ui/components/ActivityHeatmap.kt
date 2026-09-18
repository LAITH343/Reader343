package com.reader343.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import com.reader343.ui.theme.spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
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
    cellDescription: (@Composable (date: LocalDate, value: Int) -> String)? = null,
    tooltip: (@Composable (date: LocalDate, value: Int) -> Unit)? = null,
) {
    val locale = currentLocale()
    val start = remember(today, weeks) {
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks((weeks - 1).toLong())
    }
    val palette = heatmapPalette()
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val cellShape = MaterialTheme.appShapes.swatch

    val cellPx = with(density) { cellSize.toPx() }
    val gapPx = with(density) { cellGap.toPx() }
    val stepPx = cellPx + gapPx
    val gridWidthPx = weeks * stepPx - gapPx

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
        val y = offset.y
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
            val labels = remember(locale) {
                (0 until DAYS_PER_WEEK).map { row ->
                    if (row % 2 == 0) DayOfWeek.MONDAY.plus(row.toLong()).getDisplayName(DateTextStyle.NARROW_STANDALONE, locale) else ""
                }
            }
            Column(
                modifier = Modifier
                    .padding(end = HeatmapDefaults.LabelGap)
                    .clearAndSetSemantics {},
                verticalArrangement = Arrangement.spacedBy(cellGap),
            ) {
                labels.forEach { label ->
                    Box(modifier = Modifier.height(cellSize), contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 9.sp),
                            color = MaterialTheme.appColors.ink3,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.horizontalScroll(scrollState)) {
            Row(
                modifier = if (tooltip != null) {
                    Modifier.pointerInput(start, weeks, rtl, stepPx) {
                        detectTapGestures { offset ->
                            val date = cellAt(offset)
                            selected = if (date == null || date == selected) null else date
                        }
                    }
                } else {
                    Modifier
                },
                horizontalArrangement = Arrangement.spacedBy(cellGap),
            ) {
                for (column in 0 until weeks) {
                    Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                        for (row in 0 until DAYS_PER_WEEK) {
                            val date = start.plusDays(column * DAYS_PER_WEEK.toLong() + row)
                            if (date.isAfter(today)) {
                                Spacer(Modifier.size(cellSize))
                            } else {
                                val value = values[date] ?: 0
                                val level = palette.levelFor(value, max)
                                val chosen = date == selected
                                val description = cellDescription?.invoke(date, value)
                                Box(
                                    modifier = Modifier
                                        .size(cellSize)
                                        .background(palette.levels[level], cellShape)
                                        .border(
                                            width = if (chosen) HeatmapDefaults.SelectionStroke else 1.dp,
                                            color = when {
                                                chosen -> palette.selection
                                                level == 0 -> palette.emptyBorder
                                                else -> Color.Transparent
                                            },
                                            shape = cellShape,
                                        )
                                        .then(
                                            if (description != null) {
                                                Modifier.semantics { contentDescription = description }
                                            } else {
                                                Modifier
                                            },
                                        ),
                                )
                            }
                        }
                    }
                }
            }
            val date = selected
            if (tooltip != null && date != null && !date.isBefore(start) && !date.isAfter(today)) {
                val offset = ChronoUnit.DAYS.between(start, date).toInt()
                val left = columnLeft(offset / DAYS_PER_WEEK).roundToInt()
                val top = ((offset % DAYS_PER_WEEK) * stepPx).roundToInt()
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
    cellSize: Dp = HeatmapDefaults.LegendCellSize,
) {
    val palette = heatmapPalette()
    val shape = MaterialTheme.appShapes.swatch
    Row(
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
        val labelColor = MaterialTheme.appColors.ink3
        Text(text = lessLabel, style = labelStyle, color = labelColor)
        palette.levels.forEach { color ->
            Box(
                modifier = Modifier
                    .size(cellSize)
                    .background(color, shape)
                    .border(1.dp, palette.legendBorder, shape),
            )
        }
        Text(text = moreLabel, style = labelStyle, color = labelColor)
    }
}

object HeatmapDefaults {
    val CellSize = 13.dp
    val CellGap = 3.dp
    val LegendCellSize = 12.dp
    val LabelGap = 6.dp
    val SelectionStroke = 1.5.dp
}

private class HeatmapPalette(
    val levels: List<Color>,
    val selection: Color,
    val emptyBorder: Color,
    val legendBorder: Color,
) {
    fun levelFor(value: Int, max: Int): Int {
        if (value <= 0 || max <= 0) return 0
        val steps = levels.lastIndex
        return ceil(value.toFloat() / max * steps).toInt().coerceIn(1, steps)
    }
}

@Composable
private fun heatmapPalette(): HeatmapPalette {
    val colors = MaterialTheme.appColors
    return remember(colors) {
        HeatmapPalette(
            levels = colors.heatRamp,
            selection = colors.ink,
            emptyBorder = colors.line,
            legendBorder = colors.line2,
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
