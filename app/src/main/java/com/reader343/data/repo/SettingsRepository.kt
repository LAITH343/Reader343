package com.reader343.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.reader343.domain.AppLanguage
import com.reader343.domain.AppSettings
import com.reader343.domain.DailyGoal
import com.reader343.domain.GoalChange
import com.reader343.domain.GoalUnit
import com.reader343.domain.PageAppearance
import com.reader343.domain.ReminderSettings
import com.reader343.domain.ThemeMode
import com.reader343.domain.record
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<AppSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }
        .distinctUntilChanged()

    suspend fun setTheme(value: ThemeMode) = edit { it[Keys.THEME] = value.name }

    suspend fun setPageAppearance(value: PageAppearance) = edit { it[Keys.PAGE_APPEARANCE] = value.name }

    suspend fun setLanguage(value: AppLanguage) = edit { it[Keys.LANGUAGE] = value.name }

    suspend fun setGoal(value: DailyGoal, today: LocalDate = LocalDate.now()) = edit {
        val goal = DailyGoal(value.unit, value.value.coerceAtLeast(0))
        it[Keys.GOAL_UNIT] = goal.unit.name
        it[Keys.GOAL_VALUE] = goal.value
        it[Keys.GOAL_HISTORY] = encodeHistory(decodeHistory(it[Keys.GOAL_HISTORY]).record(goal, today))
    }

    suspend fun setDailyReminder(value: Boolean) = edit { it[Keys.DAILY_REMINDER] = value }

    suspend fun setStreakAlert(value: Boolean) = edit { it[Keys.STREAK_ALERT] = value }

    suspend fun setReminderTime(value: LocalTime) = edit {
        it[Keys.REMINDER_TIME] = value.hour * MINUTES_PER_HOUR + value.minute
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            theme = enumOf(this[Keys.THEME], defaults.theme),
            pageAppearance = enumOf(this[Keys.PAGE_APPEARANCE], defaults.pageAppearance),
            language = enumOf(this[Keys.LANGUAGE], defaults.language),
            goal = DailyGoal(
                unit = enumOf(this[Keys.GOAL_UNIT], defaults.goal.unit),
                value = this[Keys.GOAL_VALUE] ?: defaults.goal.value,
            ),
            goalHistory = decodeHistory(this[Keys.GOAL_HISTORY]),
            reminders = ReminderSettings(
                dailyEnabled = this[Keys.DAILY_REMINDER] ?: defaults.reminders.dailyEnabled,
                streakEnabled = this[Keys.STREAK_ALERT] ?: defaults.reminders.streakEnabled,
                time = this[Keys.REMINDER_TIME]?.let(::timeOf) ?: defaults.reminders.time,
            ),
        )
    }

    private fun timeOf(minuteOfDay: Int): LocalTime {
        val clamped = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        return LocalTime.of(clamped / MINUTES_PER_HOUR, clamped % MINUTES_PER_HOUR)
    }

    private fun encodeHistory(history: List<GoalChange>): String =
        history.joinToString(ENTRY_SEPARATOR) { "${it.date.toEpochDay()}$FIELD_SEPARATOR${it.goal.unit.name}$FIELD_SEPARATOR${it.goal.value}" }

    private fun decodeHistory(raw: String?): List<GoalChange> =
        raw.orEmpty().split(ENTRY_SEPARATOR).mapNotNull { entry ->
            val fields = entry.split(FIELD_SEPARATOR)
            if (fields.size != 3) return@mapNotNull null
            val day = fields[0].toLongOrNull() ?: return@mapNotNull null
            val unit = GoalUnit.entries.firstOrNull { it.name == fields[1] } ?: return@mapNotNull null
            val value = fields[2].toIntOrNull() ?: return@mapNotNull null
            GoalChange(LocalDate.ofEpochDay(day), DailyGoal(unit, value))
        }

    private inline fun <reified T : Enum<T>> enumOf(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val PAGE_APPEARANCE = stringPreferencesKey("page_appearance")
        val LANGUAGE = stringPreferencesKey("language")
        val GOAL_UNIT = stringPreferencesKey("goal_unit")
        val GOAL_VALUE = intPreferencesKey("goal_value")
        val GOAL_HISTORY = stringPreferencesKey("goal_history")
        val DAILY_REMINDER = booleanPreferencesKey("daily_reminder")
        val STREAK_ALERT = booleanPreferencesKey("streak_alert")
        val REMINDER_TIME = intPreferencesKey("reminder_at")
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * 60
        const val ENTRY_SEPARATOR = ";"
        const val FIELD_SEPARATOR = ":"
    }
}
