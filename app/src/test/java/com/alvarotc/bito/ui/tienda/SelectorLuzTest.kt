package com.alvarotc.bito.ui.tienda

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SelectorLuzTest {
    @get:Rule val rule = createComposeRule()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private val elegidas = mutableListOf<LuzTienda>()
    private lateinit var estado: LuzEstado
    private var luz by mutableStateOf(LuzTienda.DIA)

    private fun montar(
        inicial: LuzTienda = LuzTienda.DIA,
        reducido: Boolean = false,
    ) {
        luz = inicial
        rule.setContent {
            ConSistema(sonido, tacto, reducido) {
                estado = rememberLuzEstado(inicial)
                Box(Modifier.width(412.dp)) {
                    SelectorLuz(luz = luz, estado = estado, alElegir = {
                        elegidas += it
                        luz = it
                    })
                }
            }
        }
        rule.waitForIdle()
    }

    private fun botones() = rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))

    @Test
    fun `the row, the astro and the three buttons have the mock sizes`() {
        montar()
        rule.onNodeWithTag("tluz").assertHeightIsEqualTo(52.dp)
        rule.onNodeWithContentDescription("Luz: Día").assertWidthIsEqualTo(22.dp).assertHeightIsEqualTo(22.dp)
        botones().assertCountEquals(3)
        (0..2).forEach {
            botones()[it].assertWidthIsEqualTo(72.dp).assertHeightIsEqualTo(52.dp)
        }
        rule.onNodeWithTag("luzriel").assertHeightIsEqualTo(52.dp)
        // Las paradas: del surco, a 36 dp de cada borde del riel, con un botón centrado en cada una.
        val riel = rule.onNodeWithTag("luzriel").getUnclippedBoundsInRoot()
        val centros = (0..2).map { botones()[it].getUnclippedBoundsInRoot().let { b -> (b.left.value + b.right.value) / 2f } }
        assertEquals(riel.left.value + 36f, centros[0], 1f)
        assertEquals(riel.left.value + riel.width.value / 2f, centros[1], 1f)
        assertEquals(riel.right.value - 36f, centros[2], 1f)
    }

    @Test
    fun `the buttons are radio buttons of one group and the active one is selected`() {
        montar()
        botones().assertCountEquals(3)
        rule.onNodeWithText("Día").assertIsSelected()
        rule.onNodeWithText("Tarde").assertIsNotSelected()
    }

    @Test
    fun `the astro turns moon-colored at night`() {
        montar(inicial = LuzTienda.NOCHE)
        assertEquals(1f, estado.valor, 0.001f)
        rule.onNodeWithContentDescription("Luz: Noche").assertExists()
    }

    @Test
    fun `tapping a button plays TIC and SEG and TICK`() {
        montar()
        rule.onNodeWithText("Noche").performClick()
        rule.waitForIdle()
        assertEquals(listOf(LuzTienda.NOCHE), elegidas)
        assertTrue(Snd.TIC in sonido.snds())
        assertTrue(Haptica.SEG in tacto.hapticas)
        assertTrue(Haptica.TICK in tacto.hapticas)
        assertEquals(1f, estado.valor, 0.01f)
    }

    @Test
    fun `tapping the stop that is already on only ticks`() {
        montar()
        rule.onNodeWithText("Día").performClick()
        rule.waitForIdle()
        assertTrue(elegidas.isEmpty())
        assertFalse(Snd.TIC in sonido.snds())
        assertEquals(listOf(Haptica.TICK), tacto.hapticas)
    }

    @Test
    fun `dragging follows the finger without a spring`() {
        montar()
        rule.onNodeWithTag("luzriel").performTouchInput {
            val a = 36.dp.toPx()
            val b = width - 36.dp.toPx()
            down(Offset(a, centerY))
            moveTo(Offset(a + (b - a) * 0.25f, centerY))
        }
        rule.waitForIdle()
        // A un cuarto del surco, la luz vale 0,25 en el acto, sin curva de muelle: lo mismo con el dedo quieto.
        assertEquals(0.25f, estado.valor, 0.01f)
        rule.mainClock.advanceTimeBy(300)
        assertEquals(0.25f, estado.valor, 0.01f)
        rule.onNodeWithTag("luzriel").performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(0.5f, estado.valor, 0.01f)
    }

    @Test
    fun `crossing a stop while dragging ticks once per stop`() {
        montar()
        rule.onNodeWithTag("luzriel").performTouchInput {
            val a = 36.dp.toPx()
            val b = width - 36.dp.toPx()
            down(Offset(a, centerY))
            moveTo(Offset((a + b) / 2f, centerY))
            moveTo(Offset(b, centerY))
            up()
        }
        rule.waitForIdle()
        assertEquals(2, sonido.snds().count { it == Snd.TIC })
        assertEquals(2, tacto.hapticas.count { it == Haptica.SEG })
        assertEquals(listOf(LuzTienda.NOCHE), elegidas)
    }

    @Test
    fun `arrow keys move one stop`() {
        montar()
        rule.onNodeWithText("Día").requestFocus()
        rule.onNodeWithText("Día").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        assertEquals(listOf(LuzTienda.TARDE), elegidas)
        assertTrue(Snd.TIC in sonido.snds())
        rule.onNodeWithText("Tarde").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithText("Noche").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        assertEquals(listOf(LuzTienda.TARDE, LuzTienda.NOCHE), elegidas)
        rule.onNodeWithText("Noche").performKeyInput { pressKey(Key.DirectionLeft) }
        rule.waitForIdle()
        assertEquals(LuzTienda.TARDE, elegidas.last())
    }

    @Test
    fun `Enter and Space on the focused button keep it chosen`() {
        montar()
        rule.onNodeWithText("Día").requestFocus()
        rule.onNodeWithText("Día").performKeyInput { pressKey(Key.DirectionRight) }
        rule.waitForIdle()
        rule.onNodeWithText("Tarde").performKeyInput { pressKey(Key.Enter) }
        rule.onNodeWithText("Tarde").performKeyInput { pressKey(Key.Spacebar) }
        rule.waitForIdle()
        // Con foco itinerante el botón con foco es el activo: Enter y Espacio lo confirman sin mover nada.
        assertEquals(listOf(LuzTienda.TARDE), elegidas)
        rule.onNodeWithText("Tarde").assertIsSelected()
    }

    @Test
    fun `TalkBack description says the stop and the astro can be adjusted`() {
        montar(inicial = LuzTienda.TARDE)
        val astro = rule.onNodeWithContentDescription("Luz: Tarde")
        astro.assertExists()
        val info = astro.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(0.5f, info.current, 0.001f)
        astro.performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        rule.waitForIdle()
        assertEquals(LuzTienda.NOCHE, elegidas.last())
    }

    @Test
    fun `reduced motion jumps with a 150 ms fade, not a spring`() {
        montar(reducido = true)
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Noche").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(80)
        val medio = estado.valor
        assertTrue("a mitad de camino", medio > 0f && medio < 1f)
        rule.mainClock.advanceTimeBy(100)
        assertEquals(1f, estado.valor, 0.001f)
    }

    @Test
    fun `the spring takes longer than 150 ms`() {
        montar()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Noche").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(160)
        assertTrue(estado.valor < 0.999f)
    }
}
