package com.alvarotc.bito.ui.premios

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.PointsLedgerEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.MarcaLibro
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.model.GardenPlot
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenSpecies
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.GrowthStage
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
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
class CelebracionesViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val hoy = 20_300

    private lateinit var db: BitoDatabase
    private lateinit var domainStateRepo: DomainStateRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var rewardsRepo: RewardsRepository
    private lateinit var vm: CelebracionesViewModel

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
        settingsRepo = SettingsRepository(settingsStore("celebraciones-vm"))
        rewardsRepo = RewardsRepository(db)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private fun crear() = CelebracionesViewModel(domainStateRepo, rewardsRepo, settingsRepo)

    private fun fila(
        id: String,
        t: Long,
        reason: PointsReason,
        ref: String,
        delta: Int = 1,
    ): PointsLedgerEntity = pointsLedgerEntity(id = id, delta = delta, reason = reason, refId = ref, logicalDay = hoy, createdAtMillis = t)

    private suspend fun insertar(
        id: String,
        t: Long,
        reason: PointsReason,
        ref: String,
        delta: Int = 1,
    ) = db.pointsLedgerDao().insert(fila(id, t, reason, ref, delta))

    private suspend fun marca() = settingsRepo.settings.first().celebratedLedgerMark

    @Test
    fun `the mark only moves when the last moment of its batch finishes`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(celebratedLedgerMark = MarcaLibro(0, "")) }
            vm = crear()
            vm.enHoy(true)
            advanceUntilIdle()
            // Las dos en una sola escritura: así llegan en la misma emisión (un solo lote). Con dos inserciones
            // sueltas podrían ser dos lotes y la marca avanzaría tras la primera: el test sería frágil.
            db.pointsLedgerDao().insertAll(
                listOf(fila("d", 10, PointsReason.PERFECT_DAY, "day:$hoy", 3), fila("r", 11, PointsReason.STREAK_MILESTONE, "h1:7", 5)),
            )
            advanceUntilIdle()
            vm.terminado(vm.actual.value!!) // la veta del día
            advanceUntilIdle()
            assertEquals(MarcaLibro(0, ""), marca()) // falta la racha
            vm.terminado(vm.actual.value!!) // la de la racha
            advanceUntilIdle()
            assertEquals(MarcaLibro(11, "r"), marca())
        }

    @Test
    fun `finishing a perfect day vein marks the day as celebrated`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(celebratedLedgerMark = MarcaLibro(0, "")) }
            vm = crear()
            vm.enHoy(true)
            advanceUntilIdle()
            insertar("d", 10, PointsReason.PERFECT_DAY, "day:$hoy", 3)
            advanceUntilIdle()
            val veta = vm.actual.value as Momento.Veta
            assertEquals(TipoVeta.DIA, veta.tipo)
            vm.terminado(veta)
            advanceUntilIdle()
            assertEquals(hoy, settingsRepo.settings.first().perfectDayCelebratedDay)
            assertEquals(MarcaLibro(10, "d"), marca())
        }

    @Test
    fun `a vein queued while away is dropped if the review celebrated it meanwhile`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(celebratedLedgerMark = MarcaLibro(0, "")) }
            vm = crear()
            vm.enHoy(false)
            advanceUntilIdle()
            insertar("d", 10, PointsReason.PERFECT_DAY, "day:$hoy", 3)
            advanceUntilIdle()
            settingsRepo.update { it.copy(perfectDayCelebratedDay = hoy) } // como hace el repaso
            advanceUntilIdle()
            vm.enHoy(true)
            advanceUntilIdle()
            assertNull(vm.actual.value)
            assertEquals(MarcaLibro(10, "d"), marca())
        }

    @Test
    fun `a new badge waits in the queue and finishing it advances badgesSeenUntilMillis`() =
        runTest(dispatcher) {
            vm = crear()
            vm.enHoy(true)
            advanceUntilIdle()
            db.badgeDao().insert(BadgeEntity("first-habit", 500))
            advanceUntilIdle()
            val logro = vm.actual.value as Momento.Logro
            assertEquals("first-habit", logro.badgeId)
            vm.terminado(logro)
            advanceUntilIdle()
            assertTrue(settingsRepo.settings.first().badgesSeenUntilMillis >= 500)
            assertNull(vm.actual.value)
        }

    @Test
    fun `what arrives while away from Today is celebrated on return, without coins`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(celebratedLedgerMark = MarcaLibro(0, "")) }
            vm = crear()
            vm.enHoy(false)
            advanceUntilIdle()
            insertar("d", 10, PointsReason.PERFECT_DAY, "day:$hoy", 3)
            insertar("a", 11, PointsReason.HABIT_DONE, "h1:$hoy")
            advanceUntilIdle()
            assertNull(vm.actual.value)
            vm.enHoy(true)
            advanceUntilIdle()
            assertEquals(TipoVeta.DIA, (vm.actual.value as Momento.Veta).tipo)
            vm.terminado(vm.actual.value!!)
            advanceUntilIdle()
            assertNull("no hay monedas pendientes", vm.actual.value)
        }

    @Test
    fun `a new special and a new stage come from the garden`() =
        runTest(dispatcher) {
            vm = crear()
            vm.enHoy(true)
            advanceUntilIdle()
            val g0 = GardenState.INITIAL
            vm.jardin(g0)
            advanceUntilIdle()
            assertNull(vm.actual.value) // el primer jardín solo se apunta
            val g1 =
                g0.copy(
                    specials = setOf(GardenSpecial.LANTERN),
                    plots = g0.plots + GardenPlot(GardenSpecies.SUNFLOWERS, GrowthStage.SEED),
                )
            vm.jardin(g1)
            advanceUntilIdle()
            val primero = vm.actual.value as Momento.Especial
            assertEquals(GardenSpecial.LANTERN, primero.especial)
            vm.terminado(primero)
            advanceUntilIdle()
            val etapa = vm.actual.value as Momento.Etapa
            assertEquals(1, etapa.parcela)
            assertTrue(etapa.parcelaNueva)
        }

    @Test
    fun `leaving Today closes the one playing for its batch and returning resumes the rest`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(celebratedLedgerMark = MarcaLibro(0, "")) }
            vm = crear()
            vm.enHoy(true)
            advanceUntilIdle()
            db.pointsLedgerDao().insertAll(
                listOf(fila("d", 10, PointsReason.PERFECT_DAY, "day:$hoy", 3), fila("r", 11, PointsReason.STREAK_MILESTONE, "h1:7", 5)),
            )
            advanceUntilIdle()
            vm.enHoy(false)
            advanceUntilIdle()
            assertNull(vm.actual.value)
            assertEquals(MarcaLibro(0, ""), marca()) // la racha sigue esperando
            vm.enHoy(true)
            advanceUntilIdle()
            assertEquals(TipoVeta.RACHA, (vm.actual.value as Momento.Veta).tipo)
            vm.terminado(vm.actual.value!!)
            advanceUntilIdle()
            assertEquals(MarcaLibro(11, "r"), marca())
        }

    @Test
    fun `after a restore nothing old is celebrated`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(celebratedLedgerMark = MarcaLibro(0, "")) }
            vm = crear()
            vm.enHoy(true)
            advanceUntilIdle()
            // Restaurar: la base trae el libro de la copia (ids nuevos, semanas, meses y rachas viejas) y los ajustes
            // se escriben enteros, con la marca a null. La base puede llegar antes que los ajustes: lo peor.
            db.pointsLedgerDao().insertAll(
                listOf(
                    fila("s", 20, PointsReason.PERFECT_DAY, "week:2950", 10),
                    fila("m", 21, PointsReason.PERFECT_DAY, "month:24300", 50),
                    fila("k", 22, PointsReason.STREAK_MILESTONE, "h1:30", 20),
                ),
            )
            advanceUntilIdle() // dentro de la sesión, esto se habría encolado
            settingsRepo.update { it.copy(celebratedLedgerMark = null) }
            advanceUntilIdle() // incluye el respiro: si quedara algo en la cola, ya habría salido
            assertNull(vm.actual.value) // lo que sonaba se cortó y la cola quedó sin lo del libro
            assertEquals(MarcaLibro(22, "k"), marca()) // refijada sin celebrar
        }

    @Test
    fun `a later batch never moves the mark past an earlier batch still open`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(celebratedLedgerMark = MarcaLibro(0, "")) }
            vm = crear()
            vm.enHoy(false)
            advanceUntilIdle()
            insertar("r", 10, PointsReason.STREAK_MILESTONE, "h1:7", 5) // veta de racha en pausa
            advanceUntilIdle()
            insertar("b", 20, PointsReason.BUY_ITEM, "item", -5) // un lote sin momentos
            advanceUntilIdle()
            assertEquals("si la app muriera ahora, la racha seguiría pendiente", MarcaLibro(0, ""), marca())
            vm.enHoy(true)
            advanceUntilIdle()
            vm.terminado(vm.actual.value!!)
            advanceUntilIdle()
            assertEquals(MarcaLibro(20, "b"), marca())
        }

    @Test
    fun `restoring on a fresh install, which never wrote a mark, celebrates nothing old`() =
        runTest(dispatcher) {
            vm = crear()
            vm.enHoy(true)
            advanceUntilIdle()
            db.pointsLedgerDao().insertAll(
                listOf(
                    fila("s", 20, PointsReason.PERFECT_DAY, "week:2950", 10),
                    fila("m", 21, PointsReason.PERFECT_DAY, "month:24300", 50),
                    fila("k", 22, PointsReason.STREAK_MILESTONE, "h1:30", 20),
                ),
            )
            advanceUntilIdle()
            settingsRepo.update { it.copy(celebratedLedgerMark = null) }
            advanceUntilIdle()
            assertNull(vm.actual.value)
            assertEquals(MarcaLibro(22, "k"), marca())
        }

    @Test
    fun `a restore whose settings arrive before the restored ledger celebrates nothing old`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(celebratedLedgerMark = MarcaLibro(0, "")) }
            vm = crear()
            vm.enHoy(true)
            advanceUntilIdle()
            settingsRepo.update { it.copy(celebratedLedgerMark = null) }
            advanceUntilIdle()
            db.pointsLedgerDao().insertAll(
                listOf(
                    fila("s", 20, PointsReason.PERFECT_DAY, "week:2950", 10),
                    fila("k", 22, PointsReason.STREAK_MILESTONE, "h1:30", 20),
                ),
            )
            advanceUntilIdle()
            assertNull(vm.actual.value)
            assertEquals(MarcaLibro(22, "k"), marca())
        }

    @Test
    fun `a ledger that replaces the old one is a restore even before the settings arrive`() =
        runTest(dispatcher) {
            insertar("o", 5, PointsReason.HABIT_DONE, "h1:$hoy")
            settingsRepo.update { it.copy(celebratedLedgerMark = MarcaLibro(5, "o")) }
            vm = crear()
            vm.enHoy(true)
            advanceUntilIdle()
            db.withTransaction {
                db.pointsLedgerDao().deleteAll()
                db.pointsLedgerDao().insertAll(
                    listOf(
                        fila("s", 20, PointsReason.PERFECT_DAY, "week:2950", 10),
                        fila("k", 22, PointsReason.STREAK_MILESTONE, "h1:30", 20),
                    ),
                )
            }
            advanceUntilIdle()
            assertNull(vm.actual.value)
            assertEquals(MarcaLibro(22, "k"), marca())
        }

    @Test
    fun `leaving Today while a perfect day vein plays marks the day as celebrated`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(celebratedLedgerMark = MarcaLibro(0, "")) }
            vm = crear()
            vm.enHoy(true)
            advanceUntilIdle()
            insertar("d", 10, PointsReason.PERFECT_DAY, "day:$hoy", 3)
            advanceUntilIdle()
            vm.enHoy(false)
            advanceUntilIdle()
            assertEquals(hoy, settingsRepo.settings.first().perfectDayCelebratedDay)
        }

    @Test
    fun `a badge that arrives before its ledger entry plays after the coins (G14)`() =
        runTest(dispatcher) {
            settingsRepo.update { it.copy(celebratedLedgerMark = MarcaLibro(0, "")) }
            vm = crear()
            vm.enHoy(true)
            advanceUntilIdle()
            db.badgeDao().insert(BadgeEntity("first-habit", 500)) // el logro se escribe antes que la fila del libro
            advanceTimeBy(100)
            runCurrent()
            assertNull("el logro espera a ver si llega algo del libro", vm.actual.value)
            insertar("a", 600, PointsReason.HABIT_DONE, "h1:$hoy")
            runCurrent()
            val monedas = vm.actual.value as Momento.Monedas
            vm.terminado(monedas)
            advanceUntilIdle()
            assertEquals("first-habit", (vm.actual.value as Momento.Logro).badgeId)
        }

    @Test
    fun `a badge with no ledger entry still plays once the window passes`() =
        runTest(dispatcher) {
            vm = crear()
            vm.enHoy(true)
            advanceUntilIdle()
            db.badgeDao().insert(BadgeEntity("first-habit", 500))
            advanceTimeBy(VENTANA_LOGRO_MS - 50)
            runCurrent()
            assertNull(vm.actual.value)
            advanceTimeBy(100)
            runCurrent()
            assertEquals("first-habit", (vm.actual.value as Momento.Logro).badgeId)
        }
}
