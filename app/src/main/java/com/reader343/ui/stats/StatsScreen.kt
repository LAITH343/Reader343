package com.reader343.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader343.R
import com.reader343.domain.BookStats
import com.reader343.domain.DayStats
import com.reader343.domain.ReadingStats
import com.reader343.ui.theme.Reader343Theme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun StatsRoute(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    StatsScreen(
        state = state,
        onBack = onBack,
        onMetricSelected = viewModel::onMetricSelected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    state: StatsUiState,
    onBack: () -> Unit,
    onMetricSelected: (ChartMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state) {
                StatsUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                StatsUiState.Empty -> EmptyStats(Modifier.align(Alignment.Center))
                is StatsUiState.Content -> StatsContent(
                    stats = state.stats,
                    metric = state.metric,
                    onMetricSelected = onMetricSelected,
                )
            }
        }
    }
}

@Composable
private fun EmptyStats(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.stats_empty),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.stats_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StatsContent(
    stats: ReadingStats,
    metric: ChartMetric,
    onMetricSelected: (ChartMetric) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { OverviewCard(stats) }
        item {
            ChartCard(
                days = stats.days,
                metric = metric,
                onMetricSelected = onMetricSelected,
            )
        }
        if (stats.books.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.stats_books),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(stats.books, key = { it.bookId }) { BookStatsCard(it) }
        }
    }
}

@Composable
private fun OverviewCard(stats: ReadingStats) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    value = pluralStringResource(R.plurals.stats_days, stats.streakDays, stats.streakDays),
                    label = stringResource(R.string.stats_streak),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = formatDuration(stats.totalTimeMs),
                    label = stringResource(R.string.stats_total_time),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = stats.booksInProgress.toString(),
                    label = stringResource(R.string.stats_in_progress),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    value = formatDuration(stats.avgSessionMs),
                    label = stringResource(R.string.stats_avg_session),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = formatDecimal(stats.pagesPerDay),
                    label = stringResource(R.string.stats_pages_per_day),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = stats.sessionCount.toString(),
                    label = stringResource(R.string.stats_sessions),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartCard(
    days: List<DayStats>,
    metric: ChartMetric,
    onMetricSelected: (ChartMetric) -> Unit,
) {
    val values = days.map { if (metric == ChartMetric.Time) it.timeMs.toFloat() else it.pages.toFloat() }
    val summary = when (metric) {
        ChartMetric.Time -> stringResource(R.string.stats_chart_time, formatDuration(days.sumOf { it.timeMs }))
        ChartMetric.Pages -> {
            val pages = days.sumOf { it.pages }
            pluralStringResource(R.plurals.stats_chart_pages, pages, pages)
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.stats_last_days),
                style = MaterialTheme.typography.titleMedium,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ChartMetric.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = entry == metric,
                        onClick = { onMetricSelected(entry) },
                        shape = SegmentedButtonDefaults.itemShape(index, ChartMetric.entries.size),
                    ) {
                        Text(
                            stringResource(
                                if (entry == ChartMetric.Time) R.string.stats_metric_time else R.string.stats_metric_pages,
                            ),
                        )
                    }
                }
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BarChart(
                values = values,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                days.forEachIndexed { index, day ->
                    val today = index == days.lastIndex
                    Text(
                        text = day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                        color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
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
    val emptyColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val max = values.max().coerceAtLeast(1f)
        val slot = size.width / values.size
        val barWidth = slot * 0.6f
        val radius = CornerRadius(barWidth / 4f, barWidth / 4f)
        val stub = 2.dp.toPx()
        values.forEachIndexed { index, value ->
            val left = index * slot + (slot - barWidth) / 2f
            val height = if (value > 0f) (value / max * size.height).coerceAtLeast(stub * 2f) else stub
            drawRoundRect(
                color = if (value > 0f) barColor else emptyColor,
                topLeft = Offset(left, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun BookStatsCard(book: BookStats) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.library_percent, (book.percent * 100).roundToInt()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            LinearProgressIndicator(
                progress = { book.percent.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(
                    R.string.stats_book_detail,
                    formatDuration(book.totalTimeMs),
                    pluralStringResource(R.plurals.stats_sessions_count, book.sessionCount, book.sessionCount),
                    formatDuration(book.avgSessionMs),
                    pluralStringResource(R.plurals.stats_pages_count, book.pagesRead, book.pagesRead),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000L
    val hours = minutes / 60L
    return when {
        hours > 0L -> stringResource(R.string.duration_hours_minutes, hours, minutes % 60L)
        minutes > 0L -> stringResource(R.string.duration_minutes, minutes)
        else -> stringResource(R.string.duration_seconds, ms / 1_000L)
    }
}

private fun formatDecimal(value: Float): String =
    if (value >= 10f || value == value.roundToInt().toFloat()) {
        value.roundToInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }

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
            ),
            onBack = {},
            onMetricSelected = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StatsScreenEmptyPreview() {
    Reader343Theme {
        StatsScreen(state = StatsUiState.Empty, onBack = {}, onMetricSelected = {})
    }
}
