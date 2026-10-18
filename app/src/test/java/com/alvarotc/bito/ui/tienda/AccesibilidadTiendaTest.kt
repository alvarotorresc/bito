package com.alvarotc.bito.ui.tienda

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.componentes.AvisoEstado
import com.alvarotc.bito.ui.sistema.componentes.AvisoHost
import com.alvarotc.bito.ui.tienda.habi.ConSistemaLigero
import com.alvarotc.bito.ui.tienda.habi.RaizRanuras
import com.alvarotc.bito.ui.tienda.habi.RanuraHabi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * H2 (letra al 200 %) y H11 (TalkBack) de la spec §10: lo que la tienda dice a los lectores de pantalla y que con la
 * letra al doble nada de la barra, las cabeceras y los botones se corta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccesibilidadTiendaTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val acciones = AccionesGrabadas()
    private val gorro = PruebaTienda.Pieza("head-gorro-lana")

    // ---- H11: TalkBack ----

    @Test
    fun `the root describes itself and each knot says Jump to its slot as a button with the active one selected`() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistemaLigero {
                Box(Modifier.fillMaxSize()) { RaizRanuras(RanuraHabi.CUELLO, paddingSuperior = 56.dp, alPulsar = {}) }
            }
        }
        rule.mainClock.advanceTimeBy(600)
        assertEquals(
            "Saltar a una ranura",
            rule.onNodeWithTag("raiz").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single(),
        )
        RanuraHabi.entries.forEach { r ->
            val config = rule.onNodeWithTag("raiz-${r.id}").fetchSemanticsNode().config
            val descripcion = config[SemanticsProperties.ContentDescription].single()
            assertTrue("«$descripcion» empieza por Saltar a", descripcion.startsWith("Saltar a "))
            assertEquals(Role.Button, config[SemanticsProperties.Role])
            assertEquals("solo la activa va seleccionada: ${r.id}", r == RanuraHabi.CUELLO, config[SemanticsProperties.Selected])
        }
    }

    @Test
    fun `the bar is announced when it comes out and its buttons carry their text`() {
        montarTienda(estadoTienda(prueba = gorro, barra = BarraPrueba.Comprar("Gorro de lana", 60, 80, null, false)))
        val barra = rule.onNodeWithTag("barra").fetchSemanticsNode().config
        assertEquals(LiveRegionMode.Polite, barra[SemanticsProperties.LiveRegion])
        listOf("barra-comprar" to "Comprar por 60", "barra-ahora-no" to "Ahora no").forEach { (tag, texto) ->
            rule.onNodeWithText(texto, useUnmergedTree = true).assertExists()
            assertTrue("$tag se puede pulsar", SemanticsActions.OnClick in rule.onNodeWithTag(tag).fetchSemanticsNode().config)
        }
    }

    @Test
    fun `the undo notice is announced when it comes out and Deshacer is a button with its own label`() {
        val avisos = AvisoEstado()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistema {
                val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) { scope.launch { avisos.mostrar("Congelador comprado", "Deshacer") {} } }
                AvisoHost(avisos)
            }
        }
        rule.mainClock.advanceTimeBy(600)
        val aviso = rule.onNodeWithTag("aviso").fetchSemanticsNode().config
        assertEquals(LiveRegionMode.Polite, aviso[SemanticsProperties.LiveRegion])
        rule.onNodeWithText("Congelador comprado", useUnmergedTree = true).assertExists()
        val boton = rule.onNode(hasText("Deshacer") and hasClickAction()).fetchSemanticsNode()
        assertEquals(Role.Button, boton.config[SemanticsProperties.Role])
    }

    // ---- H2: letra al 200 % ----

    private val doble = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)

    /** Los textos bajo [tag] (árbol sin fusionar) que se cortan: no caben en alto o se acaban en puntos suspensivos. */
    private fun cortados(tag: String): List<String> {
        val nodos = rule.onAllNodes(doble and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
        return nodos.mapNotNull { nodo ->
            val resultados = mutableListOf<TextLayoutResult>()
            nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
            val r = resultados.singleOrNull() ?: return@mapNotNull null
            // didOverflowWidth no vale: el BasicText de las etiquetas lo da a 1x también, con letra normal.
            val cortado = r.didOverflowHeight || (0 until r.lineCount).any { r.isLineEllipsized(it) }
            if (cortado) r.layoutInput.text.text else null
        }
    }

    private fun montarTienda(
        estado: TiendaUiState,
        escala: Float = 1f,
    ) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = escala)) {
                ConSistema { TiendaContenido(estado, acciones, emptyFlow(), {}, paneles = { PanelFalso("X") }) }
            }
        }
        rule.mainClock.advanceTimeBy(900)
    }

    @Test
    fun `at 200 percent the header, light selector, tabs and bar keep every text whole`() {
        montarTienda(
            estadoTienda(prueba = gorro, barra = BarraPrueba.Comprar("Gorro de lana", 60, 80, null, false)),
            escala = 2f,
        )
        assertEquals(
            "cabecera, selector y pestañas",
            emptyList<String>(),
            cortados("tienda-escena") + cortados("tluz") + cortados("ttab-habi"),
        )
        assertEquals(emptyList<String>(), cortados("ttab-jardin") + cortados("ttab-congeladores"))
        assertEquals("la barra, con sus botones", emptyList<String>(), cortados("barra"))
        // el catálogo sigue existiendo y la barra cabe en la pantalla
        rule.onNodeWithTag("tienda-catalogo").assertExists()
        val barra = rule.onNodeWithTag("barra").fetchSemanticsNode().boundsInRoot
        assertTrue("la barra entra en la pantalla (${barra.top})", barra.top >= 0f)
    }

    @Test
    fun `at 200 percent the selector and the tabs grow in height and not in width`() {
        montarTienda(estadoTienda(), escala = 2f)
        val luz = rule.onNodeWithTag("tluz").fetchSemanticsNode().boundsInRoot
        val pestana = rule.onNodeWithTag("ttab-habi").fetchSemanticsNode().boundsInRoot
        with(rule.density) {
            assertTrue("la luz crece sobre 52 dp (${luz.height})", luz.height > 52.dp.toPx() + 1f)
            assertTrue("la pestaña crece sobre 46 dp (${pestana.height})", pestana.height > 46.dp.toPx() + 1f)
            assertTrue("la luz no se sale del ancho", luz.width <= 412.dp.toPx() + 1f)
        }
    }
}
