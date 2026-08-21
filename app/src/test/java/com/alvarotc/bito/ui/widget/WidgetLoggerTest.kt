package com.alvarotc.bito.ui.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetLoggerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val testZone = ZoneId.of("UTC")

    private lateinit var db: BitoDatabase
    private lateinit var domainStateRepo: DomainStateRepository
    private lateinit var habitsRepo: HabitsRepository
    private lateinit var journal: JournalRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var reconciler: PointsReconciler

    /** 2025-08-15T00:00:00Z plus [hour]:[minute] on that UTC day. */
    private fun millisAt(
        hour: Int,
        minute: Int,
    ) = 1_755_216_000_000L + (hour * 60 + minute) * 60_000L

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        domainStateRepo = DomainStateRepository(db)
        habitsRepo = HabitsRepository(db)
        journal = JournalRepository(db)
        settingsRepo = SettingsRepository(settingsStore("widget-logger"))
        reconciler = PointsReconciler(domainStateRepo, RewardsRepository(db))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `a widget tap logs the amount on the logical day given the cutoff`() =
        runTest {
            // cutoff 03:00, "now" = 01:00 on the 15th -> logical day = the 14th
            settingsRepo.update { it.copy(dayCutoffMinutes = 180) }
            val habit = habitEntity(id = "h1", metric = Metric.COUNT, direction = Direction.AT_LEAST, target = 10)
            habitsRepo.create(habit)
            val logger =
                WidgetLogger(journal, reconciler, settingsRepo, now = { millisAt(1, 0) }, zone = { testZone })

            logger.log(habit.id, 3)

            val entries = db.entryDao().all()
            assertEquals(1, entries.size)
            assertEquals(3, entries.single().value)
            val aug14 = LocalDate.of(2025, 8, 14).toEpochDay().toInt()
            assertEquals(aug14, entries.single().logicalDay)
        }

    @Test
    fun `widget logging reconciles points immediately`() =
        runTest {
            val aug15 = LocalDate.of(2025, 8, 15).toEpochDay().toInt()
            val habit =
                habitEntity(
                    id = "h1",
                    metric = Metric.CHECK,
                    direction = Direction.AT_LEAST,
                    target = 1,
                    createdOnDay = aug15,
                )
            habitsRepo.create(habit)
            val logger =
                WidgetLogger(journal, reconciler, settingsRepo, now = { millisAt(12, 0) }, zone = { testZone })
            assertTrue(db.pointsLedgerDao().all().isEmpty())

            logger.log(habit.id, 1)

            assertTrue(
                db.pointsLedgerDao().all().any { it.reason == PointsReason.HABIT_DONE && it.refId == "h1:$aug15" },
            )
        }

    @Test
    fun `logging the last pending habit of the day returns true, and a later log on an already-fulfilled habit returns false`() =
        runTest {
            val aug15 = LocalDate.of(2025, 8, 15).toEpochDay().toInt()
            val h1 = habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = aug15)
            val h2 = habitEntity(id = "h2", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = aug15)
            habitsRepo.create(h1)
            habitsRepo.create(h2)
            val logger =
                WidgetLogger(journal, reconciler, settingsRepo, now = { millisAt(12, 0) }, zone = { testZone })
            // h2 already done; h1 is the last habit left, so completing it makes today perfect.
            logger.log(h2.id, 1)

            val completing = logger.log(h1.id, 1)

            assertTrue(completing)

            val alreadyGranted = logger.log(h2.id, 1)

            assertFalse(alreadyGranted)
        }
}
