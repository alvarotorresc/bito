package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.topCenter
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Canal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MesaCanalesTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `setting a rail reports the level and previews its channel`() {
        val sonido = SonidoGrabado()
        var cambio: Pair<CanalMesa, Int>? = null
        rule.setContent { ConSistema(sonido) { MesaCanales(CanalMesa.entries.associateWith { 5 }, { c, n -> cambio = c to n }) } }
        rule.onNodeWithTag("riel-EFECTOS").performSemanticsAction(SemanticsActions.SetProgress) { it(8f) }
        rule.waitForIdle()
        assertEquals(CanalMesa.EFECTOS to 8, cambio)
        assertEquals(Canal.EFECTOS to 8, sonido.probados.last())
    }

    @Test
    fun `a switched off rail and a silenced mesa do not answer`() {
        var cambios = 0
        rule.setContent {
            ConSistema {
                MesaCanales(CanalMesa.entries.associateWith { 5 }, { _, _ -> cambios++ }, apagados = setOf(CanalMesa.HABI))
            }
        }
        rule.onNodeWithTag("riel-HABI").performTouchInput { click(topCenter) }
        assertEquals(0, cambios)
    }

    @Test
    fun `a silenced mesa answers on no rail`() {
        var cambios = 0
        rule.setContent {
            ConSistema {
                MesaCanales(CanalMesa.entries.associateWith { 5 }, { _, _ -> cambios++ }, todoApagado = true)
            }
        }
        CanalMesa.entries.forEach { rule.onNodeWithTag("riel-${it.name}").performTouchInput { click(topCenter) } }
        assertEquals(0, cambios)
    }

    @Test
    fun `previews are throttled to one every 90 ms`() {
        val sonido = SonidoGrabado()
        var nivel by mutableStateOf(5)
        rule.mainClock.autoAdvance = false
        rule.setContent { ConSistema(sonido) { MesaCanales(mapOf(CanalMesa.AMBIENTE to nivel), { _, n -> nivel = n }) } }
        rule.onNodeWithTag("riel-AMBIENTE").performSemanticsAction(SemanticsActions.SetProgress) { it(6f) }
        rule.mainClock.advanceTimeBy(20)
        rule.onNodeWithTag("riel-AMBIENTE").performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        rule.mainClock.advanceTimeBy(20)
        assertEquals(1, sonido.probados.size)
        rule.mainClock.advanceTimeBy(100)
        assertEquals(2, sonido.probados.size)
        assertEquals(Canal.AMBIENTE to 7, sonido.probados.last()) // la última se oye al final del plazo
    }
}
