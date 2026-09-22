package com.reader343.ui.settings

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import com.reader343.domain.ReminderSettings
import com.reader343.domain.ThemeMode
import com.reader343.ui.components.AppCard
import com.reader343.ui.components.AppSwitch
import com.reader343.ui.components.LoadingState
import com.reader343.ui.components.Pill
import com.reader343.ui.components.PillTone
import com.reader343.ui.components.SectionLabel
import com.reader343.ui.components.SelectableSurface
import com.reader343.ui.components.appClickable
import com.reader343.ui.components.focusRing
import com.reader343.ui.components.formatMinutes
import com.reader343.ui.components.formatNumber
import com.reader343.ui.metadata.MetadataPickerHost
import com.reader343.ui.metadata.rememberMetadataPicker
import com.reader343.ui.theme.PaperSwatch
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import com.reader343.ui.theme.appType
import com.reader343.update.UpdateSummary
import kotlinx.coroutines.launch
import java.time.LocalTime

@Composable
fun SettingsRoute(
    onOpenUpdate: () -> Unit,
    onOpenGoalReminders: () -> Unit,
    onOpenReadAloud: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val picker = rememberMetadataPicker()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    SettingsScreen(
        state = state,
        actions = SettingsActions(
            onTheme = viewModel::setTheme,
            onPageAppearance = viewModel::setPageAppearance,
            onLanguage = viewModel::setLanguage,
            onOpenGoalReminders = onOpenGoalReminders,
            onOpenReadAloud = onOpenReadAloud,
            onOpenUpdate = onOpenUpdate,
            onAutoFetch = viewModel::setAutoFetchMetadata,
            onReview = { state?.reviewBookIds?.firstOrNull()?.let(picker::openPicker) },
        ),
        update = update,
        snackbarHostState = snackbarHostState,
    )

    MetadataPickerHost(
        viewModel = picker,
        onMessage = { message -> scope.launch { snackbarHostState.showSnackbar(message) } },
    )
}

class SettingsActions(
    val onTheme: (ThemeMode) -> Unit,
    val onPageAppearance: (PageAppearance) -> Unit,
    val onLanguage: (AppLanguage) -> Unit,
    val onOpenGoalReminders: () -> Unit,
    val onOpenReadAloud: () -> Unit,
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
    update: UpdateSummary? = null,
) {
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
                settingsContent(state = state, actions = actions, update = update)
            }
        }
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
    update: UpdateSummary?,
) {
    val settings = state.settings

    item(key = "appearance_label") { GroupLabel(R.string.settings_appearance, top = 16.dp) }
    item(key = "appearance") {
        SettingsGroup {
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
            GroupDivider()
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

    item(key = "reading_label") { GroupLabel(R.string.settings_reading) }
    item(key = "reading") {
        SettingsGroup {
            NavRow(
                icon = R.drawable.ic_ph_target,
                title = stringResource(R.string.settings_goal_reminders),
                subtitle = goalSummary(settings.goal, state.goalContext),
                onClick = actions.onOpenGoalReminders,
            )
            GroupDivider()
            NavRow(
                icon = R.drawable.ic_ph_headphones,
                title = stringResource(R.string.settings_read_aloud),
                subtitle = stringResource(R.string.settings_read_aloud_hint),
                onClick = actions.onOpenReadAloud,
            )
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
internal fun GroupLabel(@StringRes text: Int, top: Dp = 18.dp) {
    SectionLabel(
        text = stringResource(text),
        modifier = Modifier.padding(start = ScreenPadding, top = top, end = ScreenPadding),
    )
}

@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ScreenPadding, top = 8.dp, end = ScreenPadding),
        content = content,
    )
}

@Composable
internal fun GroupDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = RowPadding)
            .height(1.dp)
            .background(MaterialTheme.appColors.line),
    )
}

@Composable
internal fun RowText(title: String, body: String, modifier: Modifier = Modifier, titleSize: Int = 15) {
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
internal fun IconTile(@DrawableRes icon: Int, container: Color, content: Color) {
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
internal fun Caret() {
    Icon(
        painter = painterResource(R.drawable.ic_ph_caret_right),
        contentDescription = null,
        tint = MaterialTheme.appColors.ink3,
        modifier = Modifier.size(16.dp),
    )
}

@Composable
internal fun NavRow(
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
internal fun ChoiceGroup(
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
internal fun ChoiceText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun SwitchRow(
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
internal fun WarningRow(
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

@Composable
internal fun goalSummary(goal: DailyGoal, context: GoalContext): String {
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

internal val ScreenPadding = 20.dp
internal val RowPadding = 16.dp
private val IconTileSize = 36.dp
internal val ChoiceHeight = 44.dp
private const val MINUTE_MS = 60_000L

private val PreviewState = SettingsUiState(
    settings = AppSettings(
        goal = DailyGoal(GoalUnit.Minutes, 15),
        reminders = ReminderSettings(dailyEnabled = true, streakEnabled = true, time = LocalTime.of(19, 0)),
    ),
    goalContext = GoalContext(metLastWeek = 5, avgSessionMs = 22 * MINUTE_MS, pagesPerDay = 15),
    reviewBookIds = listOf(3L),
)

private val PreviewActions = SettingsActions({}, {}, {}, {}, {}, {})

private val PreviewUpdate = UpdateSummary(available = true, versionName = "1.1", versionCode = 118, sizeBytes = 24_600_000L)

@Preview(name = "Dark", showBackground = true, heightDp = 1400)
@Composable
private fun SettingsDarkPreview() {
    Reader343Theme(darkTheme = true) {
        SettingsScreen(
            state = PreviewState,
            actions = PreviewActions,
            update = PreviewUpdate,
        )
    }
}

@Preview(name = "Light", showBackground = true, heightDp = 1400)
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

@Preview(name = "RTL", showBackground = true, heightDp = 1400, locale = "ar")
@Composable
private fun SettingsRtlPreview() {
    Reader343Theme(darkTheme = true) {
        SettingsScreen(state = PreviewState, actions = PreviewActions)
    }
}
