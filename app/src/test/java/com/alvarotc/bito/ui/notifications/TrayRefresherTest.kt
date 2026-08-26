package com.alvarotc.bito.ui.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.R
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Personality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Drives [TrayRefresher]'s testable repo-based seam over an in-memory Room database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrayRefresherTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private lateinit var db: BitoDatabase
    private lateinit var domainStateRepo: DomainStateRepository
    private lateinit var habitsRepo: HabitsRepository
    private lateinit var settingsRepo: SettingsRepository

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    @Before
    fun setUp() {
        NotificationChannels.ensure(context)
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        domainStateRepo = DomainStateRepository(db)
        habitsRepo = HabitsRepository(db)
        settingsRepo = SettingsRepository(settingsStore("tray-refresher"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Posts a stand-in notification under [Notifier.REMINDER_ID], simulating an already-showing tray. */
    private fun postStaleReminder() {
        val notification =
            NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
                .setSmallIcon(R.drawable.ic_stat_habi)
                .setContentTitle("stale")
                .build()
        NotificationManagerCompat.from(context).notify(Notifier.REMINDER_ID, notification)
    }

    /** [Notification.EXTRA_TITLE] of whatever is currently posted under [Notifier.REMINDER_ID], or `null`. */
    private fun postedTitle(): String? =
        shadowOf(notificationManager)
            .getNotification(Notifier.REMINDER_ID)
            ?.extras
            ?.getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()

    /** [Notification.EXTRA_TEXT] of whatever is currently posted under [Notifier.REMINDER_ID], or `null`. */
    private fun postedText(): String? =
        shadowOf(notificationManager)
            .getNotification(Notifier.REMINDER_ID)
            ?.extras
            ?.getCharSequence(Notification.EXTRA_TEXT)
            ?.toString()

    /** The NEUTRA morning title an unnamed user gets — the default-settings voice of a 10:00 refresh. */
    private fun morningTitleForFallbackName(): String =
        context.getString(
            R.string.notif_reminder_title_neutra_morning,
            context.getString(R.string.habi_name_fallback),
        )

    @Test
    fun `a pending payload only refreshes an already shown notification`() =
        runTest(dispatcher) {
            habitsRepo.create(
                habitEntity(id = "h1", name = "Agua", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1),
            )

            // No tray showing yet — a pending payload alone must never conjure one.
            TrayRefresher.refresh(context, settingsRepo, habitsRepo, domainStateRepo)
            assertNull(shadowOf(notificationManager).getNotification(Notifier.REMINDER_ID))

            // Once the tray is already up, the same pending payload refreshes its actual content —
            // proven by the title moving off the stand-in "stale" text to the real voiced title,
            // not just by a notification of *some* kind still existing under the id.
            postStaleReminder()
            TrayRefresher.refresh(context, settingsRepo, habitsRepo, domainStateRepo, minutesOfDay = 10 * 60)
            assertEquals(morningTitleForFallbackName(), postedTitle())
        }

    @Test
    fun `treatAsActive refreshes the payload even when nothing is currently showing`() =
        runTest(dispatcher) {
            habitsRepo.create(
                habitEntity(id = "h1", name = "Agua", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1),
            )
            assertNull(shadowOf(notificationManager).getNotification(Notifier.REMINDER_ID))

            TrayRefresher.refresh(context, settingsRepo, habitsRepo, domainStateRepo, treatAsActive = true, minutesOfDay = 10 * 60)

            assertEquals(morningTitleForFallbackName(), postedTitle())
        }

    @Test
    fun `an empty payload cancels the tray`() =
        runTest(dispatcher) {
            // Nothing pending: the only habit is already done for the day is out of scope here —
            // no habits at all is the simplest "nothing pending" state.
            postStaleReminder()
            assertNotNull(shadowOf(notificationManager).getNotification(Notifier.REMINDER_ID))

            TrayRefresher.refresh(context, settingsRepo, habitsRepo, domainStateRepo)

            assertNull(shadowOf(notificationManager).getNotification(Notifier.REMINDER_ID))
        }

    @Test
    fun `a refreshed reminder only alerts once, not on every re-post`() =
        runTest(dispatcher) {
            habitsRepo.create(
                habitEntity(id = "h1", name = "Agua", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1),
            )

            TrayRefresher.refresh(context, settingsRepo, habitsRepo, domainStateRepo, treatAsActive = true)

            val notification = shadowOf(notificationManager).getNotification(Notifier.REMINDER_ID)
            assertNotNull(notification)
            assertTrue((notification!!.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0)
        }

    @Test
    fun `a refresh with no review pending cancels the review notification`() =
        runTest(dispatcher) {
            // Post a stale review notification first
            val reviewNotification =
                NotificationCompat.Builder(context, NotificationChannels.REVIEW)
                    .setSmallIcon(R.drawable.ic_stat_habi)
                    .setContentTitle("stale review")
                    .build()
            NotificationManagerCompat.from(context).notify(Notifier.REVIEW_ID, reviewNotification)
            assertNotNull(shadowOf(notificationManager).getNotification(Notifier.REVIEW_ID))

            // Refresh with no pending review (empty state)
            TrayRefresher.refresh(context, settingsRepo, habitsRepo, domainStateRepo)

            // Review notification should be cancelled
            assertNull(shadowOf(notificationManager).getNotification(Notifier.REVIEW_ID))
        }

    @Test
    fun `a refresh with review pending leaves the review notification alone`() =
        runTest(dispatcher) {
            // Post a review notification
            val reviewNotification =
                NotificationCompat.Builder(context, NotificationChannels.REVIEW)
                    .setSmallIcon(R.drawable.ic_stat_habi)
                    .setContentTitle("review")
                    .build()
            NotificationManagerCompat.from(context).notify(Notifier.REVIEW_ID, reviewNotification)
            assertNotNull(shadowOf(notificationManager).getNotification(Notifier.REVIEW_ID))

            // Create a habit that hasn't been done yet to make today unsealed with content
            habitsRepo.create(
                habitEntity(id = "h1", name = "Agua", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1),
            )

            // Refresh with pending review (unsealed today with something to act on)
            TrayRefresher.refresh(context, settingsRepo, habitsRepo, domainStateRepo)

            // Review notification should still be there
            assertNotNull(shadowOf(notificationManager).getNotification(Notifier.REVIEW_ID))
        }

    @Test
    fun `the tray speaks the configured personality, the user's name, and the hour's flavor`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(personality = Personality.SARGENTO, userName = "Álvaro") }
            habitsRepo.create(
                habitEntity(id = "h1", name = "Agua", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1),
            )

            TrayRefresher.refresh(context, settingsRepo, habitsRepo, domainStateRepo, treatAsActive = true, minutesOfDay = 21 * 60)

            assertEquals(context.getString(R.string.notif_reminder_title_sargento_evening, "Álvaro"), postedTitle())
            val expectedBody =
                context.resources.getQuantityString(R.plurals.notif_reminder_body_sargento_evening, 1, 1, "Agua", 0, 1)
            assertEquals(expectedBody, postedText())
            // BigTextStyle mirrors the body so the expanded notification breathes.
            val bigText =
                shadowOf(notificationManager)
                    .getNotification(Notifier.REMINDER_ID)
                    ?.extras
                    ?.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?.toString()
            assertEquals(expectedBody, bigText)
        }

    @Test
    fun `the body names at most three pending habits while the count keeps the truth`() =
        runTest(dispatcher) {
            listOf("Agua", "Leer", "Gym", "Meditar").forEachIndexed { index, name ->
                habitsRepo.create(
                    habitEntity(
                        id = "h$index",
                        name = name,
                        metric = Metric.CHECK,
                        direction = Direction.AT_LEAST,
                        target = 1,
                        sortOrder = index,
                    ),
                )
            }

            TrayRefresher.refresh(context, settingsRepo, habitsRepo, domainStateRepo, treatAsActive = true, minutesOfDay = 15 * 60)

            assertEquals(
                context.resources.getQuantityString(R.plurals.notif_reminder_body_neutra_afternoon, 4, 4, "Agua, Leer, Gym", 0, 4),
                postedText(),
            )
        }
}
