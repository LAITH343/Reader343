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
    val enabled: Boolean = false,
    val readingTime: LocalTime = LocalTime.of(20, 0),
    val streakEnabled: Boolean = true,
)

data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    val pageAppearance: PageAppearance = PageAppearance.Normal,
    val language: AppLanguage = AppLanguage.System,
    val goal: DailyGoal = DailyGoal(),
    val reminders: ReminderSettings = ReminderSettings(),
)
