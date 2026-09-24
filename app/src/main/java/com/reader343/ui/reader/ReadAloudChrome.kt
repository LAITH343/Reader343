package com.reader343.ui.reader

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reader343.R
import com.reader343.domain.ReadAloudAvailability
import com.reader343.domain.SpeechUnit
import com.reader343.domain.TtsStatus
import com.reader343.domain.TtsVoice
import com.reader343.domain.readAloudSpeedLabel
import com.reader343.ui.components.BookCover
import com.reader343.ui.components.appClickable
import com.reader343.ui.components.currentLocale
import com.reader343.ui.components.formatMinutes
import com.reader343.ui.components.formatNumber
import com.reader343.ui.components.reducedMotion
import com.reader343.ui.components.riseIn
import com.reader343.ui.readaloud.MissingVoice
import com.reader343.ui.readaloud.VoiceSheet
import com.reader343.ui.theme.Reader343Theme
import com.reader343.ui.theme.appColors
import com.reader343.ui.theme.appShapes
import java.util.Locale

data class ReadAloudUi(
    val availability: ReadAloudAvailability = ReadAloudAvailability.Hidden,
    val status: TtsStatus = TtsStatus.Idle,
    val title: String = "",
    val coverPath: String? = null,
    val unit: SpeechUnit? = null,
    val sentence: Int = 0,
    val sentences: Int = 0,
    val remainingMs: Long? = null,
    val inChapter: Boolean = false,
    val rate: Float = 1f,
    val voice: TtsVoice? = null,
    val locale: Locale? = null,
    val voices: List<TtsVoice> = emptyList(),
    val voicesVisible: Boolean = false,
    val highlight: Boolean = true,
    val keepScreenOn: Boolean = false,
    val missingLanguage: Locale? = null,
    val fallbackLanguage: Locale? = null,
) {
    val sheetVisible: Boolean get() = voicesVisible || missingLanguage != null
    val spoken: SpeechUnit? get() = unit.takeIf { highlight }
    val active: Boolean get() = status != TtsStatus.Idle
    val playing: Boolean get() = status == TtsStatus.Playing
    val shown: Boolean get() = active || availability != ReadAloudAvailability.Hidden
    val enabled: Boolean get() = active || availability == ReadAloudAvailability.Ready
    val noText: Boolean get() = availability == ReadAloudAvailability.NoText
}

interface ReadAloudActions {
    fun onReadAloud()
    fun onReadAloudToggle()
    fun onReadAloudPrevious()
    fun onReadAloudNext()
    fun onReadAloudStop()
    fun onReadAloudSpeed()
    fun onShowVoices()
    fun onHideVoices()
    fun onSelectVoice(voice: TtsVoice)
    fun onAwaitVoice()
    fun onReadInstead()

    companion object {
        val None = object : ReadAloudActions {
            override fun onReadAloud() = Unit
            override fun onReadAloudToggle() = Unit
            override fun onReadAloudPrevious() = Unit
            override fun onReadAloudNext() = Unit
            override fun onReadAloudStop() = Unit
            override fun onReadAloudSpeed() = Unit
            override fun onShowVoices() = Unit
            override fun onHideVoices() = Unit
            override fun onSelectVoice(voice: TtsVoice) = Unit
            override fun onAwaitVoice() = Unit
            override fun onReadInstead() = Unit
        }
    }
}

@Composable
internal fun ScanNotice(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.control
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surf0, shape)
            .border(1.dp, colors.line, shape)
            .padding(horizontal = 11.dp, vertical = 9.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ph_scan),
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.read_aloud_scan_notice),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 17.sp),
            fontWeight = FontWeight.Normal,
            color = colors.ink2,
        )
    }
}

@Composable
internal fun ReadAloudMiniPlayer(
    readAloud: ReadAloudUi,
    actions: ReadAloudActions,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.stepper
    val stopLabel = stringResource(R.string.read_aloud_stop)
    Column(
        modifier = modifier
            .riseIn()
            .fillMaxWidth()
            .background(colors.surf, shape)
            .border(1.dp, colors.accLine, shape)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                title = readAloud.title,
                coverPath = readAloud.coverPath,
                shape = RoundedCornerShape(7.dp),
                modifier = Modifier.size(width = 32.dp, height = 44.dp),
            )
            MiniPlayerStatus(readAloud = readAloud, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(MinTouch)
                    .appClickable(shape = MaterialTheme.appShapes.small, onClick = actions::onReadAloudStop)
                    .semantics { contentDescription = stopLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ph_x),
                    contentDescription = null,
                    tint = colors.ink3,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(
                icon = R.drawable.ic_ph_skip_back,
                description = stringResource(R.string.read_aloud_previous),
                onClick = actions::onReadAloudPrevious,
            )
            TransportButton(
                icon = if (readAloud.playing) R.drawable.ic_ph_pause else R.drawable.ic_ph_play,
                description = stringResource(if (readAloud.playing) R.string.read_aloud_pause else R.string.read_aloud_play),
                onClick = actions::onReadAloudToggle,
                primary = true,
            )
            TransportButton(
                icon = R.drawable.ic_ph_skip_forward,
                description = stringResource(R.string.read_aloud_next),
                onClick = actions::onReadAloudNext,
            )
            Spacer(Modifier.weight(1f))
            val speed = readAloudSpeedLabel(readAloud.rate)
            PlayerChip(
                icon = R.drawable.ic_ph_gauge,
                text = speed,
                label = stringResource(R.string.read_aloud_speed),
                onClick = actions::onReadAloudSpeed,
                accent = true,
                ltr = true,
            )
            PlayerChip(
                icon = R.drawable.ic_ph_user_sound,
                text = voiceLabel(readAloud.voice, readAloud.locale),
                label = stringResource(R.string.read_aloud_voice),
                onClick = actions::onShowVoices,
                accent = false,
                modifier = Modifier.widthIn(max = VoiceChipMaxWidth),
            )
        }
    }
}

