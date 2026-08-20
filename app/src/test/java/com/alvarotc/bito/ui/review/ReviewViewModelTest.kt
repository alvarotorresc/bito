package com.alvarotc.bito.ui.review

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
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.ui.habi.HabiSounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReviewViewModelTest {
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
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var rewardsRepo: RewardsRepository
    private lateinit var reconciler: PointsReconciler
    private lateinit var habiSounds: HabiSounds
    private lateinit var vm: ReviewViewModel

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    private fun newViewModel() =
        ReviewViewModel(
            domainStateRepo,
            habitsRepo,
            journal,
            settingsRepo,
            reconciler,
            rewardsRepo,
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
        settingsRepo = SettingsRepository(settingsStore("review-vm"))
        rewardsRepo = RewardsRepository(db)
        reconciler = PointsReconciler(domainStateRepo, rewardsRepo)
        habiSounds = HabiSounds(context, settingsRepo, dispatcher = dispatcher)
        vm = newViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun TestScope.state(): ReviewUiState {
        backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()
        return vm.uiState.value
    }

    @Test
    fun `markDone logs today and the row disappears`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today),
            )
            val card = state().rows.single { it.id == "h1" }

            vm.markDone(card)
            advanceUntilIdle()

            assertTrue(state().rows.none { it.id == "h1" })
            assertEquals(1, db.entryDao().all().single { it.habitId == "h1" }.value)
        }

    @Test
    fun `acknowledge hides the row without writing anything`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today),
            )
            val before = state()
            assertTrue(before.rows.any { it.id == "h1" })

            vm.acknowledge("h1")
            advanceUntilIdle()

            assertTrue(state().rows.none { it.id == "h1" })
            assertTrue(db.entryDao().all().isEmpty())
            assertTrue(db.daySealDao().all().isEmpty())
            assertTrue(db.pointsLedgerDao().all().isEmpty())
        }

    @Test
    fun `sealToday seals today, reconciles and flips todaySealed`() =
        runTest {
            // Warm the flow first so the constructor's own reconcile (no habits yet, a no-op)
            // runs and settles before h1's entry exists — otherwise the "ledger still empty"
            // assertion below would race that queued coroutine once it finally runs.
            assertFalse(state().todaySealed)

            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today),
            )
            journal.log(entryEntity(id = "e1", habitId = "h1", logicalDay = today, value = 1))
            assertTrue(db.pointsLedgerDao().all().isEmpty())

            vm.sealToday()
            advanceUntilIdle()

            assertEquals(today, db.daySealDao().all().single().logicalDay)
            val after = state()
            assertTrue(after.todaySealed)
            assertTrue(after.perfectToday)
            assertTrue(
                db.pointsLedgerDao().all().any { it.reason == PointsReason.PERFECT_DAY && it.refId == "day:$today" },
            )
        }

    @Test
    fun `logRelapse logs value 1 on a ZERO habit`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.ZERO, target = 0, createdOnDay = today),
            )
            val card = state().rows.single { it.id == "h1" }

            vm.logRelapse(card)
            advanceUntilIdle()

            val entry = db.entryDao().all().single { it.habitId == "h1" }
            assertEquals(1, entry.value)
            assertEquals(today, entry.logicalDay)
        }

    @Test
    fun `sealPendingDays seals every past pending day`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.ZERO, period = Period.DAY, createdOnDay = today - 2),
            )
            val before = state()
            assertEquals(listOf(today - 2, today - 1), before.pendingSealDays)

            vm.sealPendingDays()
            advanceUntilIdle()

            val seals = db.daySealDao().all().map { it.logicalDay }.sorted()
            assertEquals(listOf(today - 2, today - 1), seals)
            assertTrue(seals.none { it == today })
        }

    // R7 follow-up: the screen's LaunchedEffect(state.todaySealed, state.perfectToday) can re-run
    // with both keys still true (e.g. an activity recreation), which would replay the sound for
    // the very same sealed day if cue() itself weren't guarded — see cuedForDay's KDoc.
    @Test
    fun `cue plays once per day`() =
        runTest {
            state() // warms uiState so today isn't ReviewUiState()'s default 0

            vm.cue()
            vm.cue()

            assertEquals(today, vm.cuedDay)
        }

    @Test
    fun `markCelebrated and markBadgesSeen write the settings markers`() =
        runTest {
            vm.markCelebrated()
            advanceUntilIdle()
            assertEquals(today, settingsRepo.settings.first().perfectDayCelebratedDay)

            vm.markBadgesSeen()
            advanceUntilIdle()
            assertEquals(fixedNow, settingsRepo.settings.first().badgesSeenUntilMillis)
        }
}
