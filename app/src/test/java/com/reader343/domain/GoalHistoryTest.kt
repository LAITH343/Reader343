package com.reader343.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalHistoryTest {

    private val today = LocalDate.of(2026, 9, 18)
    private val minutes15 = DailyGoal(GoalUnit.Minutes, 15)
    private val minutes30 = DailyGoal(GoalUnit.Minutes, 30)
    private val off = DailyGoal(GoalUnit.Minutes, 0)

    private fun daysAgo(days: Long) = today.minusDays(days)

    private fun read(vararg entries: Pair<Long, Long>): Map<LocalDate, DayStats> =
        entries.associate { (ago, minutes) ->
            daysAgo(ago) to DayStats(date = daysAgo(ago), timeMs = minutes * MINUTE, pages = 0)
        }

    private fun streak(days: Map<LocalDate, DayStats>, history: List<GoalChange>): Int =
        currentStreak(goalMetDays(days, history), today, history.streakBreaks())

    @Test
    fun noGoalMeansNoMetDays() {
        assertTrue(goalMetDays(read(0L to 60L, 1L to 60L), emptyList()).isEmpty())
    }

    @Test
    fun readingBelowGoalDoesNotCount() {
        val history = emptyList<GoalChange>().record(minutes15, daysAgo(5))
        assertEquals(setOf(daysAgo(1)), goalMetDays(read(0L to 10L, 1L to 20L), history))
    }

    @Test
    fun daysBeforeGoalWasSetDoNotCount() {
        val history = emptyList<GoalChange>().record(minutes15, daysAgo(1))
        assertEquals(2, streak(read(0L to 20L, 1L to 20L, 2L to 20L), history))
    }

    @Test
    fun streakStaysAliveUntilTodayEnds() {
        val history = emptyList<GoalChange>().record(minutes15, daysAgo(5))
        assertEquals(2, streak(read(0L to 5L, 1L to 20L, 2L to 20L), history))
    }

    @Test
    fun changingGoalKeepsPastDays() {
        val history = emptyList<GoalChange>()
            .record(minutes15, daysAgo(5))
            .record(minutes30, today)
        val days = read(0L to 31L, 1L to 16L, 2L to 16L)
        assertEquals(3, streak(days, history))
        assertEquals(minutes15, history.goalOn(daysAgo(1)))
        assertEquals(minutes30, history.goalOn(today))
    }

    @Test
    fun turningGoalOffEndsStreak() {
        val history = emptyList<GoalChange>()
            .record(minutes15, daysAgo(5))
            .record(off, today)
        assertEquals(0, streak(read(0L to 20L, 1L to 20L), history))
    }

    @Test
    fun turningGoalBackOnSameDayStillResetsStreak() {
        val history = emptyList<GoalChange>()
            .record(minutes15, daysAgo(5))
            .record(off, today)
            .record(minutes15, today)
        assertEquals(listOf(off, minutes15), history.filter { it.date == today }.map { it.goal })
        assertEquals(0, streak(read(0L to 5L, 1L to 20L), history))
        assertEquals(1, streak(read(0L to 20L, 1L to 20L), history))
    }

    @Test
    fun offDayInThePastBreaksBestStreak() {
        val history = emptyList<GoalChange>()
            .record(minutes15, daysAgo(6))
            .record(off, daysAgo(3))
            .record(minutes15, daysAgo(3))
        val met = goalMetDays(read(6L to 20L, 5L to 20L, 4L to 20L, 3L to 20L, 2L to 20L), history)
        assertEquals(3, bestStreakDays(met, history.streakBreaks()))
    }

    @Test
    fun sameDayChangesCollapse() {
        val history = emptyList<GoalChange>()
            .record(minutes15, today)
            .record(minutes30, today)
        assertEquals(listOf(GoalChange(today, minutes30)), history)
        assertFalse(history.streakBreaks().contains(today))
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
