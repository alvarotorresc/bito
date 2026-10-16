package com.alvarotc.bito.ui.today

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w360dp-h640dp")
class HojaOtraHoraTest {
    @get:Rule val rule = createComposeRule()

    private val guardados = mutableListOf<Int>()

    private fun montar(
        abierta: Boolean = true,
        desde: Int = 13 * 60,
        hasta: Int = 20 * 60,
        actual: Int = 14 * 60 + 30,
    ) {
        rule.setContent {
            ConSistema {
                HojaOtraHora(abierta, desde, hasta, actual, alGuardar = { guardados += it }, alCerrar = {})
            }
        }
        rule.waitForIdle()
    }

    private fun mover(minutos: Int) {
        rule.onNodeWithTag("hoja-hora-reloj").performSemanticsAction(SemanticsActions.SetProgress) { it(minutos.toFloat()) }
        rule.waitForIdle()
    }

    @Test
    fun `the clock is bounded to the window it was given`() {
        montar()
        val rango = rule.onNodeWithTag("hoja-hora-reloj").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(13 * 60f, rango.range.start, 0f)
        assertEquals(20 * 60f, rango.range.endInclusive, 0f)
        assertEquals(14 * 60 + 30f, rango.current, 0f)
    }

    @Test
    fun `saving hands over the chosen minute`() {
        montar()
        mover(16 * 60)
        rule.onNodeWithTag("hoja-hora-guardar").performClick()
        assertEquals(listOf(16 * 60), guardados)
    }

    @Test
    fun `a minute past the end is pulled back to the end before saving`() {
        montar()
        mover(23 * 60)
        rule.onNodeWithTag("hoja-hora-guardar").performClick()
        assertEquals(listOf(20 * 60), guardados)
    }

    @Test
    fun `a current time outside the window opens on its nearest end`() {
        montar(actual = 9 * 60)
        rule.onNodeWithTag("hoja-hora-guardar").performClick()
        assertEquals(listOf(13 * 60), guardados)
    }

    @Test
    fun `closed it composes nothing`() {
        montar(abierta = false)
        rule.onAllNodesWithTag("hoja-hora").assertCountEquals(0)
    }
}
