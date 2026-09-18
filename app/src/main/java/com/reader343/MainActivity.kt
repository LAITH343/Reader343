package com.reader343

import android.content.Intent
import android.graphics.Color
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

    private val continueRequests = Channel<Long?>(Channel.CONFLATED)
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
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                        navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
                    )
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
        if (intent?.action != ACTION_CONTINUE_READING) return
        lifecycleScope.launch { continueRequests.send(libraryRepository.continueBook()?.id) }
    }

    private fun AppSettings.isDark(system: Boolean): Boolean = when (theme) {
        ThemeMode.System -> system
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    companion object {
        const val ACTION_CONTINUE_READING = "com.reader343.action.CONTINUE_READING"
        private val LightScrim = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        private val DarkScrim = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
