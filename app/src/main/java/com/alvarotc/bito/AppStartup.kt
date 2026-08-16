package com.alvarotc.bito

import android.app.Application
import com.alvarotc.bito.ui.notifications.NotificationChannels
import com.alvarotc.bito.ui.notifications.ReminderSync
import com.alvarotc.bito.ui.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide services (channels, widget refresh, alarm sync) hang off here. */
object AppStartup {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start(
        app: Application,
        container: AppContainer,
    ) {
        NotificationChannels.ensure(app)
        WidgetRefresher.start(app, container, scope)
        ReminderSync.start(app, container, scope)
    }
}
