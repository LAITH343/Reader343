package com.reader343.tts

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.reader343.MainActivity
import com.reader343.R
import com.reader343.data.repo.SettingsRepository
import com.reader343.domain.AppLanguage
import com.reader343.domain.AppSettings
import com.reader343.domain.ReadAloudProgress
import com.reader343.domain.SpeechUnit
import com.reader343.domain.ThemeMode
import com.reader343.domain.TtsState
import com.reader343.domain.TtsStatus
import com.reader343.domain.chapterAt
import com.reader343.domain.estimateReadAloudProgress
import com.reader343.domain.readAloudSpeedLabel
import com.reader343.ui.settings.localizedFor
import com.reader343.ui.theme.DarkAppColors
import com.reader343.ui.theme.LightAppColors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import javax.inject.Inject

@AndroidEntryPoint
class TtsPlaybackService : Service() {

    @Inject
    lateinit var controller: TtsController

    @Inject
    lateinit var player: ReadAloudPlayer

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private lateinit var focusRequest: AudioFocusRequestCompat

    private var settings = AppSettings()
    private var channelLanguage: AppLanguage? = null
    private var hasFocus = false
    private var pausedByFocus = false
    private var noisyRegistered = false
    private var coverPath: String? = null
    private var cover: Bitmap? = null
    private var pageUnits: List<SpeechUnit> = emptyList()
    private var progress: ReadAloudProgress? = null

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pausedByFocus = false
                controller.pause()
            }
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> if (pausedByFocus) {
                pausedByFocus = false
                if (settings.readAloud.resumeAfterCall) controller.resume() else render(controller.state.value)
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByFocus = false
                controller.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> if (controller.state.value.status == TtsStatus.Playing) {
                pausedByFocus = true
                controller.pause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = checkNotNull(getSystemService())
        focusRequest = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributesCompat.Builder()
                    .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(focusListener, Handler(Looper.getMainLooper()))
            .build()
        session = MediaSessionCompat(this, SESSION_TAG).apply {
            setCallback(sessionCallback)
        }
        ensureChannel()
        scope.launch {
            combine(controller.state, player.book, settingsRepository.settings) { state, _, loaded ->
                settings = loaded
                state
            }.collectLatest { state ->
                if (settings.language != channelLanguage) ensureChannel()
                refresh(state)
                render(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> promote(buildNotification(controller.state.value))
            ACTION_TOGGLE -> if (controller.state.value.status == TtsStatus.Playing) controller.pause() else play()
            ACTION_NEXT -> controller.next()
            ACTION_PREVIOUS -> controller.previous()
            ACTION_SPEED -> player.cycleSpeed()
            ACTION_STOP -> player.stop()
        }
        if (intent?.action != ACTION_START && controller.state.value.status == TtsStatus.Idle) finish()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        unregisterNoisy()
        abandonFocus()
        session.isActive = false
        session.release()
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() = play()

        override fun onPause() = controller.pause()

        override fun onSkipToNext() = controller.next()

        override fun onSkipToPrevious() = controller.previous()

        override fun onStop() = player.stop()

        override fun onCustomAction(action: String?, extras: Bundle?) {
            when (action) {
                CUSTOM_SPEED -> player.cycleSpeed()
                CUSTOM_STOP -> player.stop()
            }
        }
    }

    private fun play() {
        when (controller.state.value.status) {
            TtsStatus.Playing -> Unit
            TtsStatus.Paused -> {
                controller.clearIssue()
                controller.resume()
            }
            TtsStatus.Idle -> player.toggle()
        }
    }

    private suspend fun refresh(state: TtsState) {
        val book = player.book.value
        val path = book?.coverPath
        if (path != coverPath) {
            coverPath = path
            cover = path?.let { loadCover(it) }
        }
        val position = state.position
        if (book == null || position == null) {
            pageUnits = emptyList()
            progress = null
            return
        }
        pageUnits = player.pageUnits(position.page)
        val chapter = book.outline.chapterAt(position.page, book.pageCount)
        progress = estimateReadAloudProgress(
            pageUnits = pageUnits,
            sentenceIndex = position.sentenceIndex,
            page = position.page,
            startPage = chapter?.startPage ?: 0,
            endPage = chapter?.endPage ?: book.pageCount,
        )
    }

    private fun render(state: TtsState) {
        when (state.status) {
            TtsStatus.Idle -> {
                finish()
                return
            }
            TtsStatus.Playing -> {
                pausedByFocus = false
                requestFocus()
                registerNoisy()
            }
            TtsStatus.Paused -> {
                unregisterNoisy()
                if (!pausedByFocus) abandonFocus()
            }
        }
        session.setPlaybackState(playbackState(state))
        session.setMetadata(metadata(state))
        session.isActive = true
        val notification = buildNotification(state)
        if (state.status == TtsStatus.Playing || pausedByFocus) {
            promote(notification)
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            post(notification)
        }
    }

    private fun finish() {
        unregisterNoisy()
        abandonFocus()
        pausedByFocus = false
        session.isActive = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun promote(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        } catch (_: IllegalStateException) {
            post(notification)
        }
    }

    private fun post(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
    }

    private fun requestFocus() {
        if (hasFocus) return
        hasFocus = AudioManagerCompat.requestAudioFocus(audioManager, focusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!hasFocus) controller.pause()
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        hasFocus = false
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, focusRequest)
    }

    private fun registerNoisy() {
        if (noisyRegistered) return
        ContextCompat.registerReceiver(
            this,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        unregisterReceiver(noisyReceiver)
        noisyRegistered = false
    }

    private fun localized(): Context = localizedFor(settings.language)

    private fun ensureChannel() {
        channelLanguage = settings.language
        val context = localized()
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.read_aloud_channel_name))
            .setDescription(context.getString(R.string.read_aloud_channel_description))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun subtitle(context: Context, state: TtsState): String? {
        val book = player.book.value ?: return null
        val page = state.position?.page ?: return null
        book.outline.chapterAt(page, book.pageCount)?.let { return it.title }
        val format = NumberFormat.getIntegerInstance(context.resources.configuration.locales[0])
        return context.getString(R.string.reader_subtitle_page, format.format(page + 1))
    }

    private fun statusLine(context: Context, state: TtsState): String? {
        val position = state.position ?: return null
        val total = pageUnits.size.takeIf { it > 0 } ?: return null
        val index = pageUnits.indexOfFirst { it.sentenceIndex == position.sentenceIndex }.coerceAtLeast(0)
        val format = NumberFormat.getIntegerInstance(context.resources.configuration.locales[0])
        val line = context.getString(R.string.read_aloud_status, format.format(index + 1), format.format(total))
        return if (state.status == TtsStatus.Playing) line else context.getString(R.string.read_aloud_status_paused, line)
    }

    private fun speedName(context: Context, state: TtsState): String =
        context.getString(R.string.read_aloud_speed_value, readAloudSpeedLabel(state.rate))

    private fun playbackState(state: TtsState): PlaybackStateCompat {
        val context = localized()
        val playing = state.status == TtsStatus.Playing
        return PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP,
            )
            .setState(
                if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                progress?.positionMs ?: PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                if (playing) state.rate else 0f,
            )
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(CUSTOM_SPEED, speedName(context, state), R.drawable.ic_ph_gauge)
                    .build(),
            )
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    CUSTOM_STOP,
                    context.getString(R.string.read_aloud_stop),
                    R.drawable.ic_ph_stop,
                ).build(),
            )
            .build()
    }

    private fun metadata(state: TtsState): MediaMetadataCompat {
        val context = localized()
        val book = player.book.value
        val subtitle = subtitle(context, state)
        val status = statusLine(context, state)
        return MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, book?.title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, book?.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, status)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, status)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, cover)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, progress?.durationMs ?: -1L)
            .build()
    }

    private fun buildNotification(state: TtsState): Notification {
        val context = localized()
        val book = player.book.value
        val playing = state.status == TtsStatus.Playing
        val toggle = if (playing) {
            action(R.drawable.ic_ph_pause, context.getString(R.string.read_aloud_pause), ACTION_TOGGLE)
        } else {
            action(R.drawable.ic_ph_play, context.getString(R.string.read_aloud_play), ACTION_TOGGLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ph_book_open)
            .setColor(accentColor())
            .setContentTitle(book?.title ?: context.getString(R.string.read_aloud_notification_tag))
            .setContentText(subtitle(context, state))
            .setSubText(context.getString(R.string.read_aloud_notification_tag))
            .setLargeIcon(cover)
            .setContentIntent(openReaderIntent(book?.id, state.position?.page))
            .setDeleteIntent(serviceIntent(ACTION_STOP))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(action(R.drawable.ic_ph_skip_back, context.getString(R.string.read_aloud_previous), ACTION_PREVIOUS))
            .addAction(toggle)
            .addAction(action(R.drawable.ic_ph_skip_forward, context.getString(R.string.read_aloud_next), ACTION_NEXT))
            .addAction(action(R.drawable.ic_ph_gauge, speedName(context, state), ACTION_SPEED))
            .addAction(action(R.drawable.ic_ph_stop, context.getString(R.string.read_aloud_stop), ACTION_STOP))
            .setStyle(
                MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(1, 2),
            )
            .build()
    }

    private fun action(icon: Int, title: String, command: String): NotificationCompat.Action =
        NotificationCompat.Action(icon, title, serviceIntent(command))

    private fun serviceIntent(command: String): PendingIntent =
        PendingIntent.getService(
            this,
            command.hashCode(),
            Intent(this, TtsPlaybackService::class.java).setAction(command),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openReaderIntent(bookId: Long?, page: Int?): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (bookId != null) {
            intent.setAction(MainActivity.ACTION_OPEN_READER)
                .putExtra(MainActivity.EXTRA_BOOK_ID, bookId)
                .putExtra(MainActivity.EXTRA_PAGE, page ?: -1)
        }
        return PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun accentColor(): Int {
        val systemDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = when (settings.theme) {
            ThemeMode.System -> systemDark
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
        return (if (dark) DarkAppColors.acc else LightAppColors.acc).toArgb()
    }

    private suspend fun loadCover(path: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= COVER_SIZE_PX && bounds.outHeight / (sample * 2) >= COVER_SIZE_PX) {
                sample *= 2
            }
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    companion object {
        const val CHANNEL_ID = "read_aloud"
        private const val NOTIFICATION_ID = 4301
        private const val SESSION_TAG = "ReadAloud"
        private const val COVER_SIZE_PX = 320
        private const val ACTION_START = "com.reader343.action.READ_ALOUD_START"
        private const val ACTION_TOGGLE = "com.reader343.action.READ_ALOUD_TOGGLE"
        private const val ACTION_NEXT = "com.reader343.action.READ_ALOUD_NEXT"
        private const val ACTION_PREVIOUS = "com.reader343.action.READ_ALOUD_PREVIOUS"
        private const val ACTION_SPEED = "com.reader343.action.READ_ALOUD_SPEED"
        private const val ACTION_STOP = "com.reader343.action.READ_ALOUD_STOP"
        private const val CUSTOM_SPEED = "speed"
        private const val CUSTOM_STOP = "stop"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, TtsPlaybackService::class.java).setAction(ACTION_START),
                )
            } catch (_: IllegalStateException) {
            }
        }
    }
}
