package com.reader343.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader343.R
import com.reader343.domain.BookStats
import com.reader343.domain.DayStats
import com.reader343.domain.ReadingStats
import com.reader343.domain.TimeSlot
import com.reader343.domain.weekOverWeek
import com.reader343.ui.components.ActivityHeatmap
import com.reader343.ui.components.AppCard
import com.reader343.ui.components.EmphasizedEasing
import com.reader343.ui.components.EmptyState
import com.reader343.ui.components.ErrorState
import com.reader343.ui.components.HeatmapLegend
import com.reader343.ui.components.HeatmapTooltip
import com.reader343.ui.components.HeroCard
import com.reader343.ui.components.LoadingState
import com.reader343.ui.components.PrimaryButton
import com.reader343.ui.components.ProgressBar
import com.reader343.ui.components.SectionHeader
import com.reader343.ui.components.SegmentItem
import com.reader343.ui.components.SegmentedControl
import com.reader343.ui.components.currentLocale
import com.reader343.ui.components.formatDate
import com.reader343.ui.components.formatDecimal
import com.reader343.ui.components.formatDuration
import com.reader343.ui.components.formatMinutes
import com.reader343.ui.components.formatNumber
import com.reader343.ui.components.formatPercent
import com.reader343.ui.components.formatWeekday
import com.reader343.ui.components.reducedMotion
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import com.reader343.ui.theme.appType
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.abs

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

@Composable
fun StatsScreen(
    state: StatsUiState,
    onOpenLibrary: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onMetricSelected: (ChartMetric) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.appColors.bg,
    ) { padding ->
        if (state is StatsUiState.Content) {
            StatsContent(
                state = state,
                onMetricSelected = onMetricSelected,
                onOpenBook = onOpenBook,
                contentPadding = padding,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                StatsHeader()
                val stateModifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                when (state) {
                    StatsUiState.Loading -> LoadingState(stateModifier)
                    StatsUiState.Error -> ErrorState(
                        message = stringResource(R.string.stats_load_failed),
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = onRetry,
                        modifier = stateModifier,
                    )
                    else -> EmptyState(
                        icon = R.drawable.ic_ph_chart_bar,
                        title = stringResource(R.string.stats_empty),
                        body = stringResource(R.string.stats_empty_hint),
                        modifier = stateModifier,
                        action = {
                            PrimaryButton(text = stringResource(R.string.stats_go_library), onClick = onOpenLibrary)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsHeader() {
    val colors = MaterialTheme.appColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, top = 10.dp, end = ScreenPadding)
            .semantics(mergeDescendants = true) { heading() },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = pluralStringResource(R.plurals.stats_last_weeks, HEATMAP_WEEKS, formatNumber(HEATMAP_WEEKS)),
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
        )
        Text(text = stringResource(R.string.stats_title), style = MaterialTheme.appType.screenTitle, color = colors.ink)
    }
}

@Composable
private fun StatsContent(
    state: StatsUiState.Content,
    onMetricSelected: (ChartMetric) -> Unit,
    onOpenBook: (Long) -> Unit,
    contentPadding: PaddingValues,
) {
    val stats = state.stats
    val cardModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = ScreenPadding)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") { StatsHeader() }
        item(key = "streak") { StreakHero(stats = stats, modifier = cardModifier) }
        item(key = "metric") {
            MetricSelector(metric = state.metric, onMetricSelected = onMetricSelected, modifier = cardModifier)
        }
        item(key = "activity") {
            ActivityCard(activity = state.activity, today = state.today, metric = state.metric, modifier = cardModifier)
        }
        item(key = "chart") {
            ChartCard(stats = stats, today = state.today, metric = state.metric, modifier = cardModifier)
        }
        if (stats.books.isNotEmpty()) {
            item(key = "booksHeader") {
                SectionHeader(
                    text = stringResource(R.string.stats_by_book),
                    modifier = Modifier.padding(horizontal = ScreenPadding),
                )
            }
            items(stats.books, key = { it.bookId }) { book ->
                BookStatsCard(book = book, onClick = { onOpenBook(book.bookId) }, modifier = cardModifier)
            }
        }
    }
}

@Composable
private fun StreakHero(stats: ReadingStats, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.appColors
    val tiles = listOf(
        formatDuration(stats.totalTimeMs) to stringResource(R.string.stats_total_time),
        formatNumber(stats.booksInProgress) to stringResource(R.string.stats_in_progress),
        formatDuration(stats.avgSessionMs) to stringResource(R.string.stats_avg_session),
        formatDecimal(stats.pagesPerDay) to stringResource(R.string.stats_pages_per_day),
        formatNumber(stats.sessionCount) to stringResource(R.string.stats_sessions),
        (stats.goalHitRate?.let { formatPercent(it) } ?: stringResource(R.string.stats_value_none)) to
            stringResource(R.string.stats_goal_hit_rate),
    )
    val detail = stringResource(
        R.string.stats_streak_detail,
        stringResource(
            R.string.stats_best_streak,
            pluralStringResource(R.plurals.stats_days, stats.bestStreakDays, formatNumber(stats.bestStreakDays)),
        ),
        stringResource(
            when {
                !stats.goalSet -> R.string.stats_no_goal
                stats.goalMetToday -> R.string.stats_goal_met_today
                else -> R.string.stats_goal_not_met_today
            },
        ),
    )
    HeroCard(
        modifier = modifier,
        shape = MaterialTheme.appShapes.hero,
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tileShape = MaterialTheme.appShapes.button
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(colors.accLtTint16, tileShape)
                    .border(1.dp, colors.heroLine, tileShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ph_flame_fill),
                    contentDescription = null,
                    tint = colors.accTx,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column {
                Text(
                    text = if (stats.goalSet && stats.streakDays > 0) {
                        pluralStringResource(R.plurals.stats_streak_value, stats.streakDays, formatNumber(stats.streakDays))
                    } else {
                        stringResource(R.string.stats_no_streak)
                    },
                    style = MaterialTheme.typography.displayMedium.copy(lineHeight = 1.1.em),
                    color = colors.ink,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = detail, style = MaterialTheme.typography.bodySmall, color = colors.ink2)
                    if (stats.goalSet && stats.goalMetToday) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ph_check),
                            contentDescription = null,
                            tint = colors.ink2,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.heroLine),
            )
            Column(
                modifier = Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                tiles.chunked(HERO_COLUMNS).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (value, label) ->
                            HeroStat(value = value, label = label, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.appColors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
            color = MaterialTheme.appColors.ink2,
            maxLines = 2,
        )
    }
}

