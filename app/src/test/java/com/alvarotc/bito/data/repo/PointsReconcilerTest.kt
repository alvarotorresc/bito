package com.alvarotc.bito.data.repo

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.DAY_ZERO
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.GardenEngine
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.GrowthStage
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PointsReconcilerTest {
    private lateinit var db: BitoDatabase
    private lateinit var habits: HabitsRepository
    private lateinit var journal: JournalRepository
    private lateinit var rewards: RewardsRepository
    private lateinit var reconciler: PointsReconciler

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        habits = HabitsRepository(db)
        journal = JournalRepository(db)
        rewards = RewardsRepository(db)
        reconciler = PointsReconciler(DomainStateRepository(db), rewards)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `reconcile appends the grants history justifies`() =
        runTest {
            habits.create(habitEntity(id = "cama", metric = Metric.CHECK, target = 1))
            journal.log(entryEntity(id = "e1", habitId = "cama", logicalDay = DAY_ZERO, value = 1))
            reconciler.reconcile(today = DAY_ZERO, nowMillis = 10L)
            val ledger = db.pointsLedgerDao().all()
            assertTrue(ledger.any { it.reason == PointsReason.HABIT_DONE && it.refId == "cama:$DAY_ZERO" })
        }

    @Test
    fun `reconcile twice grants nothing twice`() =
        runTest {
            habits.create(habitEntity(id = "cama", metric = Metric.CHECK, target = 1))
            journal.log(entryEntity(id = "e1", habitId = "cama", logicalDay = DAY_ZERO, value = 1))
            reconciler.reconcile(DAY_ZERO, 10L)
            val count = db.pointsLedgerDao().all().size
            reconciler.reconcile(DAY_ZERO, 20L)
            assertEquals(count, db.pointsLedgerDao().all().size)
        }

    @Test
    fun `an undone entry never claws back a granted point`() =
        runTest {
            habits.create(habitEntity(id = "cama", metric = Metric.CHECK, target = 1))
            journal.log(entryEntity(id = "e1", habitId = "cama", logicalDay = DAY_ZERO, value = 1))
            reconciler.reconcile(DAY_ZERO, 10L)
            journal.remove("e1")
            reconciler.reconcile(DAY_ZERO, 20L)
            assertTrue(db.pointsLedgerDao().all().any { it.refId == "cama:$DAY_ZERO" })
        }

    @Test
    fun `reconcile grants the flower when a habit reaches a 7-day streak`() =
        runTest {
            val today = DAY_ZERO + 6
            habits.create(habitEntity(id = "cama", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1))
            for (day in DAY_ZERO..today) {
                journal.log(entryEntity(id = "e$day", habitId = "cama", logicalDay = day, value = 1))
            }
            reconciler.reconcile(today = today, nowMillis = 10L)
            val granted = db.customizationItemDao().all().single { it.itemId == "head-flor" }
            assertEquals(CustomizationCategory.HEAD, granted.category)
            assertFalse(granted.equipped)
        }

    @Test
    fun `reconcile never touches an already granted item`() =
        runTest {
            val today = DAY_ZERO + 6
            habits.create(habitEntity(id = "cama", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1))
            for (day in DAY_ZERO..today) {
                journal.log(entryEntity(id = "e$day", habitId = "cama", logicalDay = day, value = 1))
            }
            reconciler.reconcile(today = today, nowMillis = 10L)
            rewards.equip("head-flor")
            for (day in DAY_ZERO..today) journal.remove("e$day")

            reconciler.reconcile(today = today, nowMillis = 20L)

            val granted = db.customizationItemDao().all().single { it.itemId == "head-flor" }
            assertTrue(granted.equipped)
        }

    @Test
    fun `grantItems is idempotent thanks to insert-ignore`() =
        runTest {
            rewards.grantItems(setOf("head-flor"), 10L)
            rewards.equip("head-flor")
            rewards.grantItems(setOf("head-flor"), 20L)
            val rows = db.customizationItemDao().all().filter { it.itemId == "head-flor" }
            assertEquals(1, rows.size)
            assertTrue(rows.single().equipped)
        }

    @Test
    fun `reconcile unlocks first-habit and returns what it granted`() =
        runTest {
            habits.create(habitEntity(id = "cama", metric = Metric.CHECK, target = 1))
            val result = reconciler.reconcile(today = DAY_ZERO, nowMillis = 10L)
            assertTrue("first-habit" in result.newBadges)
            assertEquals(setOf("first-habit"), db.badgeDao().all().map { it.badgeId }.toSet())
            assertEquals(10L, db.badgeDao().all().single().unlockedAtMillis)
        }

    @Test
    fun `reconcile twice unlocks nothing twice and reports nothing new`() =
        runTest {
            habits.create(habitEntity(id = "cama", metric = Metric.CHECK, target = 1))
            reconciler.reconcile(DAY_ZERO, 10L)
            val second = reconciler.reconcile(DAY_ZERO, 20L)
            assertTrue(second.newBadges.isEmpty())
            assertTrue(second.newEvents.isEmpty())
            assertEquals(10L, db.badgeDao().all().single().unlockedAtMillis)
        }

    @Test
    fun `a perfect day reports reachedPerfectDay for that day only`() =
        runTest {
            habits.create(
                habitEntity(id = "cama", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = DAY_ZERO),
            )
            journal.log(entryEntity(id = "e1", habitId = "cama", logicalDay = DAY_ZERO, value = 1))
            val result = reconciler.reconcile(DAY_ZERO, 10L)
            assertTrue(result.reachedPerfectDay(DAY_ZERO))
            assertFalse(result.reachedPerfectDay(DAY_ZERO + 1))
            assertTrue("perfect-day-1" in result.newBadges)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `unmarking a past day after a reconcile keeps the high mark and the stage`() =
        runTest {
            val settings =
                SettingsRepository(
                    PreferenceDataStoreFactory.create(scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())) {
                        File(tmp.root, "garden.preferences_pb")
                    },
                )
            val withMark = PointsReconciler(DomainStateRepository(db), rewards, settings = settings)
            val today = DAY_ZERO + 7
            habits.create(habitEntity(id = "cama", metric = Metric.CHECK, target = 1))
            for (day in DAY_ZERO..today) {
                journal.log(entryEntity(id = "e$day", habitId = "cama", logicalDay = day, value = 1))
            }
            withMark.reconcile(today, 10L)
            assertEquals(8.0, settings.settings.first().gardenWaterHighMark, 1e-9)

            // El anti-sargento deja desmarcar el pasado: el agua calculada baja a 7 (brote)...
            journal.remove("e${DAY_ZERO + 3}")
            withMark.reconcile(today, 20L)
            val state = DomainStateRepository(db).snapshot()
            assertEquals(7.0, GardenEngine.totalWater(state, today), 1e-9)

            // ...pero la marca no baja y la planta sigue siendo planta.
            val mark = settings.settings.first().gardenWaterHighMark
            assertEquals(8.0, mark, 1e-9)
            val noon = LocalDateTime.of(2024, 10, 11, 12, 0)
            assertEquals(GrowthStage.PLANT, GardenEngine.gardenOf(state, today, noon, today, emptySet(), mark).plots.single().stage)
        }
}
