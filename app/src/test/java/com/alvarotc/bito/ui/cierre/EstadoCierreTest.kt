package com.alvarotc.bito.ui.cierre

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EstadoCierreTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var estado: EstadoCierre
    private lateinit var scope: CoroutineScope

    private fun montar(
        abierto: Boolean = false,
        sellado: Boolean = false,
    ) {
        rule.setContent {
            estado = rememberEstadoCierre(abierto, sellado)
            scope = rememberCoroutineScope()
        }
        rule.waitForIdle()
    }

    @Test
    fun `opened at the start it is already at the close`() {
        montar(abierto = true)
        assertEquals(1f, estado.c.value)
        assertTrue(estado.abierto)
        assertEquals(0f, estado.noche.value)
    }

    @Test
    fun `closed at the start it is on Today and draws the day`() {
        montar()
        assertEquals(0f, estado.c.value)
        assertFalse(estado.abierto)
        assertEquals(0f, estado.nocheDelJardin(diaSellado = false))
        assertEquals(1f, estado.nocheDelJardin(diaSellado = true))
    }

    @Test
    fun `opening with the day already sealed leaves the night at one`() {
        montar(abierto = true, sellado = true)
        assertEquals(1f, estado.noche.value)
        assertEquals(1f, estado.nocheDelJardin(diaSellado = true))
    }

    @Test
    fun `adjusting the night on opening follows the sealed day`() {
        montar()
        rule.runOnIdle { scope.launch { estado.ajustarNoche(diaSellado = true) } }
        rule.waitForIdle()
        assertEquals(1f, estado.noche.value)
        rule.runOnIdle { scope.launch { estado.ajustarNoche(diaSellado = false) } }
        rule.waitForIdle()
        assertEquals(0f, estado.noche.value)
    }

    @Test
    fun `with the close open the night follows its animation even if the day gets sealed halfway`() {
        montar(abierto = true)
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { scope.launch { estado.noche.animateTo(1f, tween(2400, easing = LinearEasing)) } }
        rule.mainClock.advanceTimeBy(1_300)
        val aMitad = estado.noche.value
        assertTrue("a mitad del anochecer: $aMitad", aMitad > 0f && aMitad < 1f)
        // El día ya está sellado (lo sella el cierre a los 1,3 s): el jardín sigue a la animación, sin saltar a 1.
        assertEquals(aMitad, estado.nocheDelJardin(diaSellado = true))
    }

    @Test
    fun `a dusk still running after the close goes down keeps drawing its value`() {
        montar(abierto = true)
        rule.mainClock.autoAdvance = false
        rule.runOnIdle {
            scope.launch { estado.noche.animateTo(1f, tween(2400, easing = LinearEasing)) }
            scope.launch { estado.c.snapTo(0f) }
        }
        rule.mainClock.advanceTimeBy(600)
        assertFalse(estado.abierto)
        val corriendo = estado.noche.value
        assertTrue(corriendo < 1f)
        assertEquals(corriendo, estado.nocheDelJardin(diaSellado = true))
    }
}
