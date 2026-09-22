package com.reader343.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reader343.R
import com.reader343.domain.ReadAloudPitches
import com.reader343.domain.ReadAloudSettings
import com.reader343.domain.ReadAloudSpeedSteps
import com.reader343.domain.SleepTimer
import com.reader343.domain.TtsVoice
import com.reader343.domain.readAloudSpeedLabel
import com.reader343.ui.components.AppTopBar
import com.reader343.ui.components.LoadingState
import com.reader343.ui.components.appClickable
import com.reader343.ui.components.formatNumber
import com.reader343.ui.readaloud.NotInstalledChip
import com.reader343.ui.readaloud.VoiceSheet
import com.reader343.ui.readaloud.voiceDisplayName
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import java.util.Locale
import kotlin.math.roundToInt

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
fun ReadAloudSettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val voices by viewModel.voices.collectAsStateWithLifecycle()
    var voiceSheet by rememberSaveable { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshVoices() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.stopPreview() }

    ReadAloudSettingsScreen(
        settings = state?.settings?.readAloud,
        voices = voices,
        onBack = onBack,
        actions = object : ReadAloudSettingsActions {
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
    )

    val aloud = state?.settings?.readAloud
    if (voiceSheet && aloud != null) {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadAloudSettingsScreen(
    settings: ReadAloudSettings?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    voices: List<TtsVoice> = emptyList(),
    actions: ReadAloudSettingsActions = ReadAloudSettingsActions.None,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.appColors.bg,
        topBar = {
            AppTopBar(title = stringResource(R.string.settings_read_aloud), onBack = onBack)
        },
    ) { padding ->
        if (settings == null) {
            LoadingState(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item(key = "read_aloud") {
                    ReadAloudGroup(settings = settings, voices = voices, actions = actions)
                }
            }
        }
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
        RateSlider(
            title = stringResource(R.string.read_aloud_speed),
            options = ReadAloudSpeedSteps,
            value = settings.speed,
            onSelect = actions::onSpeed,
        )
        GroupDivider()
        RateSlider(
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
private fun RateSlider(
    title: String,
    options: List<Float>,
    value: Float,
    onSelect: (Float) -> Unit,
) {
    val colors = MaterialTheme.appColors
    var dragging by remember { mutableStateOf<Float?>(null) }
    val selected = options.indexOfNearest(value).toFloat()
    val position = dragging ?: selected
    val label = readAloudSpeedLabel(options[position.roundToInt().coerceIn(options.indices)])
    Column(
        modifier = Modifier.padding(horizontal = RowPadding, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
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
        Slider(
            value = position,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSelect(options[it.roundToInt().coerceIn(options.indices)]) }
                dragging = null
            },
            valueRange = 0f..(options.size - 1).toFloat(),
            steps = (options.size - 2).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = colors.accTx,
                activeTrackColor = colors.acc,
                inactiveTrackColor = colors.line2,
                activeTickColor = colors.bg,
                inactiveTickColor = colors.ink3,
            ),
            modifier = Modifier.semantics {
                contentDescription = title
                stateDescription = label
            },
        )
    }
}

private fun List<Float>.indexOfNearest(value: Float): Int {
    val index = indices.minByOrNull { kotlin.math.abs(this[it] - value) }
    return index ?: 0
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

internal fun ReadAloudSettings.preferredLanguage(): String = language ?: Locale.getDefault().language

internal fun ReadAloudSettings.selectedVoice(voices: List<TtsVoice>): TtsVoice? {
    val name = this.voices[preferredLanguage()] ?: return null
    return voices.firstOrNull { it.name == name }
}

private val PreviewReadAloud = ReadAloudSettings(
    speed = 1.25f,
    voices = mapOf("en" to "en-us-x-iob-local"),
    language = "en",
    sleep = SleepTimer.Minutes30,
)

private val PreviewReadAloudVoices = listOf(
    TtsVoice(name = "en-us-x-iob-local", locale = Locale.US, installed = true),
    TtsVoice(name = "ar-xa-x-arz-local", locale = Locale.forLanguageTag("ar-SA"), installed = false),
)

@Preview(name = "Dark", showBackground = true, heightDp = 1400)
@Composable
private fun ReadAloudSettingsDarkPreview() {
    Reader343Theme(darkTheme = true) {
        ReadAloudSettingsScreen(
            settings = PreviewReadAloud,
            onBack = {},
            voices = PreviewReadAloudVoices,
        )
    }
}

@Preview(name = "RTL", showBackground = true, heightDp = 1400, locale = "ar")
@Composable
private fun ReadAloudSettingsRtlPreview() {
    Reader343Theme(darkTheme = false) {
        ReadAloudSettingsScreen(
            settings = PreviewReadAloud,
            onBack = {},
            voices = PreviewReadAloudVoices,
        )
    }
}
