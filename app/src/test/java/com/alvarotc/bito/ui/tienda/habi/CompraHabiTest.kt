package com.alvarotc.bito.ui.tienda.habi

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.pointsLedgerEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.tienda.AccionesGrabadas
import com.alvarotc.bito.ui.tienda.BarraPruebaView
import com.alvarotc.bito.ui.tienda.CapaMoneda
import com.alvarotc.bito.ui.tienda.ContextoPanel
import com.alvarotc.bito.ui.tienda.EscenaArmarioEstado
import com.alvarotc.bito.ui.tienda.EtiquetaHornacina
import com.alvarotc.bito.ui.tienda.EventoTienda
import com.alvarotc.bito.ui.tienda.PruebaTienda
import com.alvarotc.bito.ui.tienda.RelojTienda
import com.alvarotc.bito.ui.tienda.SaldoConRodillo
import com.alvarotc.bito.ui.tienda.TiendaAcciones
import com.alvarotc.bito.ui.tienda.TiendaContenido
import com.alvarotc.bito.ui.tienda.TiendaUiState
import com.alvarotc.bito.ui.tienda.TiendaViewModel
import com.alvarotc.bito.ui.tienda.VueloMoneda
import com.alvarotc.bito.ui.tienda.estadoTienda
import com.alvarotc.bito.ui.tienda.origenMoneda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId

/**
 * Un trozo de tienda con lo que importa de la compra: el saldo (origen de la moneda), la escena de Habi, la capa de
 * monedas y la barra. Todo lo de dentro es lo real; solo cambia quién da el estado, las acciones y los eventos.
 */
