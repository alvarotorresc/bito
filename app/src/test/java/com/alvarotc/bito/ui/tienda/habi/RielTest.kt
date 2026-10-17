package com.alvarotc.bito.ui.tienda.habi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.tienda.estadoTienda
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
class RielTest {
    @get:Rule val rule = createComposeRule()

    private fun tira(saldo: Int) {
        rule.setContent {
            ConSistema { Box(Modifier.fillMaxSize()) { TiraHabi(estadoTienda(saldo = saldo), alTocar = {}) } }
        }
        rule.waitForIdle()
    }

    private fun alto(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    @Test
    fun `rest padding-top is 56`() {
        assertEquals(56f, paddingRiel(null), 0f)
        assertEquals(56f, paddingRiel(14f), 0f)
        assertEquals(56f, paddingRiel(60f), 0f)
        tira(saldo = 140)
        // la primera tecla del riel baja 56 dp desde su borde superior
        assertEquals(56f, alto("raiz-cabeza").top.value - alto("raiz").top.value, 0.6f)
    }

    @Test
    fun `padding-top follows y minus 4 once it passes 60`() {
        assertEquals(96f, paddingRiel(100f), 0f)
        assertEquals(56f, paddingRiel(-30f), 0f)
    }

    @Test
    fun `with the welcome note the rail drops with the first row`() {
        tira(saldo = 0)
        val hueco = rule.onAllNodesWithTag("hornacina-hueco", useUnmergedTree = true)[0].getBoundsInRoot()
        val boton = alto("raiz-cabeza")
        // el nudo de 40 dp queda a 4 dp del borde del botón: su borde superior es el de la primera hornacina
        assertEquals(hueco.top.value, boton.top.value + 4f, 1f)
        assertTrue(boton.top.value - alto("raiz").top.value > 56.5f)
    }

    @Test
    fun `the thread starts at padding-top plus 24 and is 288 dp`() {
        tira(saldo = 0)
        val raiz = alto("raiz")
        val hilo = alto("raiz-hilo")
        val boton = alto("raiz-cabeza")
        assertEquals(boton.top.value - raiz.top.value + 24f, hilo.top.value - raiz.top.value, 0.6f)
        assertEquals(288f, hilo.height.value, 0.6f)
        assertEquals(2f, hilo.width.value, 0.6f)
        assertEquals(27f, hilo.left.value - raiz.left.value, 0.6f)
    }

    @Test
    fun `the tile column is aligned to the top for one and two line names`() {
        tira(saldo = 140)
        val a = alto("item-head-gorro-lana")
        val b = alto("item-head-paja")
        val c = alto("item-head-boina")
        assertEquals(a.top.value, b.top.value, 0.5f)
        assertEquals(a.top.value, c.top.value, 0.5f)
    }
}
