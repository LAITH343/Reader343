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
)

interface SpeechSource {
    val bookId: Long
    val pageCount: Int
    val locale: Locale
    suspend fun units(page: Int): List<SpeechUnit>
}

val ReadAloudSpeeds: List<Float> = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

fun nextReadAloudSpeed(current: Float): Float =
    ReadAloudSpeeds.firstOrNull { it > current + SPEED_EPSILON } ?: ReadAloudSpeeds.first()

fun readAloudSpeedLabel(speed: Float): String {
    val plain = BigDecimal(speed.toString()).stripTrailingZeros().toPlainString()
    return (if (plain.contains('.')) plain else "$plain.0") + "×"
}

data class ReadAloudProgress(
    val positionMs: Long,
    val durationMs: Long,
)

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
private const val CHARS_PER_SECOND = 14
