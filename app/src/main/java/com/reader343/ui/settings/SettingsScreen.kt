package com.reader343.ui.settings

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader343.BuildConfig
import com.reader343.R
import com.reader343.domain.AppLanguage
import com.reader343.domain.AppSettings
import com.reader343.domain.DailyGoal
import com.reader343.domain.GoalUnit
import com.reader343.domain.PageAppearance
import com.reader343.domain.ThemeMode
import com.reader343.ui.components.AppCard
import com.reader343.ui.components.AppSwitch
import com.reader343.ui.components.AppTopBar
import com.reader343.ui.components.LoadingState
import com.reader343.ui.components.SectionHeader
import com.reader343.ui.components.SegmentItem
import com.reader343.ui.components.SegmentedControl
import com.reader343.ui.components.currentLocale
import com.reader343.ui.components.formatMinutes
import com.reader343.ui.components.formatNumber
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.spacing
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val access = rememberNotificationAccess()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var askPermission by rememberSaveable { mutableStateOf(false) }

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

    SettingsScreen(
        settings = settings,
        actions = SettingsActions(
            onTheme = viewModel::setTheme,
            onPageAppearance = viewModel::setPageAppearance,
            onLanguage = viewModel::setLanguage,
            onGoal = viewModel::setGoal,
            onRemindersEnabled = { enabled ->
                if (enabled && access.needsPrompt) askPermission = true else viewModel.setRemindersEnabled(enabled)
            },
            onReminderTime = viewModel::setReminderTime,
            onStreakReminder = viewModel::setStreakReminder,
            onStreakTime = viewModel::setStreakTime,
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

    if (askPermission) {
        NotificationRationaleDialog(
            onConfirm = {
                askPermission = false
                access.request { result ->
                    if (result == PermissionResult.Granted) viewModel.setRemindersEnabled(true) else showDenied()
                }
            },
            onDismiss = { askPermission = false },
        )
    }
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
    )
}

class SettingsActions(
    val onTheme: (ThemeMode) -> Unit,
    val onPageAppearance: (PageAppearance) -> Unit,
    val onLanguage: (AppLanguage) -> Unit,
    val onGoal: (DailyGoal) -> Unit,
    val onRemindersEnabled: (Boolean) -> Unit,
    val onReminderTime: (LocalTime) -> Unit,
    val onStreakReminder: (Boolean) -> Unit,
    val onStreakTime: (LocalTime) -> Unit,
)

