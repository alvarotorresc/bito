package com.alvarotc.bito.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The only place that touches [AppCompatDelegate] for per-app locales — everyone else goes through
 * [SettingsViewModel] (or [com.alvarotc.bito.ui.onboarding.OnboardingViewModel] during first run).
 *
 * Two stores end up holding the chosen language, and they exist for different reasons.
 * [com.alvarotc.bito.data.settings.Settings.languageTag] in DataStore is the source of truth: it's
 * what [com.alvarotc.bito.data.backup.BackupFile] carries in Bito's own export/import (its own
 * feature, independent of Android's unrelated auto-backup, which this app opts out of via
 * `allowBackup="false"`), and it's what every screen reads to decide what's selected. AppCompat's
 * own on-disk cache — written by [apply] below, read back automatically by AppCompatDelegate on
 * API<33 only because the manifest's `AppLocalesMetadataHolderService`/`autoStoreLocales`
 * declaration opts into it (see that entry in AndroidManifest.xml) — is a SEPARATE, on-device-only
 * applier cache: it's what actually resolves the locale before the first frame, but it isn't what
 * travels anywhere. The two reconcile at three call sites that write DataStore's tag and call
 * [apply] together, in the same breath: [SettingsViewModel.setLanguage],
 * [com.alvarotc.bito.ui.onboarding.OnboardingViewModel.setLanguage], and — closing the restore gap
 * this KDoc used to track for M9.5 — [com.alvarotc.bito.ui.settings.BackupViewModel.confirmImport],
 * which re-applies whatever `languageTag` the backup just persisted right after a successful
 * import, instead of leaving AppCompat to keep resolving whatever it last cached until the user
 * happens to open Settings and touch the language row again.
 */
object AppLocale {
    /** null → follow the system. Persisting is the VM's job; this only applies. */
    fun apply(tag: String?) =
        AppCompatDelegate.setApplicationLocales(
            if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )

    /**
     * What a stored `languageTag` actually displays as, for UI that needs to pick ONE of "es"/"en"
     * to highlight rather than SettingsScreen's textual third option (`R.string.language_system`).
     * Only "es"/"en" resolve to themselves; anything else — `null` ("follow the system") or a tag
     * `locales_config` doesn't declare (a backup restored from, or a device running, a locale
     * outside {es, en}) — falls through to [Locale.getDefault], the same "out-of-set == system"
     * bucket [SettingsScreen]'s language row already treats identically via its own `else` branch.
     * Falls all the way to "en" (the base resource language) only when even THAT resolved value
     * isn't "es"/"en" either — [com.alvarotc.bito.ui.onboarding.OnboardingScreen]'s WelcomeScene has
     * no third chip to land an unresolvable case on, unlike Settings' text row.
     */
    fun resolveDisplayLanguage(tag: String?): String =
        (tag.takeIf { it == "es" || it == "en" } ?: Locale.getDefault().language)
            .takeIf { it == "es" } ?: "en"
}
