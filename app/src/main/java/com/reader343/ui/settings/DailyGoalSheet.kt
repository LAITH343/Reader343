package com.reader343.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader343.R
import com.reader343.domain.DailyGoal
import com.reader343.domain.GoalContext
import com.reader343.domain.GoalUnit
import com.reader343.ui.components.AppBottomSheet
import com.reader343.ui.components.GhostButton
import com.reader343.ui.components.PrimaryButton
import com.reader343.ui.components.SecondaryButton
import com.reader343.ui.components.SegmentItem
import com.reader343.ui.components.SegmentedControl
import com.reader343.ui.components.SelectableSurface
import com.reader343.ui.components.appClickable
import com.reader343.ui.components.disabledAlpha
import com.reader343.ui.components.formatMinutes
import com.reader343.ui.components.formatNumber
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import com.reader343.ui.theme.appType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyGoalSheetHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    viewModel: DailyGoalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state
    if (!visible || current == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }
    AppBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        DailyGoalSheetContent(
            goal = current.goal,
            context = current.context,
            onSave = {
                viewModel.save(it)
                close()
            },
            onCancel = close,
        )
    }
}

@Composable
fun DailyGoalSheetContent(
    goal: DailyGoal,
    context: GoalContext,
    onSave: (DailyGoal) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    var unit by rememberSaveable { mutableStateOf(goal.unit) }
    var value by rememberSaveable { mutableIntStateOf(if (goal.enabled) goal.value else defaultGoal(goal.unit)) }
    val max = maxGoal(unit)

    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_daily_goal),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.goal_sheet_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                )
            }
            if (goal.enabled) {
                GhostButton(
                    text = stringResource(R.string.goal_sheet_turn_off),
                    onClick = { onSave(DailyGoal(goal.unit, 0)) },
                )
            }
        }

        SegmentedControl(
            items = GoalUnit.entries.map { SegmentItem(stringResource(it.labelRes)) },
            selectedIndex = unit.ordinal,
            onSelect = { index ->
                val next = GoalUnit.entries[index]
                if (next != unit) {
                    unit = next
                    value = defaultGoal(next)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton(
                icon = R.drawable.ic_ph_minus,
                label = stringResource(R.string.goal_sheet_decrease),
                enabled = value > MIN_GOAL,
                onClick = { value = (value - GOAL_STEP).coerceAtLeast(MIN_GOAL) },
            )
            Column(
                modifier = Modifier
                    .widthIn(min = 116.dp)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = formatNumber(value),
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = pluralStringResource(
                        if (unit == GoalUnit.Minutes) R.plurals.goal_sheet_minutes_a_day else R.plurals.goal_sheet_pages_a_day,
                        value,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                )
            }
            StepperButton(
                icon = R.drawable.ic_ph_plus,
                label = stringResource(R.string.goal_sheet_increase),
                enabled = value < max,
                onClick = { value = (value + GOAL_STEP).coerceAtMost(max) },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets(unit).forEach { preset ->
                SelectableSurface(
                    selected = value == preset,
                    onClick = { value = preset },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.appShapes.item,
                    minHeight = 40.dp,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text(text = formatNumber(preset), maxLines = 1)
                }
            }
        }

        GoalHint(text = goalHint(unit, value, context))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                text = stringResource(R.string.action_cancel),
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.goal_sheet_save),
                onClick = { onSave(DailyGoal(unit, value)) },
                modifier = Modifier.weight(1.4f),
            )
        }
    }
}

@Composable
private fun StepperButton(
    @DrawableRes icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.stepper
    Box(
        modifier = Modifier
            .size(StepperSize)
            .disabledAlpha(enabled)
            .background(colors.surf2, shape)
            .border(1.dp, colors.line2, shape)
            .appClickable(shape = shape, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun GoalHint(text: String) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.control
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accTint10, shape)
            .border(1.dp, colors.accLine, shape)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ph_lightbulb),
            contentDescription = null,
            tint = colors.accTx,
            modifier = Modifier.size(16.dp),
        )
        Text(text = text, style = MaterialTheme.appType.caption, color = colors.accTx)
    }
}

@Composable
private fun goalHint(unit: GoalUnit, value: Int, context: GoalContext): String = when {
    unit == GoalUnit.Minutes && context.avgSessionMs >= MINUTE_MS -> {
        val average = formatMinutes(context.avgSessionMs)
        val target = formatMinutes(value * MINUTE_MS)
        if (value * MINUTE_MS <= context.avgSessionMs) {
            stringResource(R.string.goal_hint_minutes_within, average, target)
        } else {
            stringResource(R.string.goal_hint_minutes_above, average, target)
        }
    }
    unit == GoalUnit.Pages && context.pagesPerDay > 0 ->
        pluralStringResource(R.plurals.goal_hint_pages, context.pagesPerDay, formatNumber(context.pagesPerDay))
    else -> stringResource(R.string.goal_hint_start)
}

@get:StringRes
internal val GoalUnit.labelRes: Int
    get() = when (this) {
        GoalUnit.Minutes -> R.string.settings_goal_unit_minutes
        GoalUnit.Pages -> R.string.settings_goal_unit_pages
    }

private fun defaultGoal(unit: GoalUnit): Int = if (unit == GoalUnit.Minutes) 15 else 10

private fun maxGoal(unit: GoalUnit): Int = if (unit == GoalUnit.Minutes) 120 else 60

private fun presets(unit: GoalUnit): List<Int> =
    if (unit == GoalUnit.Minutes) listOf(5, 10, 15, 30, 45) else listOf(5, 10, 15, 20, 30)

private const val MIN_GOAL = 5
private const val GOAL_STEP = 5
private const val MINUTE_MS = 60_000L
private val StepperSize = 48.dp

@Preview(showBackground = true)
@Composable
private fun DailyGoalSheetPreview() {
    Reader343Theme {
        DailyGoalSheetContent(
            goal = DailyGoal(GoalUnit.Minutes, 15),
            context = GoalContext(metLastWeek = 5, avgSessionMs = 22 * MINUTE_MS, pagesPerDay = 15),
            onSave = {},
            onCancel = {},
        )
    }
}
