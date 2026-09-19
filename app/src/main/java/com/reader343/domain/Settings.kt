package com.reader343.domain

import java.time.LocalTime

enum class ThemeMode { System, Light, Dark }

enum class PageAppearance { Normal, Night, Sepia }

enum class AppLanguage(val tag: String) {
    System(""),
    English("en"),
    Arabic("ar"),
}

enum class GoalUnit { Minutes, Pages }

data class DailyGoal(
    val unit: GoalUnit = GoalUnit.Minutes,
    val value: Int = 0,
) {
    val enabled: Boolean get() = value > 0
}

data class ReminderSettings(
    val dailyEnabled: Boolean = false,
    val streakEnabled: Boolean = false,
    val time: LocalTime = DefaultReminderTime,
) {
    val anyEnabled: Boolean get() = dailyEnabled || streakEnabled

    val streakTime: LocalTime get() = streakAlertTime(time)
}

fun streakAlertTime(time: LocalTime): LocalTime {
    val shifted = time.plusHours(STREAK_DELAY_HOURS)
    return if (shifted.isBefore(time)) LatestStreakAlert else shifted
}

val DefaultReminderTime: LocalTime = LocalTime.of(19, 0)

private val LatestStreakAlert: LocalTime = LocalTime.of(23, 59)
private const val STREAK_DELAY_HOURS = 2L

data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    val pageAppearance: PageAppearance = PageAppearance.Normal,
    val language: AppLanguage = AppLanguage.System,
    val goal: DailyGoal = DailyGoal(),
    val goalHistory: List<GoalChange> = emptyList(),
    val reminders: ReminderSettings = ReminderSettings(),
    val autoFetchMetadata: Boolean = true,
    val readAloud: ReadAloudSettings = ReadAloudSettings(),
)
