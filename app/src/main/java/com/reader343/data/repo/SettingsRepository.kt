package com.reader343.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.reader343.domain.AppLanguage
import com.reader343.domain.AppSettings
import com.reader343.domain.DailyGoal
import com.reader343.domain.GoalChange
import com.reader343.domain.GoalUnit
import com.reader343.domain.PageAppearance
import com.reader343.domain.ReadAloudSettings
import com.reader343.domain.ReminderSettings
import com.reader343.domain.SleepTimer
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

    suspend fun setAutoFetchMetadata(value: Boolean) = edit { it[Keys.AUTO_FETCH_METADATA] = value }

    suspend fun setReadAloudSpeed(value: Float) = edit { it[Keys.ALOUD_SPEED] = value }

    suspend fun setReadAloudPitch(value: Float) = edit { it[Keys.ALOUD_PITCH] = value }

    suspend fun setReadAloudVoice(language: String, name: String) = edit {
        it[Keys.ALOUD_VOICES] = encodeVoices(decodeVoices(it[Keys.ALOUD_VOICES]) + (language to name))
        it[Keys.ALOUD_LANGUAGE] = language
    }

    suspend fun setReadAloudHighlight(value: Boolean) = edit { it[Keys.ALOUD_HIGHLIGHT] = value }

    suspend fun setReadAloudAutoPage(value: Boolean) = edit { it[Keys.ALOUD_AUTO_PAGE] = value }

    suspend fun setReadAloudSkipFurniture(value: Boolean) = edit { it[Keys.ALOUD_SKIP_FURNITURE] = value }

    suspend fun setReadAloudResumeAfterCall(value: Boolean) = edit { it[Keys.ALOUD_RESUME_AFTER_CALL] = value }

    suspend fun setReadAloudSleep(value: SleepTimer) = edit { it[Keys.ALOUD_SLEEP] = value.name }

    suspend fun setReadAloudKeepScreenOn(value: Boolean) = edit { it[Keys.ALOUD_KEEP_SCREEN_ON] = value }

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
            autoFetchMetadata = this[Keys.AUTO_FETCH_METADATA] ?: defaults.autoFetchMetadata,
            readAloud = toReadAloud(defaults.readAloud),
        )
    }

    private fun Preferences.toReadAloud(defaults: ReadAloudSettings) = ReadAloudSettings(
        speed = this[Keys.ALOUD_SPEED] ?: defaults.speed,
        pitch = this[Keys.ALOUD_PITCH] ?: defaults.pitch,
        voices = decodeVoices(this[Keys.ALOUD_VOICES]),
        language = this[Keys.ALOUD_LANGUAGE] ?: defaults.language,
        highlight = this[Keys.ALOUD_HIGHLIGHT] ?: defaults.highlight,
        autoPage = this[Keys.ALOUD_AUTO_PAGE] ?: defaults.autoPage,
        skipFurniture = this[Keys.ALOUD_SKIP_FURNITURE] ?: defaults.skipFurniture,
        resumeAfterCall = this[Keys.ALOUD_RESUME_AFTER_CALL] ?: defaults.resumeAfterCall,
        sleep = enumOf(this[Keys.ALOUD_SLEEP], defaults.sleep),
        keepScreenOn = this[Keys.ALOUD_KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
    )

    private fun encodeVoices(voices: Map<String, String>): String =
        voices.entries.joinToString(ENTRY_SEPARATOR) { "${it.key}$VOICE_SEPARATOR${it.value}" }

    private fun decodeVoices(raw: String?): Map<String, String> =
        raw.orEmpty().split(ENTRY_SEPARATOR).mapNotNull { entry ->
            val language = entry.substringBefore(VOICE_SEPARATOR, "")
            val name = entry.substringAfter(VOICE_SEPARATOR, "")
            if (language.isEmpty() || name.isEmpty()) null else language to name
        }.toMap()

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
        val AUTO_FETCH_METADATA = booleanPreferencesKey("auto_fetch_metadata")
        val ALOUD_SPEED = floatPreferencesKey("aloud_speed")
        val ALOUD_PITCH = floatPreferencesKey("aloud_pitch")
        val ALOUD_VOICES = stringPreferencesKey("aloud_voices")
        val ALOUD_LANGUAGE = stringPreferencesKey("aloud_language")
        val ALOUD_HIGHLIGHT = booleanPreferencesKey("aloud_highlight")
        val ALOUD_AUTO_PAGE = booleanPreferencesKey("aloud_auto_page")
        val ALOUD_SKIP_FURNITURE = booleanPreferencesKey("aloud_skip_furniture")
        val ALOUD_RESUME_AFTER_CALL = booleanPreferencesKey("aloud_resume_after_call")
        val ALOUD_SLEEP = stringPreferencesKey("aloud_sleep")
        val ALOUD_KEEP_SCREEN_ON = booleanPreferencesKey("aloud_keep_screen_on")
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * 60
        const val ENTRY_SEPARATOR = ";"
        const val FIELD_SEPARATOR = ":"
        const val VOICE_SEPARATOR = "="
    }
}
