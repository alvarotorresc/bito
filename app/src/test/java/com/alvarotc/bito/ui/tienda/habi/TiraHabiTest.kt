package com.alvarotc.bito.ui.tienda.habi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.height
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.tienda.TiendaUiState
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
class TiraHabiTest {
    @get:Rule val rule = createComposeRule()

    private val toques = mutableListOf<String>()

    private fun tira(estado: TiendaUiState = estadoTienda(saldo = 140)) {
        rule.setContent {
            ConSistema { Box(Modifier.fillMaxSize()) { TiraHabi(estado, alTocar = { toques += it }) } }
        }
        rule.waitForIdle()
    }

    private fun caja(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    @Test
    fun `slots come in order with their headers`() {
        val cabeceras = construirTira(false, null, false, EquippedSet()).filterIsInstance<EntradaTira.Cabecera>()
        assertEquals(RanuraHabi.entries, cabeceras.map { it.ranura })
        tira()
        rule.onNodeWithTag("ranura-cabeza").assertIsDisplayed()
        rule.onNodeWithText("Cabeza").assertIsDisplayed()
    }

    @Test
    fun `three columns with 16 and 10 dp gaps and the side padding`() {
        tira()
        val g = caja("item-head-gorro-lana")
        val b = caja("item-head-boina")
        val p = caja("item-head-paja")
        val a = caja("item-head-auriculares")
        assertEquals(16f, g.left.value, 0.6f)
        assertEquals(10f, b.left.value - g.right.value, 0.6f)
        assertEquals(10f, p.left.value - b.right.value, 0.6f)
        assertEquals(412f - 62f, p.right.value, 0.6f)
        val altoFila = maxOf(g.height.value, b.height.value, p.height.value)
        assertEquals(16f, a.top.value - (g.top.value + altoFila), 0.6f)
    }

    @Test
    fun `the header meta says worn count or covered`() {
        tira(
            estadoTienda(
                saldo = 140,
            ).copy(puestos = EquippedSet(head = "head-boina"), look = EquippedSet(head = "head-boina"), poseidos = setOf("head-boina")),
        )
        rule.onNodeWithTag("meta-cabeza").assertIsDisplayed()
        rule.onNodeWithText("Puesto: boina").assertIsDisplayed()
    }

    @Test
    fun `the header says count when nothing is worn and covered under a costume`() {
        tira()
        rule.onNodeWithText("7 piezas").assertIsDisplayed()
        val tapado = EquippedSet(costume = "costume-calabaza")
        assertEquals(MetaRanura.Tapada, metaDe(RanuraHabi.PIES, tapado))
    }

    @Test
    fun `the header stays pinned while its slot scrolls`() {
        tira()
        rule.onNodeWithTag("tira-habi").performScrollToIndex(3)
        rule.onNodeWithTag("ranura-cabeza").assertIsDisplayed()
        assertEquals(0f, caja("ranura-cabeza").top.value, 0.6f)
    }

    @Test
    fun `notes appear only where they belong`() {
        tira()
        rule.onNodeWithTag("nota-intro").assertDoesNotExist()
        rule.onNodeWithTag("nota-todo").assertDoesNotExist()
        rule.onNodeWithTag("nota-vacia").assertDoesNotExist()
        rule.onNodeWithTag("tira-habi").performScrollToIndex(construirTira(false, null, false, EquippedSet()).size - 1)
        rule.onNodeWithText("Se compran en su temporada y se quedan para siempre. Probártelos se puede cuando quieras.").assertIsDisplayed()
    }

    @Test
    fun `eyes note sits under the eyes grid`() {
        tira()
        val i = construirTira(false, null, false, EquippedSet()).indexOf(EntradaTira.NotaRanura(RanuraHabi.OJOS))
        rule.onNodeWithTag("tira-habi").performScrollToIndex(i)
        rule.onNodeWithText("Solo cambia el iris; el brillo de los ojos se queda.").assertIsDisplayed()
    }

    @Test
    fun `all bought ends with its line`() {
        val todo = HabiCatalog.all.filter { (it.price ?: 0) > 0 }.map { it.id }.toSet()
        tira(estadoTienda(saldo = 0).copy(poseidos = todo))
        rule.onNodeWithTag("tira-habi").performScrollToIndex(construirTira(false, null, true, EquippedSet()).size - 1)
        rule.onNodeWithText("Ya tienes todo lo que se vende. Lo demás llega con las rachas.").assertIsDisplayed()
    }

    @Test
    fun `tapping a tile calls tocarPieza with its id`() {
        tira()
        rule.onNodeWithTag("item-head-boina").performClick()
        assertEquals(listOf("head-boina"), toques)
        assertTrue(HabiCatalog.byId("head-boina") != null)
    }
}
