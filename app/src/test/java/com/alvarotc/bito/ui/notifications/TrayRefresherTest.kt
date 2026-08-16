package com.alvarotc.bito.ui.notifications

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun `a pending payload only refreshes an already shown notification`() =
        runTest(dispatcher) {
            habitsRepo.create(
                habitEntity(id = "h1", name = "Agua", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1),
            )

            // No tray showing yet — a pending payload alone must never conjure one.
            TrayRefresher.refresh(context, settingsRepo, habitsRepo, domainStateRepo)
            assertNull(shadowOf(notificationManager).getNotification(Notifier.REMINDER_ID))

            // Once the tray is already up, the same pending payload refreshes it.
            postStaleReminder()
            TrayRefresher.refresh(context, settingsRepo, habitsRepo, domainStateRepo)
            assertNotNull(shadowOf(notificationManager).getNotification(Notifier.REMINDER_ID))
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
}
