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
import com.alvarotc.bito.domain.EyeTransition
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.Direction
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PointsReconcilerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: BitoDatabase
    private lateinit var habits: HabitsRepository
    private lateinit var journal: JournalRepository
    private lateinit var rewards: RewardsRepository
    private lateinit var reconciler: PointsReconciler
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var reconcilerWithSettings: PointsReconciler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
        habits = HabitsRepository(db)
        journal = JournalRepository(db)
        rewards = RewardsRepository(db)
        reconciler = PointsReconciler(DomainStateRepository(db), rewards)
        settingsRepo =
            SettingsRepository(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(UnconfinedTestDispatcher() + Job()),
                ) { File(tmp.root, "settings.preferences_pb") },
            )
        reconcilerWithSettings = PointsReconciler(DomainStateRepository(db), rewards, settings = settingsRepo)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedSevenDayRun(habitId: String) {
        habits.create(
            habitEntity(
                id = habitId,
                metric = Metric.CHECK,
                direction = Direction.AT_LEAST,
                target = 1,
                createdOnDay = DAY_ZERO - 6,
            ),
        )
        for (day in (DAY_ZERO - 6)..DAY_ZERO) {
            journal.log(entryEntity(id = "e$day", habitId = habitId, logicalDay = day, value = 1))
        }
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
    fun `reconcile grants the sparks pattern when a habit reaches a 7-day streak`() =
        runTest {
            val today = DAY_ZERO + 6
            habits.create(habitEntity(id = "cama", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1))
            for (day in DAY_ZERO..today) {
                journal.log(entryEntity(id = "e$day", habitId = "cama", logicalDay = day, value = 1))
            }
            reconciler.reconcile(today = today, nowMillis = 10L)
            val granted = db.customizationItemDao().all().single { it.itemId == "pattern-chispas" }
            assertEquals(CustomizationCategory.PATTERN, granted.category)
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
            rewards.equip("pattern-chispas")
            for (day in DAY_ZERO..today) journal.remove("e$day")

            reconciler.reconcile(today = today, nowMillis = 20L)

            val granted = db.customizationItemDao().all().single { it.itemId == "pattern-chispas" }
            assertTrue(granted.equipped)
        }

    @Test
    fun `grantItems is idempotent thanks to insert-ignore`() =
        runTest {
            rewards.grantItems(setOf("pattern-chispas"), 10L)
            rewards.equip("pattern-chispas")
            rewards.grantItems(setOf("pattern-chispas"), 20L)
            val rows = db.customizationItemDao().all().filter { it.itemId == "pattern-chispas" }
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

    @Test
    fun `the first habit paints the first eye, once`() =
        runTest {
            habits.create(habitEntity(id = "cama", metric = Metric.CHECK, target = 1))

            val first = reconcilerWithSettings.reconcile(DAY_ZERO, 10L)
            assertEquals(EyeTransition(0, 1), first.eyeRitual)
            assertEquals(1, settingsRepo.settings.first().habiEyesPainted)

            val second = reconcilerWithSettings.reconcile(DAY_ZERO, 20L)
            assertNull(second.eyeRitual)
        }

    @Test
    fun `a seven day run paints the second eye`() =
        runTest {
            // Sembrar un hábito diario con 7 días seguidos cumplidos hasta DAY_ZERO — mismo patrón
            // que `reconcile grants the streak exclusive` ya usa en este fichero.
            seedSevenDayRun(habitId = "cama")

            val result = reconcilerWithSettings.reconcile(DAY_ZERO, 10L)

            assertEquals(EyeTransition(0, 2), result.eyeRitual)
            assertEquals(2, settingsRepo.settings.first().habiEyesPainted)
        }

    @Test
    fun `a reconciler without settings never reports the ritual`() =
        runTest {
            habits.create(habitEntity(id = "cama", metric = Metric.CHECK, target = 1))
            assertNull(reconciler.reconcile(DAY_ZERO, 10L).eyeRitual)
        }

    @Test
    fun `a restored install heals its eyes in silence`() =
        runTest {
            habits.create(habitEntity(id = "cama", metric = Metric.CHECK, target = 1))
            settingsRepo.markRestoredForSilentEyes()

            val result = reconcilerWithSettings.reconcile(DAY_ZERO, 10L)

            assertNull(result.eyeRitual)
            assertEquals(1, settingsRepo.settings.first().habiEyesPainted)
        }

    // El marcador se reclama INCONDICIONALMENTE al entrar en healEyeRitual, antes del corte
    // "sin cambio": un restore sin hábitos deja stored=0 y derived=0 (no hay salto que sanar),
    // pero el marcador tiene que gastarse igual en ESE primer reconcile. Si se reclamara solo
    // cuando hay cambio, quedaría armado y se comería la transición real 0→1 del primer hábito
    // creado después del restore — el primer ojo nunca se celebraría.
    @Test
    fun `a restore with no habits does not swallow the first eye`() =
        runTest {
            settingsRepo.markRestoredForSilentEyes()
            assertNull(reconcilerWithSettings.reconcile(DAY_ZERO, 10L).eyeRitual)

            habits.create(habitEntity(id = "cama", metric = Metric.CHECK, target = 1))
            val second = reconcilerWithSettings.reconcile(DAY_ZERO, 20L)

            assertEquals(EyeTransition(0, 1), second.eyeRitual)
            assertEquals(1, settingsRepo.settings.first().habiEyesPainted)
        }
}
