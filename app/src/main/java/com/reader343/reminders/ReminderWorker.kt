package com.reader343.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reader343.data.repo.LibraryRepository
import com.reader343.data.repo.SettingsRepository
import com.reader343.data.repo.StatsRepository
import com.reader343.domain.AppLanguage
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val statsRepository: StatsRepository,
    private val libraryRepository: LibraryRepository,
    private val notifier: ReminderNotifier,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val type = ReminderType.fromName(inputData.getString(KEY_TYPE)) ?: return Result.failure()
        val target = inputData.getLong(KEY_TARGET, 0L)
        val settings = settingsRepository.settings.first()
        if (!type.isEnabled(settings.reminders)) return Result.success()

        if (isOnTime(type, target)) deliver(type, settings.language)
        scheduler.scheduleNext(type, settings.reminders, target)
        return Result.success()
    }

    private fun isOnTime(type: ReminderType, target: Long): Boolean {
        if (target <= 0L) return true
        val now = System.currentTimeMillis()
        if (now - target !in -EARLY_TOLERANCE_MS..LATE_WINDOW_MS) return false
        if (type != ReminderType.Streak) return true
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(target).atZone(zone).toLocalDate() == LocalDate.now(zone)
    }

    private suspend fun deliver(type: ReminderType, language: AppLanguage) {
        when (type) {
            ReminderType.Reading -> notifier.showReading(language, libraryRepository.continueBook()?.title)
            ReminderType.Streak -> {
                val streak = statsRepository.streakSnapshot()
                if (!streak.goalMetToday && streak.days > 0) notifier.showStreak(language, streak.days)
            }
        }
    }

    companion object {
        const val KEY_TYPE = "type"
        const val KEY_TARGET = "target"
        private const val EARLY_TOLERANCE_MS = 60_000L
        private const val LATE_WINDOW_MS = 2 * 60 * 60_000L
    }
}