@Composable
private fun MetricSelector(
    metric: ChartMetric,
    onMetricSelected: (ChartMetric) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedControl(
        items = ChartMetric.entries.map { entry ->
            when (entry) {
                ChartMetric.Time -> SegmentItem(stringResource(R.string.stats_metric_time), R.drawable.ic_ph_clock)
                ChartMetric.Pages -> SegmentItem(stringResource(R.string.stats_metric_pages), R.drawable.ic_ph_book_open_text)
            }
        },
        selectedIndex = metric.ordinal,
        onSelect = { onMetricSelected(ChartMetric.entries[it]) },
        containerColor = MaterialTheme.appColors.surf,
        modifier = modifier,
    )
}

@Composable
private fun metricTotal(metric: ChartMetric, value: Int): String = when (metric) {
    ChartMetric.Time -> stringResource(R.string.stats_chart_time, formatMinutes(value * MINUTE_MS))
    ChartMetric.Pages -> pluralStringResource(R.plurals.stats_chart_pages, value, formatNumber(value))
}

@Composable
private fun metricAmount(metric: ChartMetric, value: Int): String = when (metric) {
    ChartMetric.Time -> formatMinutes(value * MINUTE_MS)
    ChartMetric.Pages -> formatNumber(value)
}

@Composable
private fun CardHeader(title: String, trailing: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.appColors.ink,
            modifier = Modifier
                .weight(1f)
                .alignByBaseline()
                .semantics { heading() },
        )
        Text(
            text = trailing,
            style = MaterialTheme.appType.caption,
            color = MaterialTheme.appColors.ink3,
            textAlign = TextAlign.End,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun StatsCard(
    modifier: Modifier = Modifier,
    spacing: Int = 12,
    content: @Composable ColumnScope.() -> Unit,
) {
    AppCard(modifier = modifier, shape = MaterialTheme.appShapes.card) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.dp),
            content = content,
        )
    }
}

