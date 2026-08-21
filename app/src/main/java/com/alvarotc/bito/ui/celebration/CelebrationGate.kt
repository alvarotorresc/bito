package com.alvarotc.bito.ui.celebration

/** Pure gate for the perfect-day notification fired from outside the app. */
object CelebrationGate {
    /** The perfect-day NOTIFICATION fires only out of the app, only if the toggle is on, only the instant the grant lands. */
    fun shouldNotifyPerfectDay(
        reachedNow: Boolean,
        celebrationEnabled: Boolean,
        appVisible: Boolean,
    ): Boolean = reachedNow && celebrationEnabled && !appVisible
}
