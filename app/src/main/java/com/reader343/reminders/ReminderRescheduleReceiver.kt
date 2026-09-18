package com.reader343.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.reader343.data.repo.SettingsRepository
import com.reader343.di.ApplicationScope
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderRescheduleReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun settingsRepository(): SettingsRepository

        fun scheduler(): ReminderScheduler

        @ApplicationScope
        fun scope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val deps = EntryPointAccessors.fromApplication(context, Dependencies::class.java)
        val force = intent.action != Intent.ACTION_BOOT_COMPLETED
        val pending = goAsync()
        deps.scope().launch {
            try {
                deps.scheduler().sync(deps.settingsRepository().settings.first().reminders, force)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
