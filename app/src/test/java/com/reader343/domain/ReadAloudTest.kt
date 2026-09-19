package com.reader343.domain

import org.junit.Assert.assertEquals
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
}
