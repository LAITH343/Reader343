package com.reader343.ui.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.reader343.domain.AppLanguage
import java.util.Locale

object AppLocales {

    fun apply(language: AppLanguage) {
        val target = when (language) {
            AppLanguage.System -> LocaleListCompat.getEmptyLocaleList()
            else -> LocaleListCompat.forLanguageTags(language.tag)
        }
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != target.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(target)
        }
    }

    fun current(): AppLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return AppLanguage.System
        val language = locales[0]?.language
        return AppLanguage.entries.firstOrNull { it != AppLanguage.System && it.tag == language } ?: AppLanguage.System
    }
}

fun Context.localizedFor(language: AppLanguage): Context {
    if (language == AppLanguage.System || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return this
    val config = Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(language.tag)) }
    return createConfigurationContext(config)
}
