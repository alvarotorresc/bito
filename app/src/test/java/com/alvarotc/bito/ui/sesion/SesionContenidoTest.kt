package com.alvarotc.bito.ui.sesion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Curvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
class SesionContenidoTest {
    @get:Rule
    val compose = createComposeRule()

    private var bloque by mutableStateOf<BloqueSesion?>(BloqueSesion.PREPARAR)
    private val sonda = SondaBloques()
    private var alto = 0

    private fun montar(
        reducido: Boolean = false,
        escala: Float = 1f,
        altoCaja: Int = 400,
        contenido: @androidx.compose.runtime.Composable (BloqueSesion) -> Unit = { b ->
            repeat(3) { Box(Modifier.size(80.dp).testTag("pieza-${b.id}-$it")) }
        },
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ConSistema(reducido = reducido) {
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, escala)) {
                    Box(Modifier.height(altoCaja.dp)) {
                        SesionContenido(EstadoSesionUi(bloque), alAlto = { alto = it }, sonda = sonda, contenido = contenido)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
    }

    private fun cambiar(nuevo: BloqueSesion?) {
        Snapshot.withMutableSnapshot { bloque = nuevo }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun existe(tag: String) = compose.onAllNodesWithTagCount(tag) > 0

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    @Test
    fun `the block that is shown settles with every piece in place`() {
        montar()

        assertTrue(existe("ses-b-prep"))
        assertEquals(setOf(0, 1, 2), sonda.piezas.keys)
        sonda.piezas.values.forEach {
            assertEquals(1f, it.first, 0.001f)
            assertEquals(0f, it.second, 0.001f)
        }
    }

    @Test
    fun `block change animates 160 then 260 ms with stagger`() {
        montar()
        cambiar(BloqueSesion.FOCO)

        // A los 80 ms lo viejo se hunde (opacidad bajando, +dp) y lo nuevo aún no existe.
        compose.mainClock.advanceTimeBy(80)
        assertTrue(existe("ses-b-prep"))
        assertTrue("el bloque nuevo espera 140 ms", !existe("ses-b-foco"))
        assertEquals(1f - Curvas.out.transform(0.5f), sonda.salidaAlfa, 0.15f)
        assertTrue(sonda.salidaDy > 0f)

        // A los 160 ms lo viejo se ha ido.
        compose.mainClock.advanceTimeBy(150)
        compose.waitForIdle()
        assertTrue("lo viejo sale a los 160 ms", !existe("ses-b-prep"))

        // El nuevo existe desde los 140 ms, con todas sus piezas todavía invisibles (la primera espera 60 ms).
        assertTrue(existe("ses-b-foco"))

        // 60 ms + 130 ms tras entrar: la primera va por la mitad, la última (retraso 140 ms) apenas ha empezado.
        compose.mainClock.advanceTimeBy(180)
        val primera = sonda.piezas.getValue(0)
        val ultima = sonda.piezas.getValue(2)
        assertEquals(Curvas.out.transform(0.5f), primera.first, 0.2f)
        assertTrue("la primera va por delante de la última", primera.first > ultima.first)
        assertTrue("la primera sube desde 12 dp", primera.second > 0f)

        // Al final, todo en su sitio.
        compose.mainClock.advanceTimeBy(1_500)
        sonda.piezas.values.forEach {
            assertEquals(1f, it.first, 0.001f)
            assertEquals(0f, it.second, 0.001f)
        }
    }

    @Test
    fun `reduced fades 150 ms`() {
        montar(reducido = true)
        cambiar(BloqueSesion.FOCO)

        // Sin hundir lo viejo: se va y lo nuevo entra a la vez, sin retraso ni desplazamiento.
        compose.mainClock.advanceTimeBy(48)
        compose.waitForIdle()
        assertTrue(!existe("ses-b-prep"))
        assertTrue(existe("ses-b-foco"))
        val a = sonda.piezas.values.toList()
        assertTrue("las tres piezas a la vez", a.all { it.first == a.first().first })
        assertTrue(a.first().first in 0.05f..0.8f)
        assertTrue("sin desplazamiento", a.all { it.second == 0f })

        compose.mainClock.advanceTimeBy(150)
        sonda.piezas.values.forEach { assertEquals(1f, it.first, 0.001f) }
    }

    @Test
    fun `going to today leaves the earth empty`() {
        montar()
        cambiar(null)
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()

        assertTrue(!existe("ses-b-prep"))
        assertNull(bloque)
    }

    private fun maxScroll(): Float =
        compose.onNodeWithTag("ses-tierra").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    private val ochoLineas: @androidx.compose.runtime.Composable (BloqueSesion) -> Unit = {
        Text((1..8).joinToString("\n") { "Línea $it" }, Modifier.fillMaxWidth().testTag("largo"))
    }

    /** H3: con la letra grande el bloque no cabe; aquí se reproduce con menos sitio, que es lo mismo para la tierra. */
    @Test
    fun `the earth scrolls when the block does not fit`() {
        montar(contenido = ochoLineas, altoCaja = 200)

        assertTrue("el bloque no cabe y hace scroll", maxScroll() > 0f)
        assertTrue("el alto natural del bloque llega a la cámara: $alto", alto > 200)
    }

    @Test
    fun `the earth does not scroll when the block fits`() {
        montar(contenido = ochoLineas, altoCaja = 400)

        assertEquals(0f, maxScroll(), 0.001f)
    }
}
