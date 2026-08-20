package com.alvarotc.bito.ui.notifications

import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.R
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.AppVisibility
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Uses a bare [Application] (not the manifest's BitoApp) so onCreate()'s AppStartup.start() —
 * which builds its own AppContainer — never opens a second DataStore on the same settings file
 * as the one built explicitly below (same reasoning as [com.alvarotc.bito.ui.BitoNavHostTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PerfectDayNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @After
    fun tearDown() {
        // AppVisibility is a process-wide singleton — leaving it flipped would leak into
        // whichever test class runs next in this JVM fork.
        AppVisibility.visible = false
    }

    @Test
    fun `bodyFor interpolates the user name in the personality voice`() {
        val named = Settings(userName = "Álvaro", personality = Personality.SARGENTO)
        assertTrue(PerfectDayNotifier.bodyFor(context, named).contains("Álvaro"))

        val blank = Settings(userName = "", personality = Personality.SARGENTO)
        val fallback = context.getString(R.string.habi_name_fallback)
        assertTrue(PerfectDayNotifier.bodyFor(context, blank).contains(fallback))
    }

    @Test
    fun `maybeNotify posts nothing when the toggle is off or the app is visible`() {
        NotificationChannels.ensure(context)
        val container = AppContainer(context)

        runBlocking { container.settings.update { it.copy(perfectDayCelebration = false) } }
        AppVisibility.visible = false
        runBlocking { PerfectDayNotifier.maybeNotify(context, container, reachedNow = true) }
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())

        runBlocking { container.settings.update { it.copy(perfectDayCelebration = true) } }
        AppVisibility.visible = true
        runBlocking { PerfectDayNotifier.maybeNotify(context, container, reachedNow = true) }
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }

    @Test
    fun `maybeNotify posts on the celebrations channel when reached, enabled and hidden`() {
        NotificationChannels.ensure(context)
        val container = AppContainer(context)
        runBlocking {
            container.settings.update {
                it.copy(perfectDayCelebration = true, personality = Personality.NEUTRA, userName = "Álvaro")
            }
        }
        AppVisibility.visible = false

        runBlocking { PerfectDayNotifier.maybeNotify(context, container, reachedNow = true) }

        val notifications = shadowOf(notificationManager).allNotifications
        assertEquals(1, notifications.size)
        assertEquals(NotificationChannels.CELEBRATIONS, notifications.single().channelId)
    }
}