@Composable
private fun MiniTienda(
    estado: TiendaUiState,
    acciones: TiendaAcciones,
    eventos: Flow<EventoTienda>,
    vuelo: VueloMoneda,
    armario: EscenaArmarioEstado,
    sonido: SonidoGrabado,
    tacto: TactoGrabado,
    barraVista: Boolean = false,
    escenaVisible: Boolean = true,
) {
    val ctx =
        ContextoPanel(
            estado = estado,
            acciones = acciones,
            eventos = eventos,
            luz = { 0f },
            vuelo = vuelo,
            armario = armario,
            avisos = com.alvarotc.bito.ui.sistema.componentes.rememberAvisoEstado(),
            sonido = sonido,
            tacto = tacto,
        )
    Box(Modifier.fillMaxSize()) {
        Column {
            SaldoConRodillo(estado.saldo, Modifier.origenMoneda(vuelo))
            if (escenaVisible) PanelHabi.Escena(ctx, Modifier)
        }
        if (barraVista) {
            BarraPruebaView(
                barra = estado.barra,
                txtComprar = "Comprar",
                txtAhora = "Ahora no",
                txtQuitar = "Quitárselo",
                alComprar = { (estado.prueba as? PruebaTienda.Pieza)?.let { acciones.comprar(it.itemId) } },
                alAhoraNo = acciones::quitarPrueba,
                alQuitar = acciones::quitarPrueba,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        CapaMoneda(vuelo)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompraHabiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val tmp = TemporaryFolder()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val vuelo = VueloMoneda()
    private val armario = EscenaArmarioEstado()

    private var mostrada by mutableStateOf(true)
    private var enHabi by mutableStateOf(true)

    // El banco de eventos: un flujo compartido como el del ViewModel.
    private val eventos = MutableSharedFlow<EventoTienda>(replay = 0, extraBufferCapacity = 16)
    private var estadoFalso by mutableStateOf(estadoTienda(saldo = 140))

    private lateinit var db: BitoDatabase
    private lateinit var vm: TiendaViewModel
    private val zona = ZoneId.systemDefault()

    @Before
    fun abrir() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BitoDatabase::class.java).build()
    }

    @After
    fun cerrar() {
        db.close()
    }

    private fun montarFalso(reducido: Boolean = false) {
        rule.setContent {
            ConSistema(sonido, tacto, reducido) {
                if (mostrada) MiniTienda(estadoFalso, AccionesGrabadas(), eventos, vuelo, armario, sonido, tacto, escenaVisible = enHabi)
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
    }

    private fun comprar(
        id: String,
        look: EquippedSet,
        saldoTras: Int,
    ) {
        rule.runOnIdle {
            estadoFalso = estadoFalso.copy(look = look, puestos = look, saldo = saldoTras, prueba = null, barra = null)
            Snapshot.sendApplyNotifications()
            check(eventos.tryEmit(EventoTienda.Comprado(id)))
        }
        rule.mainClock.advanceTimeByFrame()
    }

    private fun avanzar(ms: Long) {
        var t = 0L
        while (t < ms) {
            rule.mainClock.advanceTimeBy(16)
            t += 16
        }
    }

    @Test
    fun `buying plays GASTAR and CONFIRM, flies the coin 520 ms, rolls the balance, then jumps and plays HABI`() {
        montarFalso()
        comprar("head-copa", EquippedSet(head = "head-copa"), saldoTras = 20)
        avanzar(48)
        // a la vez: el sonido y la vibración al soltarse la moneda
        assertTrue(Snd.GASTAR in sonido.snds())
        assertEquals(listOf(Haptica.CONFIRM), tacto.hapticas.filter { it == Haptica.CONFIRM })
        assertEquals("la moneda está en el aire", 1, vuelo.activas.size)
        assertFalse("Habi no ha saltado aún", Snd.HABI in sonido.snds())
        assertEquals(0, armario.cuenta)

        avanzar(400)
        assertEquals("a los 450 ms sigue volando", 1, vuelo.activas.size)
        assertEquals(0, armario.cuenta)

        avanzar(130)
        assertEquals("a los 580 ms ha llegado", 0, vuelo.activas.size)
        assertEquals(1, armario.cuenta)
        assertEquals(SaltoCompra.enLienzo(0.05f), requireNotNull(armario.amplitud), 0.0001f)
        assertTrue(Snd.HABI in sonido.snds())
        assertTrue("GASTAR suena antes que HABI", sonido.snds().indexOf(Snd.GASTAR) < sonido.snds().indexOf(Snd.HABI))

        // el 12 % del salto ya va en su agachada: el panel no aplasta aparte por el canal del armario
        val escalas = mutableListOf<Float>()
        repeat(40) {
            avanzar(16)
            escalas += armario.escalaY
        }
        assertEquals(1f, escalas.min(), 0f)
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("saldo").assertContentDescriptionContains("20 bitos")
    }

    @Test
    fun `switching tab mid-celebration leaves Habi unsquashed`() {
        montarFalso()
        // el look no cambia: así solo se ve lo que hace la compra, sin la caída de la pieza
        comprar("head-copa", EquippedSet(), saldoTras = 20)
        // hasta que Habi se esté aplastando (si algo la aplastara) o, si no, hasta pasado el vuelo
        var t = 0
        while (armario.escalaY >= 1f && t < 900) {
            avanzar(16)
            t += 16
        }
        rule.runOnIdle { enHabi = false }
        rule.mainClock.advanceTimeByFrame()
        avanzar(400)
        assertEquals(1f, armario.escalaY, 0.001f)
        assertEquals(0, vuelo.activas.size)
    }

    @Test
    fun `the bar sinks when the coin leaves and a tap on it does nothing`() {
        montarTienda()
        sembrarSaldo(140)
        esperarCargado()
        rule.runOnUiThread { vm.tocarPieza("head-gorro-lana") }
        esperar { rule.onAllBarra("barra-comprar") }
        rule.onNodeWithTag("barra-comprar").performClick()
        esperar { sonido.snds().contains(Snd.GASTAR) }
        assertEquals("la barra se hunde: ya no hay barra en el estado", null, vm.uiState.value.barra)
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(48)
        assertTrue("sigue pintada mientras se hunde", rule.onAllBarra("barra-comprar"))
        rule.onNodeWithTag("barra-comprar").performClick()
        rule.mainClock.autoAdvance = true
        esperar { !rule.onAllBarra("barra") }
        esperar { sonido.snds().contains(Snd.HABI) }
        val compras = runBlocking { db.pointsLedgerDao().all() }.filter { it.reason == PointsReason.BUY_ITEM }
        assertEquals(1, compras.size)
        assertEquals(1, sonido.snds().count { it == Snd.GASTAR })
    }

    @Test
    fun `Not now plays HUNDIR once`() {
        montarTienda()
        sembrarSaldo(140)
        esperarCargado()
        rule.runOnUiThread { vm.tocarPieza("head-gorro-lana") }
        esperar { rule.onAllBarra("barra-ahora-no") }
        pasar(1500)
        sonido.sonados.clear()
        tacto.hapticas.clear()
        rule.onNodeWithTag("barra-ahora-no").performClick()
        esperar { vm.uiState.value.prueba == null }
        pasar(800)
        assertEquals(1, sonido.snds().count { it == Snd.HUNDIR })
        assertEquals(1, tacto.hapticas.count { it == Haptica.LOW })
    }

    @Test
    fun `taking off a worn piece plays HUNDIR once`() {
        montarTienda()
        sembrarSaldo(140)
        esperarCargado()
        rule.runOnUiThread { vm.tocarPieza("head-gorro-lana") }
        esperar { rule.onAllBarra("barra-comprar") }
        rule.onNodeWithTag("barra-comprar").performClick()
        esperar { vm.uiState.value.puestos.head == "head-gorro-lana" }
        esperar { sonido.snds().contains(Snd.HABI) }
        pasar(2000)
        sonido.sonados.clear()
        tacto.hapticas.clear()
        rule.runOnUiThread { vm.tocarPieza("head-gorro-lana") }
        esperar { vm.uiState.value.puestos.head == null }
        pasar(800)
        assertEquals(1, sonido.snds().count { it == Snd.HUNDIR })
        assertEquals(1, tacto.hapticas.count { it == Haptica.LOW })
    }

    @Test
    fun `the jump of a wool hat is its table amplitude scaled to the canvas`() {
        montarFalso()
        comprar("head-gorro-lana", EquippedSet(head = "head-gorro-lana"), saldoTras = 80)
        avanzar(620)
        assertEquals(SaltoCompra.enLienzo(0.15f), requireNotNull(armario.amplitud), 0.0001f)
    }

    @Test
    fun `reduced flies a short fade, does not jump and still plays the sounds`() {
        montarFalso(reducido = true)
        comprar("head-gorro-lana", EquippedSet(head = "head-gorro-lana"), saldoTras = 80)
        avanzar(48)
        assertTrue(Snd.GASTAR in sonido.snds())
        avanzar(300)
        assertEquals(0, vuelo.activas.size)
        assertEquals(0, armario.cuenta)
        assertTrue(Snd.HABI in sonido.snds())
    }

    @Test
    fun `a garden purchase does not make Habi jump`() {
        montarFalso()
        rule.runOnIdle { check(eventos.tryEmit(EventoTienda.Comprado("garden-banco"))) }
        avanzar(700)
        assertEquals(0, armario.cuenta)
        assertEquals(0, vuelo.activas.size)
    }

    @Test
    fun `leaving the store mid-flight leaves no coin and the purchase stays final`() {
        montarReal()
        sembrarSaldo(140)
        esperarCargado()
        rule.runOnUiThread { vm.tocarPieza("head-gorro-lana") }
        esperar { rule.onAllBarra("barra-comprar") }
        rule.onNodeWithTag("barra-comprar").performClick()
        // la moneda en el aire: salimos de la tienda
        esperar { vuelo.activas.isNotEmpty() }
        rule.runOnUiThread { mostrada = false }
        rule.waitForIdle()
        assertEquals("no queda ninguna moneda a medias", 0, vuelo.activas.size)
        assertFalse("Habi no salta tras salir", Snd.HABI in sonido.snds())
        val compras = runBlocking { db.pointsLedgerDao().all() }.filter { it.reason == PointsReason.BUY_ITEM }
        assertEquals("la compra escrita queda firme", 1, compras.size)
        assertTrue(runBlocking { db.customizationItemDao().byId("head-gorro-lana") }?.equipped == true)
    }

    @Test
    fun `a double tap pays once`() {
        montarReal()
        sembrarSaldo(140)
        esperarCargado()
        rule.runOnUiThread { vm.tocarPieza("head-gorro-lana") }
        esperar { rule.onAllBarra("barra-comprar") }
        rule.runOnUiThread {
            vm.comprar("head-gorro-lana")
            vm.comprar("head-gorro-lana")
        }
        esperar { "head-gorro-lana" in vm.uiState.value.poseidos }
        esperar { sonido.snds().contains(Snd.HABI) }
        val compras = runBlocking { db.pointsLedgerDao().all() }.filter { it.reason == PointsReason.BUY_ITEM }
        assertEquals(1, compras.size)
        assertEquals(1, sonido.snds().count { it == Snd.GASTAR })
        assertEquals(1, sonido.snds().count { it == Snd.HABI })
    }

    @Test
    fun `the tile reads Worn right after buying and Owned after taking it off`() {
        montarReal()
        sembrarSaldo(140)
        esperarCargado()
        val gorro = requireNotNull(HabiCatalog.byId("head-gorro-lana"))
        assertEquals(EtiquetaHornacina.PRECIO, datosDe(gorro, vm.uiState.value).etiqueta)
        rule.runOnUiThread { vm.tocarPieza("head-gorro-lana") }
        esperar { rule.onAllBarra("barra-comprar") }
        rule.onNodeWithTag("barra-comprar").performClick()
        esperar { sonido.snds().contains(Snd.GASTAR) }
        esperar { vm.uiState.value.puestos.head == "head-gorro-lana" }
        assertEquals(EtiquetaHornacina.PUESTO, datosDe(gorro, vm.uiState.value).etiqueta)
        rule.runOnUiThread { vm.tocarPieza("head-gorro-lana") }
        esperar { vm.uiState.value.puestos.head == null }
        assertEquals(EtiquetaHornacina.TUYO, datosDe(gorro, vm.uiState.value).etiqueta)
    }

    @Test
    fun `without balance there is no buy button`() {
        montarReal()
        sembrarSaldo(50)
        esperarCargado()
        rule.runOnUiThread { vm.tocarPieza("head-copa") }
        esperar { rule.onAllBarra("barra-quitar") }
        rule.onNodeWithTag("barra-comprar").assertDoesNotExist()
        rule.onNodeWithTag("barra-quitar").assertIsDisplayed()
        rule.onNodeWithTag("barra-quitar").performClick()
        esperar { vm.uiState.value.prueba == null }
        assertEquals(0, runBlocking { db.pointsLedgerDao().all() }.count { it.reason == PointsReason.BUY_ITEM })
    }

    /** Espera a algo que llega de Room y de otros hilos: la cola del hilo principal se vacía en cada vuelta. */
    private fun esperar(condicion: () -> Boolean) =
        rule.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condicion()
        }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllBarra(tag: String): Boolean =
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /** Deja correr [ms] del reloj de Compose y vuelve al reloj automático. */
    private fun pasar(ms: Long) {
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(ms)
        rule.mainClock.autoAdvance = true
    }

    /** La tienda entera (barra de la pantalla y panel real de Habi) con el ViewModel real. */
    private fun montarTienda() {
        crearVm()
        rule.setContent {
            ConSistemaLigero(sonido, tacto) {
                val estado by vm.uiState.collectAsState()
                if (mostrada) TiendaContenido(estado, vm, vm.eventos, alSalir = {})
            }
        }
    }

    private fun montarReal(): RewardsRepository {
        val rewards = crearVm()
        rule.setContent {
            ConSistema(sonido, tacto) {
                val estado by vm.uiState.collectAsState()
                if (mostrada) MiniTienda(estado, vm, vm.eventos, vuelo, armario, sonido, tacto, barraVista = true)
            }
        }
        return rewards
    }

    private fun crearVm(): RewardsRepository {
        val rewards = RewardsRepository(db)
        val store: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + Job())) {
                File(tmp.root, "compra-habi.preferences_pb")
            }
        val contexto = ApplicationProvider.getApplicationContext<Context>()
        vm =
            TiendaViewModel(
                domainState = DomainStateRepository(db),
                rewards = rewards,
                settings = SettingsRepository(store),
                reloj = RelojTienda(),
                recursos = { contexto.resources },
                saved = SavedStateHandle(),
            )
        return rewards
    }

    private fun sembrarSaldo(bitos: Int) {
        val hoy = LogicalDays.logicalDayOf(System.currentTimeMillis(), 0, zona)
        runBlocking {
            db.pointsLedgerDao().insert(
                pointsLedgerEntity(id = "semilla", delta = bitos, reason = PointsReason.HABIT_DONE, refId = null, logicalDay = hoy),
            )
        }
    }

    private fun esperarCargado() = esperar { vm.uiState.value.cargado && vm.uiState.value.saldo > 0 }
}
