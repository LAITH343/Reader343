package com.reader343.domain

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
