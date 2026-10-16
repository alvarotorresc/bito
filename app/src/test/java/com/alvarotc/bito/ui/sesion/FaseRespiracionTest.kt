package com.alvarotc.bito.ui.sesion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.BreathingRhythm
import com.alvarotc.bito.domain.model.BreathPhase
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
class FaseRespiracionTest {
    @get:Rule
    val compose = createComposeRule()

    private var punto by mutableStateOf<BreathingRhythm.Point?>(null)
    private var ciclo by mutableStateOf(1)

    private fun montar(
        mostrarPuntitos: Boolean = true,
        modifier: Modifier = Modifier,
    ) {
        compose.setContent {
            ConSistema {
                FaseRespiracion(punto, ciclo, 6, mostrarPuntitos, modifier)
            }
        }
    }

    // Calmarme (4 s dentro, 6 fuera), el ritmo de Respirar para entrar.
    private fun en(ms: Long) = BreathingRhythm.at(BreathingMode.CALM, ms)

    private fun encendidos(): Int = compose.onNodeWithTag("e-dots").fetchSemanticsNode().config[PuntitosEncendidos]

    @Test
    fun `the phase reads Inhala, Manten and Exhala`() {
        punto = en(0)
        montar()
        compose.onNodeWithTag("e-fase").assertTextEquals("Inhala")

        punto = en(5_000)
        compose.onNodeWithTag("e-fase").assertTextEquals("Exhala")

        punto = BreathingRhythm.at(BreathingMode.SLEEP, 5_000)
        compose.onNodeWithTag("e-fase").assertTextEquals("Mantén")
    }

    @Test
    fun `without a point yet it reads Inhala`() {
        montar()
        compose.onNodeWithTag("e-fase").assertTextEquals("Inhala")
    }

    @Test
    fun `the reader hears the phase change politely`() {
        punto = en(0)
        montar()
        compose.onNodeWithTag("e-fase").assert(
            SemanticsMatcher("región viva educada") {
                it.config.getOrNull(SemanticsProperties.LiveRegion) == androidx.compose.ui.semantics.LiveRegionMode.Polite
            },
        )
    }

    @Test
    fun `six dots of 9 dp, lit by finished cycles`() {
        punto = en(0)
        ciclo = 1
        montar()
        // 6 · 9 + 5 · 5 de hueco.
        compose.onNodeWithTag("e-dots").assertWidthIsEqualTo(79.dp).assertHeightIsEqualTo(9.dp)
        assertEquals(0, encendidos())

        // Tercer ciclo inhalando: los dos primeros hechos.
        punto = en(20_000)
        ciclo = 3
        compose.waitForIdle()
        assertEquals(2, encendidos())
    }

    @Test
    fun `exhale lights the current dot`() {
        punto = en(25_000)
        ciclo = 3
        montar()
        assertEquals(BreathPhase.EXHALE, punto!!.phase)
        assertEquals(3, encendidos())
    }

    @Test
    fun `finished lights them all`() {
        punto = BreathingRhythm.at(BreathingMode.CALM, 10_000L * 6).copy(finished = true)
        ciclo = 6
        montar()
        assertEquals(6, encendidos())
    }

    @Test
    fun `the loose breathing has no dots and its own tag`() {
        punto = en(0)
        montar(mostrarPuntitos = false, modifier = Modifier.testTag("s-fase"))
        compose.onNodeWithTag("s-fase").assertTextEquals("Inhala")
        compose.onNodeWithTag("e-fase").assertDoesNotExist()
        compose.onNodeWithTag("e-dots").assertDoesNotExist()
    }

    @Test
    fun `the lit count follows the mock rule`() {
        assertEquals(0, puntitosEncendidos(en(0), 1, 6))
        assertEquals(1, puntitosEncendidos(en(5_000), 1, 6))
        assertEquals(5, puntitosEncendidos(en(50_000), 6, 6))
        assertEquals(6, puntitosEncendidos(en(55_000), 6, 6))
        assertEquals(0, puntitosEncendidos(null, 1, 6))
    }
}
