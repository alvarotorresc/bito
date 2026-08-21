package com.alvarotc.bito

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.alvarotc.bito.ui.AppVisibility
import com.alvarotc.bito.ui.BitoNavHost
import com.alvarotc.bito.ui.NavRequests
import com.alvarotc.bito.ui.notifications.Notifier
import com.alvarotc.bito.ui.theme.BitoTheme

// AppCompatActivity, not ComponentActivity: per-app language needs somewhere to persist the
// chosen locale that survives process death and applies before the first frame, and this class
// swap is what buys that for free. AppCompatDelegate keeps its own locale cache and restores it
// automatically here on API<33 -- but ONLY because the manifest opts into that cache via the
// AppLocalesMetadataHolderService/autoStoreLocales declaration (see AppLocale.kt's KDoc for why
// that's needed: being an AppCompatActivity alone persists nothing). On 33+ the framework itself
// restores the per-app locale from localeConfig before this activity is even created. So onCreate
// below never reads Settings.languageTag from DataStore — AppLocale.apply (called from
// SettingsViewModel.setLanguage and OnboardingViewModel.setLanguage) is the only place that
// touches AppCompatDelegate, and only when the user changes the setting, not on every launch.
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Process death redelivers the ORIGINAL launch Intent (a fresh parcel, extra intact —
        // removeExtra below never touched it) alongside a non-null savedInstanceState, so without
        // this guard a resurrected activity would replay whatever route the user tapped before
        // the process died. onNewIntent covers every fresh tap on its own, independent of this.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            BitoTheme {
                BitoNavHost((application as BitoApp).container)
            }
        }
    }

    // singleTop (manifest) keeps the same instance alive for a notification tap while it's in
    // the foreground or the back stack, so this is what actually delivers the route extra then.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Standard singleTop idiom: without this, getIntent() would keep returning the stale
        // launch Intent instead of this one for as long as the activity stays alive.
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.visible = true
    }

    override fun onStop() {
        AppVisibility.visible = false
        super.onStop()
    }

    /**
     * Bridges a notification tap's [Notifier.EXTRA_OPEN_ROUTE] extra to [NavRequests]. Clears the
     * extra afterwards as defense in depth so the same consumed value is never read twice off the
     * same Intent object — [onCreate]'s `savedInstanceState == null` guard is what actually stops
     * a recreated activity (rotation or process death alike) from replaying it. `onNewIntent`'s
     * own delivery doesn't depend on the clear (each tap hands this a fresh Intent that's already
     * been forwarded above); it still matters there because [onNewIntent] calls `setIntent` first,
     * folding that same Intent into the activity's stored one.
     */
    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(Notifier.EXTRA_OPEN_ROUTE)?.let(NavRequests::open)
        intent?.removeExtra(Notifier.EXTRA_OPEN_ROUTE)
    }
}
