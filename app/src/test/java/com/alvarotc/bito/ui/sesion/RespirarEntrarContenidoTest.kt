package com.alvarotc.bito.ui.sesion

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.BreathingRhythm
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.planSesion
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ANCLA = 1_000L

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
class RespirarEntrarContenidoTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val motor = MotorSesionFalso()
    private var ahora = ANCLA
    private var saltos = 0
    private var visible by mutableStateOf(true)
    private val respiracion = mutableStateOf<(() -> Float)?>(null)

    private fun montar(reducido: Boolean = false) {
        val instante = motor.viva(planSesion(entrada = Entrada.RESPIRAR), 0L)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ConSistema(reducido = reducido) {
                if (visible) {
                    Column(Modifier.width(380.dp)) {
                        RespirarEntrarContenido(
                            instante,
                            ANCLA,
                            alSaltar = { saltos++ },
                            respiracion = respiracion,
                            elapsed = { ahora },
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)
    }

    private fun alMs(ms: Long) {
        ahora = ANCLA + ms
        compose.mainClock.advanceTimeBy(100)
    }

    @Test
    fun `it shows the phase, the dots, the count and skip with the mock texts`() {
        montar()

        compose.onNodeWithTag("e-fase").assertTextEquals("Inhala")
        compose.onNodeWithTag("e-dots").assertExists()
        compose.onNodeWithTag("e-cuenta").assertTextEquals("1 de 6")
        compose.onNodeWithTag("e-saltar").assertExists()
    }

    @Test
    fun `the count says cycle of 6 and dots light`() {
        montar()

        // Ciclo 3, exhalando: 25 s de sesión = 2 ciclos de 10 s y 5 s del tercero (4 s de inhala).
        alMs(25_000)

        compose.onNodeWithTag("e-cuenta").assertTextEquals("3 de 6")
        compose.onNodeWithTag("e-fase").assertTextEquals("Exhala")
        val encendidos = compose.onNodeWithTag("e-dots").fetchSemanticsNode().config[PuntitosEncendidos]
        assertEquals("los 2 hechos y el actual al exhalar", 3, encendidos)
    }

    @Test
    fun `skip goes to focus`() {
        montar()

        compose.onNodeWithTag("e-saltar").performClick()

        assertEquals(1, saltos)
    }

    @Test
    fun `habi fill is read from the anchor clock and released when the block goes`() {
        montar()
        assertNotNull(respiracion.value)

        alMs(2_000)
        val esperado = BreathingRhythm.at(BreathingMode.CALM, 2_000, 6).fill
        assertEquals(esperado, respiracion.value!!(), 0.0001f)

        alMs(7_000)
        val luego = BreathingRhythm.at(BreathingMode.CALM, 7_000, 6).fill
        assertEquals(luego, respiracion.value!!(), 0.0001f)

        compose.runOnUiThread { visible = false }
        compose.mainClock.advanceTimeBy(100)
        assertNull("Habi abre los ojos", respiracion.value)
    }

    @Test
    fun `the screen is kept on only while breathing`() {
        fun encendida() = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        assertFalse(encendida())

        montar()
        assertTrue(encendida())

        compose.runOnUiThread { visible = false }
        compose.mainClock.advanceTimeBy(100)
        assertFalse(encendida())
    }

    @Test
    fun `the phase word is announced politely`() {
        montar()

        val config = compose.onNodeWithTag("e-fase").fetchSemanticsNode().config
        assertTrue(config.contains(SemanticsProperties.LiveRegion))
    }
}
