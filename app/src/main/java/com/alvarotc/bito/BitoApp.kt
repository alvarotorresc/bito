package com.alvarotc.bito

import android.app.Application

class BitoApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        AppStartup.start(this, container)
    }
}
