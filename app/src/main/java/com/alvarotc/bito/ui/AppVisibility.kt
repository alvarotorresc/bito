package com.alvarotc.bito.ui

/**
 * Whether the app is currently in the foreground. Flipped by MainActivity's `onStart`/`onStop`
 * (T11); read by the out-of-app write paths (the quick-action receiver, the widget) so the
 * perfect-day notification never fires while the celebration sheet can show in-app instead.
 */
object AppVisibility {
    @Volatile
    var visible: Boolean = false
}
