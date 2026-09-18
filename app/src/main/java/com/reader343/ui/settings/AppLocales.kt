package com.reader343.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.reader343.domain.AppLanguage

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
