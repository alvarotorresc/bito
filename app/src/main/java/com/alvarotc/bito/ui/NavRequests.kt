package com.alvarotc.bito.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one bridge from an [android.content.Intent] to the NavHost (T11). [MainActivity] decodes a
 * notification tap's route extra and calls [open]; [BitoNavHost] observes [pending] and navigates
 * to it, then [consume]s it so the request only ever fires once.
 */
object NavRequests {
    /**
     * Routes an [android.content.Intent] extra is allowed to open. `MainActivity` is exported
     * (it's the LAUNCHER activity), so its extras are untrusted input from any app on the
     * device: without this allowlist, an arbitrary string would reach
     * `NavController.navigate(String)` in [BitoNavHost] and crash Bito with
     * `IllegalArgumentException` for any route that isn't a real destination.
     */
    private val deepLinkable = setOf("review")

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** No-ops for anything outside [deepLinkable] instead of forwarding it to the NavHost. */
    fun open(route: String) {
        if (route in deepLinkable) _pending.value = route
    }

    fun consume() {
        _pending.value = null
    }
}
