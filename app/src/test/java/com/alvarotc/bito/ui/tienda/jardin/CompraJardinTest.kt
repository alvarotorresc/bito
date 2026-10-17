package com.alvarotc.bito.ui.tienda.jardin

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.tienda.BarraPrueba
import com.alvarotc.bito.ui.tienda.BarrasTienda
import com.alvarotc.bito.ui.tienda.EventoTienda
import com.alvarotc.bito.ui.tienda.PestanaTienda
import com.alvarotc.bito.ui.tienda.PruebaTienda
import com.alvarotc.bito.ui.tienda.TiendaContenido
import com.alvarotc.bito.ui.tienda.TiendaUiState
import com.alvarotc.bito.ui.tienda.estadoTienda
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** Comprar en la pestaña Jardín, de la barra a la moneda y al meceo (tienda-jardin.md «Comprar», `comprar()` del mock). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompraJardinTest {
    @get:Rule
    val compose = createComposeRule()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val acciones = AccionesJardin()
    private val panel = PanelEspia()
    private val eventos = MutableSharedFlow<EventoTienda>(extraBufferCapacity = 16)
    private var estado by mutableStateOf(estadoTienda())
    private var mostrada by mutableStateOf(true)
    private val recursos get() = ApplicationProvider.getApplicationContext<Context>().resources

    private fun jardinCon(
        prueba: PruebaTienda?,
        saldo: Int,
        poseidos: Set<String> = emptySet(),
        barra: BarraPrueba? = BarrasTienda.barraDe(prueba, poseidos, saldo, 0, LocalDate.of(2025, 10, 17), recursos),
    ): TiendaUiState =
        estadoTienda(pestana = PestanaTienda.JARDIN, prueba = prueba, barra = barra, saldo = saldo).copy(
            jardin = GardenState.INITIAL.copy(objects = poseidos.map { it.removePrefix("garden-") }.toSet()),
            poseidos = poseidos,
            objetosJardin = poseidos,
            puestos = EquippedSet(),
        )

    private fun montar() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ConSistema(sonido, tacto) {
                if (mostrada) TiendaContenido(estado, acciones, eventos, {}, paneles = { panel })
            }
        }
        compose.mainClock.advanceTimeBy(800)
    }

    private fun poner(cambio: () -> Unit) =
        compose.runOnUiThread {
            cambio()
            Snapshot.sendApplyNotifications()
        }

    /** Hace de modelo: cobra, coloca el objeto, suelta la prueba y avisa, en ese orden. */
    private fun cobrarAlComprar(
        precio: Int,
        saldo: Int,
    ) {
        acciones.alComprar = { id ->
            estado = jardinCon(null, saldo - precio, setOf(id), barra = null)
            Snapshot.sendApplyNotifications()
            eventos.tryEmit(EventoTienda.Comprado(id))
        }
    }

    @Test
    fun `buy bench - GASTAR and CONFIRM, coin flies to the object, balance rolls, object rocks on arrival`() {
        estado = jardinCon(PruebaTienda.Objeto("garden-banco"), 140)
        cobrarAlComprar(80, 140)
        montar()
        compose.mainClock.advanceTimeBy(400)
        val ticksAntes = tacto.hapticas.count { it == Haptica.TICK }
        compose.onNodeWithTag("barra-comprar").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        assertEquals(listOf("garden-banco"), acciones.compras)
        assertEquals(1, sonido.snds().count { it == Snd.GASTAR })
        assertEquals(1, tacto.hapticas.count { it == Haptica.CONFIRM })
        assertEquals("la moneda sale", 1, panel.escenas.last().vuelo.activas.size)
        assertFalse("aún no se mece", Snd.PLANTA in sonido.snds())

        compose.mainClock.advanceTimeBy(300)
        assertEquals("la moneda sigue en el aire", 1, panel.escenas.last().vuelo.activas.size)
        assertEquals("la cámara sigue en el objeto", 1.12f, panel.escenas.last().jardin.camara.actual.escala, 0.01f)
        assertFalse(Snd.PLANTA in sonido.snds())

        compose.mainClock.advanceTimeBy(300)
        assertEquals("la moneda llegó", 0, panel.escenas.last().vuelo.activas.size)
        assertEquals("se mece al llegar", 1, sonido.snds().count { it == Snd.PLANTA })
        assertEquals(1, tacto.hapticas.count { it == Haptica.TICK } - ticksAntes)
        compose.mainClock.advanceTimeBy(400)
        val saldo =
            compose.onNodeWithTag(
                "saldo",
                useUnmergedTree = true,
            ).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        assertTrue("el saldo ya es 60: $saldo", saldo.any { "60" in it })
        val dice = compose.onNodeWithTag("item-garden-banco").fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        assertTrue(dice.toString(), dice.any { "In your garden" in it })
        compose.onNodeWithTag("barra").assertDoesNotExist()
    }

    @Test
    fun `buying a thing of the closet does nothing in the garden scene`() {
        estado = jardinCon(null, 140, barra = null)
        montar()
        poner { eventos.tryEmit(EventoTienda.Comprado("head-gorro-lana")) }
        compose.mainClock.advanceTimeBy(700)
        assertFalse(Snd.GASTAR in sonido.snds())
        assertEquals(0, panel.escenas.last().vuelo.activas.size)
    }

    @Test
    fun `leaving the store mid-flight leaves no coin and the purchase stays`() {
        estado = jardinCon(PruebaTienda.Objeto("garden-banco"), 140)
        cobrarAlComprar(80, 140)
        montar()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("barra-comprar").performClick()
        compose.mainClock.advanceTimeBy(200)
        val vuelo = panel.escenas.last().vuelo
        assertEquals(1, vuelo.activas.size)
        poner { mostrada = false }
        compose.mainClock.advanceTimeBy(100)
        assertEquals("no queda una moneda a medias", 0, vuelo.activas.size)
        assertEquals("el cobro es firme", 60, estado.saldo)
        assertTrue("banco" in estado.jardin.objects)
        compose.mainClock.advanceTimeBy(1000)
        assertFalse("sin meceo fuera de la tienda", Snd.PLANTA in sonido.snds())
    }

    @Test
    fun `without balance the bench has no buy button but the watering can does with the same balance`() {
        estado = jardinCon(PruebaTienda.Objeto("garden-banco"), 52)
        montar()
        compose.onNodeWithTag("barra").assertExists()
        compose.onNodeWithTag("barra-comprar").assertDoesNotExist()
        compose.onNodeWithTag("barra-quitar").assertExists()
        compose.onNodeWithText(
            "It costs 80 bitos and you have 52: 28 short. Each habit done earns 1, and a perfect day earns 3 more.",
        ).assertExists()
        compose.onNodeWithText("Remove it").assertExists()

        poner { estado = jardinCon(PruebaTienda.Objeto("garden-regadera"), 52) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("barra-comprar").assertExists()
        compose.onNodeWithText("Buy for 25").assertExists()
        compose.onNodeWithText("Not now").assertExists()
    }

    @Test
    fun `a special has only Remove it and Not for sale`() {
        estado = jardinCon(PruebaTienda.Especial("streak-7"), 140)
        montar()
        compose.onNodeWithTag("barra-comprar").assertDoesNotExist()
        compose.onNodeWithTag("barra-ahora-no").assertDoesNotExist()
        compose.onNodeWithTag("barra-quitar").assertExists()
        compose.onNodeWithText("Comes with the First flame badge: a streak of 7 in any habit. Not for sale.").assertExists()
    }

    @Test
    fun `the bar texts match in English`() {
        estado = jardinCon(PruebaTienda.Objeto("garden-banco"), 140)
        montar()
        compose.onNodeWithText("80 bitos. You'd have 60 left. It stays where you see it.").assertExists()
        compose.onNodeWithText("Buy for 80").assertExists()
        compose.onNodeWithText("Not now").assertExists()
    }

    @Test
    @Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
    fun `the bar texts match in Spanish`() {
        estado = jardinCon(PruebaTienda.Objeto("garden-banco"), 140)
        montar()
        compose.onNodeWithText("80 bitos. Te quedarían 60. Se queda donde lo ves.").assertExists()
        compose.onNodeWithText("Comprar por 80").assertExists()
        compose.onNodeWithText("Ahora no").assertExists()
        poner { estado = jardinCon(PruebaTienda.Especial("streak-7"), 140) }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Llega con el logro Primera llama", substring = true).assertExists()
        compose.onNodeWithText("Quitarlo").assertExists()
    }
}
