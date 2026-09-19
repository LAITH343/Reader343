package com.reader343.domain

import java.time.LocalDate

data class GoalChange(
    val date: LocalDate,
    val goal: DailyGoal,
)

fun DailyGoal.isMet(day: DayStats): Boolean = when {
    !enabled -> day.timeMs > 0L || day.pages > 0
    unit == GoalUnit.Minutes -> day.timeMs >= value * MINUTE_MS
    else -> day.pages >= value
}

fun List<GoalChange>.goalOn(date: LocalDate): DailyGoal =
    lastOrNull { !it.date.isAfter(date) }?.goal ?: DailyGoal()

fun List<GoalChange>.record(goal: DailyGoal, date: LocalDate): List<GoalChange> {
    val earlier = filter { it.date.isBefore(date) }
    val turnedOff = !goal.enabled || any { it.date == date && !it.goal.enabled }
    val entries = if (turnedOff && goal.enabled) {
        listOf(GoalChange(date, DailyGoal(goal.unit, 0)), GoalChange(date, goal))
    } else {
        listOf(GoalChange(date, goal))
    }
    return earlier + entries
}

fun List<GoalChange>.streakBreaks(): Set<LocalDate> =
    filter { !it.goal.enabled }.mapTo(HashSet()) { it.date }

fun goalMetDays(days: Map<LocalDate, DayStats>, history: List<GoalChange>): Set<LocalDate> =
    days.values
        .filter { day -> history.goalOn(day.date).let { it.enabled && it.isMet(day) } }
        .mapTo(HashSet()) { it.date }

private const val MINUTE_MS = 60_000L
