package com.reader343.update

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.text.format.Formatter
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.reader343.MainActivity
import com.reader343.R
import com.reader343.data.repo.SettingsRepository
import com.reader343.data.repo.UpdateRepository
import com.reader343.data.repo.UpdateStatus
import com.reader343.domain.AppLanguage
import com.reader343.ui.settings.localizedFor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UpdateDownloadService : Service() {

    @Inject
    lateinit var downloader: ApkDownloader

    @Inject
    lateinit var repository: UpdateRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var updateNotifier: UpdateNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var language: AppLanguage = AppLanguage.System
    private var lastPercent = -1
    private var lastDownloading: Boolean? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels()
        scope.launch {
            combine(repository.status, settingsRepository.settings) { status, settings ->
                if (settings.language != language) {
                    language = settings.language
                    ensureChannels()
                    lastPercent = -1
                    lastDownloading = null
                }
                status
            }.collect(::render)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                NotificationManagerCompat.from(this).cancel(READY_NOTIFICATION_ID)
                updateNotifier.cancelAvailable()
                promote(progressNotification(downloader.state.value, null))
            }
            ACTION_PAUSE -> downloader.pause()
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        downloader.pause()
        finish()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(status: UpdateStatus) {
        when (val download = status.download) {
            is DownloadState.Downloading -> {
                val percent = percentOf(download.bytes, download.total)
                if (lastDownloading == true && percent == lastPercent) return
                lastDownloading = true
                lastPercent = percent
                promote(progressNotification(download, status.release?.versionName))
            }
            DownloadState.Verifying -> {
                if (lastDownloading == false) return
                lastDownloading = false
                promote(progressNotification(download, status.release?.versionName))
            }
            is DownloadState.Ready -> {
                if (lastDownloading != null) post(READY_NOTIFICATION_ID, readyNotification(status.release?.versionName))
                finish()
            }
            else -> finish()
        }
    }

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        NotificationManagerCompat.from(this).cancel(PROGRESS_NOTIFICATION_ID)
        stopSelf()
    }

    private fun promote(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, PROGRESS_NOTIFICATION_ID, notification, type)
        } catch (_: IllegalStateException) {
            post(PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    private fun post(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this).notify(id, notification)
    }

    private fun localized(): Context = localizedFor(language)

    private fun ensureChannels() {
        val context = localized()
        val manager = NotificationManagerCompat.from(this)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(PROGRESS_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.update_progress_channel_name))
                .setDescription(context.getString(R.string.update_progress_channel_description))
                .setShowBadge(false)
                .build(),
        )
        updateNotifier.ensureChannel(language)
    }

    private fun progressNotification(download: DownloadState, versionName: String?): Notification {
        val context = localized()
        val builder = NotificationCompat.Builder(this, PROGRESS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ph_download_simple)
            .setContentTitle(context.getString(R.string.update_stage_downloading))
            .setSubText(versionName?.let { context.getString(R.string.update_headline_version, it) })
            .setContentIntent(openUpdateIntent())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        if (download is DownloadState.Downloading) {
            val percent = percentOf(download.bytes, download.total)
            builder
                .setContentText(
                    context.getString(
                        R.string.update_transferred,
                        Formatter.formatShortFileSize(context, download.bytes),
                        Formatter.formatShortFileSize(context, download.total),
                    ),
                )
                .setProgress(PERCENT_MAX, percent.coerceAtLeast(0), percent < 0)
                .addAction(
                    R.drawable.ic_ph_pause,
                    context.getString(R.string.update_action_pause),
                    serviceIntent(ACTION_PAUSE),
                )
        } else {
            builder
                .setContentText(context.getString(R.string.update_sub_verifying))
                .setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun readyNotification(versionName: String?): Notification {
        val context = localized()
        val body = versionName?.let { context.getString(R.string.update_ready_body_version, it) }
            ?: context.getString(R.string.update_ready_body)
        return NotificationCompat.Builder(this, UpdateNotifier.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ph_download_simple)
            .setContentTitle(context.getString(R.string.update_kicker_ready))
            .setContentText(body)
            .setContentIntent(openUpdateIntent())
            .addAction(
                R.drawable.ic_ph_download_simple,
                context.getString(R.string.update_action_install),
                openUpdateIntent(),
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
    }

    private fun serviceIntent(command: String): PendingIntent =
        PendingIntent.getService(
            this,
            command.hashCode(),
            Intent(this, UpdateDownloadService::class.java).setAction(command),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openUpdateIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            READY_NOTIFICATION_ID,
            MainActivity.updateIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun percentOf(bytes: Long, total: Long): Int =
        if (total > 0L) (bytes * PERCENT_MAX / total).toInt().coerceIn(0, PERCENT_MAX) else -1

    companion object {
        private const val PROGRESS_CHANNEL_ID = "update_progress"
        private const val PROGRESS_NOTIFICATION_ID = 4401
        private const val READY_NOTIFICATION_ID = 4402
        private const val PERCENT_MAX = 100
        private const val ACTION_START = "com.reader343.action.UPDATE_DOWNLOAD_START"
        private const val ACTION_PAUSE = "com.reader343.action.UPDATE_DOWNLOAD_PAUSE"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, UpdateDownloadService::class.java).setAction(ACTION_START),
                )
            } catch (_: IllegalStateException) {
            }
        }
    }
}
