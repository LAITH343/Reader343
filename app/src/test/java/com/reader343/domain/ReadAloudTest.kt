package com.reader343.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReadAloudTest {

    private fun units(vararg lengths: Int) =
        lengths.mapIndexed { index, length -> SpeechUnit(0, index, 0, length, "x".repeat(length), emptyList()) }

    @Test
    fun speedCyclesThroughStepsAndWraps() {
        assertEquals(1f, nextReadAloudSpeed(0.75f))
        assertEquals(1.25f, nextReadAloudSpeed(1f))
        assertEquals(2f, nextReadAloudSpeed(1.5f))
        assertEquals(0.75f, nextReadAloudSpeed(2f))
    }

    @Test
    fun speedOffStepMovesToNextStep() {
        assertEquals(1.25f, nextReadAloudSpeed(1.1f))
        assertEquals(0.75f, nextReadAloudSpeed(3f))
        assertEquals(0.75f, nextReadAloudSpeed(0.25f))
    }

    @Test
    fun speedLabelKeepsOneDecimalForWholeValues() {
        assertEquals("1.0×", readAloudSpeedLabel(1f))
        assertEquals("2.0×", readAloudSpeedLabel(2f))
        assertEquals("0.75×", readAloudSpeedLabel(0.75f))
        assertEquals("1.5×", readAloudSpeedLabel(1.5f))
    }

    @Test
    fun progressCountsPagesAndSentencesBefore() {
        val page = units(140, 280)
        val progress = estimateReadAloudProgress(page, sentenceIndex = 1, page = 11, startPage = 10, endPage = 14)
        assertEquals(40_000L, progress.positionMs)
        assertEquals(120_000L, progress.durationMs)
    }

    @Test
    fun progressAtChapterStartIsZero() {
        val progress = estimateReadAloudProgress(units(70), sentenceIndex = 0, page = 3, startPage = 3, endPage = 4)
        assertEquals(0L, progress.positionMs)
        assertEquals(5_000L, progress.durationMs)
    }

    @Test
    fun progressWithoutTextStillHasDuration() {
        val progress = estimateReadAloudProgress(emptyList(), sentenceIndex = 0, page = 0, startPage = 0, endPage = 1)
        assertEquals(0L, progress.positionMs)
        assertEquals(71L, progress.durationMs)
    }

    @Test
    fun remainingScalesWithRate() {
        val progress = ReadAloudProgress(positionMs = 40_000L, durationMs = 120_000L)
        assertEquals(80_000L, progress.remainingMs(1f))
        assertEquals(40_000L, progress.remainingMs(2f))
        assertEquals(0L, ReadAloudProgress(positionMs = 9L, durationMs = 5L).remainingMs(1f))
    }

    @Test
    fun availabilityHidesBookWithoutTextLayer() {
        assertEquals(ReadAloudAvailability.Hidden, readAloudAvailability(hasTextLayer = false, pageUsable = true))
        assertEquals(ReadAloudAvailability.NoText, readAloudAvailability(hasTextLayer = true, pageUsable = false))
        assertEquals(ReadAloudAvailability.NoText, readAloudAvailability(hasTextLayer = null, pageUsable = false))
        assertEquals(ReadAloudAvailability.Ready, readAloudAvailability(hasTextLayer = true, pageUsable = true))
        assertEquals(ReadAloudAvailability.Ready, readAloudAvailability(hasTextLayer = null, pageUsable = null))
    }

    @Test
    fun unitAtCharFindsContainingSentence() {
        val page = listOf(
            SpeechUnit(0, 0, 0, 10, "a", emptyList()),
            SpeechUnit(0, 1, 12, 30, "b", emptyList()),
        )
        assertEquals(0, page.unitAtChar(3)?.sentenceIndex)
        assertEquals(1, page.unitAtChar(12)?.sentenceIndex)
        assertEquals(0, page.unitAtChar(11)?.sentenceIndex)
        assertEquals(1, page.unitAtChar(99)?.sentenceIndex)
        assertEquals(null, emptyList<SpeechUnit>().unitAtChar(0))
    }

    @Test
    fun spokenFillSkipsRectsUnderSavedHighlights() {
        val first = NormRect(0.1f, 0.1f, 0.9f, 0.12f)
        val second = NormRect(0.1f, 0.13f, 0.5f, 0.15f)
        val saved = listOf(NormRect(0.2f, 0.1f, 0.4f, 0.12f))
        assertEquals(listOf(second), spokenFillRects(listOf(first, second), saved))
        assertEquals(listOf(first, second), spokenFillRects(listOf(first, second), emptyList()))
    }

    @Test
    fun sleepTimerCyclesAndWraps() {
        assertEquals(
            listOf(SleepTimer.Minutes15, SleepTimer.Minutes30, SleepTimer.Minutes60, SleepTimer.EndOfChapter, SleepTimer.Off),
            generateSequence(SleepTimer.Off.next()) { it.next() }.take(5).toList(),
        )
    }

    @Test
    fun arabicTextUsesArabicVoice() {
        assertEquals("ar", speechLanguage("كان يا ما كان (2021).", "en"))
        assertEquals("en", speechLanguage("It was a bright cold day.", "en"))
        assertEquals("fr", speechLanguage("Il fait beau.", "fr"))
    }

    @Test
    fun latinTextFallsBackWhenArabicPreferred() {
        assertEquals("en", speechLanguage("It was a bright cold day.", "ar"))
        assertEquals("ar", speechLanguage("12 - 14", "ar"))
    }

    @Test
    fun fallbackAvoidsMissingLanguage() {
        assertEquals("fr", VoicePreferences(language = "fr").fallback("ar"))
        assertNotEquals("ar", VoicePreferences(language = "ar").fallback("ar"))
    }
}
