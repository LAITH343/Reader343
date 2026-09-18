package com.reader343.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekProgressTest {

    private val wednesday = LocalDate.of(2026, 9, 16)

    private fun day(date: LocalDate, minutes: Long = 0, pages: Int = 0) =
        DayStats(date = date, timeMs = minutes * 60_000L, pages = pages)

    @Test
    fun weekStartsOnMondayAndMarksTodayAndFuture() {
        val week = weekProgress(emptyList(), DailyGoal(GoalUnit.Minutes, 15), wednesday)
        assertEquals(7, week.size)
        assertEquals(LocalDate.of(2026, 9, 14), week.first().date)
        assertEquals(LocalDate.of(2026, 9, 20), week.last().date)
        assertEquals(
            listOf(
                WeekDayState.Missed,
                WeekDayState.Missed,
                WeekDayState.Today,
                WeekDayState.Future,
                WeekDayState.Future,
                WeekDayState.Future,
                WeekDayState.Future,
            ),
            week.map { it.state },
        )
    }

    @Test
    fun minutesGoalRequiresReachingTarget() {
        val days = listOf(day(wednesday.minusDays(2), minutes = 15), day(wednesday.minusDays(1), minutes = 14))
        val week = weekProgress(days, DailyGoal(GoalUnit.Minutes, 15), wednesday)
        assertEquals(WeekDayState.Met, week[0].state)
        assertEquals(WeekDayState.Missed, week[1].state)
    }

    @Test
    fun pagesGoalUsesPages() {
        val days = listOf(day(wednesday.minusDays(2), minutes = 60, pages = 4), day(wednesday.minusDays(1), pages = 10))
        val week = weekProgress(days, DailyGoal(GoalUnit.Pages, 10), wednesday)
        assertEquals(WeekDayState.Missed, week[0].state)
        assertEquals(WeekDayState.Met, week[1].state)
    }

    @Test
    fun withoutGoalAnyReadingCounts() {
        val days = listOf(day(wednesday.minusDays(2), minutes = 1), day(wednesday.minusDays(1)))
        val week = weekProgress(days, DailyGoal(), wednesday)
        assertEquals(WeekDayState.Met, week[0].state)
        assertEquals(WeekDayState.Missed, week[1].state)
    }

    @Test
    fun sundayIsLastDayOfItsWeek() {
        val sunday = LocalDate.of(2026, 9, 20)
        val week = weekProgress(emptyList(), DailyGoal(), sunday)
        assertEquals(LocalDate.of(2026, 9, 14), week.first().date)
        assertEquals(WeekDayState.Today, week.last().state)
    }
}
