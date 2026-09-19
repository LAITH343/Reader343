package com.reader343.ui.readaloud

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader343.R
import com.reader343.domain.TtsVoice
import com.reader343.ui.components.AppBottomSheet
import com.reader343.ui.components.PrimaryButton
import com.reader343.ui.components.SecondaryButton
import com.reader343.ui.components.appClickable
import com.reader343.ui.components.currentLocale
import com.reader343.ui.components.riseIn
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import kotlinx.coroutines.launch
import java.util.Locale

data class MissingVoice(val locale: Locale, val voiceName: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSheet(
    voices: List<TtsVoice>,
    selected: TtsVoice?,
    language: String,
    onSelect: (TtsVoice) -> Unit,
    onOpenSettings: (MissingVoice) -> Unit,
    onDismiss: () -> Unit,
    missing: MissingVoice? = null,
    fallback: Locale? = null,
    onReadInstead: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var prompt by remember(missing) { mutableStateOf(missing) }
    val hide: (() -> Unit) -> Unit = { then ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }
    AppBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val current = prompt
        if (current == null) {
            VoiceListContent(
                voices = sheetVoices(voices, language),
                selected = selected,
                onSelect = onSelect,
                onMissing = { prompt = MissingVoice(it.locale, it.name) },
                onDone = { hide(onDismiss) },
            )
        } else {
            MissingVoiceContent(
                locale = current.locale,
                fallback = fallback.takeIf { missing != null },
                onOpenSettings = {
                    openVoiceSettings(context)
                    hide { onOpenSettings(current) }
                },
                onReadInstead = { hide(onReadInstead) },
                onNotNow = { hide(onDismiss) },
            )
        }
    }
}

fun openVoiceSettings(context: Context) {
    val intents = listOf(
        Intent(TTS_SETTINGS_ACTION),
        Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
}

fun sheetVoices(voices: List<TtsVoice>, language: String): List<TtsVoice> =
    voices
        .filter { it.installed || it.locale.language == language }
        .sortedWith(compareBy({ !it.installed }, { it.locale.language != language }))

@Composable
fun voiceDisplayName(voice: TtsVoice?, fallback: Locale?): String {
    val display = currentLocale()
    val locale = voice?.locale ?: fallback ?: display
    return locale.getDisplayName(display).replaceFirstChar { it.titlecase(display) }
}

@Composable
private fun VoiceListContent(
    voices: List<TtsVoice>,
    selected: TtsVoice?,
    onSelect: (TtsVoice) -> Unit,
    onMissing: (TtsVoice) -> Unit,
    onDone: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = stringResource(R.string.read_aloud_voices_title),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.read_aloud_voices_subtitle),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = colors.ink3,
            )
        }
        LazyColumn(
            modifier = Modifier
                .heightIn(max = VoiceListMaxHeight)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            items(voices, key = { it.name }) { voice ->
                VoiceRow(
                    voice = voice,
                    selected = voice.installed && voice.name == selected?.name,
                    onClick = { if (voice.installed) onSelect(voice) else onMissing(voice) },
                )
            }
        }
        PrimaryButton(
            text = stringResource(R.string.read_aloud_voices_done),
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VoiceRow(voice: TtsVoice, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.appColors
    val shape = RoundedCornerShape(15.dp)
    val display = currentLocale()
    val interaction = if (voice.installed) {
        Modifier
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    } else {
        Modifier.appClickable(shape = shape, onClick = onClick)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = VoiceRowHeight)
            .background(if (selected) colors.accTint14 else colors.surf2, shape)
            .border(1.dp, if (selected) colors.accMid else colors.line2, shape)
            .then(interaction)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(if (selected) colors.accTint22 else colors.surf2, MaterialTheme.appShapes.iconTile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (voice.installed) R.drawable.ic_ph_user_sound else R.drawable.ic_ph_cloud_arrow_down),
                contentDescription = null,
                tint = if (selected) colors.accTx else colors.ink3,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = voice.locale.getDisplayName(display),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp),
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = voice.name,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, textDirection = TextDirection.Ltr),
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (voice.installed) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(2.dp, if (selected) colors.acc else colors.handle, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Box(Modifier.size(10.dp).background(colors.acc, CircleShape))
            }
        } else {
            NotInstalledChip(withIcon = true)
        }
    }
}

