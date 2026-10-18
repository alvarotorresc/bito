package com.alvarotc.bito.ui.tienda.jardin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import com.alvarotc.bito.ui.tienda.ContextoPanel
import com.alvarotc.bito.ui.tienda.PanelTienda
import com.alvarotc.bito.ui.tienda.PestanaTienda
import com.alvarotc.bito.ui.tienda.PruebaTienda
import com.alvarotc.bito.ui.tienda.TiendaContenido
import com.alvarotc.bito.ui.tienda.TiendaUiState
import com.alvarotc.bito.ui.tienda.estadoTienda
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** El catálogo de la pestaña Jardín (tienda-jardin.css `.tj-sec`, `.tj-grid`, `.tj-item`; tienda-jardin.js `itemHTML`, `tocarItem`). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CatalogoJardinTest {
    @get:Rule
    val compose = createComposeRule()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val acciones = AccionesJardin()
    private val panel = PanelEspia()
    private var estado by mutableStateOf(base())

    private fun base(
        prueba: PruebaTienda? = null,
        jardin: GardenState = GardenState.INITIAL,
        saldo: Int = 140,
    ): TiendaUiState =
        estadoTienda(pestana = PestanaTienda.JARDIN, prueba = prueba, saldo = saldo).copy(
            jardin = jardin,
            objetosJardin = jardin.objects.map { "garden-$it" }.toSet(),
            poseidos = jardin.objects.map { "garden-$it" }.toSet(),
            puestos = EquippedSet(),
        )

    // Las demás pestañas no pintan nada: así se ve qué pasa con la escena del Jardín al irse y volver.
    private val vacio =
        object : PanelTienda {
            @Composable
            override fun Escena(
                ctx: ContextoPanel,
                modifier: Modifier,
            ) = Unit

            @Composable
            override fun Catalogo(
                ctx: ContextoPanel,
                modifier: Modifier,
            ) = Unit
        }

    private fun montar() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ConSistema(sonido, tacto) {
                TiendaContenido(estado, acciones, emptyFlow(), {}, paneles = { if (it == PestanaTienda.JARDIN) panel else vacio })
            }
        }
        compose.mainClock.advanceTimeBy(600)
    }

    private fun poner(nuevo: TiendaUiState) {
        compose.runOnUiThread {
            estado = nuevo
            Snapshot.sendApplyNotifications()
        }
        compose.mainClock.advanceTimeBy(50)
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    /** Lo que dice la hornacina (nombre y línea de debajo): la descripción de su nodo, que es lo que lee el lector de pantalla. */
    private fun dice(tag: String): String =
        compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString(" ")

    @Test
    fun `two sections with their hints and the eight tiles`() {
        montar()
        compose.onNodeWithText("For your garden").assertExists()
        compose.onNodeWithText("Tap one to place it above, in its spot.").assertExists()
        compose.onNodeWithText("From badges").assertExists()
        compose.onNodeWithText("Not for sale: they arrive with your consistency. Tap one to see where it will go.").assertExists()
        listOf("banco", "regadera", "farolillo", "caminito", "estanque", "setas", "cerezo-grande", "arbol-dorado").forEach {
            compose.onNodeWithTag("tj-$it").assertExists()
        }
        assertTrue(dice("item-garden-banco"), dice("item-garden-banco").let { "Bench" in it && "80" in it })
        assertTrue(dice("item-garden-regadera"), dice("item-garden-regadera").let { "Watering can" in it && "25" in it })
    }

    @Test
    fun `the sections use the mock margins and three columns`() {
        montar()
        // Cabecera a 20 dp del borde; la rejilla a 16 dp con tres columnas.
        assertEquals(20f, bounds("tj-cabecera-venta").left.value, 0.5f)
        val banco = bounds("tj-banco")
        val regadera = bounds("tj-regadera")
        val farolillo = bounds("tj-farolillo")
        val caminito = bounds("tj-caminito")
        val estanque = bounds("tj-estanque")
        assertEquals(16f, banco.left.value, 0.5f)
        assertEquals(10f, regadera.left.value - banco.right.value, 0.5f)
        // Tres columnas del mismo ancho: (412 - 32 - 20) / 3 = 120 dp.
        assertEquals(120f, farolillo.width.value, 0.5f)
        assertEquals(farolillo.width.value, caminito.width.value, 0.5f)
        assertEquals(10f, caminito.left.value - farolillo.right.value, 0.5f)
        assertEquals(10f, estanque.left.value - caminito.right.value, 0.5f)
        // Dos filas de logros, a 16 dp una de otra.
        val setas = bounds("tj-setas")
        assertEquals(16f, setas.top.value - farolillo.bottom.value, 0.5f)
    }

    @Test
    fun `bought shows In your garden and never Owned`() {
        estado = base(jardin = GardenState.INITIAL.copy(objects = setOf("banco")))
        montar()
        assertTrue(dice("item-garden-banco"), "In your garden" in dice("item-garden-banco"))
        assertTrue("Owned" !in dice("item-garden-banco"))
        // La regadera sigue con su precio.
        assertTrue("25" in dice("item-garden-regadera"))
    }

    @Test
    fun `badge specials show their badge name`() {
        montar()
        val logros = listOf("First flame", "Full week", "Perfect month", "Resurrection", "Perfect hundred", "Around the sun")
        val nombres = listOf("Lantern", "Stone path", "Pond", "Mushrooms", "Big cherry tree", "Golden tree")
        val claves = listOf("farolillo", "caminito", "estanque", "setas", "cerezo-grande", "arbol-dorado")
        claves.indices.forEach { i ->
            val d = dice("item-${claves[i]}")
            assertTrue(d, nombres[i] in d && logros[i] in d)
        }
    }

    @Test
    fun `an earned special reads In your garden and the rest keep their badge`() {
        estado = base(jardin = GardenState.INITIAL.copy(specials = setOf(GardenSpecial.LANTERN)))
        montar()
        assertTrue(dice("item-farolillo"), "In your garden" in dice("item-farolillo"))
        assertTrue("First flame" !in dice("item-farolillo"))
        assertTrue("Full week" in dice("item-caminito"))
    }

    @Test
    fun `the trial reads Trying`() {
        estado = base(prueba = PruebaTienda.Objeto("garden-banco"))
        montar()
        assertTrue(dice("item-garden-banco"), "Trying" in dice("item-garden-banco"))
    }

    @Test
    fun `no root rail in this tab`() {
        montar()
        compose.onNodeWithTag("raiz").assertDoesNotExist()
    }

    @Test
    fun `tapping an object tries it and a special tries its badge`() {
        montar()
        compose.onNodeWithTag("tj-banco").performClick()
        compose.onNodeWithTag("tj-farolillo").performClick()
        assertEquals(listOf("garden-banco"), acciones.piezas)
        assertEquals(listOf("streak-7"), acciones.especiales)
        assertTrue(Haptica.TICK in tacto.hapticas)
    }

    @Test
    fun `tapping the one you are trying takes the trial off`() {
        estado = base(prueba = PruebaTienda.Objeto("garden-banco"))
        montar()
        compose.onNodeWithTag("tj-banco").performClick()
        assertEquals(1, acciones.quitadas)
        assertTrue(acciones.piezas.isEmpty())
        assertTrue(Haptica.LOW in tacto.hapticas)
    }

    @Test
    fun `tapping an owned object rocks it without a bar and without trying anything`() {
        estado = base(jardin = GardenState.INITIAL.copy(objects = setOf("banco")))
        montar()
        val ticksAntes = tacto.hapticas.count { it == Haptica.TICK }
        compose.onNodeWithTag("tj-banco").performClick()
        compose.mainClock.advanceTimeBy(2000)
        assertTrue(acciones.piezas.isEmpty())
        assertTrue(acciones.especiales.isEmpty())
        compose.onNodeWithTag("barra").assertDoesNotExist()
        assertEquals(1, sonido.snds().count { it == Snd.PLANTA })
        assertEquals("un solo TICK, el del toque", 1, tacto.hapticas.count { it == Haptica.TICK } - ticksAntes)
        assertEquals(1.12f, panel.escenas.last().jardin.camara.actual.escala, 0.001f)
    }

    @Test
    fun `tapping an earned special rocks it and never tries it`() {
        estado = base(jardin = GardenState.INITIAL.copy(specials = setOf(GardenSpecial.LANTERN)))
        montar()
        compose.onNodeWithTag("tj-farolillo").performClick()
        compose.mainClock.advanceTimeBy(1500)
        assertTrue(acciones.especiales.isEmpty())
        assertEquals(1, sonido.snds().count { it == Snd.PLANTA })
    }

    @Test
    fun `tapping an owned object while trying another drops the trial and still rocks it`() {
        val propios = GardenState.INITIAL.copy(objects = setOf("banco"))
        estado = base(prueba = PruebaTienda.Objeto("garden-regadera"), jardin = propios)
        // La prueba se va un rato después del toque, como cuando el estado llega por un flujo.
        acciones.alQuitar = {}
        montar()
        compose.onNodeWithTag("tj-banco").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertEquals(1, acciones.quitadas)
        poner(base(prueba = null, jardin = propios))
        compose.mainClock.advanceTimeBy(2000)
        assertEquals("se mece aunque la prueba tardara en irse", 1, sonido.snds().count { it == Snd.PLANTA })
    }

    @Test
    fun `the scene and the catalog share their garden across recompositions`() {
        montar()
        poner(base(saldo = 100))
        poner(base(saldo = 90))
        val ctxs = panel.escenas + panel.catalogos
        assertTrue("el contexto se rehace al recomponer", panel.catalogos.size > 1)
        ctxs.forEach { assertSame(ctxs.first().jardin, it.jardin) }
        assertNotEquals(null, ctxs.first().jardin)
    }

    @Test
    fun `English fits in the tiles and the hints`() {
        montar()
        listOf("item-cerezo-grande", "item-arbol-dorado", "item-estanque").forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        val tile = bounds("tj-cerezo-grande")
        assertTrue("el nombre cabe en 120 dp", tile.width <= 121.dp)
        // Cada texto medible del catálogo (cabeceras y pistas; los nombres de las hornacinas no exponen su maquetación) se ve entero: sin recorte ni puntos suspensivos.
        val conTexto = SemanticsMatcher("tiene texto medido") { it.config.getOrNull(SemanticsActions.GetTextLayoutResult) != null }
        val nodos =
            compose.onAllNodes(conTexto, useUnmergedTree = true).fetchSemanticsNodes().filter { n ->
                generateSequence(n) { it.parent }.any { it.config.getOrNull(SemanticsProperties.TestTag) == "tj-catalogo" }
            }
        assertTrue("hay textos que medir: ${nodos.size}", nodos.size >= 12)
        nodos.forEach { nodo ->
            val resultados = mutableListOf<TextLayoutResult>()
            nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
            resultados.forEach { r ->
                val texto = r.layoutInput.text.text
                // `hasVisualOverflow` da falso positivo con un texto que se ajusta a su ancho: se miran las líneas una a una.
                (0 until r.lineCount).forEach { l ->
                    assertFalse("puntos suspensivos: $texto", r.isLineEllipsized(l))
                    assertTrue("se sale por la derecha: $texto", r.getLineRight(l) <= r.size.width + 1f)
                }
                assertTrue("se corta por abajo: $texto", r.size.height >= r.multiParagraph.height.toInt())
            }
        }
    }

    @Test
    fun `coming back to the garden tab shows the whole garden and rocks nothing`() {
        val propios = GardenState.INITIAL.copy(objects = setOf("banco"))
        estado = base(jardin = propios)
        montar()
        val jardin = panel.escenas.last().jardin
        val entera = jardin.camara.actual
        compose.onNodeWithTag("tj-banco").performClick()
        compose.mainClock.advanceTimeBy(100)
        assertTrue("la cámara va al banco", jardin.camara.actual.escala > entera.escala)
        poner(base(jardin = propios).copy(pestana = PestanaTienda.HABI))
        compose.mainClock.advanceTimeBy(600)
        val mecidosAlIrse = sonido.snds().count { it == Snd.PLANTA }
        poner(base(jardin = propios))
        compose.mainClock.advanceTimeBy(2000)
        val ahora = panel.escenas.last().jardin
        assertEquals("el jardín entero, no lo último que se tocó", entera.escala, ahora.camara.actual.escala, 0.001f)
        assertEquals("al volver no se mece nada", mecidosAlIrse, sonido.snds().count { it == Snd.PLANTA })
        assertEquals(null, ahora.caida.pose("banco"))
    }

    @Test
    fun `leaving the tab in the middle of the rock leaves nothing tilted and comes back to the whole garden`() {
        val propios = GardenState.INITIAL.copy(objects = setOf("banco"))
        estado = base(jardin = propios)
        montar()
        val jardin = panel.escenas.last().jardin
        val entera = jardin.camara.actual
        compose.onNodeWithTag("tj-banco").performClick()
        compose.mainClock.advanceTimeBy(400)
        assertTrue("se está meciendo", abs(jardin.caida.pose("banco")?.giro ?: 0f) > 0.5f)
        poner(base(jardin = propios).copy(pestana = PestanaTienda.HABI))
        compose.mainClock.advanceTimeBy(600)
        poner(base(jardin = propios))
        compose.mainClock.advanceTimeBy(2000)
        val ahora = panel.escenas.last().jardin
        assertEquals(null, ahora.caida.pose("banco"))
        assertEquals(entera.escala, ahora.camara.actual.escala, 0.001f)
        assertEquals("solo el meceo de antes", 1, sonido.snds().count { it == Snd.PLANTA })
    }

    @Test
    fun `trying again the item that was on trial when an owned one was tapped moves the camera to it`() {
        val propios = GardenState.INITIAL.copy(objects = setOf("banco"))
        estado = base(prueba = PruebaTienda.Objeto("garden-regadera"), jardin = propios)
        acciones.alQuitar = { estado = base(prueba = null, jardin = propios) }
        montar()
        compose.onNodeWithTag("tj-banco").performClick()
        compose.mainClock.advanceTimeBy(800)
        assertEquals("la cámara está en el banco", 1.12f, panel.escenas.last().jardin.camara.actual.escala, 0.001f)
        // La regadera no cabe a 1,12: su encuadre es el de escala 1.
        poner(base(prueba = PruebaTienda.Objeto("garden-regadera"), jardin = propios))
        compose.mainClock.advanceTimeBy(800)
        assertEquals("la cámara va a la regadera", 1f, panel.escenas.last().jardin.camara.actual.escala, 0.001f)
    }
}
