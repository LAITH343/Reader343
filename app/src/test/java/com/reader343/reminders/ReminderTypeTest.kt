package com.reader343.reminders

import com.reader343.domain.ReminderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderTypeTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun millis(dateTime: LocalDateTime) = dateTime.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun togglesAreIndependent() {
        val daily = ReminderSettings(dailyEnabled = true, streakEnabled = false)
        assertTrue(ReminderType.Reading.isEnabled(daily))
        assertFalse(ReminderType.Streak.isEnabled(daily))

        val streak = ReminderSettings(dailyEnabled = false, streakEnabled = true)
        assertFalse(ReminderType.Reading.isEnabled(streak))
        assertTrue(ReminderType.Streak.isEnabled(streak))
    }

    @Test
    fun bothRemindersShareOneTime() {
        val settings = ReminderSettings(time = LocalTime.of(18, 15))
        assertEquals(LocalTime.of(18, 15), ReminderType.Reading.timeOf(settings))
        assertEquals(LocalTime.of(20, 15), ReminderType.Streak.timeOf(settings))
    }

    @Test
    fun lateReminderKeepsStreakAlertOnSameDay() {
        val settings = ReminderSettings(time = LocalTime.of(22, 30))
        assertEquals(LocalTime.of(23, 59), ReminderType.Streak.timeOf(settings))
    }

    @Test
    fun nextOccurrenceIsLaterToday() {
        val from = millis(LocalDateTime.of(2026, 9, 18, 10, 0))
        val next = nextOccurrence(LocalTime.of(19, 0), from, zone)
        assertEquals(millis(LocalDateTime.of(2026, 9, 18, 19, 0)), next)
    }

    @Test
    fun nextOccurrenceRollsToTomorrowWhenPassed() {
        val from = millis(LocalDateTime.of(2026, 9, 18, 19, 0))
        val next = nextOccurrence(LocalTime.of(19, 0), from, zone)
        assertEquals(millis(LocalDateTime.of(2026, 9, 19, 19, 0)), next)
    }

    @Test
    fun nextOccurrenceCrossesMidnight() {
        val from = millis(LocalDateTime.of(2026, 9, 18, 23, 59, 30))
        val next = nextOccurrence(LocalTime.of(0, 30), from, zone)
        assertEquals(millis(LocalDateTime.of(2026, 9, 19, 0, 30)), next)
    }
}
