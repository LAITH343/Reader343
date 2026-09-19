package com.reader343.ui.settings

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader343.BuildConfig
import com.reader343.R
import com.reader343.domain.AppLanguage
import com.reader343.domain.AppSettings
import com.reader343.domain.DailyGoal
import com.reader343.domain.GoalContext
import com.reader343.domain.GoalUnit
import com.reader343.domain.PageAppearance
import com.reader343.domain.ReadAloudPitches
import com.reader343.domain.ReadAloudSettings
import com.reader343.domain.ReadAloudSpeeds
import com.reader343.domain.ReminderSettings
import com.reader343.domain.SleepTimer
import com.reader343.domain.ThemeMode
import com.reader343.domain.TtsVoice
import com.reader343.domain.readAloudSpeedLabel
import com.reader343.ui.components.AppCard
import com.reader343.ui.components.AppSwitch
import com.reader343.ui.components.LoadingState
import com.reader343.ui.components.Pill
import com.reader343.ui.components.PillTone
import com.reader343.ui.components.SectionLabel
import com.reader343.ui.components.SelectableSurface
import com.reader343.ui.components.appClickable
import com.reader343.ui.components.currentLocale
import com.reader343.ui.components.disabledAlpha
import com.reader343.ui.components.focusRing
import com.reader343.ui.components.formatMinutes
import com.reader343.ui.components.formatNumber
import com.reader343.ui.metadata.MetadataPickerHost
import com.reader343.ui.metadata.rememberMetadataPicker
import com.reader343.ui.readaloud.NotInstalledChip
import com.reader343.ui.readaloud.VoiceSheet
import com.reader343.ui.readaloud.voiceDisplayName
import com.reader343.ui.theme.PaperSwatch
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import com.reader343.ui.theme.appType
import com.reader343.update.UpdateSummary
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

enum class ReminderKind { Daily, Streak }

interface ReadAloudSettingsActions {
    fun onShowVoices()
    fun onSpeed(value: Float)
    fun onPitch(value: Float)
    fun onHighlight(value: Boolean)
    fun onAutoPage(value: Boolean)
    fun onSkipFurniture(value: Boolean)
    fun onResumeAfterCall(value: Boolean)
    fun onKeepScreenOn(value: Boolean)
    fun onSleep(value: SleepTimer)

    companion object {
        val None = object : ReadAloudSettingsActions {
            override fun onShowVoices() = Unit
            override fun onSpeed(value: Float) = Unit
            override fun onPitch(value: Float) = Unit
            override fun onHighlight(value: Boolean) = Unit
            override fun onAutoPage(value: Boolean) = Unit
            override fun onSkipFurniture(value: Boolean) = Unit
            override fun onResumeAfterCall(value: Boolean) = Unit
            override fun onKeepScreenOn(value: Boolean) = Unit
            override fun onSleep(value: SleepTimer) = Unit
        }
    }
}

