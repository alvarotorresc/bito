package com.alvarotc.bito

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.alvarotc.bito.ui.AppVisibility
import com.alvarotc.bito.ui.BitoNavHost
import com.alvarotc.bito.ui.NavRequests
import com.alvarotc.bito.ui.notifications.Notifier
import com.alvarotc.bito.ui.theme.BitoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
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
     * Bridges a notification tap's [Notifier.EXTRA_OPEN_ROUTE] extra to [NavRequests]. Clears
     * the extra afterwards: it only matters for the `onCreate`/rotation path — `getIntent()`
     * keeps returning this same stored Intent across an activity recreation (e.g. a rotation),
     * so without this, `onCreate` would re-read the already-consumed extra and replay the
     * navigation. `onNewIntent`'s own delivery doesn't depend on it (each tap hands this a fresh
     * Intent that's already been forwarded above); it matters there too only because
     * [onNewIntent] now calls `setIntent` first, folding that same Intent into the activity's
     * stored one for any recreation that happens afterward.
     */
    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(Notifier.EXTRA_OPEN_ROUTE)?.let(NavRequests::open)
        intent?.removeExtra(Notifier.EXTRA_OPEN_ROUTE)
    }
}