@Composable
private fun MiniPlayerStatus(readAloud: ReadAloudUi, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.appColors
    val reduced = reducedMotion()
    val fraction = if (readAloud.sentences > 0) readAloud.sentence.toFloat() / readAloud.sentences else 0f
    val animated by animateFloatAsState(
        targetValue = fraction,
        animationSpec = if (reduced) snap() else tween(ProgressMs),
        label = "readAloudProgress",
    )
    val line = if (readAloud.sentences > 0) {
        stringResource(R.string.read_aloud_status, formatNumber(readAloud.sentence), formatNumber(readAloud.sentences))
    } else {
        readAloud.title
    }
    val status = if (readAloud.playing) line else stringResource(R.string.read_aloud_status_paused, line)
    val remaining = readAloud.remainingMs?.let { ms ->
        val minutes = formatMinutes(ms.coerceAtLeast(MIN_REMAINING_MS))
        stringResource(if (readAloud.inChapter) R.string.read_aloud_left_in_chapter else R.string.reader_time_left, minutes)
    }
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressLine(fraction = animated, modifier = Modifier.weight(1f))
            if (remaining != null) {
                Text(
                    text = remaining,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Normal,
                    color = colors.ink3,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.appColors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier = modifier.height(ProgressHeight)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = colors.line2, cornerRadius = radius)
        val width = size.width * fraction.coerceIn(0f, 1f)
        if (width > 0f) {
            drawRoundRect(
                color = colors.acc,
                topLeft = Offset(if (rtl) size.width - width else 0f, 0f),
                size = Size(width, size.height),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun TransportButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.control
    Box(
        modifier = Modifier
            .size(width = if (primary) PlayWidth else MinTouch, height = MinTouch)
            .background(if (primary) colors.accLtTint16 else colors.surf2, shape)
            .border(1.dp, if (primary) colors.accLt else colors.line2, shape)
            .appClickable(shape = shape, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(if (primary) 20.dp else 18.dp),
        )
    }
}

@Composable
private fun PlayerChip(
    @DrawableRes icon: Int,
    text: String,
    label: String,
    onClick: () -> Unit,
    accent: Boolean,
    modifier: Modifier = Modifier,
    ltr: Boolean = false,
) {
    val colors = MaterialTheme.appColors
    val shape = MaterialTheme.appShapes.item
    val content = if (accent) colors.accTx else colors.ink2
    Row(
        modifier = modifier
            .heightIn(min = MinTouch)
            .background(if (accent) colors.accTint12 else colors.surf2, shape)
            .border(1.dp, if (accent) colors.accLine else colors.line2, shape)
            .appClickable(shape = shape, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = text
            }
            .padding(horizontal = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 13.sp,
                textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content,
            ),
            fontWeight = if (accent) FontWeight.Bold else FontWeight.SemiBold,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun voiceLabel(voice: TtsVoice?, locale: Locale?): String {
    val display = currentLocale()
    return (voice?.locale ?: locale ?: display).getDisplayLanguage(display).replaceFirstChar { it.titlecase(display) }
}

@Composable
internal fun ReadAloudVoicesSheet(
    readAloud: ReadAloudUi,
    actions: ReadAloudActions,
) {
    val missing = readAloud.missingLanguage
    VoiceSheet(
        voices = readAloud.voices,
        selected = readAloud.voice,
        language = (readAloud.voice?.locale ?: readAloud.locale ?: currentLocale()).language,
        onSelect = actions::onSelectVoice,
        onOpenSettings = { actions.onAwaitVoice() },
        onDismiss = actions::onHideVoices,
        missing = missing?.let { MissingVoice(it) },
        fallback = readAloud.fallbackLanguage,
        onReadInstead = actions::onReadInstead,
    )
}

private val MinTouch = 44.dp
private val PlayWidth = 52.dp
private val VoiceChipMaxWidth = 108.dp
private val ProgressHeight = 3.dp
private const val ProgressMs = 300
private const val MIN_REMAINING_MS = 60_000L

private val PreviewVoices = listOf(
    TtsVoice(name = "en-us-x-iob-local", locale = Locale.US, installed = true),
    TtsVoice(name = "en-gb-x-gbd-local", locale = Locale.UK, installed = true),
    TtsVoice(name = "ar-xa-x-arz-local", locale = Locale.forLanguageTag("ar-SA"), installed = false),
)

private val PreviewReadAloud = ReadAloudUi(
    availability = ReadAloudAvailability.Ready,
    status = TtsStatus.Playing,
    title = "Designing Data-Intensive Applications",
    sentence = 3,
    sentences = 9,
    remainingMs = 8 * 60_000L,
    inChapter = true,
    voice = PreviewVoices.first(),
    voices = PreviewVoices,
)

@Preview(showBackground = true)
@Composable
private fun ReadAloudMiniPlayerPreview() {
    Reader343Theme {
        Column(
            modifier = Modifier
                .background(MaterialTheme.appColors.bg)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReadAloudMiniPlayer(readAloud = PreviewReadAloud, actions = ReadAloudActions.None)
            ScanNotice()
        }
    }
}

@Preview(showBackground = true, locale = "ar")
@Composable
private fun ReadAloudMiniPlayerRtlPreview() {
    Reader343Theme(darkTheme = false) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.appColors.bg)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReadAloudMiniPlayer(
                readAloud = PreviewReadAloud.copy(status = TtsStatus.Paused, rate = 1.25f),
                actions = ReadAloudActions.None,
            )
            ScanNotice()
        }
    }
}
