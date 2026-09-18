package com.reader343.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.reader343.R
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import com.reader343.ui.theme.appType

@Preview(name = "Light", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "RTL", widthDp = 360, locale = "ar", uiMode = Configuration.UI_MODE_NIGHT_YES)
annotation class ThemePreviews

@Composable
private fun PreviewFrame(content: @Composable ColumnScope.() -> Unit) {
    Reader343Theme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.appColors.bg)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@ThemePreviews
@Composable
private fun ButtonsPreview() {
    PreviewFrame {
        PrimaryButton(
            text = stringResource(R.string.home_continue),
            onClick = {},
            icon = R.drawable.ic_ph_play_fill,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(text = stringResource(R.string.action_cancel), onClick = {}, modifier = Modifier.weight(1f))
            PrimaryButton(text = stringResource(R.string.action_save), onClick = {}, modifier = Modifier.weight(1.4f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryButton(text = stringResource(R.string.library_import), onClick = {}, icon = R.drawable.ic_ph_plus, compact = true)
            GhostButton(text = stringResource(R.string.home_all_books), onClick = {})
            DestructiveTextButton(text = stringResource(R.string.action_delete), onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBadgeButton(
                icon = R.drawable.ic_ph_download_simple,
                contentDescription = stringResource(R.string.action_more),
                onClick = {},
                tone = IconButtonTone.Accent,
                badge = true,
            )
            IconBadgeButton(icon = R.drawable.ic_ph_gear_six, contentDescription = stringResource(R.string.action_settings), onClick = {})
            IconBadgeButton(
                icon = R.drawable.ic_ph_dots_three_vertical,
                contentDescription = stringResource(R.string.action_more),
                onClick = {},
                tone = IconButtonTone.Plain,
            )
        }
    }
}

@ThemePreviews
@Composable
private fun ChipsPreview() {
    PreviewFrame {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(text = pluralStringResource(R.plurals.stats_days, 6, formatNumber(6)), tone = PillTone.Accent, icon = R.drawable.ic_ph_flame_fill)
            Pill(text = stringResource(R.string.stats_in_progress), icon = R.drawable.ic_ph_books)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(text = stringResource(R.string.settings_reading), tone = PillTone.Accent, height = SmallPillHeight)
            Pill(text = stringResource(R.string.library_not_started), tone = PillTone.Amber, height = SmallPillHeight)
            Pill(text = stringResource(R.string.notes_title), tone = PillTone.Muted, icon = R.drawable.ic_ph_highlighter, height = SmallPillHeight)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableChip(text = stringResource(R.string.settings_reading), selected = true, onClick = {})
            SelectableChip(text = stringResource(R.string.home_all_books), selected = false, onClick = {})
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableChip(text = stringResource(R.string.settings_option_system), selected = false, onClick = {}, icon = R.drawable.ic_ph_circle_half)
            SelectableChip(text = stringResource(R.string.settings_theme_light), selected = false, onClick = {}, icon = R.drawable.ic_ph_sun)
            SelectableChip(text = stringResource(R.string.settings_theme_dark), selected = true, onClick = {}, icon = R.drawable.ic_ph_moon)
        }
        SegmentedControl(
            items = listOf(
                SegmentItem(stringResource(R.string.stats_metric_time), R.drawable.ic_ph_clock),
                SegmentItem(stringResource(R.string.stats_metric_pages), R.drawable.ic_ph_book_open_text),
            ),
            selectedIndex = 0,
            onSelect = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@ThemePreviews
@Composable
private fun ProgressPreview() {
    PreviewFrame {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            GoalRing(progress = 0.8f, pulse = true) {
                Text(text = formatPercent(0.8f), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressBar(progress = 0.05f, color = MaterialTheme.appColors.accLt, trackColor = MaterialTheme.appColors.accLine)
                ProgressBar(progress = 0.48f, height = 4.dp)
                ProgressBar(progress = 0.76f, height = 5.dp, color = MaterialTheme.appColors.amber.bar)
                BookProgress(percent = 0.48f)
            }
        }
    }
}

@ThemePreviews
@Composable
private fun SwitchPreview() {
    PreviewFrame {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.settings_reminders_enabled), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.settings_reminders_enabled_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.ink3,
                    )
                }
                AppSwitch(checked = true, onCheckedChange = {})
            }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_streak_reminder),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                AppSwitch(checked = false, onCheckedChange = {})
            }
        }
    }
}

@ThemePreviews
@Composable
private fun CardsPreview() {
    PreviewFrame {
        Text(text = stringResource(R.string.stats_title), style = MaterialTheme.appType.screenTitle, color = MaterialTheme.appColors.ink)
        SectionLabel(text = stringResource(R.string.settings_reading))
        HeroCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.home_continue).uppercase(), style = MaterialTheme.appType.kicker, color = MaterialTheme.appColors.accLt)
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
            ProgressBar(progress = 0.05f, color = MaterialTheme.appColors.accLt, trackColor = MaterialTheme.appColors.accLine)
        }
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(text = stringResource(R.string.library_title), actionLabel = stringResource(R.string.home_all_books), onAction = {})
                QuoteBlock(text = stringResource(R.string.notes_empty_hint))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@ThemePreviews
@Composable
private fun TopBarPreview() {
    Reader343Theme {
        AppTopBar(
            title = stringResource(R.string.notes_title),
            subtitle = stringResource(R.string.app_name),
            onBack = {},
            actions = { TopBarAction(icon = R.drawable.ic_ph_note, contentDescription = stringResource(R.string.notes_title), onClick = {}, badge = true) },
        )
    }
}

@ThemePreviews
@Composable
private fun SheetPreview() {
    Reader343Theme {
        val colors = MaterialTheme.appColors
        Box(modifier = Modifier.background(colors.scrim).padding(top = 40.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .sheetTopBorder(colors.accLine, SheetRadius)
                    .background(colors.surf, MaterialTheme.appShapes.sheet)
                    .padding(bottom = 16.dp),
            ) {
                SheetHandle()
                SheetHeader(title = stringResource(R.string.settings_daily_goal), trailing = stringResource(R.string.note_page, 27))
            }
        }
    }
}
