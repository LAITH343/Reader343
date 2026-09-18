package com.reader343.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ReadingSession(
    val bookId: Long,
    val startTs: Long,
    val endTs: Long,
    val pages: Int,
) {
    val durationMs: Long get() = (endTs - startTs).coerceAtLeast(0L)
}

enum class TimeSlot { Morning, Afternoon, Evening, Night }

fun Long.toLocalDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun dailyStats(sessions: List<ReadingSession>, zone: ZoneId): Map<LocalDate, DayStats> =
    sessions.groupBy { it.startTs.toLocalDate(zone) }
        .mapValues { (date, daySessions) ->
            DayStats(
                date = date,
                timeMs = daySessions.sumOf { it.durationMs },
                pages = daySessions.sumOf { it.pages },
            )
        }

fun currentStreak(activeDays: Set<LocalDate>, today: LocalDate): Int {
    var day = when {
        today in activeDays -> today
        today.minusDays(1) in activeDays -> today.minusDays(1)
        else -> return 0
    }
    var count = 0
    while (day in activeDays) {
        count++
        day = day.minusDays(1)
    }
    return count
}

fun bestStreakDays(activeDays: Set<LocalDate>): Int {
    var best = 0
    var run = 0
    var previous: LocalDate? = null
    for (day in activeDays.sorted()) {
        run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
        best = maxOf(best, run)
        previous = day
    }
    return best
}

fun goalHitRate(
    days: Map<LocalDate, DayStats>,
    goal: DailyGoal,
    today: LocalDate,
    windowStart: LocalDate,
): Float? {
    if (!goal.enabled) return null
    val first = days.values
        .filter { it.isActive && !it.date.isBefore(windowStart) && !it.date.isAfter(today) }
        .minOfOrNull { it.date } ?: return null
    val todayMet = days[today]?.let(goal::isMet) == true
    val last = if (todayMet) today else today.minusDays(1)
    if (last.isBefore(first)) return null
    val total = ChronoUnit.DAYS.between(first, last) + 1
    val met = (0 until total).count { offset -> days[first.plusDays(offset)]?.let(goal::isMet) == true }
    return met.toFloat() / total
}

fun weekOverWeek(days: List<DayStats>, today: LocalDate, metric: ActivityMetric): Float? {
    val thisWeekStart = today.minusDays(DAYS_PER_WEEK - 1)
    val lastWeekStart = thisWeekStart.minusDays(DAYS_PER_WEEK)
    fun total(from: LocalDate, to: LocalDate): Long = days
        .filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        .sumOf { if (metric == ActivityMetric.Minutes) it.timeMs else it.pages.toLong() }
    val previous = total(lastWeekStart, thisWeekStart.minusDays(1))
    if (previous <= 0L) return null
    val current = total(thisWeekStart, today)
    return (current - previous).toFloat() / previous
}

fun strongestSlot(
    sessions: List<ReadingSession>,
    today: LocalDate,
    zone: ZoneId,
    windowDays: Long = SLOT_WINDOW_DAYS,
    minSessions: Int = SLOT_MIN_SESSIONS,
): TimeSlot? {
    val windowStart = today.minusDays(windowDays - 1)
    val recent = sessions.filter { session ->
        val date = session.startTs.toLocalDate(zone)
        !date.isBefore(windowStart) && !date.isAfter(today)
    }
    if (recent.size < minSessions) return null
    val totals = LongArray(TimeSlot.entries.size)
    recent.forEach { session ->
        var cursor = session.startTs
        while (cursor < session.endTs) {
            val time = Instant.ofEpochMilli(cursor).atZone(zone)
            val boundary = nextSlotBoundary(time.toLocalDate(), time.hour, zone)
            val end = minOf(session.endTs, boundary)
            totals[slotOf(time.hour).ordinal] += end - cursor
            cursor = end
        }
    }
    val best = totals.indices.maxBy { totals[it] }
    return if (totals[best] > 0L) TimeSlot.entries[best] else null
}

fun slotOf(hour: Int): TimeSlot = when (hour) {
    in MORNING_HOUR until AFTERNOON_HOUR -> TimeSlot.Morning
    in AFTERNOON_HOUR until EVENING_HOUR -> TimeSlot.Afternoon
    in EVENING_HOUR until NIGHT_HOUR -> TimeSlot.Evening
    else -> TimeSlot.Night
}

private fun nextSlotBoundary(date: LocalDate, hour: Int, zone: ZoneId): Long {
    val next = SlotBoundaries.firstOrNull { it > hour }
    val boundary = if (next != null) date.atTime(next, 0) else date.plusDays(1).atTime(MORNING_HOUR, 0)
    return boundary.atZone(zone).toInstant().toEpochMilli()
}

private val DayStats.isActive: Boolean get() = timeMs > 0L || pages > 0

private const val DAYS_PER_WEEK = 7L
private const val SLOT_WINDOW_DAYS = 28L
private const val SLOT_MIN_SESSIONS = 3
private const val MORNING_HOUR = 5
private const val AFTERNOON_HOUR = 12
private const val EVENING_HOUR = 17
private const val NIGHT_HOUR = 22
private val SlotBoundaries = listOf(MORNING_HOUR, AFTERNOON_HOUR, EVENING_HOUR, NIGHT_HOUR)
