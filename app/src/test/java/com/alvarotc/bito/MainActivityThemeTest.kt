package com.alvarotc.bito

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A bare AppCompatActivity themed with the app's real `Theme.Bito`, deliberately not
 * [MainActivity] itself: touching `(application as BitoApp).container` would start
 * [AppStartup]'s real background sync against a real [AppContainer], which is the leak the rest
 * of this suite goes out of its way to avoid (see [com.alvarotc.bito.ui.settings.SettingsScreenTest]'s
 * class doc). This activity isolates exactly the one thing MainActivity's `AppCompatActivity`
 * switch risks: AppCompatDelegate's `ensureSubDecor()` throws unless the theme resolves as a
 * `Theme.AppCompat.*` descendant, and that check fires from `onCreate`'s `setContentView` call
 * whether or not any container is ever touched.
 */
private class ThemeBitoCheckActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Bito)
        super.onCreate(savedInstanceState)
        setContentView(View(this))
    }
}

/**
 * Regression guard for the M9 "language applied" task: MainActivity became an AppCompatActivity
 * so AppCompatDelegate can persist/restore the per-app locale, which only works if the manifest
 * theme is an AppCompat descendant — a plain `android:Theme.*` parent compiles fine and crashes
 * on first launch. See `themes.xml`'s comment on `Theme.Bito` for the full mechanism.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityThemeTest {
    @Test
    fun `Theme_Bito resolves as an AppCompat descendant, so AppCompatActivity does not crash on create`() {
        // No assertion beyond "doesn't throw": IllegalStateException("You need to use a
        // Theme.AppCompat theme...") is exactly what a non-AppCompat theme produces here.
        Robolectric.buildActivity(ThemeBitoCheckActivity::class.java).create()
    }
}
