package com.alvarotc.bito.ui.detail

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.habi.HabiSounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DetailViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)

    private lateinit var db: BitoDatabase
    private lateinit var domainStateRepo: DomainStateRepository
    private lateinit var habitsRepo: HabitsRepository
    private lateinit var journal: JournalRepository
    private lateinit var rewardsRepo: RewardsRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var reconciler: PointsReconciler
    private lateinit var habiSounds: HabiSounds

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    private fun newViewModel(habitId: String = "h1") =
        DetailViewModel(
            habitId,
            domainStateRepo,
            habitsRepo,
            journal,
            rewardsRepo,
            settingsRepo,
            reconciler,
            habiSounds,
            now = { fixedNow },
            zone = { utc },
            defaultDispatcher = dispatcher,
        )

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
        rewardsRepo = RewardsRepository(db)
        settingsRepo = SettingsRepository(settingsStore("detail-vm"))
        reconciler = PointsReconciler(domainStateRepo, rewardsRepo)
        habiSounds = HabiSounds(context, settingsRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `apply freezer writes the use and reconciles with the actual today`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.ZERO, period = Period.DAY, createdOnDay = today - 2),
            )
            journal.log(entryEntity(id = "relapse", habitId = "h1", logicalDay = today - 2, value = 1))
            val vm = newViewModel("h1")
            advanceUntilIdle() // the VM's own init-time reconcile settles before h2 exists below

            // A second habit's fulfilled entry is logged directly (bypassing the VM, so its
            // HABIT_DONE grant is not yet in the ledger) to prove applyFreezer's write reconciles
            // against the CURRENT today, not just the protected day.
            habitsRepo.create(
                habitEntity(id = "h2", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today),
            )
            journal.log(entryEntity(id = "e2", habitId = "h2", logicalDay = today, value = 1))
            assertTrue(db.pointsLedgerDao().all().none { it.refId == "h2:$today" })

            vm.applyFreezer(today - 2)
            advanceUntilIdle()

            val use = db.freezerUseDao().all().single()
            assertEquals("h1", use.habitId)
            assertEquals(today - 2, use.protectedDay)
            assertTrue(db.pointsLedgerDao().all().any { it.refId == "h2:$today" })
        }

    @Test
    fun `marking a past day clean seals that day`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.ZERO, period = Period.DAY, createdOnDay = today - 3),
            )
            val vm = newViewModel()
            advanceUntilIdle()

            vm.markCleanAndSeal(today - 3)
            advanceUntilIdle()

            assertTrue(db.daySealDao().all().any { it.logicalDay == today - 3 })
            assertTrue(db.entryDao().all().none { it.habitId == "h1" && it.logicalDay == today - 3 })
        }

    @Test
    fun `clearing a past day removes its entries`() =
        runTest {
            habitsRepo.create(habitEntity(id = "h1", metric = Metric.COUNT, target = 8, createdOnDay = today - 2))
            journal.log(entryEntity(id = "e1", habitId = "h1", logicalDay = today - 2, value = 5))
            val vm = newViewModel()
            advanceUntilIdle()

            vm.clearDay(today - 2)
            advanceUntilIdle()

            assertTrue(db.entryDao().all().none { it.habitId == "h1" && it.logicalDay == today - 2 })
        }

    @Test
    fun `unarchive restores an archived habit to active`() =
        runTest {
            habitsRepo.create(habitEntity(id = "h1", metric = Metric.CHECK, target = 1, createdOnDay = today - 5))
            habitsRepo.archive("h1", today = today, nowMillis = fixedNow)
            val vm = newViewModel()
            advanceUntilIdle()

            vm.unarchive()
            advanceUntilIdle()

            assertEquals(HabitStatus.ACTIVE, habitsRepo.habit("h1")!!.status)
        }

    @Test
    fun `month navigation never goes past the current month`() =
        runTest {
            habitsRepo.create(habitEntity(id = "h1", metric = Metric.CHECK, target = 1, createdOnDay = today))
            val vm = newViewModel()
            val currentMonth = YearMonth.from(LocalDate.ofEpochDay(today.toLong()))

            repeat(3) {
                vm.nextMonth()
                advanceUntilIdle()
            }

            assertEquals(currentMonth, vm.month.value)
        }
}
