package com.reader343.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader343.R
import com.reader343.domain.BookStats
import com.reader343.domain.DayStats
import com.reader343.domain.ReadingStats
import com.reader343.ui.components.ActivityHeatmap
import com.reader343.ui.components.AppCard
import com.reader343.ui.components.AppTopBar
import com.reader343.ui.components.BookProgress
import com.reader343.ui.components.CardEmphasis
import com.reader343.ui.components.EmptyState
import com.reader343.ui.components.ErrorState
import com.reader343.ui.components.LoadingState
import com.reader343.ui.components.PrimaryButton
import com.reader343.ui.components.SectionHeader
import com.reader343.ui.components.SegmentItem
import com.reader343.ui.components.SegmentedControl
import com.reader343.ui.components.StatTile
import com.reader343.ui.components.formatDate
import com.reader343.ui.components.formatDecimal
import com.reader343.ui.components.formatDuration
import com.reader343.ui.components.formatMinutes
import com.reader343.ui.components.HeatmapLegend
import com.reader343.ui.components.HeatmapTooltip
import com.reader343.ui.components.formatNumber
import com.reader343.ui.components.formatWeekday
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.spacing
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun StatsRoute(
    onOpenLibrary: () -> Unit,
    onOpenBook: (Long) -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsScreen(
        state = state,
        onOpenLibrary = onOpenLibrary,
        onOpenBook = onOpenBook,
        onMetricSelected = viewModel::onMetricSelected,
        onRetry = viewModel::retry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    state: StatsUiState,
    onOpenLibrary: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onMetricSelected: (ChartMetric) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.stats_title),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (state) {
            StatsUiState.Loading -> LoadingState(contentModifier)
            StatsUiState.Error -> ErrorState(
                message = stringResource(R.string.stats_load_failed),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
                modifier = contentModifier,
            )
            StatsUiState.Empty -> EmptyState(
                icon = R.drawable.ic_ph_chart_bar,
                title = stringResource(R.string.stats_empty),
                body = stringResource(R.string.stats_empty_hint),
                modifier = contentModifier,
                action = {
                    PrimaryButton(text = stringResource(R.string.stats_go_library), onClick = onOpenLibrary)
                },
            )
            is StatsUiState.Content -> StatsContent(
                stats = state.stats,
                metric = state.metric,
                activity = state.activity,
                today = state.today,
                onMetricSelected = onMetricSelected,
                onOpenBook = onOpenBook,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun StatsContent(
    stats: ReadingStats,
    metric: ChartMetric,
    activity: Map<LocalDate, Int>,
    today: LocalDate,
    onMetricSelected: (ChartMetric) -> Unit,
    onOpenBook: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item { OverviewCard(stats) }
        item { MetricSelector(metric = metric, onMetricSelected = onMetricSelected) }
        item { SectionHeader(stringResource(R.string.stats_activity)) }
        item { ActivityCard(activity = activity, today = today, metric = metric) }
        item { SectionHeader(pluralStringResource(R.plurals.stats_last_days, stats.days.size, formatNumber(stats.days.size))) }
        item { ChartCard(days = stats.days, metric = metric) }
        if (stats.books.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.stats_books)) }
            items(stats.books, key = { it.bookId }) { book ->
                BookStatsCard(book = book, onClick = { onOpenBook(book.bookId) })
            }
        }
    }
}

