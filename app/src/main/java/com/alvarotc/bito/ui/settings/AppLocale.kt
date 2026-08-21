package com.alvarotc.bito.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** The only place that touches [AppCompatDelegate] for per-app locales — everyone else goes through [SettingsViewModel]. */
object AppLocale {
    /** null → follow the system. Persisting is the VM's job; this only applies. */
    fun apply(tag: String?) =
        AppCompatDelegate.setApplicationLocales(
            if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
}
