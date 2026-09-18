package com.reader343.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReadingInsightsTest {

    private val zone = ZoneId.of("Europe/Istanbul")
    private val today = LocalDate.of(2026, 9, 18)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(date, LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun session(date: LocalDate, hour: Int, minutes: Long, pages: Int = 1, minute: Int = 0): ReadingSession {
        val start = at(date, hour, minute)
        return ReadingSession(bookId = 1, startTs = start, endTs = start + minutes * MINUTE, pages = pages)
    }

    private fun day(date: LocalDate, minutes: Long = 0, pages: Int = 0) =
        DayStats(date = date, timeMs = minutes * MINUTE, pages = pages)

    private fun daysAgo(days: Long) = today.minusDays(days)

    @Test
    fun midnightCrossingSessionCountsOnceOnStartDay() {
        val stats = dailyStats(listOf(session(daysAgo(1), 23, 90, pages = 12)), zone)
        assertEquals(setOf(daysAgo(1)), stats.keys)
        assertEquals(90 * MINUTE, stats.getValue(daysAgo(1)).timeMs)
        assertEquals(12, stats.getValue(daysAgo(1)).pages)
    }

    @Test
    fun currentStreakCountsFromTodayOrYesterday() {
        assertEquals(3, currentStreak(setOf(today, daysAgo(1), daysAgo(2), daysAgo(4)), today))
        assertEquals(2, currentStreak(setOf(daysAgo(1), daysAgo(2)), today))
        assertEquals(0, currentStreak(setOf(daysAgo(2), daysAgo(3)), today))
    }

    @Test
    fun midnightCrossingSessionDoesNotExtendStreak() {
        val active = dailyStats(listOf(session(daysAgo(1), 23, 120)), zone).keys
        assertEquals(1, currentStreak(active, today))
        assertEquals(1, bestStreakDays(active))
    }

    @Test
    fun bestStreakFindsLongestRun() {
        val active = setOf(
            daysAgo(20), daysAgo(19), daysAgo(18), daysAgo(17),
            daysAgo(10), daysAgo(9),
            today,
        )
        assertEquals(4, bestStreakDays(active))
        assertEquals(0, bestStreakDays(emptySet()))
    }

    @Test
    fun goalHitRateIsNullWithoutGoal() {
        val days = mapOf(today to day(today, minutes = 30))
        assertNull(goalHitRate(days, DailyGoal(GoalUnit.Minutes, 0), today, daysAgo(139)))
    }

    @Test
    fun goalHitRateCountsDaysSinceFirstSession() {
        val goal = DailyGoal(GoalUnit.Minutes, 15)
        val days = listOf(
            day(daysAgo(3), minutes = 20),
            day(daysAgo(2), minutes = 5),
            day(daysAgo(0), minutes = 16),
        ).associateBy { it.date }
        assertEquals(2f / 4f, goalHitRate(days, goal, today, daysAgo(139))!!, 0.0001f)
    }

    @Test
    fun goalHitRateSkipsTodayUntilMet() {
        val goal = DailyGoal(GoalUnit.Pages, 10)
        val days = listOf(
            day(daysAgo(1), pages = 12),
            day(today, pages = 3),
        ).associateBy { it.date }
        assertEquals(1f, goalHitRate(days, goal, today, daysAgo(139))!!, 0.0001f)
    }

    @Test
    fun goalHitRateIgnoresDaysBeforeWindow() {
        val goal = DailyGoal(GoalUnit.Minutes, 10)
        val days = listOf(
            day(daysAgo(200), minutes = 30),
            day(daysAgo(1), minutes = 30),
        ).associateBy { it.date }
        assertEquals(1f, goalHitRate(days, goal, today, daysAgo(139))!!, 0.0001f)
    }

    @Test
    fun goalHitRateCountsMidnightCrossingSessionOnStartDay() {
        val goal = DailyGoal(GoalUnit.Minutes, 30)
        val days = dailyStats(listOf(session(daysAgo(1), 23, 45)), zone)
        assertEquals(1f, goalHitRate(days, goal, today, daysAgo(139))!!, 0.0001f)
    }

    @Test
    fun weekOverWeekComparesRollingWeeks() {
        val days = (13L downTo 0L).map { offset ->
            if (offset >= 7) day(daysAgo(offset), minutes = 10, pages = 4) else day(daysAgo(offset), minutes = 20, pages = 2)
        }
        assertEquals(1f, weekOverWeek(days, today, ActivityMetric.Minutes)!!, 0.0001f)
        assertEquals(-0.5f, weekOverWeek(days, today, ActivityMetric.Pages)!!, 0.0001f)
    }

    @Test
    fun weekOverWeekIsNullWhenLastWeekEmpty() {
        val days = (6L downTo 0L).map { day(daysAgo(it), minutes = 10) }
        assertNull(weekOverWeek(days, today, ActivityMetric.Minutes))
    }

    @Test
    fun strongestSlotPicksMostTime() {
        val sessions = listOf(
            session(daysAgo(1), 8, 10),
            session(daysAgo(2), 18, 40),
            session(daysAgo(3), 13, 20),
        )
        assertEquals(TimeSlot.Evening, strongestSlot(sessions, today, zone))
    }

    @Test
    fun strongestSlotNeedsThreeSessions() {
        val sessions = listOf(session(daysAgo(1), 8, 60), session(daysAgo(2), 9, 60))
        assertNull(strongestSlot(sessions, today, zone))
    }

    @Test
    fun strongestSlotIgnoresSessionsOlderThan28Days() {
        val sessions = listOf(
            session(daysAgo(30), 8, 300),
            session(daysAgo(1), 14, 20),
            session(daysAgo(2), 14, 20),
            session(daysAgo(3), 14, 20),
        )
        assertEquals(TimeSlot.Afternoon, strongestSlot(sessions, today, zone))
    }

    @Test
    fun strongestSlotSplitsSessionsAcrossBoundaries() {
        val sessions = listOf(
            session(daysAgo(1), 21, 90, minute = 50),
            session(daysAgo(2), 21, 90, minute = 50),
            session(daysAgo(3), 21, 30),
        )
        assertEquals(TimeSlot.Night, strongestSlot(sessions, today, zone))
    }

    @Test
    fun strongestSlotHandlesMidnightCrossing() {
        val sessions = listOf(
            session(daysAgo(1), 23, 120),
            session(daysAgo(2), 17, 60),
            session(daysAgo(3), 17, 50),
        )
        assertEquals(TimeSlot.Night, strongestSlot(sessions, today, zone))
    }

    @Test
    fun slotBucketsMatchHours() {
        assertEquals(TimeSlot.Night, slotOf(4))
        assertEquals(TimeSlot.Morning, slotOf(5))
        assertEquals(TimeSlot.Morning, slotOf(11))
        assertEquals(TimeSlot.Afternoon, slotOf(12))
        assertEquals(TimeSlot.Evening, slotOf(17))
        assertEquals(TimeSlot.Night, slotOf(22))
        assertEquals(TimeSlot.Night, slotOf(0))
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
