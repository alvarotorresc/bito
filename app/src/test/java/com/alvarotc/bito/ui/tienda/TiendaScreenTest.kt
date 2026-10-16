package com.alvarotc.bito.ui.tienda

import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.ui.premios.HojaSaldo
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TiendaScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val acciones = AccionesGrabadas()
    private var estado by mutableStateOf(estadoTienda())
    private var salidas = 0
    private var mostrada by mutableStateOf(true)

    private val habi = PanelFalso("HABI")
    private val jardin = PanelFalso("JARDIN")
    private val congeladores = PanelFalso("CONGELADORES")

    private fun panel(p: PestanaTienda) =
        when (p) {
            PestanaTienda.HABI -> habi
            PestanaTienda.JARDIN -> jardin
            PestanaTienda.CONGELADORES -> congeladores
        }

    private fun montar(
        reducido: Boolean = false,
        paneles: (PestanaTienda) -> PanelTienda = ::panel,
    ) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistema(sonido, tacto, reducido) {
                if (mostrada) {
                    TiendaContenido(estado, acciones, emptyFlow(), { salidas++ }, paneles = paneles)
                }
            }
        }
        rule.mainClock.advanceTimeBy(600)
    }

    /** Cambia el estado desde el hilo de UI y lo aplica ya: con el reloj parado nadie más lo haría. */
    private fun poner(nuevo: TiendaUiState) {
        rule.runOnUiThread {
            estado = nuevo
            Snapshot.sendApplyNotifications()
        }
    }

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun atras() = rule.runOnIdle { rule.activity.onBackPressedDispatcher.onBackPressed() }

    private val gorro = PruebaTienda.Pieza("head-gorro-lana")
    private val barraComprar = BarraPrueba.Comprar("Gorro de lana", 60, 80, null, false)

    @Test
    fun `the vertical order is 300 + 6, 52, 46 and the catalog from 404`() {
        montar()
        val escena = bounds("tienda-escena")
        assertEquals(0f, escena.top.value, 0.5f)
        assertEquals(306f, escena.height.value, 0.5f)
        assertEquals(306f, bounds("tluz").top.value, 0.5f)
        assertEquals(52f, bounds("tluz").height.value, 0.5f)
        assertEquals(358f, bounds("ttab-habi").top.value, 0.5f)
        assertEquals(46f, bounds("ttab-habi").height.value, 0.5f)
        assertEquals(404f, bounds("tienda-catalogo").top.value, 0.5f)
        assertEquals(915f, bounds("tienda-catalogo").bottom.value, 0.5f)
    }

    @Test
    @Config(sdk = [35], qualifiers = "es-w412dp-h500dp")
    fun `on a short screen the scene drops to 260 before the catalog goes under 120 dp`() {
        estado = estadoTienda(prueba = gorro, barra = barraComprar)
        montar()
        assertEquals(266f, bounds("tienda-escena").height.value, 0.5f)
        assertEquals(266f, bounds("tluz").top.value, 0.5f)
        assertEquals(500f - 364f, bounds("tienda-catalogo").height.value, 0.5f)
        assertTrue("el catálogo no baja de 120 dp", bounds("tienda-catalogo").height >= 120.dp)
    }

    @Test
    @Config(sdk = [35], qualifiers = "es-w412dp-h500dp")
    fun `without the bar a short screen keeps the full scene`() {
        montar()
        assertEquals(306f, bounds("tienda-escena").height.value, 0.5f)
    }

    @Test
    fun `it opens on Habi with no trial`() {
        montar()
        rule.onNodeWithTag("ttab-habi").assertIsSelected()
        rule.onNodeWithTag("escena-HABI").assertExists()
        rule.onNodeWithTag("barra").assertDoesNotExist()
        assertEquals(0, salidas)
    }

    @Test
    fun `it shows only paper until the first real state`() {
        estado = estadoTienda(cargado = false)
        montar()
        rule.onNodeWithTag("tienda-cargando").assertExists()
        rule.onNodeWithTag("ttab-habi").assertDoesNotExist()
    }

    @Test
    fun `back with a trial removes the trial and the next back leaves`() {
        estado = estadoTienda(prueba = gorro, barra = barraComprar)
        montar()
        atras()
        assertEquals(1, acciones.quitadas)
        assertEquals(0, salidas)
        poner(estadoTienda())
        rule.mainClock.advanceTimeBy(300)
        atras()
        assertEquals(1, acciones.quitadas)
        assertEquals(1, salidas)
    }

    @Test
    fun `back with the undo notice visible leaves`() {
        estado = estadoTienda(prueba = gorro, barra = barraComprar)
        val conAviso = PanelFalso("HABI") { it.avisos.mostrar("Congelador comprado", "Deshacer") }
        montar(paneles = { conAviso })
        rule.mainClock.advanceTimeBy(300)
        rule.onNodeWithTag("aviso").assertExists()
        atras()
        assertEquals(0, acciones.quitadas)
        assertEquals(1, salidas)
    }

    @Test
    fun `tapping the balance opens the sheet without the store button`() {
        montar()
        rule.onNodeWithTag("saldo").performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithTag("hoja-saldo").assertExists()
        rule.onNodeWithTag("saldo-tienda").assertDoesNotExist()
        rule.onNodeWithTag("saldo-cerrar").assertExists()
    }

    @Test
    fun `the sheet from Today still has the store button`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistema { HojaSaldo(true, 140, emptyList(), EconomyConfig(), alIrTienda = {}, alCerrar = {}) }
        }
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithTag("saldo-tienda").assertExists()
    }

    @Test
    fun `changing tab asks the screen to change tab`() {
        montar()
        rule.onNodeWithTag("ttab-jardin").performClick()
        assertEquals(listOf(PestanaTienda.JARDIN), acciones.pestanas)
    }

    @Test
    fun `a restored tab and trial come back and the light does not`() {
        estado = estadoTienda(pestana = PestanaTienda.JARDIN, luz = LuzTienda.DIA, prueba = PruebaTienda.Objeto("garden-banco"))
        montar()
        rule.onNodeWithTag("ttab-jardin").assertIsSelected()
        rule.onNodeWithTag("escena-JARDIN").assertExists()
        rule.onNodeWithTag("catalogo-JARDIN").assertExists()
        // la luz de entrada es la del estado (la de ahora), no una guardada
        assertEquals(LuzTienda.DIA.parada, jardin.escenas.first().luz())
    }

    @Test
    fun `the screen locks portrait and releases it`() {
        montar()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, rule.activity.requestedOrientation)
        rule.runOnUiThread {
            mostrada = false
            Snapshot.sendApplyNotifications()
        }
        rule.mainClock.advanceTimeBy(100)
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, rule.activity.requestedOrientation)
    }

    @Test
    fun `the content enters from 24 dp`() {
        montar()
        poner(estadoTienda(pestana = PestanaTienda.JARDIN))
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        val ida = bounds("catalogo-JARDIN").left.value
        assertTrue("entra desplazado: $ida", ida > 0f && ida <= 24f)
        rule.mainClock.advanceTimeBy(400)
        assertEquals(0f, bounds("catalogo-JARDIN").left.value, 0.5f)
        rule.onNodeWithTag("catalogo-HABI").assertDoesNotExist()
    }

    @Test
    fun `reduced motion fades the content in 150 ms without moving it`() {
        montar(reducido = true)
        poner(estadoTienda(pestana = PestanaTienda.JARDIN))
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertEquals(0f, bounds("catalogo-JARDIN").left.value, 0.5f)
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithTag("catalogo-HABI").assertDoesNotExist()
    }

    @Test
    fun `the bar buy button calls buy and the quit buttons remove the trial`() {
        estado = estadoTienda(prueba = gorro, barra = barraComprar)
        montar()
        rule.onNodeWithTag("barra-comprar").performClick()
        assertEquals(listOf("head-gorro-lana"), acciones.compras)
        rule.onNodeWithTag("barra-ahora-no").performClick()
        assertEquals(1, acciones.quitadas)
        poner(estadoTienda(prueba = PruebaTienda.Pieza("head-corona"), barra = BarraPrueba.SinSaldo("Corona", 120, 50, false)))
        rule.mainClock.advanceTimeBy(600)
        rule.onNodeWithTag("barra-quitar").performClick()
        assertEquals(2, acciones.quitadas)
        assertEquals(1, acciones.compras.size)
    }

    @Test
    fun `entering plays the sprout and a tick, leaving the sink and a low tick`() {
        montar()
        assertEquals(listOf(Snd.BROTE), sonido.snds())
        assertEquals(listOf(Haptica.TICK), tacto.hapticas)
        rule.onNodeWithTag("tienda-atras").performClick()
        assertEquals(listOf(Snd.BROTE, Snd.HUNDIR), sonido.snds())
        assertEquals(listOf(Haptica.TICK, Haptica.LOW), tacto.hapticas)
        assertEquals(1, salidas)
    }

    @Test
    fun `the arrow with a trial removes it with the sink sound and does not leave`() {
        estado = estadoTienda(prueba = gorro, barra = barraComprar)
        montar()
        rule.onNodeWithTag("tienda-atras").performClick()
        assertEquals(1, acciones.quitadas)
        assertEquals(0, salidas)
        assertTrue(Snd.HUNDIR in sonido.snds())
    }
}
