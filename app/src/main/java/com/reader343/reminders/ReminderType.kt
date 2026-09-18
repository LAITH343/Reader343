package com.reader343.reminders

import com.reader343.domain.ReminderSettings
import java.time.LocalTime

enum class ReminderType(val workName: String, val notificationId: Int) {
    Reading("reminder_reading", 1001),
    Streak("reminder_streak", 1002);

    fun isEnabled(settings: ReminderSettings): Boolean =
        settings.enabled && (this == Reading || settings.streakEnabled)

    fun timeOf(settings: ReminderSettings): LocalTime = when (this) {
        Reading -> settings.readingTime
        Streak -> settings.streakTime
    }

    companion object {
        fun fromName(name: String?): ReminderType? = entries.firstOrNull { it.name == name }
    }
}
