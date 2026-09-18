package com.reader343.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.reader343.domain.ReminderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    suspend fun sync(settings: ReminderSettings, force: Boolean = false) {
        ReminderType.entries.forEach { type ->
            if (!type.isEnabled(settings)) {
                workManager.cancelUniqueWork(type.workName)
                return@forEach
            }
            val time = type.timeOf(settings)
            if (force || !isScheduled(type, time)) {
                enqueue(type, time, System.currentTimeMillis())
            }
        }
    }

    fun scheduleNext(type: ReminderType, settings: ReminderSettings, previousTarget: Long) {
        val from = maxOf(System.currentTimeMillis(), previousTarget + MIN_GAP_MS)
        enqueue(type, type.timeOf(settings), from)
    }

    private suspend fun isScheduled(type: ReminderType, time: LocalTime): Boolean =
        workManager.getWorkInfosForUniqueWorkFlow(type.workName).first()
            .any { !it.state.isFinished && timeTag(time) in it.tags }

    private fun enqueue(type: ReminderType, time: LocalTime, fromMs: Long) {
        val target = nextOccurrence(time, fromMs, ZoneId.systemDefault())
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay((target - System.currentTimeMillis()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    ReminderWorker.KEY_TYPE to type.name,
                    ReminderWorker.KEY_TARGET to target,
                ),
            )
            .addTag(timeTag(time))
            .build()
        workManager.enqueueUniqueWork(type.workName, ExistingWorkPolicy.REPLACE, request)
    }

    private fun timeTag(time: LocalTime): String =
        String.format(Locale.ROOT, "at:%02d:%02d", time.hour, time.minute)

    private companion object {
        const val MIN_GAP_MS = 60_000L
    }
}

internal fun nextOccurrence(time: LocalTime, fromMs: Long, zone: ZoneId): Long {
    val from = Instant.ofEpochMilli(fromMs).atZone(zone)
    var candidate = ZonedDateTime.of(from.toLocalDate(), time, zone)
    if (!candidate.isAfter(from)) {
        candidate = ZonedDateTime.of(from.toLocalDate().plusDays(1), time, zone)
    }
    return candidate.toInstant().toEpochMilli()
}
