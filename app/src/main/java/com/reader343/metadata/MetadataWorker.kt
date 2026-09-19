package com.reader343.metadata

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.reader343.data.repo.EnrichOutcome
import com.reader343.data.repo.MetadataRepository
import com.reader343.data.repo.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class MetadataWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MetadataRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getLong(KEY_BOOK_ID, -1L)
        if (bookId < 0) return Result.success()
        if (!settingsRepository.settings.first().autoFetchMetadata) return Result.success()
        return when (repository.autoEnrich(bookId)) {
            EnrichOutcome.Done -> Result.success()
            EnrichOutcome.Retry -> if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val KEY_BOOK_ID = "book_id"
        private const val MAX_ATTEMPTS = 3
        private const val BACKOFF_MINUTES = 5L

        fun enqueue(context: Context, bookId: Long) {
            val request = OneTimeWorkRequestBuilder<MetadataWorker>()
                .setInputData(workDataOf(KEY_BOOK_ID to bookId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(workName(bookId), ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context, bookId: Long) {
            WorkManager.getInstance(context).cancelUniqueWork(workName(bookId))
        }

        private fun workName(bookId: Long) = "metadata_$bookId"
    }
}
