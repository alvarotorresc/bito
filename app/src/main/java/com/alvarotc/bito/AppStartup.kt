package com.alvarotc.bito

import android.app.Application

/** Process-wide services (channels, widget refresh, alarm sync) hang off here. */
object AppStartup {
    fun start(
        app: Application,
        container: AppContainer,
    ) = Unit
}
