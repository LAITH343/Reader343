package com.reader343

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.reader343.data.repo.LibraryRepository
import com.reader343.data.repo.SettingsRepository
import com.reader343.domain.AppSettings
import com.reader343.domain.ThemeMode
import com.reader343.ui.nav.AppNav
import com.reader343.ui.nav.ReaderRequest
import com.reader343.ui.settings.AppLocales
import com.reader343.ui.theme.Reader343Theme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var libraryRepository: LibraryRepository

    private val continueRequests = Channel<ReaderRequest>(Channel.CONFLATED)
    private val continueFlow = continueRequests.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            val applied = AppLocales.current()
            if (settingsRepository.settings.first().language != applied) settingsRepository.setLanguage(applied)
        }
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val systemDark = isSystemInDarkTheme()
            settings?.let { loaded ->
                val darkTheme = loaded.isDark(systemDark)
                DisposableEffect(darkTheme) {
                    val style = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
                    onDispose {}
                }
                Reader343Theme(darkTheme = darkTheme) {
                    AppNav(continueRequests = continueFlow)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_CONTINUE_READING -> lifecycleScope.launch {
                continueRequests.send(ReaderRequest(libraryRepository.continueBook()?.id))
            }
            ACTION_OPEN_READER -> {
                val bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1L).takeIf { it >= 0 } ?: return
                continueRequests.trySend(ReaderRequest(bookId, intent.getIntExtra(EXTRA_PAGE, -1)))
            }
        }
    }

    private fun AppSettings.isDark(system: Boolean): Boolean = when (theme) {
        ThemeMode.System -> system
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    companion object {
        const val ACTION_CONTINUE_READING = "com.reader343.action.CONTINUE_READING"
        const val ACTION_OPEN_READER = "com.reader343.action.OPEN_READER"
        const val EXTRA_BOOK_ID = "com.reader343.extra.BOOK_ID"
        const val EXTRA_PAGE = "com.reader343.extra.PAGE"
    }
}