@Composable
fun SettingsRoute(
    onOpenUpdate: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val picker = rememberMetadataPicker()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    var voiceSheet by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshVoices() }
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

    SettingsScreen(
        state = state,
        actions = SettingsActions(
            onTheme = viewModel::setTheme,
            onPageAppearance = viewModel::setPageAppearance,
            onLanguage = viewModel::setLanguage,
            onEditGoal = { goalSheet = true },
            onReminder = { kind, enabled ->
                if (enabled && access.needsPrompt) pendingReminder = kind else setReminder(kind, enabled)
            },
            onReminderTime = viewModel::setReminderTime,
            onOpenUpdate = onOpenUpdate,
            onAutoFetch = viewModel::setAutoFetchMetadata,
            onReview = { state?.reviewBookIds?.firstOrNull()?.let(picker::openPicker) },
        ),
        update = update,
        voices = voices,
        readAloudActions = object : ReadAloudSettingsActions {
            override fun onShowVoices() {
                viewModel.refreshVoices()
                voiceSheet = true
            }
            override fun onSpeed(value: Float) = viewModel.setReadAloudSpeed(value)
            override fun onPitch(value: Float) = viewModel.setReadAloudPitch(value)
            override fun onHighlight(value: Boolean) = viewModel.setReadAloudHighlight(value)
            override fun onAutoPage(value: Boolean) = viewModel.setReadAloudAutoPage(value)
            override fun onSkipFurniture(value: Boolean) = viewModel.setReadAloudSkipFurniture(value)
            override fun onResumeAfterCall(value: Boolean) = viewModel.setReadAloudResumeAfterCall(value)
            override fun onKeepScreenOn(value: Boolean) = viewModel.setReadAloudKeepScreenOn(value)
            override fun onSleep(value: SleepTimer) = viewModel.setReadAloudSleep(value)
        },
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

    val loaded = state
    if (voiceSheet && loaded != null) {
        val aloud = loaded.settings.readAloud
        VoiceSheet(
            voices = voices,
            selected = aloud.selectedVoice(voices),
            language = aloud.preferredLanguage(),
            onSelect = viewModel::selectVoice,
            onOpenSettings = {
                viewModel.awaitVoice(it)
                voiceSheet = false
            },
            onDismiss = { voiceSheet = false },
        )
    }

    MetadataPickerHost(
        viewModel = picker,
        onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
    )

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

class SettingsActions(
    val onTheme: (ThemeMode) -> Unit,
    val onPageAppearance: (PageAppearance) -> Unit,
    val onLanguage: (AppLanguage) -> Unit,
    val onEditGoal: () -> Unit,
    val onReminder: (ReminderKind, Boolean) -> Unit,
    val onReminderTime: (LocalTime) -> Unit,
    val onOpenUpdate: () -> Unit,
    val onAutoFetch: (Boolean) -> Unit = {},
    val onReview: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    state: SettingsUiState?,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    notificationsBlocked: Boolean = false,
    onFixNotifications: () -> Unit = {},
    update: UpdateSummary? = null,
    voices: List<TtsVoice> = emptyList(),
    readAloudActions: ReadAloudSettingsActions = ReadAloudSettingsActions.None,
) {
    var editTime by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.appColors.bg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                SettingsHeader()
                LoadingState(Modifier.weight(1f))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item(key = "header") { SettingsHeader() }
                settingsContent(
                    state = state,
                    actions = actions,
                    onEditTime = { editTime = true },
                    notificationsBlocked = notificationsBlocked,
                    onFixNotifications = onFixNotifications,
                    update = update,
                    voices = voices,
                    readAloudActions = readAloudActions,
                )
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
private fun SettingsHeader() {
    Text(
        text = stringResource(R.string.settings_title),
        style = MaterialTheme.appType.screenTitle,
        color = MaterialTheme.appColors.ink,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, top = 10.dp, end = ScreenPadding)
            .semantics { heading() },
    )
}

private fun LazyListScope.settingsContent(
    state: SettingsUiState,
    actions: SettingsActions,
    onEditTime: () -> Unit,
    notificationsBlocked: Boolean,
    onFixNotifications: () -> Unit,
    update: UpdateSummary?,
    voices: List<TtsVoice>,
    readAloudActions: ReadAloudSettingsActions,
) {
    val settings = state.settings
    val reminders = settings.reminders

    item(key = "reading_label") { GroupLabel(R.string.settings_reading, top = 16.dp) }
    item(key = "reading") {
        SettingsGroup {
            NavRow(
                icon = R.drawable.ic_ph_target,
                title = stringResource(R.string.settings_daily_goal),
                subtitle = goalSummary(settings.goal, state.goalContext),
                onClick = actions.onEditGoal,
            )
            GroupDivider()
            ChoiceGroup(title = stringResource(R.string.settings_page_appearance)) {
                PageAppearance.entries.forEach { mode ->
                    SelectableSurface(
                        selected = settings.pageAppearance == mode,
                        onClick = { actions.onPageAppearance(mode) },
                        modifier = Modifier.weight(1f),
                        minHeight = 46.dp,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 22.dp, height = 14.dp)
                                    .background(mode.swatch, MaterialTheme.appShapes.swatch)
                                    .border(1.dp, MaterialTheme.appColors.handle, MaterialTheme.appShapes.swatch),
                            )
                            ChoiceText(stringResource(mode.labelRes))
                        }
                    }
                }
            }
        }
    }

    item(key = "read_aloud_label") { GroupLabel(R.string.settings_read_aloud) }
    item(key = "read_aloud") {
        ReadAloudGroup(settings = settings.readAloud, voices = voices, actions = readAloudActions)
    }

    item(key = "book_info_label") { GroupLabel(R.string.book_info_title) }
    item(key = "book_info") {
        SettingsGroup {
            SwitchRow(
                title = stringResource(R.string.settings_auto_fetch),
                body = stringResource(R.string.settings_auto_fetch_hint),
                checked = settings.autoFetchMetadata,
                onCheckedChange = actions.onAutoFetch,
            )
            if (state.reviewBookIds.isNotEmpty()) {
                GroupDivider()
                ReviewRow(count = state.reviewBookIds.size, onClick = actions.onReview)
            }
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
            ReminderTimeRow(reminders = reminders, onClick = onEditTime)
        }
    }

    item(key = "appearance_label") { GroupLabel(R.string.settings_appearance) }
    item(key = "appearance") {
        SettingsGroup {
            ChoiceGroup(title = stringResource(R.string.settings_theme)) {
                ThemeMode.entries.forEach { mode ->
                    SelectableSurface(
                        selected = settings.theme == mode,
                        onClick = { actions.onTheme(mode) },
                        modifier = Modifier.weight(1f),
                        minHeight = ChoiceHeight,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Icon(
                            painter = painterResource(mode.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        ChoiceText(stringResource(mode.labelRes))
                    }
                }
            }
            GroupDivider()
            ChoiceGroup(title = stringResource(R.string.settings_language)) {
                AppLanguage.entries.forEach { language ->
                    SelectableSurface(
                        selected = settings.language == language,
                        onClick = { actions.onLanguage(language) },
                        modifier = Modifier.weight(1f),
                        minHeight = ChoiceHeight,
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        ChoiceText(stringResource(language.labelRes))
                    }
                }
            }
        }
    }

    item(key = "about_label") { GroupLabel(R.string.settings_about) }
    item(key = "about") {
        SettingsGroup {
            UpdateRow(update = update, onClick = actions.onOpenUpdate)
            GroupDivider()
            AboutRow()
        }
    }
}

@Composable
private fun GroupLabel(@StringRes text: Int, top: Dp = 18.dp) {
    SectionLabel(
        text = stringResource(text),
        modifier = Modifier.padding(start = ScreenPadding, top = top, end = ScreenPadding),
    )
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, top = 8.dp, end = ScreenPadding),
        content = content,
    )
}

