package com.reader343.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class WeekDayState { Met, Today, Missed, Future }

data class WeekDay(
    val date: LocalDate,
    val state: WeekDayState,
)

fun DailyGoal.isMet(day: DayStats): Boolean = when {
    !enabled -> day.timeMs > 0L || day.pages > 0
    unit == GoalUnit.Minutes -> day.timeMs >= value * MINUTE_MS
    else -> day.pages >= value
}

fun weekProgress(days: List<DayStats>, goal: DailyGoal, today: LocalDate): List<WeekDay> {
    val byDate = days.associateBy { it.date }
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0L until DAYS_PER_WEEK).map { offset ->
        val date = monday.plusDays(offset)
        val state = when {
            date == today -> WeekDayState.Today
            date.isAfter(today) -> WeekDayState.Future
            byDate[date]?.let(goal::isMet) == true -> WeekDayState.Met
            else -> WeekDayState.Missed
        }
        WeekDay(date, state)
    }
}

private const val MINUTE_MS = 60_000L
private const val DAYS_PER_WEEK = 7L
