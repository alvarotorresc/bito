package com.alvarotc.bito.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

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
 * travels anywhere. The two reconcile at [SettingsViewModel.setLanguage] and
 * [com.alvarotc.bito.ui.onboarding.OnboardingViewModel.setLanguage], the only two call sites that
 * write DataStore's tag and call [apply] together, in the same breath. One gap remains, tracked for
 * M9.5, not fixed here: restoring a backup writes `languageTag` straight to DataStore without a
 * matching [apply] call, so the restored language sits unapplied — AppCompat keeps resolving
 * whatever it last cached — until the user opens Settings and picks a language again, which is the
 * only path that calls [apply].
 */
object AppLocale {
    /** null → follow the system. Persisting is the VM's job; this only applies. */
    fun apply(tag: String?) =
        AppCompatDelegate.setApplicationLocales(
            if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
}
