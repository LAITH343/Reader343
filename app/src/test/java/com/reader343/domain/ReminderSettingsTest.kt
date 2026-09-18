package com.reader343.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class ReminderSettingsTest {

    @Test
    fun defaultsToSevenWithStreakAlertAtNine() {
        val settings = ReminderSettings()
        assertEquals(LocalTime.of(19, 0), settings.time)
        assertEquals(LocalTime.of(21, 0), settings.streakTime)
        assertFalse(settings.anyEnabled)
    }

    @Test
    fun streakAlertIsTwoHoursLater() {
        assertEquals(LocalTime.of(10, 30), streakAlertTime(LocalTime.of(8, 30)))
        assertEquals(LocalTime.of(23, 59), streakAlertTime(LocalTime.of(21, 59)))
    }

    @Test
    fun streakAlertIsCappedBeforeMidnight() {
        assertEquals(LocalTime.of(23, 59), streakAlertTime(LocalTime.of(22, 0)))
        assertEquals(LocalTime.of(23, 59), streakAlertTime(LocalTime.of(23, 15)))
        assertEquals(LocalTime.of(23, 59), streakAlertTime(LocalTime.of(23, 59)))
    }

    @Test
    fun anyEnabledReflectsEitherToggle() {
        assertTrue(ReminderSettings(dailyEnabled = true).anyEnabled)
        assertTrue(ReminderSettings(streakEnabled = true).anyEnabled)
    }
}