@Composable
private fun OverviewCard(stats: ReadingStats) {
    val tiles = listOf(
        pluralStringResource(R.plurals.stats_days, stats.streakDays, formatNumber(stats.streakDays)) to
            stringResource(R.string.stats_streak),
        formatDuration(stats.totalTimeMs) to stringResource(R.string.stats_total_time),
        formatNumber(stats.booksInProgress) to stringResource(R.string.stats_in_progress),
        formatDuration(stats.avgSessionMs) to stringResource(R.string.stats_avg_session),
        formatDecimal(stats.pagesPerDay) to stringResource(R.string.stats_pages_per_day),
        formatNumber(stats.sessionCount) to stringResource(R.string.stats_sessions),
    )
    AppCard(
        emphasis = CardEmphasis.Highlighted,
        modifier = Modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(Modifier.padding(MaterialTheme.spacing.lg)) {
            val columns = if (maxWidth < WideLayout) 2 else 3
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)) {
                tiles.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
                        row.forEach { (value, label) ->
                            StatTile(value = value, label = label, modifier = Modifier.weight(1f))
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MetricSelector(
    metric: ChartMetric,
    onMetricSelected: (ChartMetric) -> Unit,
) {
    SegmentedControl(
        items = ChartMetric.entries.map { entry ->
            if (entry == ChartMetric.Time) {
                SegmentItem(stringResource(R.string.stats_metric_time), R.drawable.ic_ph_clock)
            } else {
                SegmentItem(stringResource(R.string.stats_metric_pages), R.drawable.ic_ph_book_open_text)
            }
        },
        selectedIndex = metric.ordinal,
        onSelect = { onMetricSelected(ChartMetric.entries[it]) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun metricTotal(metric: ChartMetric, value: Int): String = when (metric) {
    ChartMetric.Time -> stringResource(R.string.stats_chart_time, formatMinutes(value * MINUTE_MS))
    ChartMetric.Pages -> pluralStringResource(R.plurals.stats_chart_pages, value, formatNumber(value))
}

@Composable
private fun ActivityCard(
    activity: Map<LocalDate, Int>,
    today: LocalDate,
    metric: ChartMetric,
) {
    val from = today.minusWeeks(HEATMAP_WEEKS.toLong())
    val visible = activity.filterKeys { it.isAfter(from) && !it.isAfter(today) }
    val activeDays = visible.size
    val summary = stringResource(
        R.string.stats_activity_summary,
        pluralStringResource(R.plurals.stats_active_days, activeDays, formatNumber(activeDays)),
        metricTotal(metric, visible.values.sum()),
    )
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ActivityHeatmap(
                values = activity,
                today = today,
                weeks = HEATMAP_WEEKS,
                tooltip = { date, value ->
                    HeatmapTooltip(
                        title = formatDate(date),
                        body = if (value > 0) metricTotal(metric, value) else stringResource(R.string.stats_no_reading),
                    )
                },
            )
            HeatmapLegend(
                lessLabel = stringResource(R.string.stats_less),
                moreLabel = stringResource(R.string.stats_more),
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun ChartCard(
    days: List<DayStats>,
    metric: ChartMetric,
) {
    val values = days.map { if (metric == ChartMetric.Time) it.timeMs.toFloat() else it.pages.toFloat() }
    val summary = when (metric) {
        ChartMetric.Time -> stringResource(R.string.stats_chart_time, formatDuration(days.sumOf { it.timeMs }))
        ChartMetric.Pages -> {
            val pages = days.sumOf { it.pages }
            pluralStringResource(R.plurals.stats_chart_pages, pages, formatNumber(pages))
        }
    }
    val dayDescriptions = days.map { day ->
        stringResource(
            R.string.stats_chart_day,
            formatDate(day.date),
            when (metric) {
                ChartMetric.Time -> formatDuration(day.timeMs)
                ChartMetric.Pages -> pluralStringResource(R.plurals.stats_pages_value, day.pages, formatNumber(day.pages))
            },
        )
    }
    val chartDescription = (listOf(summary) + dayDescriptions).joinToString(separator = "\n")
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier.clearAndSetSemantics { contentDescription = chartDescription },
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            ) {
                BarChart(
                    values = values,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ChartHeight),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    days.forEachIndexed { index, day ->
                        val today = index == days.lastIndex
                        Text(
                            text = formatWeekday(day.date, TextStyle.NARROW_STANDALONE),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                            color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val todayColor = MaterialTheme.colorScheme.tertiary
    val emptyColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val max = values.max().coerceAtLeast(1f)
        val slot = size.width / values.size
        val barWidth = slot * BAR_FILL
        val radius = CornerRadius(barWidth / 4f, barWidth / 4f)
        val stub = 2.dp.toPx()
        val rtl = layoutDirection == LayoutDirection.Rtl
        values.forEachIndexed { index, value ->
            val slotIndex = if (rtl) values.lastIndex - index else index
            val left = slotIndex * slot + (slot - barWidth) / 2f
            val height = if (value > 0f) (value / max * size.height).coerceAtLeast(stub * 2f) else stub
            val color = when {
                value <= 0f -> emptyColor
                index == values.lastIndex -> todayColor
                else -> barColor
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(left, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun BookStatsCard(book: BookStats, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        onClickLabel = stringResource(R.string.stats_open_book),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BookProgress(percent = book.percent)
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                StatTile(
                    value = formatDuration(book.totalTimeMs),
                    label = stringResource(R.string.stats_total_time),
                    compact = true,
                    labelColor = labelColor,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = formatNumber(book.sessionCount),
                    label = stringResource(R.string.stats_sessions),
                    compact = true,
                    labelColor = labelColor,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = formatDuration(book.avgSessionMs),
                    label = stringResource(R.string.stats_avg_session),
                    compact = true,
                    labelColor = labelColor,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = formatNumber(book.pagesRead),
                    label = stringResource(R.string.stats_metric_pages),
                    compact = true,
                    labelColor = labelColor,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val WideLayout = 480.dp
private val ChartHeight = 140.dp
private const val BAR_FILL = 0.6f
private const val HEATMAP_WEEKS = 26
private const val MINUTE_MS = 60_000L

@Preview(showBackground = true)
@Composable
private fun StatsScreenPreview() {
    val today = LocalDate.of(2026, 9, 18)
    val days = (13 downTo 0).map { offset ->
        val active = offset % 3 != 1
        DayStats(
            date = today.minusDays(offset.toLong()),
            timeMs = if (active) (offset + 2) * 180_000L else 0L,
            pages = if (active) offset + 3 else 0,
        )
    }
    Reader343Theme {
        StatsScreen(
            state = StatsUiState.Content(
                stats = ReadingStats(
                    streakDays = 2,
                    totalTimeMs = 15_300_000L,
                    booksInProgress = 2,
                    sessionCount = 14,
                    avgSessionMs = 1_092_000L,
                    pagesPerDay = 8.4f,
                    days = days,
                    books = listOf(
                        BookStats(1, "Designing Data-Intensive Applications", 9_000_000L, 9, 1_000_000L, 112, 0.42f),
                        BookStats(2, "The Pragmatic Programmer", 6_300_000L, 5, 1_260_000L, 64, 0.18f),
                    ),
                ),
                metric = ChartMetric.Time,
                activity = (0L..180L).filter { it % 4 != 1L }.associate { today.minusDays(it) to (it * 7 % 50).toInt() + 1 },
                today = today,
            ),
            onOpenLibrary = {},
            onOpenBook = {},
            onMetricSelected = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatsScreenEmptyPreview() {
    Reader343Theme {
        StatsScreen(state = StatsUiState.Empty, onOpenLibrary = {}, onOpenBook = {}, onMetricSelected = {}, onRetry = {})
    }
}
