package com.alvarotc.bito.ui.notifications

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.BitoApp
import com.alvarotc.bito.R
import com.alvarotc.bito.data.db.TaskEntity
import com.alvarotc.bito.data.settings.FocusSession
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.TaskStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Drives [FocusAlarm]'s exact-alarm scheduling (molde de `ExactAlarmReceiverTest:97`, que ya usa
 * `shadowOf(alarmManager).scheduledAlarms`), the two focus notifications on [Notifier], and the
 * two goAsync receivers — [FocusReceiver] and `BootReceiver`'s focus block — dispatched through
 * [Context.sendBroadcast] against the manifest so `goAsync()`/`finish()` see a real pending
 * result. The class default runs with a bare [Application] (no [BitoApp] boot, no `AppStartup`
 * collectors) for the tests that don't need a container; the two receiver tests override that
 * per-method to get a real [BitoApp].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FocusAlarmTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        NotificationChannels.ensure(context)
        shadowOf(notificationManager).setNotificationsEnabled(true)
    }

    @Suppress("DEPRECATION") // ShadowAlarmManager.ScheduledAlarm#operation has no replacement accessor.
    @Test
    fun `scheduling leaves exactly one pending alarm`() {
        FocusAlarm.schedule(context, System.currentTimeMillis() + 5 * 60_000L)

        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Suppress("DEPRECATION") // ShadowAlarmManager.ScheduledAlarm#operation has no replacement accessor.
    @Test
    fun `scheduling again replaces it instead of stacking`() {
        val now = System.currentTimeMillis()

        FocusAlarm.schedule(context, now + 5 * 60_000L)
        FocusAlarm.schedule(context, now + 10 * 60_000L)
        FocusAlarm.schedule(context, now + 15 * 60_000L)
        FocusAlarm.schedule(context, now + 20 * 60_000L)

        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun `cancel leaves none`() {
        FocusAlarm.schedule(context, System.currentTimeMillis() + 5 * 60_000L)

        FocusAlarm.cancel(context)

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Test
    fun `the focus notification is ongoing and survives a tap`() {
        Notifier.showFocus(context, "Leer", System.currentTimeMillis() + 5 * 60_000L)

        val posted = shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID)

        assertNotNull(posted)
        assertTrue(posted.flags and Notification.FLAG_ONGOING_EVENT != 0)
        // The whole point of setAutoCancel(false) overriding baseBuilder's default true: a
        // permanent notification that a tap could dismiss isn't permanent.
        assertTrue(posted.flags and Notification.FLAG_AUTO_CANCEL == 0)
        // SystemUI paints the countdown itself (setUsesChronometer + setChronometerCountDown) —
        // this is the actual claim in Notifier.showFocus's KDoc, not just the flags above.
        assertTrue(posted.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(posted.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
    }

    @Test
    fun `cancelFocus clears the tray`() {
        Notifier.showFocus(context, "Leer", System.currentTimeMillis() + 5 * 60_000L)

        Notifier.cancelFocus(context)

        assertNull(shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID))
    }

    @Config(application = BitoApp::class)
    @Test
    fun `a fired alarm with no live session only cleans the tray`() {
        postStandInFocusNotification()

        context.sendBroadcast(Intent(context, FocusReceiver::class.java))
        shadowOf(Looper.getMainLooper()).idle()

        eventually { shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID) == null }
    }

    @Config(application = BitoApp::class)
    @Test
    fun `boot reschedules a future end and clears a past one without notifying`() {
        val container = (context.applicationContext as BitoApp).container
        val now = System.currentTimeMillis()
        runBlocking { container.tasks.create(taskEntity("t1", "Leer")) }

        // A session whose end is still ahead: boot must reschedule the alarm and repost the tray.
        runBlocking {
            container.focus.start(
                FocusSession(
                    taskId = "t1",
                    startedAtMillis = now,
                    endsAtMillis = now + 5 * 60_000L,
                    endsAtElapsed = 0L,
                    bootMillis = 0L,
                ),
            )
        }

        context.sendBroadcast(Intent(context, BootReceiver::class.java).setAction(Intent.ACTION_BOOT_COMPLETED))
        shadowOf(Looper.getMainLooper()).idle()

        eventually { shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID) != null }
        val reposted = shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID)
        assertEquals("Leer", reposted.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(
            "a rescheduled focus alarm targets FocusReceiver",
            shadowOf(alarmManager).scheduledAlarms.any {
                shadowOf(it.operation).savedIntent.component?.className == FocusReceiver::class.java.name
            },
        )

        // Same session, now in the past: boot must clear it silently, no "time's up" notification.
        runBlocking {
            container.focus.start(
                FocusSession(
                    taskId = "t1",
                    startedAtMillis = now - 10 * 60_000L,
                    endsAtMillis = now - 60_000L,
                    endsAtElapsed = 0L,
                    bootMillis = 0L,
                ),
            )
        }

        context.sendBroadcast(Intent(context, BootReceiver::class.java).setAction(Intent.ACTION_BOOT_COMPLETED))
        shadowOf(Looper.getMainLooper()).idle()

        eventually { runBlocking { container.focus.session.first() } == null }
        eventually { shadowOf(notificationManager).getNotification(Notifier.FOCUS_ID) == null }
    }

    private fun postStandInFocusNotification() {
        val notification =
            NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_habi)
                .setContentTitle("stand-in")
                .build()
        NotificationManagerCompat.from(context).notify(Notifier.FOCUS_ID, notification)
    }

    private fun taskEntity(
        id: String,
        title: String,
    ) = TaskEntity(
        id = id,
        title = title,
        firstStep = null,
        dueKind = DueKind.NONE,
        dueDay = null,
        status = TaskStatus.OPEN,
        createdAtMillis = 0L,
        createdOnDay = 0,
        doneAtMillis = null,
        doneOnDay = null,
    )

    private fun eventually(
        timeoutMs: Long = 2_000,
        check: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!check()) {
            if (System.currentTimeMillis() > deadline) error("condition not met within ${timeoutMs}ms")
            Thread.sleep(5)
        }
    }
}