private enum class SettingsDialog { Goal, ReminderTime, StreakTime }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings?,
    actions: SettingsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    notificationsBlocked: Boolean = false,
    onFixNotifications: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.settings_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (settings == null) {
            LoadingState(modifier = Modifier.padding(padding))
        } else {
            SettingsContent(
                settings = settings,
                actions = actions,
                onEditGoal = { dialog = SettingsDialog.Goal },
                onEditReminderTime = { dialog = SettingsDialog.ReminderTime },
                onEditStreakTime = { dialog = SettingsDialog.StreakTime },
                notificationsBlocked = notificationsBlocked,
                onFixNotifications = onFixNotifications,
                contentPadding = padding,
            )
        }
    }

    if (settings != null) {
        when (dialog) {
            SettingsDialog.Goal -> GoalDialog(
                goal = settings.goal,
                onConfirm = {
                    actions.onGoal(it)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
            SettingsDialog.ReminderTime -> ReminderTimeDialog(
                title = stringResource(R.string.settings_reminder_time),
                time = settings.reminders.readingTime,
                onConfirm = {
                    actions.onReminderTime(it)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
            SettingsDialog.StreakTime -> ReminderTimeDialog(
                title = stringResource(R.string.settings_streak_time),
                time = settings.reminders.streakTime,
                onConfirm = {
                    actions.onStreakTime(it)
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
            null -> Unit
        }
    }
}

@Composable
private fun SettingsContent(
    settings: AppSettings,
    actions: SettingsActions,
    onEditGoal: () -> Unit,
    onEditReminderTime: () -> Unit,
    onEditStreakTime: () -> Unit,
    notificationsBlocked: Boolean,
    onFixNotifications: () -> Unit,
    contentPadding: PaddingValues,
) {
    val spacing = MaterialTheme.spacing
    val reminders = settings.reminders
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item(key = "appearance_header") { SectionHeader(text = stringResource(R.string.settings_appearance)) }
        item(key = "appearance") {
            SettingsGroup {
                ChoiceRow(
                    title = stringResource(R.string.settings_theme),
                    options = ThemeMode.entries,
                    selected = settings.theme,
                    label = { it.labelRes },
                    onSelect = actions.onTheme,
                )
                GroupDivider()
                ChoiceRow(
                    title = stringResource(R.string.settings_page_appearance),
                    options = PageAppearance.entries,
                    selected = settings.pageAppearance,
                    label = { it.labelRes },
                    onSelect = actions.onPageAppearance,
                )
                GroupDivider()
                ChoiceRow(
                    title = stringResource(R.string.settings_language),
                    options = AppLanguage.entries,
                    selected = settings.language,
                    label = { it.labelRes },
                    onSelect = actions.onLanguage,
                )
            }
        }
        item(key = "reading_header") { SectionHeader(text = stringResource(R.string.settings_reading)) }
        item(key = "reading") {
            SettingsGroup {
                ValueRow(
                    title = stringResource(R.string.settings_daily_goal),
                    value = goalLabel(settings.goal),
                    onClick = onEditGoal,
                )
            }
        }
        item(key = "reminders_header") { SectionHeader(text = stringResource(R.string.settings_reminders)) }
        item(key = "reminders") {
            SettingsGroup {
                SwitchRow(
                    title = stringResource(R.string.settings_reminders_enabled),
                    summary = stringResource(R.string.settings_reminders_enabled_hint),
                    checked = reminders.enabled,
                    onCheckedChange = actions.onRemindersEnabled,
                )
                if (reminders.enabled && notificationsBlocked) {
                    GroupDivider()
                    WarningRow(
                        title = stringResource(R.string.notifications_blocked),
                        summary = stringResource(R.string.notifications_blocked_hint),
                        onClick = onFixNotifications,
                    )
                }
                GroupDivider()
                ValueRow(
                    title = stringResource(R.string.settings_reminder_time),
                    value = formatTime(reminders.readingTime),
                    enabled = reminders.enabled,
                    onClick = onEditReminderTime,
                )
                GroupDivider()
                SwitchRow(
                    title = stringResource(R.string.settings_streak_reminder),
                    summary = stringResource(R.string.settings_streak_reminder_hint),
                    checked = reminders.streakEnabled,
                    enabled = reminders.enabled,
                    onCheckedChange = actions.onStreakReminder,
                )
                GroupDivider()
                ValueRow(
                    title = stringResource(R.string.settings_streak_time),
                    value = formatTime(reminders.streakTime),
                    enabled = reminders.enabled && reminders.streakEnabled,
                    onClick = onEditStreakTime,
                )
            }
        }
        item(key = "about_header") { SectionHeader(text = stringResource(R.string.settings_about)) }
        item(key = "about") {
            SettingsGroup {
                ValueRow(
                    title = stringResource(R.string.app_name),
                    value = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = MaterialTheme.spacing.xs)) { content() }
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        SegmentedControl(
            items = options.map { SegmentItem(stringResource(label(it))) },
            selectedIndex = options.indexOf(selected),
            onSelect = { onSelect(options[it]) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ValueRow(
    title: String,
    value: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        modifier = clickable,
        colors = rowColors(enabled),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { AppSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        colors = rowColors(enabled),
    )
}

@Composable
private fun WarningRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.ic_ph_warning_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.error,
        ),
    )
}

@Composable
private fun rowColors(enabled: Boolean) = ListItemDefaults.colors(
    containerColor = Color.Transparent,
    headlineColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = DisabledAlpha)
    },
    supportingColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DisabledAlpha)
    },
)

@Composable
private fun GoalDialog(
    goal: DailyGoal,
    onConfirm: (DailyGoal) -> Unit,
    onDismiss: () -> Unit,
) {
    var unit by rememberSaveable { mutableStateOf(goal.unit) }
    var input by rememberSaveable { mutableStateOf(if (goal.enabled) goal.value.toString() else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_daily_goal)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)) {
                SegmentedControl(
                    items = GoalUnit.entries.map { SegmentItem(stringResource(it.labelRes)) },
                    selectedIndex = unit.ordinal,
                    onSelect = { unit = GoalUnit.entries[it] },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { next -> input = next.filter(Char::isDigit).take(GoalDigits) },
                    label = {
                        Text(
                            stringResource(
                                if (unit == GoalUnit.Minutes) R.string.settings_goal_minutes else R.string.settings_goal_pages,
                            ),
                        )
                    },
                    supportingText = { Text(stringResource(R.string.settings_goal_hint, formatNumber(0))) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(DailyGoal(unit, input.toIntOrNull() ?: 0)) },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    title: String,
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
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun goalLabel(goal: DailyGoal): String = when {
    !goal.enabled -> stringResource(R.string.settings_goal_off)
    goal.unit == GoalUnit.Minutes ->
        stringResource(R.string.settings_goal_per_day, formatMinutes(goal.value * MinuteMs))
    else -> stringResource(
        R.string.settings_goal_per_day,
        pluralStringResource(R.plurals.stats_pages_value, goal.value, formatNumber(goal.value)),
    )
}

@Composable
private fun formatTime(time: LocalTime): String {
    val locale = currentLocale()
    val skeleton = if (DateFormat.is24HourFormat(LocalContext.current)) "Hm" else "hma"
    return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
        .withDecimalStyle(DecimalStyle.of(locale))
        .format(time)
}

@get:StringRes
private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.System -> R.string.settings_option_system
        ThemeMode.Light -> R.string.settings_theme_light
        ThemeMode.Dark -> R.string.settings_theme_dark
    }

@get:StringRes
private val PageAppearance.labelRes: Int
    get() = when (this) {
        PageAppearance.Normal -> R.string.settings_page_normal
        PageAppearance.Night -> R.string.settings_page_night
        PageAppearance.Sepia -> R.string.settings_page_sepia
    }

@get:StringRes
private val AppLanguage.labelRes: Int
    get() = when (this) {
        AppLanguage.System -> R.string.settings_option_system
        AppLanguage.English -> R.string.language_english
        AppLanguage.Arabic -> R.string.language_arabic
    }

@get:StringRes
private val GoalUnit.labelRes: Int
    get() = when (this) {
        GoalUnit.Minutes -> R.string.settings_goal_unit_minutes
        GoalUnit.Pages -> R.string.settings_goal_unit_pages
    }

private const val DisabledAlpha = 0.38f
private const val GoalDigits = 4
private const val MinuteMs = 60_000L

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    Reader343Theme {
        SettingsScreen(
            settings = AppSettings(goal = DailyGoal(GoalUnit.Minutes, 30)),
            actions = SettingsActions({}, {}, {}, {}, {}, {}, {}, {}),
            onBack = {},
        )
    }
}
