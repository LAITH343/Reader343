package com.reader343.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reader343.MainActivity
import com.reader343.R
import com.reader343.domain.AppLanguage
import com.reader343.ui.settings.localizedFor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manager: NotificationManagerCompat get() = NotificationManagerCompat.from(context)

    fun ensureChannel(language: AppLanguage) {
        val context = context.localizedFor(language)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.update_ready_channel_name))
                .setDescription(context.getString(R.string.update_ready_channel_description))
                .build(),
        )
    }

    fun showAvailable(language: AppLanguage, versionName: String) {
        ensureChannel(language)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val context = context.localizedFor(language)
        val body = context.getString(R.string.update_available_body, versionName)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ph_download_simple)
            .setContentTitle(context.getString(R.string.update_kicker_available))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openUpdateIntent())
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        manager.notify(AVAILABLE_NOTIFICATION_ID, notification)
    }

    fun cancelAvailable() = manager.cancel(AVAILABLE_NOTIFICATION_ID)

    private fun openUpdateIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            AVAILABLE_NOTIFICATION_ID,
            MainActivity.updateIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val CHANNEL_ID = "updates"
        private const val AVAILABLE_NOTIFICATION_ID = 4403
    }
}
