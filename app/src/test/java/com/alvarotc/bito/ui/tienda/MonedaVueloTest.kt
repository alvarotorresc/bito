package com.alvarotc.bito.ui.tienda

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.alvarotc.bito.ui.sistema.ConSistema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MonedaVueloTest {
    @get:Rule val rule = createComposeRule()

    private val origen = Offset(40f, 60f)
    private val destino = Offset(300f, 700f)

    private class Banco {
        val vuelo = VueloMoneda()
        var llego by mutableStateOf(false)
        var trabajo: Job? = null
    }

    private fun montar(
        banco: Banco,
        conOrigen: Boolean = true,
        reducido: Boolean = false,
    ) {
        if (conOrigen) banco.vuelo.fijarOrigen(origen)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistema(reducido = reducido) {
                Box { CapaMoneda(banco.vuelo) }
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    banco.trabajo =
                        scope.launch {
                            banco.vuelo.volar({ destino })
                            banco.llego = true
                        }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `the coin flies for 520 ms along an arc that bends right and down`() {
        val banco = Banco()
        montar(banco)
        rule.mainClock.advanceTimeBy(260)
        val moneda = banco.vuelo.activas.single()
        assertTrue(moneda.progreso > 0f && moneda.progreso < 1f)
        val medio = TrayectoMoneda.posicion(0.5f, origen, destino, abre = 150f)
        val recta = Offset((origen.x + destino.x) / 2, (origen.y + destino.y) / 2)
        assertTrue("el arco se abre a la derecha de la recta", medio.x > recta.x)
        var antes = origen.y
        listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f, 1f).forEach {
            val y = TrayectoMoneda.posicion(it, origen, destino, 150f).y
            assertTrue("baja siempre", y > antes)
            antes = y
        }
        assertEquals(destino, TrayectoMoneda.posicion(1f, origen, destino, 150f))
        rule.mainClock.advanceTimeBy(200)
        assertTrue("a los 460 ms sigue en el aire", banco.vuelo.activas.size == 1)
        rule.mainClock.advanceTimeBy(100)
        assertTrue(banco.vuelo.activas.isEmpty())
    }

    @Test
    fun `it shrinks from 1 to 0_6 and fades in the last 15 percent`() {
        assertEquals(1f, TrayectoMoneda.escala(0f), 0.0001f)
        assertEquals(0.6f, TrayectoMoneda.escala(1f), 0.0001f)
        assertEquals(0.8f, TrayectoMoneda.escala(0.5f), 0.0001f)
        assertEquals(1f, TrayectoMoneda.opacidad(0.85f), 0.0001f)
        assertEquals(1f, TrayectoMoneda.opacidad(0.4f), 0.0001f)
        assertEquals(0.5f, TrayectoMoneda.opacidad(0.925f), 0.001f)
        assertEquals(0f, TrayectoMoneda.opacidad(1f), 0.0001f)
    }

    @Test
    fun `volar returns when the coin arrives`() {
        val banco = Banco()
        montar(banco)
        rule.mainClock.advanceTimeBy(300)
        assertFalse(banco.llego)
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        assertTrue(banco.llego)
        assertTrue(banco.vuelo.activas.isEmpty())
    }

    @Test
    fun `cancelling mid-flight leaves no coin`() {
        val banco = Banco()
        montar(banco)
        rule.mainClock.advanceTimeBy(200)
        assertEquals(1, banco.vuelo.activas.size)
        banco.trabajo!!.cancel(CancellationException("sale de la tienda"))
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        assertTrue(banco.vuelo.activas.isEmpty())
        assertFalse(banco.llego)
    }

    @Test
    fun `without an origin it does not fly and does not fail`() {
        val banco = Banco()
        montar(banco, conOrigen = false)
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        assertTrue(banco.vuelo.activas.isEmpty())
        assertTrue("volar vuelve igual", banco.llego)
    }

    @Test
    fun `the balance rolls in 240 ms`() {
        var saldo by mutableStateOf(100)
        rule.mainClock.autoAdvance = false
        rule.setContent { ConSistema { SaldoConRodillo(saldo) } }
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("100", useUnmergedTree = true).assertIsDisplayed()
        rule.runOnIdle {
            saldo = 60
            Snapshot.sendApplyNotifications()
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(100)
        assertEquals(1, rule.onAllNodesWithText("100", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertEquals(1, rule.onAllNodesWithText("60", useUnmergedTree = true).fetchSemanticsNodes().size)
        rule.mainClock.advanceTimeBy(300)
        rule.onAllNodesWithText("100", useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithText("60", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `reduced fades in 150 ms without an arc`() {
        val banco = Banco()
        montar(banco, reducido = true)
        rule.mainClock.advanceTimeBy(60)
        val moneda = banco.vuelo.activas.single()
        assertEquals(destino, moneda.posicion(150f))
        assertEquals(1f, moneda.escala, 0f)
        assertTrue(moneda.opacidad in 0.01f..0.99f)
        rule.mainClock.advanceTimeBy(150)
        rule.waitForIdle()
        assertTrue(banco.llego)
        assertTrue(banco.vuelo.activas.isEmpty())
        assertNotNull(banco.trabajo)
    }
}
