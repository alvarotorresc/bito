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
import com.alvarotc.bito.data.habla.LoGordoRepository
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.GardenEngine
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Efecto
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import com.alvarotc.bito.ui.habi.HabiSounds
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.Flow
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
    private lateinit var tasksRepo: TasksRepository
    private lateinit var reconciler: PointsReconciler
    private lateinit var vm: TodayViewModel

    private fun settingsStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
        ) { File(tmp.root, "$name.preferences_pb") }

    private fun newViewModel(
        propuestas: FuentePropuestas? = null,
        habiSounds: HabiSounds? = null,
        gordo: LoGordoRepository? = null,
    ) = TodayViewModel(
        domainStateRepo,
        habitsRepo,
        journal,
        settingsRepo,
        reconciler,
        rewardsRepo,
        tasks = tasksRepo,
        now = { fixedNow },
        zone = { utc },
        defaultDispatcher = dispatcher,
        propuestas = propuestas,
        habiSounds = habiSounds,
        gordo = gordo,
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
        tasksRepo = TasksRepository(db)
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
    fun `logging from the row makes no sound in the model, the row puts the catalogue one`() =
        runTest {
            val sonido = SonidoGrabado()
            vm = newViewModel(habiSounds = HabiSounds(sonido))
            habitsRepo.create(
                habitEntity(id = "c", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today),
            )
            habitsRepo.create(
                habitEntity(id = "n", metric = Metric.COUNT, direction = Direction.AT_LEAST, target = 10, step = 2, createdOnDay = today),
            )
            val check = state().cards.single { it.id == "c" }
            val contador = state().cards.single { it.id == "n" }

            vm.tapPrimary(check)
            advanceUntilIdle()
            vm.addAmount(contador, contador.step)
            advanceUntilIdle()

            assertTrue(sonido.snds().isEmpty())
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
    fun `marking a task done grants its points once and offers an undo`() =
        runTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))

            vm.markTaskDone("t1")
            advanceUntilIdle()

            assertEquals(TaskStatus.DONE, tasksRepo.task("t1")!!.status)
            assertEquals(today, tasksRepo.task("t1")!!.doneOnDay)
            assertEquals("t1", vm.lastTaskDone.value)
            assertEquals(
                1,
                db.pointsLedgerDao().all().count { it.reason == PointsReason.TASK_DONE && it.refId == "task:t1" },
            )
        }

    @Test
    fun `undoing a task reopens it and keeps the ledger entry`() =
        runTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            vm.markTaskDone("t1")
            advanceUntilIdle()
            assertEquals(TaskStatus.DONE, tasksRepo.task("t1")!!.status)

            vm.undoTaskDone()
            advanceUntilIdle()

            val reopened = tasksRepo.task("t1")!!
            assertEquals(TaskStatus.OPEN, reopened.status)
            assertNull(reopened.doneOnDay)
            assertNull(vm.lastTaskDone.value)
            assertEquals(
                1,
                db.pointsLedgerDao().all().count { it.reason == PointsReason.TASK_DONE && it.refId == "task:t1" },
            )
        }

    @Test
    fun `postponing writes the event and takes the row out of today`() =
        runTest {
            tasksRepo.create(taskEntity(id = "t1", createdOnDay = today))
            assertTrue(state().tasks.any { it.id == "t1" })

            vm.postponeTask("t1")
            advanceUntilIdle()

            assertTrue(state().tasks.none { it.id == "t1" })
            assertTrue(
                db.taskEventDao().all().any {
                    it.taskId == "t1" && it.kind == TaskEventKind.POSTPONED && it.logicalDay == today
                },
            )
        }

    private suspend fun seedNoSmoking() =
        habitsRepo.create(
            habitEntity(id = "nosmoke", metric = Metric.CHECK, direction = Direction.ZERO, period = Period.DAY, createdOnDay = today),
        )

    @Test
    fun `relapse today logs one entry for an abstinence habit and undo removes it`() =
        runTest(dispatcher) {
            seedNoSmoking()
            val card = state().cards.single { it.id == "nosmoke" }
            assertEquals(CardKind.ABSTINENCE, card.kind)

            vm.recaerHoy(card)
            advanceUntilIdle()

            val entries = db.entryDao().all().filter { it.habitId == "nosmoke" }
            assertEquals(1, entries.size)
            assertEquals(1, entries.single().value)
            assertEquals(today, entries.single().logicalDay)
            assertEquals(entries.single().id, vm.lastLogged.value)

            vm.undo()
            advanceUntilIdle()

            assertTrue(db.entryDao().all().none { it.habitId == "nosmoke" })
        }

    @Test
    fun `relapse is ignored for other kinds`() =
        runTest(dispatcher) {
            habitsRepo.create(
                habitEntity(id = "h1", metric = Metric.CHECK, direction = Direction.AT_LEAST, target = 1, createdOnDay = today),
            )
            val card = state().cards.single { it.id == "h1" }
            assertEquals(CardKind.CHECK, card.kind)

            vm.recaerHoy(card)
            advanceUntilIdle()

            assertTrue(db.entryDao().all().isEmpty())
            assertNull(vm.lastLogged.value)
        }

    @Test
    fun `the proposal comes from the source for Today`() =
        runTest(dispatcher) {
            val respondidas = mutableListOf<Pair<String, Respuesta>>()
            val ambitos = mutableListOf<Ambito>()
            val fuente =
                object : FuentePropuestas {
                    override fun propuesta(
                        ambito: Ambito,
                        habitId: String?,
                    ): Flow<Propuesta?> {
                        ambitos += ambito
                        return flowOf(Propuesta("p", TextoHabi(1), emptyList()))
                    }

                    override suspend fun responder(
                        id: String,
                        respuesta: Respuesta,
                        hora: Int?,
                    ): Efecto {
                        respondidas += id to respuesta
                        return Efecto.Ninguno
                    }

                    override suspend fun guardarGordo(refs: List<RefGordo>) = Unit
                }
            vm = newViewModel(fuente)
            backgroundScope.launch { vm.propuesta.collect() }
            advanceUntilIdle()

            assertEquals("p", vm.propuesta.value?.id)
            assertEquals(listOf(Ambito.HOY), ambitos)

            vm.responder(Respuesta.HOY_NO)
            advanceUntilIdle()

            assertEquals(listOf("p" to Respuesta.HOY_NO), respondidas)
        }

    private class FuenteConEfectos(var efecto: Efecto) : FuentePropuestas {
        val llamadas = mutableListOf<Triple<String, Respuesta, Int?>>()

        override fun propuesta(
            ambito: Ambito,
            habitId: String?,
        ): Flow<Propuesta?> = flowOf(Propuesta("p", TextoHabi(1), emptyList()))

        override suspend fun responder(
            id: String,
            respuesta: Respuesta,
            hora: Int?,
        ): Efecto {
            llamadas += Triple(id, respuesta, hora)
            return efecto
        }

        override suspend fun guardarGordo(refs: List<RefGordo>) = Unit
    }

    @Test
    fun `each effect reaches the screen once`() =
        runTest(dispatcher) {
            val fuente = FuenteConEfectos(Efecto.AbrirCierre)
            vm = newViewModel(fuente)
            backgroundScope.launch { vm.propuesta.collect() }
            advanceUntilIdle()
            val primero = mutableListOf<Efecto>()
            val colector1 = launch { vm.efectos.collect { primero += it } }
            advanceUntilIdle()

            vm.responder(Respuesta.CERRAR_DIA)
            advanceUntilIdle()
            assertEquals(listOf<Efecto>(Efecto.AbrirCierre), primero)
            colector1.cancel()

            // Un segundo colector, ya pasado el efecto, no recibe el viejo.
            val segundo = mutableListOf<Efecto>()
            val colector2 = launch { vm.efectos.collect { segundo += it } }
            advanceUntilIdle()
            assertTrue(segundo.isEmpty())

            fuente.efecto = Efecto.AbrirLoGordo
            vm.responder(Respuesta.ELEGIR_GORDO)
            advanceUntilIdle()
            assertEquals(listOf<Efecto>(Efecto.AbrirLoGordo), segundo)
            colector2.cancel()
        }

    @Test
    fun `an answer with nothing to do emits no effect`() =
        runTest(dispatcher) {
            vm = newViewModel(FuenteConEfectos(Efecto.Ninguno))
            backgroundScope.launch { vm.propuesta.collect() }
            advanceUntilIdle()
            val recibidos = mutableListOf<Efecto>()
            backgroundScope.launch { vm.efectos.collect { recibidos += it } }
            advanceUntilIdle()
            vm.responder(Respuesta.HOY_NO)
            advanceUntilIdle()
            assertTrue(recibidos.isEmpty())
        }

    @Test
    fun `another hour reaches the source with its minute`() =
        runTest(dispatcher) {
            val fuente = FuenteConEfectos(Efecto.Ninguno)
            vm = newViewModel(fuente)
            backgroundScope.launch { vm.propuesta.collect() }
            advanceUntilIdle()
            vm.responder(Respuesta.OTRA_HORA, 600)
            advanceUntilIdle()
            assertEquals(listOf(Triple("p", Respuesta.OTRA_HORA, 600 as Int?)), fuente.llamadas)
        }

    @Test
    fun `without a source there is no proposal and answering does nothing`() =
        runTest(dispatcher) {
            assertNull(vm.propuesta.value)
            vm.responder(Respuesta.VALE)
            advanceUntilIdle()
            assertNull(vm.propuesta.value)
        }

    @Test
    fun `forcing the garden overrides it and null goes back`() =
        runTest(dispatcher) {
            val forzado = GardenState.INITIAL.copy(plots = GardenEngine.plotsFor(30.0))
            assertTrue(state().garden != forzado)

            vm.forzarJardin(forzado)
            advanceUntilIdle()
            assertEquals(forzado, vm.uiState.value.garden)

            vm.forzarJardin(null)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.garden != forzado)
        }

    @Test
    fun `the balance follows the ledger`() =
        runTest(dispatcher) {
            db.pointsLedgerDao().insertAll(
                listOf(
                    pointsLedgerEntity(id = "a", delta = 1, refId = "x:1", createdAtMillis = 10L),
                    pointsLedgerEntity(id = "b", delta = 3, refId = "x:2", createdAtMillis = 20L),
                ),
            )

            val ui = state()

            assertEquals(4, ui.saldo)
            assertEquals(listOf(3, 1), ui.movimientos.map { it.delta })
        }

    private suspend fun sembrarGordo() {
        tasksRepo.create(taskEntity(id = "hoy", title = "Informe", dueKind = DueKind.DATE, dueDay = today, createdOnDay = today - 5))
        tasksRepo.create(taskEntity(id = "vieja", title = "Dentista", dueKind = DueKind.DATE, dueDay = today - 3, createdOnDay = today - 9))
        habitsRepo.create(
            habitEntity(
                id = "leer",
                name = "Leer",
                metric = Metric.CHECK,
                direction = Direction.AT_LEAST,
                target = 1,
                createdOnDay = today - 1,
            ),
        )
    }

    @Test
    fun `the big thing candidates and the oldest overdue suggestion come from the domain`() =
        runTest(dispatcher) {
            sembrarGordo()
            vm = newViewModel(gordo = LoGordoRepository(db, now = { fixedNow }))

            val ui = state()

            assertEquals(setOf("hoy", "vieja", "leer"), ui.candidatosGordo.map { it.ref.id }.toSet())
            assertEquals(3, ui.candidatosGordo.size)
            assertEquals(RefGordo(TipoRefGordo.TAREA, "vieja"), ui.sugerenciaGordo)
            assertTrue(ui.gordo.isEmpty())
        }

    @Test
    fun `yesterdays unfinished big thing is the suggestion over the overdue one`() =
        runTest(dispatcher) {
            sembrarGordo()
            val repo = LoGordoRepository(db, now = { fixedNow })
            repo.guardar(today - 1, listOf(RefGordo(TipoRefGordo.TAREA, "hoy")))
            vm = newViewModel(gordo = repo)

            val ui = state()

            assertEquals(RefGordo(TipoRefGordo.TAREA, "hoy"), ui.sugerenciaGordo)
        }

    @Test
    fun `what was chosen today shows up in the state`() =
        runTest(dispatcher) {
            sembrarGordo()
            val repo = LoGordoRepository(db, now = { fixedNow })
            vm = newViewModel(gordo = repo)
            assertTrue(state().gordo.isEmpty())

            repo.guardar(today, listOf(RefGordo(TipoRefGordo.HABITO, "leer"), RefGordo(TipoRefGordo.TAREA, "hoy")))
            advanceUntilIdle()

            assertEquals(listOf(RefGordo(TipoRefGordo.HABITO, "leer"), RefGordo(TipoRefGordo.TAREA, "hoy")), vm.uiState.value.gordo)
        }

    @Test
    fun `without a big thing store the state has no candidates to show`() =
        runTest(dispatcher) {
            sembrarGordo()
            val ui = state()
            assertTrue(ui.gordo.isEmpty())
            assertEquals(3, ui.candidatosGordo.size)
        }
}
