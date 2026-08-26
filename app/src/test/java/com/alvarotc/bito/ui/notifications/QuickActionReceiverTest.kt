package com.alvarotc.bito.ui.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith

/**
 * Drives [QuickActionReceiver]'s injectable seam — specifically the notifier gap closed to match
 * the widget's `LogHabitAction` fix: a perfect-day notifier failure mid-tap must never skip
 * clearing the tapped notification or refreshing the GLOBAL tray.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QuickActionReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        NotificationChannels.ensure(context)
    }

    /** Posts a stand-in notification under [id], simulating the reminder the action was tapped on. */
    private fun postReminder(id: Int) {
        val notification =
            NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_habi)
                .setContentTitle("stand-in")
                .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    @Test
    fun `a notifier failure never skips the notification clear or the tray refresh`() =
        runTest {
            val habitNotificationId = 42
            postReminder(habitNotificationId)
            var refreshedWith: Boolean? = null

            QuickActionReceiver.run(
                context = context,
                notificationId = habitNotificationId,
                log = { true },
                notifyPerfectDay = { error("POST_NOTIFICATIONS revoked mid-flight") },
                refreshTray = { refreshedWith = it },
            )

            assertNull(shadowOf(notificationManager).getNotification(habitNotificationId))
            assertEquals(false, refreshedWith)
        }

    @Test
    fun `cancellation is not a failure — it propagates and the refresh does not run`() =
        runTest {
            var refreshed = false

            assertFailsWith<CancellationException> {
                QuickActionReceiver.run(
                    context = context,
                    notificationId = 42,
                    log = { false },
                    notifyPerfectDay = { throw CancellationException("scope torn down") },
                    refreshTray = { refreshed = true },
                )
            }

            assertFalse(refreshed)
        }

    @Test
    fun `an action from the global tray refreshes it as active and leaves its id alone`() =
        runTest {
            postReminder(Notifier.REMINDER_ID)
            var refreshedWith: Boolean? = null
            var notifiedReached: Boolean? = null

            QuickActionReceiver.run(
                context = context,
                notificationId = Notifier.REMINDER_ID,
                log = { true },
                notifyPerfectDay = { notifiedReached = it },
                refreshTray = { refreshedWith = it },
            )

            // The GLOBAL tray is TrayRefresher's to keep honest — the receiver never cancels it.
            assertNotNull(shadowOf(notificationManager).getNotification(Notifier.REMINDER_ID))
            assertEquals(true, refreshedWith)
            assertTrue(notifiedReached == true)
        }
}
