package com.reader343.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader343.R
import com.reader343.domain.DailyGoal
import com.reader343.domain.GoalContext
import com.reader343.domain.GoalUnit
import com.reader343.domain.ReminderSettings
import com.reader343.ui.components.AppTopBar
import com.reader343.ui.components.LoadingState
import com.reader343.ui.components.appClickable
import com.reader343.ui.components.currentLocale
import com.reader343.ui.components.disabledAlpha
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle

enum class ReminderKind { Daily, Streak }

class GoalRemindersActions(
    val onEditGoal: () -> Unit,
    val onReminder: (ReminderKind, Boolean) -> Unit,
    val onReminderTime: (LocalTime) -> Unit,
)

@Composable
fun GoalRemindersRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val access = rememberNotificationAccess()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var pendingReminder by rememberSaveable { mutableStateOf<ReminderKind?>(null) }
    var goalSheet by rememberSaveable { mutableStateOf(false) }

    val setReminder: (ReminderKind, Boolean) -> Unit = { kind, enabled ->
        when (kind) {
            ReminderKind.Daily -> viewModel.setDailyReminder(enabled)
            ReminderKind.Streak -> viewModel.setStreakAlert(enabled)
        }
    }
    val showDenied: () -> Unit = {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = resources.getString(R.string.notifications_denied),
                actionLabel = resources.getString(R.string.action_open_settings),
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed) access.openSystemSettings()
        }
    }

    GoalRemindersScreen(
        state = state,
        actions = GoalRemindersActions(
            onEditGoal = { goalSheet = true },
            onReminder = { kind, enabled ->
                if (enabled && access.needsPrompt) pendingReminder = kind else setReminder(kind, enabled)
            },
            onReminderTime = viewModel::setReminderTime,
        ),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        notificationsBlocked = !access.allowed,
        onFixNotifications = {
            if (access.needsPrompt) {
                access.request { if (it == PermissionResult.Blocked) access.openSystemSettings() }
            } else {
                access.openSystemSettings()
            }
        },
    )

    DailyGoalSheetHost(visible = goalSheet, onDismiss = { goalSheet = false })

    pendingReminder?.let { kind ->
        NotificationRationaleDialog(
            onConfirm = {
                pendingReminder = null
                access.request { result ->
                    if (result == PermissionResult.Granted) setReminder(kind, true) else showDenied()
                }
            },
            onDismiss = { pendingReminder = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalRemindersScreen(
    state: SettingsUiState?,
    actions: GoalRemindersActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    notificationsBlocked: Boolean = false,
    onFixNotifications: () -> Unit = {},
) {
    var editTime by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.appColors.bg,
        topBar = {
            AppTopBar(title = stringResource(R.string.settings_goal_reminders), onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state == null) {
            LoadingState(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            val reminders = state.settings.reminders
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item(key = "goal") {
                    SettingsGroup {
                        NavRow(
                            icon = R.drawable.ic_ph_target,
                            title = stringResource(R.string.settings_daily_goal),
                            subtitle = goalSummary(state.settings.goal, state.goalContext),
                            onClick = actions.onEditGoal,
                        )
                    }
                }
                item(key = "reminders_label") { GroupLabel(R.string.settings_reminders) }
                item(key = "reminders") {
                    SettingsGroup {
                        SwitchRow(
                            title = stringResource(R.string.settings_daily_reminder),
                            body = stringResource(R.string.settings_daily_reminder_hint),
                            checked = reminders.dailyEnabled,
                            onCheckedChange = { actions.onReminder(ReminderKind.Daily, it) },
                        )
                        GroupDivider()
                        SwitchRow(
                            title = stringResource(R.string.settings_streak_alert),
                            body = stringResource(R.string.settings_streak_alert_hint),
                            checked = reminders.streakEnabled,
                            onCheckedChange = { actions.onReminder(ReminderKind.Streak, it) },
                        )
                        if (reminders.anyEnabled && notificationsBlocked) {
                            GroupDivider()
                            WarningRow(
                                title = stringResource(R.string.notifications_blocked),
                                body = stringResource(R.string.notifications_blocked_hint),
                                onClick = onFixNotifications,
                            )
                        }
                        GroupDivider()
                        ReminderTimeRow(reminders = reminders, onClick = { editTime = true })
                    }
                }
            }
        }
    }

    if (state != null && editTime) {
        ReminderTimeDialog(
            time = state.settings.reminders.time,
            onConfirm = {
                actions.onReminderTime(it)
                editTime = false
            },
            onDismiss = { editTime = false },
        )
    }
}

@Composable
private fun ReminderTimeRow(
    reminders: ReminderSettings,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    val enabled = reminders.anyEnabled
    val time = formatTime(reminders.time)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appClickable(
                shape = RectangleShape,
                enabled = enabled,
                onClickLabel = stringResource(R.string.settings_reminder_time_change),
                onClick = onClick,
            )
            .disabledAlpha(enabled)
            .padding(horizontal = RowPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(
            title = stringResource(R.string.settings_reminder_time),
            body = stringResource(R.string.settings_reminder_time_hint, time, formatTime(reminders.streakTime)),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 36.dp)
                .background(colors.accTint12, MaterialTheme.appShapes.iconTile)
                .border(1.dp, colors.accLine, MaterialTheme.appShapes.iconTile)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.labelMedium,
                color = colors.accTx,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    time: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_reminder_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        containerColor = MaterialTheme.appColors.surf,
    )
}

@Composable
private fun NotificationRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_ph_books), contentDescription = null) },
        title = { Text(stringResource(R.string.notifications_rationale_title)) },
        text = { Text(stringResource(R.string.notifications_rationale)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_allow)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) }
        },
        containerColor = MaterialTheme.appColors.surf,
    )
}

@Composable
internal fun formatTime(time: LocalTime): String {
    val locale = currentLocale()
    val skeleton = if (DateFormat.is24HourFormat(LocalContext.current)) "Hm" else "hma"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        .withDecimalStyle(DecimalStyle.of(locale))
        .format(time)
}

private val PreviewGoalState = SettingsUiState(
    settings = com.reader343.domain.AppSettings(
        goal = DailyGoal(GoalUnit.Minutes, 15),
        reminders = ReminderSettings(dailyEnabled = true, streakEnabled = true, time = LocalTime.of(19, 0)),
    ),
    goalContext = GoalContext(metLastWeek = 5, avgSessionMs = 22 * 60_000L, pagesPerDay = 15),
)

private val PreviewGoalActions = GoalRemindersActions({}, { _, _ -> }, {})

@Preview(name = "Dark", showBackground = true, heightDp = 900)
@Composable
private fun GoalRemindersDarkPreview() {
    Reader343Theme(darkTheme = true) {
        GoalRemindersScreen(
            state = PreviewGoalState,
            actions = PreviewGoalActions,
            onBack = {},
            notificationsBlocked = true,
        )
    }
}

@Preview(name = "RTL", showBackground = true, heightDp = 900, locale = "ar")
@Composable
private fun GoalRemindersRtlPreview() {
    Reader343Theme(darkTheme = false) {
        GoalRemindersScreen(state = PreviewGoalState, actions = PreviewGoalActions, onBack = {})
    }
}
