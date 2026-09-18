package com.reader343.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reader343.R
import com.reader343.ui.components.AppCard
import com.reader343.ui.components.StatTile
import com.reader343.ui.components.formatMinutes
import com.reader343.ui.components.formatNumber
import com.reader343.ui.theme.spacing

@Composable
fun HomeStatsStrip(
    stats: HomeStats,
    onOpenStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onOpenStats,
        onClickLabel = stringResource(R.string.home_open_stats),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatTile(
                value = pluralStringResource(R.plurals.stats_days, stats.streakDays, formatNumber(stats.streakDays)),
                label = stringResource(R.string.stats_streak),
                compact = true,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TodayTile(
                todayMs = stats.todayMs,
                goalMs = stats.dailyGoalMs,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = formatNumber(stats.booksInProgress),
                label = stringResource(R.string.stats_in_progress),
                compact = true,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TodayTile(
    todayMs: Long,
    goalMs: Long?,
    modifier: Modifier = Modifier,
) {
    val today = formatMinutes(todayMs)
    if (goalMs == null || goalMs <= 0L) {
        StatTile(
            value = today,
            label = stringResource(R.string.home_today),
            compact = true,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val description = stringResource(R.string.home_today_goal, today, formatMinutes(goalMs))
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            progress = { (todayMs.toFloat() / goalMs).coerceIn(0f, 1f) },
            modifier = Modifier.size(GoalRingSize),
            strokeWidth = GoalRingStroke,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        StatTile(
            value = today,
            label = stringResource(R.string.home_today),
            compact = true,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val GoalRingSize = 32.dp
private val GoalRingStroke = 4.dp
