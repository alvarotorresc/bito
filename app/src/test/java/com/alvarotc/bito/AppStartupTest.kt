package com.alvarotc.bito

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.ui.notifications.NotificationChannels
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Cross-lane smoke test: start() must not throw and must leave both channels behind. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppStartupTest {
    @Test
    fun `start does not throw and creates both notification channels`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)

        AppStartup.start(app, container)

        val channelIds =
            NotificationManagerCompat.from(app)
                .notificationChannelsCompat
                .map { it.id }
        assertTrue(channelIds.contains(NotificationChannels.REMINDERS))
        assertTrue(channelIds.contains(NotificationChannels.REVIEW))
    }
}
