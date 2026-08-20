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
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun open(route: String) {
        _pending.value = route
    }

    fun consume() {
        _pending.value = null
    }
}
