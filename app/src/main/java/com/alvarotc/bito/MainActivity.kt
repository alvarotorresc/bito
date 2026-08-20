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
     * the extra afterwards so a later recreation of this same Intent (e.g. a rotation) never
     * replays the same navigation.
     */
    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(Notifier.EXTRA_OPEN_ROUTE)?.let(NavRequests::open)
        intent?.removeExtra(Notifier.EXTRA_OPEN_ROUTE)
    }
}
