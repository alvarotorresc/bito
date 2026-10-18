package com.alvarotc.bito.ui.tienda.jardin

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.customizationItemEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.ui.tienda.EventoTienda
import com.alvarotc.bito.ui.tienda.RelojTienda
import com.alvarotc.bito.ui.tienda.TiendaViewModel
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
import java.time.ZonedDateTime

/**
 * Comprar objetos de jardín con el modelo de verdad sobre Room en memoria: el único doble es el reloj, y todo corre por el
 * mismo despachador que Room, así que cada escritura es asíncrona como en la app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompraJardinRoomTest {
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
            ) { File(tmp.root, "compra-jardin.preferences_pb") }
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
    fun `buying the watering can keeps the bench placed`() =
        runTest {
            saldo(200)
            val vm = arrancar(nuevo())
            vm.tocarPieza("garden-banco")
            advanceUntilIdle()
            vm.comprar("garden-banco")
            advanceUntilIdle()
            vm.tocarPieza("garden-regadera")
            advanceUntilIdle()
            vm.comprar("garden-regadera")
            advanceUntilIdle()

            assertEquals(setOf("banco", "regadera"), vm.uiState.value.jardin.objects)
            assertTrue(fila("garden-banco")!!.equipped)
            assertTrue(fila("garden-regadera")!!.equipped)
            assertEquals(95, vm.uiState.value.saldo)
        }

    @Test
    fun `a double tap on the bench pays once and announces it once`() =
        runTest {
            saldo(200)
            val vm = arrancar(nuevo())
            val eventos = recoger(vm)
            vm.tocarPieza("garden-banco")
            advanceUntilIdle()
            vm.comprar("garden-banco")
            vm.comprar("garden-banco")
            advanceUntilIdle()

            assertEquals(1, db.pointsLedgerDao().all().count { it.reason == PointsReason.BUY_ITEM })
            assertEquals(listOf<EventoTienda>(EventoTienda.Comprado("garden-banco")), eventos)
            assertEquals(120, vm.uiState.value.saldo)
        }

    @Test
    fun `an owned object is not tried again and a trial of the other one survives`() =
        runTest {
            saldo(200)
            tener("garden-banco", puesto = true)
            val vm = arrancar(nuevo())
            vm.tocarPieza("garden-regadera")
            advanceUntilIdle()
            vm.tocarPieza("garden-banco")
            advanceUntilIdle()

            assertEquals(com.alvarotc.bito.ui.tienda.PruebaTienda.Objeto("garden-regadera"), vm.uiState.value.prueba)
            assertEquals(setOf("banco"), vm.uiState.value.jardin.objects)
        }
}
