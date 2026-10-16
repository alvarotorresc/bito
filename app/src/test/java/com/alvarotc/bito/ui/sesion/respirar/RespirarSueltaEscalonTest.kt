package com.alvarotc.bito.ui.sesion.respirar

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El cambio de bloque de la suelta (`mostrar()` del mock) medido con el reloj de Compose. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
class RespirarSueltaEscalonTest {
    @get:Rule
    val compose = nuevaRegla()

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var m: Montaje

    @Before
    fun preparar() {
        m = Montaje(carpeta)
    }

    @After
    fun cerrar() {
        m.cerrar()
    }

    private fun existe(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun avanzar(ms: Long) = compose.mainClock.advanceTimeBy(ms)

    private fun hasta(tag: String) {
        var n = 0
        while (!existe(tag)) {
            avanzar(16)
            check(++n < 300) { "no llega $tag" }
        }
    }

    private fun alfa(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[EscalonAlfa]

    private fun dy(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().config[EscalonDesplazaDp]

    private fun montarYEmpezar() {
        compose.mainClock.autoAdvance = false
        m.montar(compose)
        hasta("r-empezar")
        avanzar(500)
        compose.onNodeWithTag("r-empezar").performClick()
        avanzar(16)
    }

    @Test
    fun `the old block leaves in 160 ms by fading and dropping 8 dp`() {
        montarYEmpezar()
        avanzar(32)
        val a = alfa("r-modo")
        val d = dy("r-modo")
        assertTrue("sale: alfa $a", a > 0f && a < 1f)
        assertTrue("baja hacia 8 dp: $d", d > 0f && d <= 8f)
        avanzar(200)
        assertFalse(existe("r-modo"))
    }

    @Test
    fun `the new block waits 140 ms plus 60 and enters from 12 dp and nothing in 260 ms`() {
        montarYEmpezar()
        avanzar(100)
        assertEquals(0f, alfa("s-fase"), 0f)
        assertEquals(12f, dy("s-fase"), 0f)
        avanzar(140)
        // 240 ms desde el cambio: lleva unos 30 ms de su animación.
        val a = alfa("s-fase")
        assertTrue("entra: alfa $a", a > 0f && a < 1f)
        assertTrue("sube hacia 0: ${dy("s-fase")}", dy("s-fase") < 12f)
        avanzar(400)
        assertEquals(1f, alfa("s-fase"), 0f)
        assertEquals(0f, dy("s-fase"), 0f)
    }

    @Test
    fun `each child of a block starts 40 ms after the previous one`() {
        montarYEmpezar()
        hasta("s-parar")
        avanzar(500)
        // Parar a los pocos segundos vuelve a Elegir (no se guarda): sus hijos son r-modo, r-ritmo, r-mus y r-empezar.
        compose.onNodeWithTag("s-parar").performClick()
        avanzar(16)
        hasta("r-modo")
        avanzar(350)
        val alfas = listOf("r-modo", "r-ritmo", "r-mus", "r-empezar").map { alfa(it) }
        assertTrue("cada uno va por detrás del anterior: $alfas", alfas.zipWithNext().all { (a, b) -> a > b })
        assertTrue("el primero ya entra: $alfas", alfas.first() > 0.3f)
        val dys = listOf("r-modo", "r-ritmo", "r-mus").map { dy(it) }
        assertTrue("y los que van detrás están más abajo: $dys", dys.zipWithNext().all { (a, b) -> a < b })
        avanzar(600)
        assertEquals(listOf(1f, 1f, 1f, 1f), listOf("r-modo", "r-ritmo", "r-mus", "r-empezar").map { alfa(it) })
    }
}

/** Con movimiento reducido: fundido de 150 ms sin desplazamiento ni escalonado. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
class RespirarSueltaEscalonReducidoTest {
    @get:Rule
    val compose = nuevaRegla()

    @get:Rule
    val carpeta = TemporaryFolder()

    @Test
    fun `reduced motion fades the block in 150 ms with no offset and no stagger`() {
        val m = Montaje(carpeta, reducido = true)
        try {
            compose.mainClock.autoAdvance = false
            m.montar(compose)
            var n = 0
            while (compose.onAllNodesWithTag("r-empezar").fetchSemanticsNodes().isEmpty()) {
                compose.mainClock.advanceTimeBy(16)
                check(++n < 300)
            }
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithTag("r-empezar").performClick()
            compose.mainClock.advanceTimeBy(16)
            val nodo = compose.onNodeWithTag("s-fase").fetchSemanticsNode()
            assertFalse(nodo.config.contains(EscalonAlfa))
            assertFalse(nodo.config.contains(EscalonDesplazaDp))
            compose.mainClock.advanceTimeBy(48)
            assertFalse(compose.onAllNodesWithTag("r-modo").fetchSemanticsNodes().isEmpty())
            compose.mainClock.advanceTimeBy(400)
            assertTrue(compose.onAllNodesWithTag("r-modo").fetchSemanticsNodes().isEmpty())
        } finally {
            m.cerrar()
        }
    }
}
