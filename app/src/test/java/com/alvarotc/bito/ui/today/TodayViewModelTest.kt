package com.alvarotc.bito.ui.today

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
import com.alvarotc.bito.domain.model.HabiCue
import com.alvarotc.bito.domain.model.HabiDayPhase
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TodayViewModelTest {
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
    private lateinit var vm: TodayViewModel

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    private fun newViewModel() =
        TodayViewModel(
            domainStateRepo,
            habitsRepo,
            journal,
            settingsRepo,
            reconciler,
            rewardsRepo,
            now = { fixedNow },
            zone = { utc },
            defaultDispatcher = dispatcher,
            // Un solo tic ya satisfecho: estos tests no ejercen el paso del tiempo, y el ticker
            // real (con delay()) deja advanceUntilIdle() sin reposo bajo tiempo virtual.
            ticker = flowOf(Unit),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                // Ties Room's Flow emissions to the test scheduler so advanceUntilIdle() is deterministic;
                // allowMainThreadQueries lifts Room's thread check since Robolectric runs the test on
                // what it reports as the main thread.
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        domainStateRepo = DomainStateRepository(db)
        habitsRepo = HabitsRepository(db)
        journal = JournalRepository(db)
        settingsRepo = SettingsRepository(settingsStore("today-vm"))
        rewardsRepo = RewardsRepository(db)
        reconciler = PointsReconciler(domainStateRepo, rewardsRepo)
        vm = newViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun TestScope.state(): TodayUiState {
        backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()
        return vm.uiState.value
    }

    @Test
    fun `tapPrimary on a CHECK card logs value 1 today and grants HABIT_DONE`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today),
            )
            val card = state().cards.single { it.id == "h1" }
            assertFalse(card.doneToday)

            vm.tapPrimary(card)
            advanceUntilIdle()

            assertTrue(state().cards.single { it.id == "h1" }.doneToday)
            assertEquals(1, db.entryDao().all().single { it.habitId == "h1" }.value)
            assertTrue(
                db.pointsLedgerDao().all().any { it.reason == PointsReason.HABIT_DONE && it.refId == "h1:$today" },
            )
        }

    @Test
    fun `tapPrimary on a done CHECK clears only todays entries`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today - 1),
            )
            journal.log(entryEntity(id = "yesterday", habitId = "h1", logicalDay = today - 1, value = 1))
            journal.log(entryEntity(id = "todayEntry", habitId = "h1", logicalDay = today, value = 1))
            val card = state().cards.single { it.id == "h1" }
            assertTrue(card.doneToday)

            vm.tapPrimary(card)
            advanceUntilIdle()

            assertFalse(state().cards.single { it.id == "h1" }.doneToday)
            val remaining = db.entryDao().all()
            assertTrue(remaining.any { it.id == "yesterday" })
            assertTrue(remaining.none { it.logicalDay == today })
        }

    @Test
    fun `tapPrimary on a COUNTER logs its step`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.COUNT, direction = Direction.AT_LEAST, target = 10, step = 2, createdOnDay = today),
            )
            val card = state().cards.single { it.id == "h1" }
            assertEquals(CardKind.COUNTER, card.kind)

            vm.tapPrimary(card)
            advanceUntilIdle()

            assertEquals(2, state().cards.single { it.id == "h1" }.progress)
        }

    @Test
    fun `setExactToday replaces todays entries with the exact value`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.COUNT, direction = Direction.AT_LEAST, target = 10, createdOnDay = today),
            )
            val card = state().cards.single { it.id == "h1" }
            vm.addAmount(card, 3)
            advanceUntilIdle()
            assertEquals(3, state().cards.single { it.id == "h1" }.progress)

            vm.setExactToday(card, 6)
            advanceUntilIdle()

            assertEquals(6, state().cards.single { it.id == "h1" }.progress)
        }

    @Test
    fun `addAmount on a DURATION card logs the given amount`() =
        runTest {
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    metric = Metric.DURATION,
                    direction = Direction.AT_LEAST,
                    target = 30,
                    unit = "min",
                    createdOnDay = today,
                ),
            )
            val card = state().cards.single { it.id == "h1" }
            assertEquals(CardKind.DURATION, card.kind)

            vm.addAmount(card, 15)
            advanceUntilIdle()

            assertEquals(15, state().cards.single { it.id == "h1" }.progress)
        }

    @Test
    fun `undo removes the last entry and consumeLogged clears lastLogged`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.COUNT, direction = Direction.AT_LEAST, target = 10, createdOnDay = today),
            )
            val card = state().cards.single { it.id == "h1" }

            vm.addAmount(card, 3)
            advanceUntilIdle()
            assertEquals(3, state().cards.single { it.id == "h1" }.progress)
            val loggedId = vm.lastLogged.value
            assertNotNull(loggedId)

            vm.undo()
            advanceUntilIdle()

            assertEquals(0, state().cards.single { it.id == "h1" }.progress)
            assertNull(vm.lastLogged.value)
            assertTrue(db.entryDao().forHabitInRange("h1", today, today).none { it.id == loggedId })

            vm.addAmount(card, 2)
            advanceUntilIdle()
            assertNotNull(vm.lastLogged.value)

            vm.consumeLogged()
            assertNull(vm.lastLogged.value)
        }

    @Test
    fun `reorder persists the new order and the cards follow it`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "a", metric = Metric.CHECK, target = 1, createdOnDay = today, sortOrder = 0),
            )
            habitsRepo.create(
                habitEntity(id = "b", metric = Metric.CHECK, target = 1, createdOnDay = today, sortOrder = 1),
            )
            assertEquals(listOf("a", "b"), state().cards.map { it.id })

            vm.reorder(listOf("b", "a"))
            advanceUntilIdle()

            assertEquals(listOf("b", "a"), state().cards.map { it.id })
        }

    @Test
    fun `sealPendingDays seals every pending day`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.ZERO, period = Period.DAY, createdOnDay = today - 3),
            )
            val before = state()
            assertEquals(listOf(today - 3, today - 2, today - 1), before.pendingSealDays)

            vm.sealPendingDays()
            advanceUntilIdle()

            val seals = db.daySealDao().all().map { it.logicalDay }.sorted()
            assertEquals(listOf(today - 3, today - 2, today - 1), seals)
            assertTrue(state().pendingSealDays.isEmpty())
        }

    @Test
    fun `constructing the view model reconciles pending grants`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today),
            )
            journal.log(entryEntity(id = "e1", habitId = "h1", logicalDay = today, value = 1))
            assertTrue(db.pointsLedgerDao().all().isEmpty())

            newViewModel()
            advanceUntilIdle()

            assertTrue(
                db.pointsLedgerDao().all().any { it.reason == PointsReason.HABIT_DONE && it.refId == "h1:$today" },
            )
        }

    @Test
    fun `logging emits a LOGGED cue with a fresh id each time`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.COUNT, direction = Direction.AT_LEAST, target = 10, step = 1, createdOnDay = today),
            )
            val card = state().cards.single { it.id == "h1" }

            vm.tapPrimary(card)
            advanceUntilIdle()
            val first = vm.habiCue.value
            assertEquals(HabiCue.LOGGED, first?.cue)

            vm.consumeHabiCue()
            assertNull(vm.habiCue.value)

            vm.tapPrimary(state().cards.single { it.id == "h1" })
            advanceUntilIdle()
            val second = vm.habiCue.value
            assertEquals(HabiCue.LOGGED, second?.cue)
            assertNotEquals(first!!.id, second!!.id)
        }

    @Test
    fun `finishing the last habit emits ALL_DONE exactly once`() =
        runTest {
            // Semanal, no diario: PerfectDays.isPerfectDay solo mira habitos de periodo DIA, asi
            // que completar este no dispara TAMBIEN un dia perfecto (que ganaria el when de
            // write() y taparia el ALL_DONE que este test quiere probar).
            habitsRepo.create(
                habitEntity(
                    id = "h1",
                    metric = Metric.CHECK,
                    direction = Direction.AT_LEAST,
                    target = 1,
                    period = Period.WEEK,
                    createdOnDay = today,
                ),
            )
            val card = state().cards.single { it.id == "h1" }

            vm.tapPrimary(card)
            advanceUntilIdle()
            assertEquals(HabiCue.ALL_DONE, vm.habiCue.value?.cue)

            vm.consumeHabiCue()
            // Una escritura real mas que NO cambia el anillo (mismo valor ya registrado) no
            // vuelve a disparar ALL_DONE. reorder() no sirve para esto: no pasa por write().
            vm.setExactToday(state().cards.single { it.id == "h1" }, 1)
            advanceUntilIdle()
            assertNull(vm.habiCue.value)
        }

    @Test
    fun `sealing pending days emits SEALED`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.ZERO, target = 0, createdOnDay = today - 2),
            )
            state()
            vm.sealPendingDays()
            advanceUntilIdle()

            assertEquals(HabiCue.SEALED, vm.habiCue.value?.cue)
        }

    @Test
    fun `crossing review time flips the day phase with no write at all`() =
        runTest {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today),
            )
            // Un reloj que solo se mueve cuando el propio test empuja un tic: el ticker real (con
            // delay()) no tiene reposo bajo tiempo virtual, asi que aqui se dispara a mano. Arranca
            // a las 21:00 y el segundo tic lo mueve a las 21:40.
            var minute = 0L
            val movingNow = { fixedNow + 21 * 3_600_000L + minute * 60_000L }
            val ticks = MutableSharedFlow<Unit>(replay = 1)
            ticks.tryEmit(Unit)
            val ticking =
                TodayViewModel(
                    domainStateRepo, habitsRepo, journal, settingsRepo, reconciler, rewardsRepo,
                    now = movingNow, zone = { utc }, defaultDispatcher = dispatcher, ticker = ticks,
                )
            backgroundScope.launch { ticking.uiState.collect() }
            advanceUntilIdle()
            assertEquals(HabiDayPhase.AWAKE, ticking.uiState.value.dayPhase)

            minute = 40 // 21:40 > 21:30
            ticks.emit(Unit)
            advanceUntilIdle()

            assertEquals(HabiDayPhase.WAITING, ticking.uiState.value.dayPhase)
        }
}
