package com.reader343.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.reader343.data.repo.LibraryRepository
import com.reader343.data.repo.SettingsRepository
import com.reader343.data.repo.StatsRepository
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
        val settings = settingsRepository.settings.first().reminders
        if (!type.isEnabled(settings)) return Result.success()

        if (isOnTime(type, target)) deliver(type)
        scheduler.scheduleNext(type, settings, target)
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

    private suspend fun deliver(type: ReminderType) {
        when (type) {
            ReminderType.Reading -> notifier.showReading(libraryRepository.continueBook()?.title)
            ReminderType.Streak -> {
                val streak = statsRepository.streakSnapshot()
                if (!streak.readToday && streak.days > 0) notifier.showStreak(streak.days)
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