@Composable
fun NotInstalledChip(withIcon: Boolean = false) {
    val amber = MaterialTheme.appColors.amber
    val shape = MaterialTheme.appShapes.pill
    Row(
        modifier = Modifier
            .height(26.dp)
            .background(amber.fill, shape)
            .border(1.dp, amber.border, shape)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (withIcon) {
            Icon(
                painter = painterResource(R.drawable.ic_ph_cloud_arrow_down),
                contentDescription = null,
                tint = amber.text,
                modifier = Modifier.size(13.dp),
            )
        }
        Text(
            text = stringResource(R.string.read_aloud_voice_not_installed),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (withIcon) 11.5.sp else 12.sp),
            fontWeight = FontWeight.Bold,
            color = amber.text,
            maxLines = 1,
        )
    }
}

@Composable
private fun MissingVoiceContent(
    locale: Locale,
    fallback: Locale?,
    onOpenSettings: () -> Unit,
    onReadInstead: () -> Unit,
    onNotNow: () -> Unit,
) {
    val colors = MaterialTheme.appColors
    val display = currentLocale()
    val amber = colors.amber
    val tileShape = RoundedCornerShape(21.dp)
    val language = locale.getDisplayLanguage(display).replaceFirstChar { it.titlecase(display) }
    Column(
        modifier = Modifier
            .riseIn()
            .fillMaxWidth()
            .padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(amber.fill, tileShape)
                .border(1.dp, amber.border, tileShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ph_cloud_arrow_down),
                contentDescription = null,
                tint = amber.text,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            text = stringResource(R.string.read_aloud_missing_title, language),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.read_aloud_missing_body, language),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
            color = colors.ink3,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 290.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            PrimaryButton(
                text = stringResource(R.string.read_aloud_open_voice_settings),
                onClick = onOpenSettings,
                icon = R.drawable.ic_ph_arrow_square_out,
                modifier = Modifier.fillMaxWidth(),
            )
            if (fallback != null) {
                SecondaryButton(
                    text = stringResource(
                        R.string.read_aloud_read_instead,
                        fallback.getDisplayLanguage(display).replaceFirstChar { it.titlecase(display) },
                    ),
                    onClick = onReadInstead,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .appClickable(shape = MaterialTheme.appShapes.control, onClick = onNotNow),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.action_not_now),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink3,
                )
            }
        }
    }
}

private const val TTS_SETTINGS_ACTION = "com.android.settings.TTS_SETTINGS"
private val VoiceRowHeight = 60.dp
private val VoiceListMaxHeight = 360.dp

private val PreviewVoices = listOf(
    TtsVoice(name = "en-us-x-iob-local", locale = Locale.US, installed = true),
    TtsVoice(name = "en-gb-x-gbd-local", locale = Locale.UK, installed = true),
    TtsVoice(name = "ar-xa-x-arz-local", locale = Locale.forLanguageTag("ar-SA"), installed = false),
)

@Preview(showBackground = true)
@Composable
private fun VoiceListPreview() {
    Reader343Theme {
        Column(Modifier.background(MaterialTheme.appColors.surf)) {
            VoiceListContent(
                voices = PreviewVoices,
                selected = PreviewVoices.first(),
                onSelect = {},
                onMissing = {},
                onDone = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MissingVoicePreview() {
    Reader343Theme(darkTheme = false) {
        Column(Modifier.background(MaterialTheme.appColors.surf)) {
            MissingVoiceContent(
                locale = Locale.forLanguageTag("ar"),
                fallback = Locale.ENGLISH,
                onOpenSettings = {},
                onReadInstead = {},
                onNotNow = {},
            )
        }
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun MissingVoiceRtlPreview() {
    Reader343Theme {
        Column(Modifier.background(MaterialTheme.appColors.surf)) {
            MissingVoiceContent(
                locale = Locale.forLanguageTag("ar"),
                fallback = Locale.ENGLISH,
                onOpenSettings = {},
                onReadInstead = {},
                onNotNow = {},
            )
        }
    }
}
