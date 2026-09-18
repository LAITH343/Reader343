package com.reader343.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class GoalContextTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 9, 18)

    private fun session(daysAgo: Long, minutes: Long, pages: Int = 0): ReadingSession {
        val start = LocalDateTime.of(today.minusDays(daysAgo), LocalTime.of(20, 0))
            .atZone(zone).toInstant().toEpochMilli()
        return ReadingSession(bookId = 1L, startTs = start, endTs = start + minutes * 60_000L, pages = pages)
    }

    @Test
    fun countsGoalMetDaysInLastSevenDaysOnly() {
        val sessions = listOf(
            session(0, 20),
            session(3, 15),
            session(5, 10),
            session(6, 30),
            session(7, 30),
        )
        val context = goalContext(sessions, DailyGoal(GoalUnit.Minutes, 15), today, zone)
        assertEquals(3, context.metLastWeek)
    }

    @Test
    fun noMetDaysWhenGoalIsOff() {
        val context = goalContext(listOf(session(0, 20)), DailyGoal(GoalUnit.Minutes, 0), today, zone)
        assertEquals(0, context.metLastWeek)
    }

    @Test
    fun averagesSessionLength() {
        val context = goalContext(listOf(session(1, 10), session(2, 30)), DailyGoal(), today, zone)
        assertEquals(20 * 60_000L, context.avgSessionMs)
    }

    @Test
    fun pagesPerDayUsesReadingDaysInLastTwoWeeks() {
        val sessions = listOf(
            session(0, 10, pages = 10),
            session(0, 10, pages = 6),
            session(4, 10, pages = 14),
            session(20, 10, pages = 100),
        )
        val context = goalContext(sessions, DailyGoal(), today, zone)
        assertEquals(15, context.pagesPerDay)
    }

    @Test
    fun emptyHistoryHasNoAverages() {
        val context = goalContext(emptyList(), DailyGoal(GoalUnit.Pages, 10), today, zone)
        assertEquals(GoalContext(metLastWeek = 0, avgSessionMs = 0L, pagesPerDay = 0), context)
    }
}
