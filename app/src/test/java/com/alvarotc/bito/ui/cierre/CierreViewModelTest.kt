package com.alvarotc.bito.ui.cierre

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.entryEntity
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.habla.AvisosAceptadosRepository
import com.alvarotc.bito.data.habla.LoGordoRepository
import com.alvarotc.bito.data.habla.MemoriaRepository
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.habla.AvisoAceptado
import com.alvarotc.bito.domain.habla.EstadoAviso
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.ui.habi.HabiSounds
import com.alvarotc.bito.ui.notifications.AvisosHabi
import com.alvarotc.bito.ui.sistema.sonido.MotorSonidoMudo
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

/** Los avisos de Habi que cuentan cuántas veces se replanifica. */
private class AvisosContados : AvisosHabi {
    var replanificados = 0

    override suspend fun replanificar() {
        replanificados++
    }

    override suspend fun cancelarAceptados(ids: List<String>) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CierreViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val fixedNow = 1_755_216_000_000L // 2025-08-15T00:00:00Z
    private val utc = ZoneId.of("UTC")
    private val today = LogicalDays.logicalDayOf(fixedNow, 0, utc)

    private lateinit var db: BitoDatabase
    private lateinit var habitsRepo: HabitsRepository
    private lateinit var journal: JournalRepository
    private lateinit var tasksRepo: TasksRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var rewardsRepo: RewardsRepository
    private lateinit var loGordo: LoGordoRepository
    private lateinit var aceptados: AvisosAceptadosRepository
    private lateinit var avisos: AvisosContados
    private lateinit var vm: CierreViewModel

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
        val domainStateRepo = DomainStateRepository(db)
        habitsRepo = HabitsRepository(db)
        journal = JournalRepository(db)
        tasksRepo = TasksRepository(db)
        settingsRepo = SettingsRepository(settingsStore("cierre-vm"))
        rewardsRepo = RewardsRepository(db)
        loGordo = LoGordoRepository(db) { fixedNow }
        aceptados = AvisosAceptadosRepository(db)
        avisos = AvisosContados()
        val reconciler = PointsReconciler(domainStateRepo, rewardsRepo)
        val sonidos = HabiSounds(MotorSonidoMudo)
        vm =
            CierreViewModel(
                domainStateRepo,
                habitsRepo,
                journal,
                settingsRepo,
                reconciler,
                rewardsRepo,
                sonidos,
                MemoriaRepository(db) { fixedNow },
                loGordo,
                aceptados,
                avisos,
                FuentePropuestasNula,
                now = { fixedNow },
                zone = { utc },
                defaultDispatcher = dispatcher,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun TestScope.state(): CierreUiState {
        backgroundScope.launch { vm.uiState.collect() }
        advanceUntilIdle()
        return vm.uiState.value
    }

    private suspend fun sembrarCheck(
        id: String,
        direction: Direction = Direction.AT_LEAST,
    ) = habitsRepo.create(
        habitEntity(
            id = id,
            metric = Metric.CHECK,
            direction = direction,
            target = if (direction == Direction.ZERO) 0 else 1,
            createdOnDay = today,
        ),
    )

    private suspend fun eventos() = db.hablaDao().todosEventos()

    @Test
    fun `markDone logs today and the row disappears`() =
        runTest {
            sembrarCheck("h1")
            val card = state().filas.single { it.id == "h1" }

            vm.markDone(card)
            advanceUntilIdle()

            assertTrue(state().filas.none { it.id == "h1" })
            assertEquals(1, db.entryDao().all().single { it.habitId == "h1" }.value)
        }

    @Test
    fun `addAmount and setExact write the day total`() =
        runTest {
            habitsRepo.create(habitEntity(id = "agua", metric = Metric.COUNT, target = 8, createdOnDay = today))
            val card = state().filas.single { it.id == "agua" }

            vm.addAmount(card, 3)
            advanceUntilIdle()
            assertEquals(3, db.entryDao().all().single { it.habitId == "agua" }.value)

            vm.setExact(card, 5)
            advanceUntilIdle()
            assertEquals(5, db.entryDao().all().filter { it.habitId == "agua" }.sumOf { it.value })
        }

    @Test
    fun `decidir dims the row and deshacer brings it back, without writing anything`() =
        runTest {
            sembrarCheck("h1")
            assertTrue(state().filas.any { it.id == "h1" })

            vm.decidir("h1")
            advanceUntilIdle()
            assertEquals(setOf("h1"), state().decididas)

            vm.deshacer("h1")
            advanceUntilIdle()
            assertTrue(state().decididas.isEmpty())

            vm.decidir("h1")
            advanceUntilIdle()
            assertTrue(db.entryDao().all().isEmpty())
            assertTrue(db.daySealDao().all().isEmpty())
            assertTrue(db.pointsLedgerDao().all().isEmpty())
            assertTrue(eventos().isEmpty())
        }

    @Test
    fun `reiniciar forgets what was decided and the mood`() =
        runTest {
            sembrarCheck("h1")
            state()
            vm.decidir("h1")
            vm.elegirAnimo(1)
            advanceUntilIdle()
            assertEquals(setOf("h1"), state().decididas)
            assertEquals(1, state().animo)

            vm.reiniciar()
            advanceUntilIdle()

            assertTrue(state().decididas.isEmpty())
            assertNull(state().animo)
        }

    @Test
    fun `logRelapse logs value 1 on a ZERO habit`() =
        runTest {
            sembrarCheck("h1", Direction.ZERO)
            val card = state().filas.single { it.id == "h1" }

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
            assertEquals(listOf(today - 2, today - 1), state().diasPendientes)

            vm.sealPendingDays()
            advanceUntilIdle()

            val seals = db.daySealDao().all().map { it.logicalDay }.sorted()
            assertEquals(listOf(today - 2, today - 1), seals)
            assertTrue(seals.none { it == today })
        }

    @Test
    fun `buenasNoches seals today, reconciles and flips sellado`() =
        runTest {
            assertFalse(state().sellado)
            sembrarCheck("h1")
            journal.log(entryEntity(id = "e1", habitId = "h1", logicalDay = today, value = 1))
            advanceUntilIdle()
            assertTrue(db.pointsLedgerDao().all().isEmpty())

            vm.buenasNoches()
            advanceUntilIdle()

            assertEquals(today, db.daySealDao().all().single().logicalDay)
            val despues = state()
            assertTrue(despues.sellado)
            assertTrue(despues.perfecto)
            assertTrue(db.pointsLedgerDao().all().any { it.reason == PointsReason.PERFECT_DAY && it.refId == "day:$today" })
            assertEquals(1, avisos.replanificados)
        }

    @Test
    fun `the mood is optional and can change until good night`() =
        runTest {
            sembrarCheck("h1")
            assertNull(state().animo)

            vm.elegirAnimo(0)
            vm.elegirAnimo(2)
            advanceUntilIdle()
            assertEquals(2, state().animo)

            vm.buenasNoches()
            advanceUntilIdle()
            assertTrue(state().sellado)

            vm.elegirAnimo(1)
            advanceUntilIdle()
            assertEquals(2, state().animo)
        }

    @Test
    fun `good night writes mood, big thing done and accepted fulfilled`() =
        runTest {
            sembrarCheck("h1")
            sembrarCheck("h2")
            tasksRepo.create(taskEntity(id = "t1", status = TaskStatus.DONE, doneAtMillis = fixedNow, doneOnDay = today))
            journal.log(entryEntity(id = "e1", habitId = "h1", logicalDay = today, value = 1))
            loGordo.guardar(today, listOf(RefGordo(TipoRefGordo.HABITO, "h1"), RefGordo(TipoRefGordo.TAREA, "t1")))
            aceptados.crear(AvisoAceptado("a1", today, "h1", 600, 0, EstadoAviso.LANZADO, "p1", 1L))
            aceptados.crear(AvisoAceptado("a2", today, "h2", 660, 0, EstadoAviso.LANZADO, "p2", 1L))
            state()
            vm.elegirAnimo(2)

            vm.buenasNoches()
            advanceUntilIdle()

            val porTipo = eventos().groupBy { it.tipo }
            assertEquals(setOf("ANIMO", "GORDO_HECHO", "ACEPTADO_CUMPLIDO"), porTipo.keys)
            assertEquals(2, porTipo.getValue("ANIMO").single().valor)
            assertEquals(2 * 10 + 2, porTipo.getValue("GORDO_HECHO").single().valor)
            assertEquals(today, porTipo.getValue("GORDO_HECHO").single().dia)
            val cumplido = porTipo.getValue("ACEPTADO_CUMPLIDO").single()
            assertEquals("h1", cumplido.ref)
            assertEquals(today, cumplido.dia)
            assertEquals(1, avisos.replanificados)
        }

    @Test
    fun `good night without a mood writes no mood`() =
        runTest {
            sembrarCheck("h1")
            journal.log(entryEntity(id = "e1", habitId = "h1", logicalDay = today, value = 1))
            state()

            vm.buenasNoches()
            advanceUntilIdle()

            assertEquals(today, db.daySealDao().all().single().logicalDay)
            assertTrue(eventos().none { it.tipo == "ANIMO" })
        }

    @Test
    fun `an unfinished big thing writes no GORDO_HECHO and is listed as pending`() =
        runTest {
            sembrarCheck("h1")
            sembrarCheck("h2")
            journal.log(entryEntity(id = "e1", habitId = "h1", logicalDay = today, value = 1))
            loGordo.guardar(today, listOf(RefGordo(TipoRefGordo.HABITO, "h1"), RefGordo(TipoRefGordo.HABITO, "h2")))

            assertEquals(listOf("Agua"), state().gordoSinHacer)
            vm.buenasNoches()
            advanceUntilIdle()

            assertTrue(eventos().none { it.tipo == "GORDO_HECHO" })
        }

    @Test
    fun `a second good night does not write the memory twice`() =
        runTest {
            sembrarCheck("h1")
            state()
            vm.elegirAnimo(0)

            vm.buenasNoches()
            vm.buenasNoches()
            advanceUntilIdle()

            assertEquals(1, eventos().count { it.tipo == "ANIMO" })
            assertEquals(1, avisos.replanificados)
        }

    @Test
    fun `tasks are listed for tomorrow and never marked`() =
        runTest {
            listOf("a", "b", "c").forEachIndexed { i, id ->
                tasksRepo.create(
                    taskEntity(id = id, title = "Tarea $id", dueKind = DueKind.DATE, dueDay = today, createdAtMillis = 1_000L + i),
                )
            }

            val s = state()

            assertEquals(listOf("Tarea a", "Tarea b"), s.tareasManana)
            assertEquals(1, s.tareasMas)
            vm.buenasNoches()
            advanceUntilIdle()
            assertTrue(db.taskDao().observeAll().first().all { it.status == TaskStatus.OPEN })
        }

    @Test
    fun `nothing is written while the state is loading`() =
        runTest {
            sembrarCheck("h1")
            journal.log(entryEntity(id = "e1", habitId = "h1", logicalDay = today, value = 1))
            vm.elegirAnimo(1)
            // Sin colector: el estado sigue siendo el de relleno, con loading = true.
            assertTrue(vm.uiState.value.loading)

            assertNull(vm.buenasNoches())
            advanceUntilIdle()

            assertTrue(db.daySealDao().all().isEmpty())
            assertTrue(eventos().isEmpty())
            assertEquals(0, avisos.replanificados)
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

    @Test
    fun `markBadgesSeen records the latest shown unlock, not the wall clock`() =
        runTest {
            db.badgeDao().insert(BadgeEntity(badgeId = "first-habit", unlockedAtMillis = fixedNow - 20_000))
            db.badgeDao().insert(BadgeEntity(badgeId = "streak-7", unlockedAtMillis = fixedNow - 5_000))

            vm.markBadgesSeen()
            advanceUntilIdle()

            assertEquals(fixedNow - 5_000, settingsRepo.settings.first().badgesSeenUntilMillis)
        }

    @Test
    fun `markBadgesSeen does not lose a higher mark committed while it is still computing`() =
        runTest {
            db.badgeDao().insert(BadgeEntity(badgeId = "first-habit", unlockedAtMillis = fixedNow - 20_000))
            state()

            vm.markBadgesSeen()
            launch {
                settingsRepo.settings.first()
                settingsRepo.update { it.copy(badgesSeenUntilMillis = fixedNow) }
            }
            advanceUntilIdle()

            assertEquals(fixedNow, settingsRepo.settings.first().badgesSeenUntilMillis)
        }

    /** Del repaso (`pointsToday` y `newBadges`): lo que el sello cuenta. */
    @Test
    fun `the seal counts today's bitos and lists only unseen badges in catalog order`() =
        runTest {
            db.pointsLedgerDao().insertAll(
                listOf(
                    pointsLedgerEntity("a", 5, PointsReason.HABIT_DONE, "h1:$today", today, 1),
                    pointsLedgerEntity("b", -3, PointsReason.BUY_ITEM, "item", today, 2),
                    pointsLedgerEntity("c", 10, PointsReason.PERFECT_DAY, "day:${today - 1}", today - 1, 3),
                ),
            )
            db.badgeDao().insertAll(
                listOf(BadgeEntity("first-habit", 30L), BadgeEntity("streak-7", 30L), BadgeEntity("perfect-day-1", 5L)),
            )
            settingsRepo.update { it.copy(badgesSeenUntilMillis = 10L) }

            val ui = state()

            assertEquals(5, ui.bitosHoy)
            // El orden del catálogo (streak-7 antes que first-habit), no el de la lista; el ya visto no sale.
            assertEquals(listOf("streak-7", "first-habit"), ui.logrosNuevos.map { it.id })
        }
}
