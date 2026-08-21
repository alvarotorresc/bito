package com.alvarotc.bito.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression guard for the M9 "language survives process death below API 33" fix:
 * [androidx.appcompat.app.AppCompatDelegate.setApplicationLocales] persists nothing on its own
 * there, unless the manifest opts into AppCompat's own on-disk cache via this exact
 * `AppLocalesMetadataHolderService` declaration. Parses the REAL merged manifest (Robolectric's
 * `isIncludeAndroidResources` loads it the same way the OS would) rather than trusting a
 * hand-maintained duplicate of the XML. See [AppLocale]'s KDoc for how this cache and
 * `Settings.languageTag` reconcile.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppLocaleManifestTest {
    @Test
    fun `the manifest opts into AppCompat's autoStoreLocales cache`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val component = ComponentName(context, "androidx.appcompat.app.AppLocalesMetadataHolderService")
        val info =
            context.packageManager.getServiceInfo(
                component,
                PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS,
            )

        // Declared disabled on purpose (it only ever needs to exist for its meta-data, AppCompat
        // never starts it) -- MATCH_DISABLED_COMPONENTS above is what still surfaces it here.
        assertFalse(info.enabled)
        assertEquals(true, info.metaData.getBoolean("autoStoreLocales"))
    }
}
