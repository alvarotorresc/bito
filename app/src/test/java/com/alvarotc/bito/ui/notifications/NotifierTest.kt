package com.alvarotc.bito.ui.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.domain.model.Personality
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [Notifier.showTasks]'s body assembly: named titles joined by " · ", with the "and N more" tail
 * (`notif_tasks_more`) appended only when [titles] doesn't already cover the full pendingCount.
 * Regression coverage for the M10 T20 review finding: picking the plural by `pendingCount` while
 * filling `%1$d` with `pendingCount - titles.size` used to print "and 0 more" whenever every
 * notice already had a name (2 or 3 notices, since MAX_NAMED_PENDING is 3).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class NotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        NotificationChannels.ensure(context)
        shadowOf(notificationManager).setNotificationsEnabled(true)
    }

    private fun postedText(): String? =
        shadowOf(notificationManager)
            .getNotification(Notifier.TASKS_ID)
            ?.extras
            ?.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()

    private fun postedTitle(): String? =
        shadowOf(notificationManager)
            .getNotification(Notifier.TASKS_ID)
            ?.extras
            ?.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()

    @Test
    fun `tasks title is plural with four tasks`() {
        Notifier.showTasks(context, listOf("A", "B", "C"), 4, Personality.NEUTRA, "Álvaro")
        assertEquals("You have 4 tasks, Álvaro.", postedTitle())

        Notifier.showTasks(context, listOf("A"), 1, Personality.NEUTRA, "Álvaro")
        assertEquals("You have a task, Álvaro.", postedTitle())
    }

    @Test
    @Config(qualifiers = "es")
    fun `tasks title is plural with four tasks in Spanish`() {
        Notifier.showTasks(context, listOf("A", "B", "C"), 4, Personality.NEUTRA, "Álvaro")
        assertEquals("Tienes 4 tareas, Álvaro.", postedTitle())

        Notifier.showTasks(context, listOf("A"), 1, Personality.NEUTRA, "Álvaro")
        assertEquals("Tienes una tarea, Álvaro.", postedTitle())
    }

    @Test
    fun `every notice named leaves no more-tail`() {
        Notifier.showTasks(context, listOf("Renovar el DNI", "Pagar el alquiler"), 2, Personality.NEUTRA, "Álvaro")

        assertEquals("Renovar el DNI · Pagar el alquiler", postedText())
    }

    @Test
    fun `a blank name gets the nameless tasks title`() {
        Notifier.showTasks(context, listOf("A", "B"), 2, Personality.NEUTRA, "")

        assertEquals("You have 2 tasks.", postedTitle())
    }

    @Test
    fun `notices left unnamed add the more-tail`() {
        Notifier.showTasks(
            context,
            listOf("Renovar el DNI", "Pagar el alquiler", "Dentista"),
            5,
            Personality.NEUTRA,
            "Álvaro",
        )

        val more = context.resources.getQuantityString(R.plurals.notif_tasks_more, 2, 2)
        assertEquals("Renovar el DNI · Pagar el alquiler · Dentista $more", postedText())
    }

    @Test
    fun `the breathing reminder speaks in Habi's voice and opens the exercise`() {
        Notifier.showBreathing(context, Personality.NEUTRA, "Álvaro")

        val notification = shadowOf(notificationManager).getNotification(Notifier.BREATHING_ID)!!
        assertEquals("Time to breathe, Álvaro.", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Whenever you can.", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(NotificationChannels.REMINDERS, notification.channelId)
        assertTrue(notification.actions.isNullOrEmpty())
        val tap = shadowOf(notification.contentIntent)
        assertEquals(5, tap.requestCode)
        assertEquals("breathing", tap.savedIntent.getStringExtra(Notifier.EXTRA_OPEN_ROUTE))
    }

    @Test
    fun `a blank name gets the nameless title`() {
        Notifier.showBreathing(context, Personality.SARGENTO, "")

        val notification = shadowOf(notificationManager).getNotification(Notifier.BREATHING_ID)!!
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()
        assertEquals("Two minutes.", title)
    }
}
