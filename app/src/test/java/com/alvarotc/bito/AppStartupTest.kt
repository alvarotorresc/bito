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

/**
 * Cross-lane smoke test: start() must not throw and must leave all three channels behind.
 * Uses a bare [Application] so the manifest's BitoApp doesn't boot its own AppContainer in
 * onCreate() — that second DataStore on the same settings file crashes a background coroutine
 * ("multiple DataStores active") that surfaces as UncaughtExceptionsBeforeTest in whichever
 * test runs next on the worker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppStartupTest {
    @Test
    fun `start does not throw and creates all three notification channels`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val container = AppContainer(app)

        AppStartup.start(app, container)

        val channelIds =
            NotificationManagerCompat.from(app)
                .notificationChannelsCompat
                .map { it.id }
        assertTrue(channelIds.contains(NotificationChannels.REMINDERS))
        assertTrue(channelIds.contains(NotificationChannels.REVIEW))
        // Proves the celebrations channel exists synchronously before any component (the
        // widget's LogHabitAction included) can run — start() is called from BitoApp.onCreate()
        // directly, not from a launched coroutine, so PerfectDayNotifier.maybeNotify (T13) never
        // races it.
        assertTrue(channelIds.contains(NotificationChannels.CELEBRATIONS))
    }
}
