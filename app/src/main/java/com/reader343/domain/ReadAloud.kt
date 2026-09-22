package com.reader343.domain

import java.math.BigDecimal
import java.util.Locale

enum class TtsStatus { Idle, Playing, Paused }

enum class TtsIssue { EngineUnavailable, MissingVoiceData, LanguageUnsupported, PlaybackFailed }

data class SpeechPosition(val page: Int, val sentenceIndex: Int)

val SpeechUnit.position: SpeechPosition get() = SpeechPosition(page, sentenceIndex)

data class TtsVoice(
    val name: String,
    val locale: Locale,
    val installed: Boolean,
)

data class TtsState(
    val status: TtsStatus = TtsStatus.Idle,
    val bookId: Long? = null,
    val position: SpeechPosition? = null,
    val unit: SpeechUnit? = null,
    val rate: Float = 1f,
    val pitch: Float = 1f,
    val locale: Locale? = null,
    val voice: TtsVoice? = null,
    val issue: TtsIssue? = null,
    val missingLanguage: String? = null,
)

interface SpeechSource {
    val bookId: Long
    val pageCount: Int
    val locale: Locale
    suspend fun units(page: Int): List<SpeechUnit>
}

val ReadAloudSpeeds: List<Float> = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

val ReadAloudSpeedSteps: List<Float> = (1..8).map { it * 0.25f }

fun nextReadAloudSpeed(current: Float): Float =
    ReadAloudSpeeds.firstOrNull { it > current + SPEED_EPSILON } ?: ReadAloudSpeeds.first()

fun readAloudSpeedLabel(speed: Float): String {
    val plain = BigDecimal(speed.toString()).stripTrailingZeros().toPlainString()
    return (if (plain.contains('.')) plain else "$plain.0") + "×"
}

val ReadAloudPitches: List<Float> = listOf(0.8f, 0.9f, 1f, 1.1f, 1.25f)

enum class SleepTimer(val minutes: Int?) {
    Off(null),
    Minutes15(15),
    Minutes30(30),
    Minutes60(60),
    EndOfChapter(null),
    ;

    fun next(): SleepTimer = entries[(ordinal + 1) % entries.size]
}

data class ReadAloudSettings(
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val voices: Map<String, String> = emptyMap(),
    val language: String? = null,
    val highlight: Boolean = true,
    val autoPage: Boolean = true,
    val skipFurniture: Boolean = true,
    val resumeAfterCall: Boolean = true,
    val sleep: SleepTimer = SleepTimer.Off,
    val keepScreenOn: Boolean = false,
)

data class VoicePreferences(
    val voices: Map<String, String> = emptyMap(),
    val language: String? = null,
) {
    fun preferred(): String = language ?: Locale.getDefault().language

    fun fallback(missing: String): String {
        val preferred = preferred()
        if (preferred != missing) return preferred
        return Locale.getDefault().language.takeIf { it != missing } ?: LATIN_FALLBACK
    }
}

val ReadAloudSettings.voicePreferences: VoicePreferences get() = VoicePreferences(voices, language)

fun speechLanguage(text: String, preferred: String): String {
    var arabic = 0
    var latin = 0
    for (c in text) {
        if (!c.isLetter()) continue
        when (Character.UnicodeScript.of(c.code)) {
            Character.UnicodeScript.ARABIC -> arabic++
            Character.UnicodeScript.LATIN -> latin++
            else -> Unit
        }
    }
    return when {
        arabic > latin -> ARABIC
        latin > arabic && preferred == ARABIC -> LATIN_FALLBACK
        else -> preferred
    }
}

enum class ReadAloudAvailability { Ready, NoText, Hidden }

fun readAloudAvailability(hasTextLayer: Boolean?, pageUsable: Boolean?): ReadAloudAvailability = when {
    hasTextLayer == false -> ReadAloudAvailability.Hidden
    pageUsable == false -> ReadAloudAvailability.NoText
    else -> ReadAloudAvailability.Ready
}

fun SpeechUnit.containsChar(index: Int): Boolean = index in charStart until charEnd

fun List<SpeechUnit>.unitAtChar(index: Int): SpeechUnit? =
    firstOrNull { it.containsChar(index) } ?: lastOrNull { it.charStart <= index } ?: firstOrNull()

fun spokenFillRects(unitRects: List<NormRect>, saved: List<NormRect>): List<NormRect> =
    unitRects.filter { rect -> saved.none { it.intersects(rect) } }

data class ReadAloudProgress(
    val positionMs: Long,
    val durationMs: Long,
) {
    fun remainingMs(rate: Float): Long =
        ((durationMs - positionMs).coerceAtLeast(0L) / rate.coerceAtLeast(SPEED_EPSILON)).toLong()
}

fun estimateReadAloudProgress(
    pageUnits: List<SpeechUnit>,
    sentenceIndex: Int,
    page: Int,
    startPage: Int,
    endPage: Int,
): ReadAloudProgress {
    val pageChars = pageUnits.sumOf { it.text.length }.coerceAtLeast(1)
    val before = pageUnits.filter { it.sentenceIndex < sentenceIndex }.sumOf { it.text.length }
    val pages = (endPage - startPage).coerceAtLeast(1)
    val elapsedChars = (page - startPage).coerceIn(0, pages - 1).toLong() * pageChars + before
    val totalChars = pages.toLong() * pageChars
    return ReadAloudProgress(
        positionMs = elapsedChars * 1000 / CHARS_PER_SECOND,
        durationMs = totalChars * 1000 / CHARS_PER_SECOND,
    )
}

private const val SPEED_EPSILON = 0.01f
private const val ARABIC = "ar"
private const val LATIN_FALLBACK = "en"
private const val CHARS_PER_SECOND = 14
