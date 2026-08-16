package com.alvarotc.bito.ui.notifications

import android.app.AlarmManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.Metric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
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

/** Drives [ExactAlarmReceiver]'s testable repo-based seam over an in-memory Room database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExactAlarmReceiverTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private lateinit var db: BitoDatabase
    private lateinit var habitsRepo: HabitsRepository
    private lateinit var settingsRepo: SettingsRepository

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        habitsRepo = HabitsRepository(db)
        settingsRepo = SettingsRepository(settingsStore("exact-alarm-receiver"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Suppress("DEPRECATION") // ShadowAlarmManager.ScheduledAlarm#operation has no replacement accessor.
    @Test
    fun `the permission grant reschedules every slot`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(globalReminderMinutes = listOf(480)) }
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    name = "Agua",
                    metric = Metric.CHECK,
                    direction = Direction.AT_LEAST,
                    reminderMinutes = 600,
                    status = HabitStatus.ACTIVE,
                ),
            )

            ExactAlarmReceiver.reschedule(context, settingsRepo, habitsRepo)

            val expectedCodes =
                ReminderScheduler.slotsOf(settingsRepo.settings.first(), habitsRepo.observeHabits().first())
                    .map(ReminderScheduler::requestCodeOf)
            val scheduledCodes =
                shadowOf(alarmManager).scheduledAlarms.map { shadowOf(it.operation).requestCode }
            assertTrue(expectedCodes.isNotEmpty())
            assertTrue(scheduledCodes.containsAll(expectedCodes))
        }
}