@Composable
private fun ActivityCard(
    activity: Map<LocalDate, Int>,
    today: LocalDate,
    metric: ChartMetric,
    modifier: Modifier = Modifier,
) {
    val from = today.minusWeeks(HEATMAP_WEEKS.toLong())
    val visible = activity.filterKeys { it.isAfter(from) && !it.isAfter(today) }
    val activeDays = visible.size
    val summary = stringResource(
        R.string.stats_activity_summary,
        pluralStringResource(R.plurals.stats_active_days, activeDays, formatNumber(activeDays)),
        metricAmount(metric, visible.values.sum()),
    )
    StatsCard(modifier = modifier) {
        CardHeader(title = stringResource(R.string.stats_activity), trailing = summary)
        ActivityHeatmap(
            values = activity,
            today = today,
            weeks = HEATMAP_WEEKS,
            cellDescription = { date, value ->
                stringResource(
                    R.string.stats_chart_day,
                    formatDate(date),
                    if (value > 0) metricTotal(metric, value) else stringResource(R.string.stats_no_reading),
                )
            },
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

@Composable
private fun ChartCard(
    stats: ReadingStats,
    today: LocalDate,
    metric: ChartMetric,
    modifier: Modifier = Modifier,
) {
    val days = stats.days
    val values = days.map { if (metric == ChartMetric.Time) it.timeMs.toFloat() else it.pages.toFloat() }
    val total = when (metric) {
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
    val chartDescription = (listOf(total) + dayDescriptions).joinToString(separator = "\n")
    StatsCard(modifier = modifier, spacing = 14) {
        CardHeader(
            title = pluralStringResource(R.plurals.stats_last_days, days.size, formatNumber(days.size)),
            trailing = total,
        )
        BarChart(
            days = days,
            values = values,
            today = today,
            animationKey = metric,
            modifier = Modifier
                .fillMaxWidth()
                .height(ChartHeight)
                .clearAndSetSemantics { contentDescription = chartDescription },
        )
        Insight(
            change = weekOverWeek(days, today, metric.toActivityMetric()),
            slot = stats.strongestSlot,
        )
    }
}

@Composable
private fun BarChart(
    days: List<DayStats>,
    values: List<Float>,
    today: LocalDate,
    animationKey: Any,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    val reduced = reducedMotion()
    val grow = remember(animationKey) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(animationKey, reduced) {
        if (reduced) grow.snapTo(1f) else grow.animateTo(1f, tween(GROW_MS, easing = EmphasizedEasing))
    }
    val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val barShape = RoundedCornerShape(BarRadius)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        days.forEachIndexed { index, day ->
            val isToday = day.date == today
            val value = values[index]
            val color = when {
                isToday -> colors.acc
                value <= 0f -> colors.line
                else -> colors.heat3
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = BarMaxWidth)
                            .fillMaxWidth()
                            .heightIn(min = BarMinHeight)
                            .fillMaxHeight(value / max)
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                scaleY = grow.value
                            }
                            .background(color, barShape),
                    )
                }
                Text(
                    text = formatWeekday(day.date, TextStyle.NARROW_STANDALONE),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isToday) colors.accTx else colors.ink3,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
private fun Insight(change: Float?, slot: TimeSlot?) {
    val trend = change?.let { value ->
        val percent = formatChange(abs(value))
        when {
            abs(value) < SAME_THRESHOLD -> stringResource(R.string.stats_trend_same)
            value > 0f -> stringResource(R.string.stats_trend_more, percent)
            else -> stringResource(R.string.stats_trend_less, percent)
        }
    }
    val slotText = slot?.let { stringResource(it.messageRes) }
    val text = when {
        trend != null && slotText != null -> stringResource(R.string.stats_insight, trend, slotText)
        else -> trend ?: slotText ?: return
    }
    val icon = if (change != null && change <= -SAME_THRESHOLD) R.drawable.ic_ph_trend_down else R.drawable.ic_ph_trend_up
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.item
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accTint10, shape)
            .border(1.dp, colors.accLine, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.accTx,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.appType.caption.copy(lineHeight = 1.4.em),
            color = colors.accTx,
        )
    }
}

private val TimeSlot.messageRes: Int
    get() = when (this) {
        TimeSlot.Morning -> R.string.stats_slot_morning
        TimeSlot.Afternoon -> R.string.stats_slot_afternoon
        TimeSlot.Evening -> R.string.stats_slot_evening
        TimeSlot.Night -> R.string.stats_slot_night
    }

@Composable
private fun formatChange(fraction: Float): String {
    val locale = currentLocale()
    return remember(locale, fraction) {
        NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 0 }.format(fraction.toDouble())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookStatsCard(book: BookStats, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.appColors
    val cells = listOf(
        formatDuration(book.totalTimeMs) to stringResource(R.string.stats_metric_time),
        formatNumber(book.sessionCount) to stringResource(R.string.stats_sessions),
        formatDuration(book.avgSessionMs) to stringResource(R.string.stats_avg),
        formatNumber(book.pagesRead) to stringResource(R.string.stats_metric_pages),
    )
    AppCard(
        modifier = modifier,
        onClick = onClick,
        onClickLabel = stringResource(R.string.stats_open_book),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatPercent(book.percent),
                    style = MaterialTheme.appType.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ink2,
                )
            }
            ProgressBar(progress = book.percent, height = 5.dp)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                cells.forEach { (value, label) ->
                    Column {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.ink,
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal),
                            color = colors.ink3,
                        )
                    }
                }
            }
        }
    }
}

