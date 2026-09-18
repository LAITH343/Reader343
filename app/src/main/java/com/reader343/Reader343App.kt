package com.reader343

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.reader343.data.repo.SettingsRepository
import com.reader343.di.ApplicationScope
import com.reader343.reminders.ReminderNotifier
import com.reader343.reminders.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class Reader343App : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var reminderNotifier: ReminderNotifier

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        reminderNotifier.ensureChannel()
        appScope.launch {
            settingsRepository.settings
                .map { it.reminders }
                .distinctUntilChanged()
                .collect { reminderScheduler.sync(it) }
        }
    }
}
