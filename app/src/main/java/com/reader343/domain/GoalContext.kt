package com.reader343.domain

import java.time.LocalDate
import java.time.ZoneId

data class GoalContext(
    val metLastWeek: Int,
    val avgSessionMs: Long,
    val pagesPerDay: Int,
)

fun goalContext(
    sessions: List<ReadingSession>,
    goal: DailyGoal,
    today: LocalDate,
    zone: ZoneId,
): GoalContext {
    val byDay = dailyStats(sessions, zone)
    val weekStart = today.minusDays(WEEK_DAYS - 1)
    val metLastWeek = if (goal.enabled) {
        byDay.values.count { !it.date.isBefore(weekStart) && !it.date.isAfter(today) && goal.isMet(it) }
    } else {
        0
    }
    val sessionStart = today.minusDays(SESSION_WINDOW_DAYS - 1)
    val recentSessions = sessions.filter { !it.startTs.toLocalDate(zone).isBefore(sessionStart) }
    val avgSessionMs = if (recentSessions.isEmpty()) 0L else recentSessions.sumOf { it.durationMs } / recentSessions.size
    val pageStart = today.minusDays(PAGE_WINDOW_DAYS - 1)
    val pageDays = byDay.values.filter { !it.date.isBefore(pageStart) && !it.date.isAfter(today) && it.pages > 0 }
    val pagesPerDay = if (pageDays.isEmpty()) 0 else Math.round(pageDays.sumOf { it.pages }.toFloat() / pageDays.size)
    return GoalContext(metLastWeek = metLastWeek, avgSessionMs = avgSessionMs, pagesPerDay = pagesPerDay)
}

private const val WEEK_DAYS = 7L
private const val SESSION_WINDOW_DAYS = 140L
private const val PAGE_WINDOW_DAYS = 14L