private val ScreenPadding = 20.dp
private val ChartHeight = 120.dp
private val BarMaxWidth = 16.dp
private val BarMinHeight = 4.dp
private val BarRadius = 5.dp
private const val HERO_COLUMNS = 3
private const val HEATMAP_WEEKS = 20
private const val GROW_MS = 500
private const val SAME_THRESHOLD = 0.05f
private const val MINUTE_MS = 60_000L

private fun previewState(metric: ChartMetric = ChartMetric.Time): StatsUiState.Content {
    val today = LocalDate.of(2026, 9, 18)
    val days = (13 downTo 0).map { offset ->
        val active = offset % 4 != 1
        DayStats(
            date = today.minusDays(offset.toLong()),
            timeMs = if (active) (offset + 4) * 120_000L else 0L,
            pages = if (active) offset + 3 else 0,
        )
    }
    return StatsUiState.Content(
        stats = ReadingStats(
            streakDays = 6,
            bestStreakDays = 11,
            goalSet = true,
            goalMetToday = true,
            totalTimeMs = 22_320_000L,
            booksInProgress = 3,
            sessionCount = 34,
            avgSessionMs = 1_320_000L,
            pagesPerDay = 15f,
            goalHitRate = 0.91f,
            strongestSlot = TimeSlot.Evening,
            days = days,
            books = listOf(
                BookStats(1, "Designing Data-Intensive Applications", 4_440_000L, 10, 420_000L, 27, 0.05f),
                BookStats(2, "The Rust Programming Language", 13_200_000L, 18, 720_000L, 269, 0.48f),
            ),
        ),
        metric = metric,
        activity = (0L..140L).filter { it % 3 != 1L }.associate { today.minusDays(it) to (it * 7 % 50).toInt() + 1 },
        today = today,
    )
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun StatsScreenDarkPreview() {
    Reader343Theme(darkTheme = true) {
        StatsScreen(state = previewState(), onOpenLibrary = {}, onOpenBook = {}, onMetricSelected = {}, onRetry = {})
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun StatsScreenLightPreview() {
    Reader343Theme(darkTheme = false) {
        StatsScreen(state = previewState(ChartMetric.Pages), onOpenLibrary = {}, onOpenBook = {}, onMetricSelected = {}, onRetry = {})
    }
}

@Preview(showBackground = true, heightDp = 1400, locale = "ar")
@Composable
private fun StatsScreenRtlPreview() {
    Reader343Theme(darkTheme = true) {
        StatsScreen(state = previewState(), onOpenLibrary = {}, onOpenBook = {}, onMetricSelected = {}, onRetry = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun StatsScreenEmptyPreview() {
    Reader343Theme {
        StatsScreen(state = StatsUiState.Empty, onOpenLibrary = {}, onOpenBook = {}, onMetricSelected = {}, onRetry = {})
    }
}
