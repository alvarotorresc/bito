package com.alvarotc.bito.ui.tienda

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
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
class PestanasTiendaTest {
    @get:Rule val rule = createComposeRule()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val elegidas = mutableListOf<PestanaTienda>()
    private var pestana by mutableStateOf(PestanaTienda.HABI)

    private fun montar(reducido: Boolean = false) {
        pestana = PestanaTienda.HABI
        rule.mainClock.autoAdvance = true
        rule.setContent {
            ConSistema(sonido, tacto, reducido) {
                Box(Modifier.width(412.dp)) {
                    PestanasTienda(pestana = pestana, alElegir = {
                        elegidas += it
                        pestana = it
                    })
                }
            }
        }
        rule.waitForIdle()
    }

    private fun xIndicador() = rule.onNodeWithTag("ttind").getUnclippedBoundsInRoot().left.value

    @Test
    fun `the tabs are 46 dp high, three equal, with a 16 dp margin`() {
        montar()
        val bounds = listOf("ttab-habi", "ttab-jardin", "ttab-congeladores").map { rule.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        bounds.forEach {
            assertEquals(46f, it.height.value, 0.5f)
            assertEquals((412f - 32f) / 3f, it.width.value, 1f)
        }
        assertEquals(16f, bounds[0].left.value, 0.5f)
        assertEquals(412f - 16f, bounds[2].right.value, 0.5f)
        rule.onNodeWithTag("ttab-habi").assertHeightIsEqualTo(46.dp)
        rule.onNodeWithTag("ttab-habi").assertIsSelected()
    }

    @Test
    fun `the indicator is 3 dp high and 24 dp narrower than its tab`() {
        montar()
        val indicador = rule.onNodeWithTag("ttind").getUnclippedBoundsInRoot()
        val tab = rule.onNodeWithTag("ttab-habi").getUnclippedBoundsInRoot()
        assertEquals(3f, indicador.height.value, 0.2f)
        assertEquals(tab.width.value - 24f, indicador.width.value, 0.5f)
        assertEquals(tab.left.value + 12f, indicador.left.value, 0.5f)
        // Su borde de abajo cae sobre el borde de abajo de la raya de 1,5 dp.
        assertEquals(46f + 1.5f, indicador.bottom.value, 0.6f)
    }

    @Test
    fun `the indicator slides with the indicador spring`() {
        montar()
        val desde = xIndicador()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("ttab-congeladores").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(100)
        val medio = xIndicador()
        val hasta = rule.onNodeWithTag("ttab-congeladores").getUnclippedBoundsInRoot().left.value + 12f
        assertTrue("va de camino: $desde < $medio < $hasta", medio > desde + 1f && medio < hasta - 1f)
        rule.mainClock.advanceTimeBy(1500)
        assertEquals(hasta, xIndicador(), 0.5f)
    }

    @Test
    fun `tapping plays INTERRUPTOR_ON and TICK and says the tab`() {
        montar()
        rule.onNodeWithTag("ttab-jardin").performClick()
        rule.waitForIdle()
        assertEquals(listOf(PestanaTienda.JARDIN), elegidas)
        assertEquals(listOf(Snd.INTERRUPTOR_ON), sonido.snds())
        assertEquals(listOf(Haptica.TICK), tacto.hapticas)
        rule.onNodeWithTag("ttab-jardin").assertIsSelected()
    }

    @Test
    fun `tapping the open tab does nothing`() {
        montar()
        rule.onNodeWithTag("ttab-habi").performClick()
        rule.waitForIdle()
        assertTrue(elegidas.isEmpty())
        assertTrue(sonido.snds().isEmpty())
        assertTrue(tacto.hapticas.isEmpty())
    }

    @Test
    fun `reduced has no spring`() {
        montar(reducido = true)
        val desde = xIndicador()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("ttab-congeladores").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(60)
        val hasta = rule.onNodeWithTag("ttab-congeladores").getUnclippedBoundsInRoot().left.value + 12f
        assertTrue(xIndicador() in (desde + 1f)..(hasta - 1f))
        rule.mainClock.advanceTimeBy(120)
        // Fundido de 150 ms: a los 180 ms ya está y no se ha pasado ni un punto.
        assertEquals(hasta, xIndicador(), 0.5f)
    }
}
