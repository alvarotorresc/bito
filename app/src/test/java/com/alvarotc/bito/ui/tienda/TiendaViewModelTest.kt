package com.alvarotc.bito.ui.tienda

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.customizationItemEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.DaySealEntity
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.PointsEngine
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Conduce [TiendaViewModel] sobre Room en memoria con el reloj de la tienda en movimiento. El único doble
 * es el reloj: la base, el repositorio y los ajustes son los reales, y todo corre por el mismo despachador
 * que Room, así que cada escritura es asíncrona como en la app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TiendaViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val utc = ZoneId.of("UTC")
    private var ahora = millis(2025, 8, 15, 14, 0)
    private val reloj = RelojTienda(ahora = { ahora }, zona = { utc })
    private val ticks = MutableStateFlow(0)

    private lateinit var db: BitoDatabase
    private lateinit var domainRepo: DomainStateRepository
    private lateinit var rewards: RewardsRepository
    private lateinit var settingsRepo: SettingsRepository

    private fun millis(
        y: Int,
        m: Int,
        d: Int,
        h: Int,
        min: Int,
    ) = ZonedDateTime.of(y, m, d, h, min, 0, 0, utc).toInstant().toEpochMilli()

    private fun hoy() = LogicalDays.logicalDayOf(ahora, 0, utc)

    private fun tick() {
        ticks.value += 1
    }

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java)
                .setQueryExecutor(dispatcher.asExecutor())
                .setTransactionExecutor(dispatcher.asExecutor())
                .allowMainThreadQueries()
                .build()
        domainRepo = DomainStateRepository(db)
        rewards = RewardsRepository(db)
        val store: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(UnconfinedTestDispatcher(dispatcher.scheduler) + Job()),
            ) { File(tmp.root, "tienda-vm.preferences_pb") }
        settingsRepo = SettingsRepository(store)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
        db.close()
    }

    private fun nuevo(saved: SavedStateHandle = SavedStateHandle()) =
        TiendaViewModel(
            domainState = domainRepo,
            rewards = rewards,
            settings = settingsRepo,
            reloj = reloj,
            recursos = { ApplicationProvider.getApplicationContext<Context>().resources },
            saved = saved,
            clockTicks = ticks.map { },
            defaultDispatcher = dispatcher,
        )

    /** Suscribe y espera al valor real: el de relleno de antes de cargar no cuenta. */
    private fun TestScope.arrancar(vm: TiendaViewModel): TiendaViewModel {
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        assertTrue("el estado real ha llegado", vm.uiState.value.cargado)
        return vm
    }

    private fun TestScope.recoger(vm: TiendaViewModel): List<EventoTienda> {
        val lista = mutableListOf<EventoTienda>()
        backgroundScope.launch { vm.eventos.collect { lista += it } }
        return lista
    }

    private suspend fun saldo(bitos: Int) {
        db.pointsLedgerDao().insert(
            pointsLedgerEntity(
                id = "seed-${System.nanoTime()}",
                delta = bitos,
                reason = PointsReason.HABIT_DONE,
                refId = null,
                logicalDay = hoy(),
            ),
        )
    }

    private suspend fun tener(
        id: String,
        puesto: Boolean = false,
    ) {
        rewards.acquire(customizationItemEntity(id, HabiCatalog.byId(id)!!.category, equipped = puesto))
    }

    private suspend fun fila(id: String) = db.customizationItemDao().byId(id)

    @Test
    fun `trying never writes to Room`() =
        runTest {
            saldo(100)
            val vm = arrancar(nuevo())
            vm.tocarPieza("head-gorro-lana")
            advanceUntilIdle()

            val s = vm.uiState.value
            assertEquals(PruebaTienda.Pieza("head-gorro-lana"), s.prueba)
            assertEquals("head-gorro-lana", s.look.head)
            assertNull(s.puestos.head)
            assertTrue(s.barra is BarraPrueba.Comprar)
            assertTrue(db.customizationItemDao().all().isEmpty())
            assertTrue(db.pointsLedgerDao().all().none { it.reason == PointsReason.BUY_ITEM })
        }

    @Test
    fun `trying a piece in a covered slot shows it without the costume`() =
        runTest {
            tener("costume-calabaza", puesto = true)
            val vm = arrancar(nuevo())
            vm.tocarPieza("head-gorro-lana")
            advanceUntilIdle()

            assertNull(vm.uiState.value.look.costume)
            assertEquals("head-gorro-lana", vm.uiState.value.look.head)
            assertEquals("costume-calabaza", vm.uiState.value.puestos.costume)
        }

    @Test
    fun `tapping a piece you own wears it without a bar`() =
        runTest {
            tener("head-boina")
            val vm = arrancar(nuevo())
            vm.tocarPieza("head-boina")
            advanceUntilIdle()

            assertTrue(fila("head-boina")!!.equipped)
            assertNull(vm.uiState.value.prueba)
            assertNull(vm.uiState.value.barra)
            assertEquals("head-boina", vm.uiState.value.puestos.head)
        }

    @Test
    fun `tapping the one you wear takes it off`() =
        runTest {
            tener("head-boina", puesto = true)
            val vm = arrancar(nuevo())
            vm.tocarPieza("head-boina")
            advanceUntilIdle()

            assertFalse(fila("head-boina")!!.equipped)
            assertNull(vm.uiState.value.puestos.head)
        }

    @Test
    fun `tapping the default body colour takes the other one off`() =
        runTest {
            tener("body-rosa", puesto = true)
            val vm = arrancar(nuevo())
            vm.tocarPieza(HabiCatalog.DEFAULT_BODY)
            advanceUntilIdle()

            assertFalse(fila("body-rosa")!!.equipped)
            assertEquals(HabiCatalog.DEFAULT_BODY, vm.uiState.value.puestos.bodyColor)
        }

    @Test
    fun `tapping a covered slot removes the costume and wears the piece`() =
        runTest {
            tener("costume-calabaza", puesto = true)
            tener("head-boina")
            tener("neck-bufanda", puesto = true)
            val vm = arrancar(nuevo())
            vm.tocarPieza("head-boina")
            advanceUntilIdle()

            assertFalse(fila("costume-calabaza")!!.equipped)
            assertTrue(fila("head-boina")!!.equipped)
            assertTrue("lo demás que llevaba sigue puesto", fila("neck-bufanda")!!.equipped)
            assertNull(vm.uiState.value.puestos.costume)
            assertEquals("head-boina", vm.uiState.value.puestos.head)
        }

    @Test
    fun `a costume bought out of season is worn without a bar`() =
        runTest {
            tener("costume-reno")
            val vm = arrancar(nuevo())
            vm.tocarPieza("costume-reno")
            advanceUntilIdle()

            assertTrue(fila("costume-reno")!!.equipped)
            assertNull(vm.uiState.value.barra)
        }

    @Test
    fun `tapping a special tries it`() =
        runTest {
            val vm = arrancar(nuevo())
            vm.tocarEspecial("streak-7")
            advanceUntilIdle()

            assertEquals(PruebaTienda.Especial("streak-7"), vm.uiState.value.prueba)
            val barra = vm.uiState.value.barra as BarraPrueba.NoSeCompra
            assertEquals(MotivoNoSeCompra.LOGRO, barra.motivo)
            assertTrue(barra.objeto)
        }

    @Test
    fun `tapping a garden object you do not own tries it and one you own does nothing`() =
        runTest {
            saldo(100)
            val vm = arrancar(nuevo())
            vm.tocarPieza("garden-banco")
            advanceUntilIdle()
            assertEquals(PruebaTienda.Objeto("garden-banco"), vm.uiState.value.prueba)
            assertTrue((vm.uiState.value.barra as BarraPrueba.Comprar).objeto)

            vm.quitarPrueba()
            tener("garden-banco", puesto = true)
            vm.tocarPieza("garden-banco")
            advanceUntilIdle()
            assertNull(vm.uiState.value.prueba)
            assertNull(vm.uiState.value.barra)
            assertTrue(fila("garden-banco")!!.equipped)
            assertEquals(setOf("garden-banco"), vm.uiState.value.objetosJardin)
        }

    @Test
    fun `quitarPrueba clears the trial`() =
        runTest {
            val vm = arrancar(nuevo())
            vm.tocarPieza("head-gorro-lana")
            advanceUntilIdle()
            vm.quitarPrueba()
            advanceUntilIdle()

            assertNull(vm.uiState.value.prueba)
            assertNull(vm.uiState.value.barra)
        }

    @Test
    fun `changing tab drops the trial`() =
        runTest {
            val vm = arrancar(nuevo())
            vm.tocarPieza("head-gorro-lana")
            advanceUntilIdle()
            vm.cambiarPestana(PestanaTienda.JARDIN)
            advanceUntilIdle()

            assertEquals(PestanaTienda.JARDIN, vm.uiState.value.pestana)
            assertNull(vm.uiState.value.prueba)
        }

    @Test
    fun `buying keeps it worn and emits Comprado`() =
        runTest {
            saldo(100)
            val vm = arrancar(nuevo())
            val eventos = recoger(vm)
            vm.tocarPieza("head-gorro-lana")
            advanceUntilIdle()
            vm.comprar("head-gorro-lana")
            advanceUntilIdle()

            assertEquals(listOf<EventoTienda>(EventoTienda.Comprado("head-gorro-lana")), eventos)
            assertTrue(fila("head-gorro-lana")!!.equipped)
            assertEquals("head-gorro-lana", vm.uiState.value.puestos.head)
            assertNull(vm.uiState.value.prueba)
            assertNull(vm.uiState.value.barra)
            assertEquals(40, vm.uiState.value.saldo)
        }

    @Test
    fun `a failed purchase keeps the bar and refreshes the balance`() =
        runTest {
            saldo(60)
            val vm = arrancar(nuevo())
            val eventos = recoger(vm)
            vm.tocarPieza("head-gorro-lana")
            advanceUntilIdle()
            assertTrue(vm.uiState.value.barra is BarraPrueba.Comprar)

            // El saldo baja por otro lado antes de que llegue el toque.
            rewards.spend(-50, PointsReason.BUY_FREEZER, "otro", hoy(), ahora)
            vm.comprar("head-gorro-lana")
            advanceUntilIdle()

            assertTrue(eventos.isEmpty())
            assertEquals(PruebaTienda.Pieza("head-gorro-lana"), vm.uiState.value.prueba)
            assertEquals(BarraPrueba.SinSaldo(vm.uiState.value.barra!!.nombre, 60, 10, false), vm.uiState.value.barra)
            assertEquals(10, vm.uiState.value.saldo)
            assertNull(fila("head-gorro-lana"))
        }

    @Test
    fun `a double tap pays once`() =
        runTest {
            saldo(200)
            val vm = arrancar(nuevo())
            val eventos = recoger(vm)
            vm.tocarPieza("head-gorro-lana")
            advanceUntilIdle()
            vm.comprar("head-gorro-lana")
            vm.comprar("head-gorro-lana")
            advanceUntilIdle()

            assertEquals(1, db.pointsLedgerDao().all().count { it.reason == PointsReason.BUY_ITEM })
            assertEquals(1, eventos.size)
            assertEquals(140, vm.uiState.value.saldo)
        }

    @Test
    fun `a double tap on the freezer pays once`() =
        runTest {
            saldo(300)
            val vm = arrancar(nuevo())
            vm.comprarCongelador()
            vm.comprarCongelador()
            advanceUntilIdle()

            assertEquals(1, db.pointsLedgerDao().all().count { it.reason == PointsReason.BUY_FREEZER })
        }

    @Test
    fun `buying with exactly 100 bitos buys a freezer and leaves 0`() =
        runTest {
            saldo(100)
            val vm = arrancar(nuevo())
            val eventos = recoger(vm)
            vm.comprarCongelador()
            advanceUntilIdle()

            assertEquals(0, vm.uiState.value.saldo)
            assertEquals(1, vm.uiState.value.congeladores)
            val compra = db.pointsLedgerDao().all().single { it.reason == PointsReason.BUY_FREEZER }
            assertEquals(-100, compra.delta)
            assertEquals(listOf<EventoTienda>(EventoTienda.CongeladorComprado(compra.refId!!, 1)), eventos)
        }

    @Test
    fun `a freezer without balance writes nothing`() =
        runTest {
            saldo(99)
            val vm = arrancar(nuevo())
            val eventos = recoger(vm)
            vm.comprarCongelador()
            advanceUntilIdle()

            assertTrue(eventos.isEmpty())
            assertTrue(db.pointsLedgerDao().all().none { it.reason == PointsReason.BUY_FREEZER })
            assertEquals(99, vm.uiState.value.saldo)
        }

    @Test
    fun `undo returns the freezer and the coins`() =
        runTest {
            saldo(100)
            val vm = arrancar(nuevo())
            val eventos = recoger(vm)
            vm.comprarCongelador()
            advanceUntilIdle()
            vm.deshacerCongelador()
            advanceUntilIdle()

            assertEquals(100, vm.uiState.value.saldo)
            assertEquals(0, vm.uiState.value.congeladores)
            assertEquals(EventoTienda.CongeladorDeshecho, eventos.last())
            vm.deshacerCongelador()
            advanceUntilIdle()
            assertEquals("deshacer dos veces no devuelve dos veces", 100, vm.uiState.value.saldo)
            assertEquals(2, eventos.size)
        }

    @Test
    fun `a second purchase replaces the undo of the first`() =
        runTest {
            saldo(200)
            val vm = arrancar(nuevo())
            vm.comprarCongelador()
            advanceUntilIdle()
            val primera = db.pointsLedgerDao().all().single { it.reason == PointsReason.BUY_FREEZER }.refId
            vm.comprarCongelador()
            advanceUntilIdle()
            vm.deshacerCongelador()
            advanceUntilIdle()

            assertEquals("solo se deshace la última", 100, vm.uiState.value.saldo)
            assertEquals(1, vm.uiState.value.congeladores)
            val quedan = db.pointsLedgerDao().all().filter { it.reason == PointsReason.BUY_FREEZER }
            assertEquals(listOf(primera), quedan.map { it.refId })
            vm.deshacerCongelador()
            advanceUntilIdle()
            assertEquals("la primera ya no se puede deshacer", 100, vm.uiState.value.saldo)
        }

    @Test
    fun `undo after the freezer was used is refused`() =
        runTest {
            saldo(100)
            val vm = arrancar(nuevo())
            val eventos = recoger(vm)
            vm.comprarCongelador()
            advanceUntilIdle()
            // Se usa en la ficha: ahora hay un uso registrado de ese congelador.
            db.habitDao().upsert(com.alvarotc.bito.data.habitEntity(id = "h1"))
            db.freezerUseDao().insert(com.alvarotc.bito.data.freezerUseEntity(habitId = "h1"))
            advanceUntilIdle()
            vm.deshacerCongelador()
            advanceUntilIdle()

            assertEquals(0, vm.uiState.value.saldo)
            assertTrue(eventos.none { it is EventoTienda.CongeladorDeshecho })
        }

    @Test
    fun `a restored trial that is already owned is dropped`() =
        runTest {
            tener("head-gorro-lana")
            val saved =
                SavedStateHandle(mapOf(TiendaViewModel.CLAVE_PESTANA to "JARDIN", TiendaViewModel.CLAVE_PRUEBA to "pieza:head-gorro-lana"))
            val vm = arrancar(nuevo(saved))

            assertEquals(PestanaTienda.JARDIN, vm.uiState.value.pestana)
            assertNull(vm.uiState.value.prueba)
            assertNull(vm.uiState.value.barra)
        }

    @Test
    fun `a restored trial that no longer exists is dropped`() =
        runTest {
            val saved = SavedStateHandle(mapOf(TiendaViewModel.CLAVE_PRUEBA to "pieza:head-sombrero-que-ya-no-existe"))
            val vm = arrancar(nuevo(saved))

            assertNull(vm.uiState.value.prueba)
            assertNull(vm.uiState.value.barra)
        }

    @Test
    fun `the light is not restored but the tab and the trial are`() =
        runTest {
            val saved =
                SavedStateHandle(
                    mapOf(
                        TiendaViewModel.CLAVE_PESTANA to "CONGELADORES",
                        TiendaViewModel.CLAVE_PRUEBA to "pieza:head-gorro-lana",
                        "tienda_luz" to "NOCHE",
                    ),
                )
            val vm = arrancar(nuevo(saved))

            assertEquals(PestanaTienda.CONGELADORES, vm.uiState.value.pestana)
            assertEquals(PruebaTienda.Pieza("head-gorro-lana"), vm.uiState.value.prueba)
            assertEquals(LuzTienda.DIA, vm.uiState.value.luz)
        }

    @Test
    fun `the tab and the trial are saved as they change`() =
        runTest {
            val saved = SavedStateHandle()
            val vm = arrancar(nuevo(saved))
            vm.cambiarPestana(PestanaTienda.JARDIN)
            vm.tocarPieza("garden-banco")
            advanceUntilIdle()

            assertEquals("JARDIN", saved.get<String>(TiendaViewModel.CLAVE_PESTANA))
            assertEquals("objeto:garden-banco", saved.get<String>(TiendaViewModel.CLAVE_PRUEBA))
            vm.quitarPrueba()
            assertNull(saved.get<String>(TiendaViewModel.CLAVE_PRUEBA))
        }

    @Test
    fun `it opens on Habi with the light of now`() =
        runTest {
            val vm = arrancar(nuevo())
            assertEquals(PestanaTienda.HABI, vm.uiState.value.pestana)
            assertEquals(LuzTienda.DIA, vm.uiState.value.luz)
            assertNull(vm.uiState.value.prueba)
        }

    @Test
    fun `the light of the afternoon opens on afternoon`() =
        runTest {
            ahora = millis(2025, 8, 15, 18, 0)
            assertEquals(LuzTienda.TARDE, arrancar(nuevo()).uiState.value.luz)
        }

    @Test
    fun `it opens on night when the day is closed`() =
        runTest {
            db.daySealDao().insert(DaySealEntity(hoy(), ahora))
            assertEquals(LuzTienda.NOCHE, arrancar(nuevo()).uiState.value.luz)
        }

    @Test
    fun `the chosen light stays`() =
        runTest {
            val vm = arrancar(nuevo())
            vm.cambiarLuz(LuzTienda.NOCHE)
            advanceUntilIdle()
            tick()
            advanceUntilIdle()
            assertEquals(LuzTienda.NOCHE, vm.uiState.value.luz)
        }

    @Test
    fun `crossing midnight turns the season bar into the out-of-season one`() =
        runTest {
            ahora = millis(2025, 11, 2, 23, 59)
            saldo(300)
            val vm = arrancar(nuevo())
            vm.tocarPieza("costume-calabaza")
            advanceUntilIdle()
            assertTrue(vm.uiState.value.barra is BarraPrueba.Comprar)

            ahora = millis(2025, 11, 3, 0, 1)
            tick()
            advanceUntilIdle()
            assertEquals(LocalDate.of(2025, 11, 3), vm.uiState.value.hoy)
            assertEquals(MotivoNoSeCompra.TEMPORADA, (vm.uiState.value.barra as BarraPrueba.NoSeCompra).motivo)

            vm.comprar("costume-calabaza")
            advanceUntilIdle()
            assertNull("purchase lo rechaza igual", fila("costume-calabaza"))
        }

    @Test
    fun `the first time Habi wears nothing`() =
        runTest {
            val vm = arrancar(nuevo())
            assertEquals(EquippedSet(), vm.uiState.value.puestos)
            assertEquals(EquippedSet(), vm.uiState.value.look)
            assertEquals(0, vm.uiState.value.saldo)
            assertEquals(PointsEngine.balance(emptyList()), vm.uiState.value.saldo)
        }
}