@Composable
private fun GroupDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RowPadding)
            .height(1.dp)
            .background(MaterialTheme.appColors.line),
    )
}

@Composable
private fun RowText(title: String, body: String, modifier: Modifier = Modifier, titleSize: Int = 15) {
    val colors = MaterialTheme.appColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = titleSize.sp),
            color = colors.ink,
        )
        Text(text = body, style = MaterialTheme.typography.bodySmall, color = colors.ink3)
    }
}

@Composable
private fun IconTile(@DrawableRes icon: Int, container: Color, content: Color) {
    Box(
        modifier = Modifier
            .size(IconTileSize)
            .background(container, MaterialTheme.appShapes.item),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Caret() {
    Icon(
        painter = painterResource(R.drawable.ic_ph_caret_right),
        contentDescription = null,
        tint = MaterialTheme.appColors.ink3,
        modifier = Modifier.size(16.dp),
    )
}

@Composable
private fun NavRow(
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .appClickable(shape = RectangleShape, onClick = onClick)
            .padding(horizontal = RowPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = icon, container = colors.accTint16, content = colors.accTx)
        RowText(title = title, body = subtitle, modifier = Modifier.weight(1f))
        Caret()
    }
}

@Composable
private fun ChoiceGroup(
    title: String,
    content: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = RowPadding, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.appColors.ink,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun ChoiceText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(interactionSource, RectangleShape)
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = RowPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowText(title = title, body = body, modifier = Modifier.weight(1f))
        AppSwitch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ReadAloudGroup(
    settings: ReadAloudSettings,
    voices: List<TtsVoice>,
    actions: ReadAloudSettingsActions,
) {
    SettingsGroup {
        VoiceRow(settings = settings, voices = voices, onClick = actions::onShowVoices)
        GroupDivider()
        RateGroup(
            title = stringResource(R.string.read_aloud_speed),
            options = ReadAloudSpeeds,
            value = settings.speed,
            onSelect = actions::onSpeed,
        )
        GroupDivider()
        RateGroup(
            title = stringResource(R.string.settings_read_aloud_pitch),
            options = ReadAloudPitches,
            value = settings.pitch,
            onSelect = actions::onPitch,
        )
        GroupDivider()
        SwitchRow(
            title = stringResource(R.string.settings_read_aloud_highlight),
            body = stringResource(R.string.settings_read_aloud_highlight_hint),
            checked = settings.highlight,
            onCheckedChange = actions::onHighlight,
        )
        GroupDivider()
        SwitchRow(
            title = stringResource(R.string.settings_read_aloud_auto_page),
            body = stringResource(R.string.settings_read_aloud_auto_page_hint),
            checked = settings.autoPage,
            onCheckedChange = actions::onAutoPage,
        )
        GroupDivider()
        SwitchRow(
            title = stringResource(R.string.settings_read_aloud_skip),
            body = stringResource(R.string.settings_read_aloud_skip_hint),
            checked = settings.skipFurniture,
            onCheckedChange = actions::onSkipFurniture,
        )
        GroupDivider()
        SwitchRow(
            title = stringResource(R.string.settings_read_aloud_resume),
            body = stringResource(R.string.settings_read_aloud_resume_hint),
            checked = settings.resumeAfterCall,
            onCheckedChange = actions::onResumeAfterCall,
        )
        GroupDivider()
        SwitchRow(
            title = stringResource(R.string.settings_read_aloud_screen),
            body = stringResource(R.string.settings_read_aloud_screen_hint),
            checked = settings.keepScreenOn,
            onCheckedChange = actions::onKeepScreenOn,
        )
        GroupDivider()
        SleepRow(sleep = settings.sleep, onClick = { actions.onSleep(settings.sleep.next()) })
    }
}

@Composable
private fun VoiceRow(
    settings: ReadAloudSettings,
    voices: List<TtsVoice>,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    val voice = settings.selectedVoice(voices)
    val display = voiceDisplayName(voice, Locale.forLanguageTag(settings.preferredLanguage()))
    val value = if (voice != null) {
        stringResource(R.string.settings_read_aloud_voice_value, display, voice.name)
    } else {
        stringResource(R.string.settings_read_aloud_voice_default, display)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .appClickable(shape = RectangleShape, onClick = onClick)
            .padding(horizontal = RowPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = R.drawable.ic_ph_user_sound, container = colors.accTint16, content = colors.accTx)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.read_aloud_voice),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                color = colors.ink,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (voice?.installed == false) NotInstalledChip()
        Caret()
    }
}

@Composable
private fun RateGroup(
    title: String,
    options: List<Float>,
    value: Float,
    onSelect: (Float) -> Unit,
) {
    val colors = MaterialTheme.appColors
    val label = readAloudSpeedLabel(value)
    Column(
        modifier = Modifier.padding(horizontal = RowPadding, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                color = colors.ink3,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                val text = readAloudSpeedLabel(option)
                SelectableSurface(
                    selected = option == value,
                    onClick = { onSelect(option) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "$title $text" },
                    minHeight = ChoiceHeight,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepRow(sleep: SleepTimer, onClick: () -> Unit) {
    val colors = MaterialTheme.appColors
    val on = sleep != SleepTimer.Off
    val label = sleepLabel(sleep)
    val shape = MaterialTheme.appShapes.iconTile
    val title = stringResource(R.string.settings_read_aloud_sleep)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .appClickable(
                shape = RectangleShape,
                onClickLabel = stringResource(R.string.settings_read_aloud_sleep_change),
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = title
                stateDescription = label
            }
            .padding(horizontal = RowPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = R.drawable.ic_ph_moon_stars, container = colors.surf2, content = colors.accLt)
        RowText(
            title = title,
            body = stringResource(R.string.settings_read_aloud_sleep_hint),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 34.dp)
                .background(if (on) colors.accTint12 else colors.surf2, shape)
                .border(1.dp, if (on) colors.accLine else colors.line2, shape)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (on) colors.accTx else colors.ink3,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun sleepLabel(sleep: SleepTimer): String {
    val minutes = sleep.minutes
    return when {
        sleep == SleepTimer.Off -> stringResource(R.string.settings_read_aloud_sleep_off)
        sleep == SleepTimer.EndOfChapter -> stringResource(R.string.settings_read_aloud_sleep_chapter)
        minutes != null -> pluralStringResource(R.plurals.settings_read_aloud_sleep_minutes, minutes, formatNumber(minutes))
        else -> ""
    }
}

private fun ReadAloudSettings.preferredLanguage(): String = language ?: Locale.getDefault().language

private fun ReadAloudSettings.selectedVoice(voices: List<TtsVoice>): TtsVoice? {
    val name = this.voices[preferredLanguage()] ?: return null
    return voices.firstOrNull { it.name == name }
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

@Composable
private fun ReviewRow(
    count: Int,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .appClickable(shape = RectangleShape, onClick = onClick)
            .padding(horizontal = RowPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = R.drawable.ic_ph_seal_question, container = colors.amber.fill, content = colors.amber.text)
        RowText(
            title = stringResource(R.string.settings_review_row),
            body = pluralStringResource(R.plurals.settings_review_row_hint, count, formatNumber(count)),
            modifier = Modifier.weight(1f),
        )
        Pill(text = formatNumber(count), tone = PillTone.Amber)
    }
}

@Composable
private fun WarningRow(
    title: String,
    body: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .appClickable(shape = RectangleShape, onClick = onClick)
            .padding(horizontal = RowPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = R.drawable.ic_ph_warning_circle, container = colors.surf2, content = colors.danger)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = colors.danger)
            Text(text = body, style = MaterialTheme.typography.bodySmall, color = colors.ink3)
        }
        Caret()
    }
}

@Composable
private fun UpdateRow(
    update: UpdateSummary?,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    val context = LocalContext.current
    val hint = when {
        update == null -> stringResource(R.string.settings_update_hint_unknown)
        update.available -> stringResource(
            R.string.settings_update_hint_available,
            update.versionName,
            Formatter.formatShortFileSize(context, update.sizeBytes),
        )
        else -> stringResource(R.string.settings_update_hint_current, update.versionName, formatNumber(update.versionCode))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .appClickable(shape = RectangleShape, onClick = onClick)
            .padding(horizontal = RowPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            IconTile(icon = R.drawable.ic_ph_download_simple, container = colors.accTint16, content = colors.accTx)
            if (update?.available == true) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .size(10.dp)
                        .background(colors.surf, CircleShape)
                        .padding(2.dp)
                        .background(colors.acc, CircleShape),
                )
            }
        }
        RowText(title = stringResource(R.string.settings_app_update), body = hint, modifier = Modifier.weight(1f))
        if (update != null) {
            Pill(
                text = stringResource(
                    if (update.available) R.string.settings_update_badge_update else R.string.settings_update_badge_current,
                ),
                tone = PillTone.Accent,
            )
        }
    }
}

@Composable
private fun AboutRow() {
    val colors = MaterialTheme.appColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = RowPadding, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = R.drawable.ic_ph_shield_check, container = colors.surf2, content = colors.accLt)
        RowText(
            title = stringResource(
                R.string.settings_about_version,
                stringResource(R.string.app_name),
                BuildConfig.VERSION_NAME,
                formatNumber(BuildConfig.VERSION_CODE),
            ),
            body = stringResource(R.string.settings_about_offline),
            titleSize = 14,
        )
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
private fun goalSummary(goal: DailyGoal, context: GoalContext): String {
    if (!goal.enabled) return stringResource(R.string.settings_goal_off_summary)
    val amount = if (goal.unit == GoalUnit.Minutes) {
        formatMinutes(goal.value * MINUTE_MS)
    } else {
        pluralStringResource(R.plurals.stats_pages_value, goal.value, formatNumber(goal.value))
    }
    return pluralStringResource(
        R.plurals.settings_goal_summary,
        context.metLastWeek,
        amount,
        formatNumber(context.metLastWeek),
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

@get:DrawableRes
private val ThemeMode.iconRes: Int
    get() = when (this) {
        ThemeMode.System -> R.drawable.ic_ph_circle_half
        ThemeMode.Light -> R.drawable.ic_ph_sun
        ThemeMode.Dark -> R.drawable.ic_ph_moon
    }

@get:StringRes
private val PageAppearance.labelRes: Int
    get() = when (this) {
        PageAppearance.Normal -> R.string.settings_page_normal
        PageAppearance.Night -> R.string.settings_page_night
        PageAppearance.Sepia -> R.string.settings_page_sepia
    }

private val PageAppearance.swatch: Color
    get() = when (this) {
        PageAppearance.Normal -> PaperSwatch.Normal
        PageAppearance.Night -> PaperSwatch.Night
        PageAppearance.Sepia -> PaperSwatch.Sepia
    }

@get:StringRes
private val AppLanguage.labelRes: Int
    get() = when (this) {
        AppLanguage.System -> R.string.settings_option_system
        AppLanguage.English -> R.string.language_english
        AppLanguage.Arabic -> R.string.language_arabic
    }

private val ScreenPadding = 20.dp
private val RowPadding = 16.dp
private val IconTileSize = 36.dp
private val ChoiceHeight = 44.dp
private const val MINUTE_MS = 60_000L

private val PreviewState = SettingsUiState(
    settings = AppSettings(
        goal = DailyGoal(GoalUnit.Minutes, 15),
        reminders = ReminderSettings(dailyEnabled = true, streakEnabled = true, time = LocalTime.of(19, 0)),
        readAloud = ReadAloudSettings(
            speed = 1.25f,
            voices = mapOf("en" to "en-us-x-iob-local"),
            language = "en",
            sleep = SleepTimer.Minutes30,
        ),
    ),
    goalContext = GoalContext(metLastWeek = 5, avgSessionMs = 22 * MINUTE_MS, pagesPerDay = 15),
    reviewBookIds = listOf(3L),
)

private val PreviewActions = SettingsActions({}, {}, {}, {}, { _, _ -> }, {}, {})

private val PreviewVoices = listOf(
    TtsVoice(name = "en-us-x-iob-local", locale = Locale.US, installed = true),
    TtsVoice(name = "ar-xa-x-arz-local", locale = Locale.forLanguageTag("ar-SA"), installed = false),
)

private val PreviewUpdate = UpdateSummary(available = true, versionName = "1.1", versionCode = 118, sizeBytes = 24_600_000L)

@Preview(name = "Dark", showBackground = true, heightDp = 2200)
@Composable
private fun SettingsDarkPreview() {
    Reader343Theme(darkTheme = true) {
        SettingsScreen(
            state = PreviewState,
            actions = PreviewActions,
            notificationsBlocked = true,
            update = PreviewUpdate,
            voices = PreviewVoices,
        )
    }
}

@Preview(name = "Light", showBackground = true, heightDp = 2200)
@Composable
private fun SettingsLightPreview() {
    Reader343Theme(darkTheme = false) {
        SettingsScreen(
            state = PreviewState,
            actions = PreviewActions,
            update = UpdateSummary(available = false, versionName = "1.0", versionCode = 1, sizeBytes = 0L),
        )
    }
}

@Preview(name = "RTL", showBackground = true, heightDp = 2200, locale = "ar")
@Composable
private fun SettingsRtlPreview() {
    Reader343Theme(darkTheme = true) {
        SettingsScreen(state = PreviewState, actions = PreviewActions, voices = PreviewVoices)
    }
}
