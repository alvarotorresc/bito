package com.alvarotc.bito.ui.tienda

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.tienda.congeladores.PanelCongeladores
import com.alvarotc.bito.ui.tienda.habi.PanelHabi
import com.alvarotc.bito.ui.tienda.jardin.PanelJardin
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
class PanelTiendaTest {
    @get:Rule val rule = createComposeRule()

    private val paneles = PestanaTienda.entries.associateWith { PanelFalso(it.name) }

    private fun montar(pestana: PestanaTienda) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistema {
                TiendaContenido(estadoTienda(pestana = pestana), AccionesGrabadas(), emptyFlow(), {}, paneles = { paneles.getValue(it) })
            }
        }
        rule.mainClock.advanceTimeBy(600)
    }

    private fun compruebaSoloPanelDe(pestana: PestanaTienda) {
        montar(pestana)
        rule.onNodeWithTag("escena-${pestana.name}").assertExists()
        rule.onNodeWithTag("catalogo-${pestana.name}").assertExists()
        PestanaTienda.entries.filter { it != pestana }.forEach {
            assertTrue("el panel de $it no debía pintarse en $pestana", paneles.getValue(it).escenas.isEmpty())
            assertTrue("el catálogo de $it no debía pintarse en $pestana", paneles.getValue(it).catalogos.isEmpty())
        }
    }

    @Test
    fun `each tab asks its own panel for scene and catalog on Habi`() = compruebaSoloPanelDe(PestanaTienda.HABI)

    @Test
    fun `each tab asks its own panel for scene and catalog on the garden`() = compruebaSoloPanelDe(PestanaTienda.JARDIN)

    @Test
    fun `each tab asks its own panel for scene and catalog on the freezers`() = compruebaSoloPanelDe(PestanaTienda.CONGELADORES)

    @Test
    fun `the registry maps every tab to its own panel`() {
        assertSame(PanelHabi, panelDe(PestanaTienda.HABI))
        assertSame(PanelJardin, panelDe(PestanaTienda.JARDIN))
        assertSame(PanelCongeladores, panelDe(PestanaTienda.CONGELADORES))
        assertEquals(3, PestanaTienda.entries.size)
    }
}
