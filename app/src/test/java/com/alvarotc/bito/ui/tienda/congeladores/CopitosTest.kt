package com.alvarotc.bito.ui.tienda.congeladores

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CopitosTest {
    @get:Rule val rule = createComposeRule()
    private val banco = Banco(rule)

    private fun copitos(
        total: Int,
        ancho: Int = 372,
    ) = banco.colocar(ancho = ancho) { Copitos(total) }

    @Test
    fun `seven fit`() {
        assertEquals(PlanCopitos(7, null), planCopitos(7))
        copitos(7)
        rule.onAllNodesWithTag("tc-copo").assertCountEquals(7)
        rule.onAllNodesWithTag("tc-mas").assertCountEquals(0)
    }

    @Test
    fun `eight shows six and +2`() {
        assertEquals(PlanCopitos(6, 2), planCopitos(8))
        copitos(8)
        rule.onAllNodesWithTag("tc-copo").assertCountEquals(6)
        rule.onNodeWithText("+2", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("2 congeladores más").assertIsDisplayed()
    }

    @Test
    fun `eleven shows six and +5`() {
        assertEquals(PlanCopitos(6, 5), planCopitos(11))
        copitos(11)
        rule.onAllNodesWithTag("tc-copo").assertCountEquals(6)
        rule.onNodeWithText("+5", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("5 congeladores más").assertIsDisplayed()
    }

    @Test
    fun `none shows the empty text`() {
        copitos(0)
        rule.onNodeWithText("Aún no tienes ninguno.").assertIsDisplayed()
        rule.onAllNodesWithTag("tc-copo").assertCountEquals(0)
        rule.onAllNodesWithTag("tc-mas").assertCountEquals(0)
        assertNull(planCopitos(0).mas)
    }

    @Test
    fun `one row with no horizontal scroll`() {
        copitos(11)
        val copos = (0 until 6).map { rule.onAllNodesWithTag("tc-copo")[it].getBoundsInRoot() }
        val mas = rule.onNodeWithTag("tc-mas", useUnmergedTree = true).getBoundsInRoot()
        val todos = copos + mas
        assertTrue("todos a la misma altura", todos.all { kotlin.math.abs(it.top.value - todos.first().top.value) < 0.5f })
        assertEquals("siete huecos de 44 con 8 de aire", 356f, (mas.right - copos.first().left).value, 0.5f)
        assertTrue("caben en los 372 de la veta", (mas.right - copos.first().left).value <= 372f)
        // Ni siquiera con el ancho justo se encoge ninguno.
        todos.forEach { assertEquals(44f, it.width.value, 0.5f) }
    }

    @Test
    fun `the pill has the same size and ice`() {
        copitos(9)
        val copo = rule.onAllNodesWithTag("tc-copo")[0].getBoundsInRoot()
        val mas = rule.onNodeWithTag("tc-mas", useUnmergedTree = true).getBoundsInRoot()
        assertEquals(44f, copo.width.value, 0.5f)
        assertEquals(copo.width.value, mas.width.value, 0.5f)
        assertEquals(copo.height.value, mas.height.value, 0.5f)
        val foto = banco.fotograma()
        val hielo = banco.colores.hielo.toArgb()
        assertTrue(cerca(hielo, banco.pixel(foto, "tc-mas", 6f, 22f)))
        assertTrue(cerca(hielo, banco.pixel(foto, "tc-copo", 6f, 22f)))
    }
}
