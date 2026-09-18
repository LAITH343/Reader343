package com.reader343.reminders

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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manager: NotificationManagerCompat get() = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.reminder_channel_name))
            .setDescription(context.getString(R.string.reminder_channel_description))
            .build()
        manager.createNotificationChannel(channel)
    }

    fun showReading(bookTitle: String?) {
        val body = if (bookTitle != null) {
            context.getString(R.string.reminder_reading_body_book, bookTitle)
        } else {
            context.getString(R.string.reminder_reading_body)
        }
        post(ReminderType.Reading, context.getString(R.string.reminder_reading_title), body)
    }

    fun showStreak(days: Int) {
        val count = NumberFormat.getIntegerInstance(context.resources.configuration.locales[0]).format(days)
        post(
            ReminderType.Streak,
            context.resources.getQuantityString(R.plurals.reminder_streak_title, days, count),
            context.getString(R.string.reminder_streak_body),
        )
    }

    private fun post(type: ReminderType, title: String, body: String) {
        ensureChannel()
        if (!context.canPostReminders()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_library)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(continueIntent(type))
            .setAutoCancel(true)
            .build()
        manager.notify(type.notificationId, notification)
    }

    private fun continueIntent(type: ReminderType): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_CONTINUE_READING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            type.notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CHANNEL_ID = "reminders"
    }
}

fun Context.canPostReminders(): Boolean {
    val manager = NotificationManagerCompat.from(this)
    if (!manager.areNotificationsEnabled()) return false
    val channel = manager.getNotificationChannelCompat(ReminderNotifier.CHANNEL_ID) ?: return true
    return channel.importance != NotificationManagerCompat.IMPORTANCE_NONE
}
