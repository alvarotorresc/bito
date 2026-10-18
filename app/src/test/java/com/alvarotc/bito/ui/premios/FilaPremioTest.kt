package com.alvarotc.bito.ui.premios

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.GardenSpecial
import com.alvarotc.bito.ui.camino.HojaLogro
import com.alvarotc.bito.ui.camino.LogroUi
import com.alvarotc.bito.ui.camino.ModoLogro
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w411dp-h891dp")
class FilaPremioTest {
    @get:Rule
    val rule = createComposeRule()

    private fun logro(id: String) = LogroUi(BadgeCatalog.byId(id)!!, 20_000, null, GardenSpecial.entries.firstOrNull { it.badgeId == id })

    private fun montar(
        id: String,
        sonido: SonidoGrabado = SonidoGrabado(),
        tacto: TactoGrabado = TactoGrabado(),
        modo: ModoLogro = ModoLogro.NUEVO,
    ) {
        rule.setContent {
            ConSistema(sonido, tacto) { HojaLogro(true, logro(id), modo, alVerEnJardin = {}, alCerrar = {}) }
        }
    }

    private fun textoDe(tag: String) = rule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun estiloDe(tag: String): TextLayoutResult {
        val resultados = mutableListOf<TextLayoutResult>()
        textoDe(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.single()
    }

    @Test
    fun `the reward row has 52 dp image, 16 sp bold line and 14 sp second line`() {
        montar("streak-7")
        textoDe("fila-premio").assertExists()
        val imagen = textoDe("store-thumb-head-flor").fetchSemanticsNode().boundsInRoot
        with(rule.density) {
            assertEquals(52.dp.toPx(), imagen.width, 0.5f)
            assertEquals(52.dp.toPx(), imagen.height, 0.5f)
        }
        val linea = estiloDe("fila-premio-linea").layoutInput.style
        assertEquals(16f, linea.fontSize.value, 0.01f)
        assertEquals(FontWeight.Bold, linea.fontWeight)
        val debajo = estiloDe("fila-premio-debajo").layoutInput.style
        assertEquals(14f, debajo.fontSize.value, 0.01f)
        assertEquals(FontWeight.Medium, debajo.fontWeight)
    }

    // El `testTag` de la fila va dentro de su relleno: el ancho del nicho (que ocupa todo el ancho de la hoja) menos el de la
    // fila da los dos rellenos laterales. El de 10 dp de arriba y abajo no se puede medir por semántica.
    @Test
    fun `the reward row has 12 dp of side padding around its 52 dp image`() {
        montar("streak-7")
        val hoja = textoDe("logro-hornacina").fetchSemanticsNode().boundsInRoot
        val fila = textoDe("fila-premio").fetchSemanticsNode().boundsInRoot
        val imagen = textoDe("store-thumb-head-flor").fetchSemanticsNode().boundsInRoot
        with(rule.density) {
            assertEquals(12.dp.toPx(), fila.left - hoja.left, 0.5f)
            assertEquals(24.dp.toPx(), hoja.width - fila.width, 0.5f)
            assertEquals(0f, imagen.left - fila.left, 0.5f)
            assertEquals(52.dp.toPx(), imagen.height, 0.5f)
            assertEquals((fila.height - imagen.height) / 2f, imagen.top - fila.top, 0.5f)
        }
    }

    private fun comprobar(
        id: String,
        linea: String,
        debajo: String,
    ) {
        montar(id)
        textoDe("fila-premio-linea").assertExists()
        rule.onNodeWithText(linea, useUnmergedTree = true).assertExists()
        rule.onNodeWithText(debajo, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the flower row says it in Spanish`() = comprobar("streak-7", "Tienes la flor.", "La encuentras en la tienda, en Cabeza.")

    @Test
    fun `the ember scarf row says it in Spanish`() =
        comprobar("streak-30", "Tienes el pañuelo brasa.", "Lo encuentras en la tienda, en Cuello.")

    @Test
    fun `the crown row says it in Spanish`() = comprobar("streak-100", "Tienes la corona.", "La encuentras en la tienda, en Cabeza.")

    @Test
    fun `the gold color row says it in Spanish`() =
        comprobar("streak-365", "Tienes el color dorado.", "Lo encuentras en la tienda, en Colores.")

    @Test
    fun `the stone path row says it in Spanish`() = comprobar("first-week", "Tienes el caminito.", "Ya está en tu jardín.")

    @Test
    fun `the pond row says it in Spanish`() = comprobar("perfect-month", "Tienes el estanque.", "Ya está en tu jardín.")

    @Test
    fun `the mushrooms row`() = comprobar("resurrection", "Tienes las setas.", "Ya está en tu jardín.")

    @Test
    fun `the big cherry row`() = comprobar("perfect-days-100", "Tienes el cerezo grande.", "Ya está en tu jardín.")

    @Test
    @Config(qualifiers = "en-w411dp-h891dp")
    fun `the flower row says it in English`() = comprobar("streak-7", "You have the flower.", "Find it in the store, under Head.")

    @Test
    @Config(qualifiers = "en-w411dp-h891dp")
    fun `the ember scarf row says it in English`() =
        comprobar("streak-30", "You have the ember scarf.", "Find it in the store, under Neck.")

    @Test
    @Config(qualifiers = "en-w411dp-h891dp")
    fun `the crown row says it in English`() = comprobar("streak-100", "You have the crown.", "Find it in the store, under Head.")

    @Test
    @Config(qualifiers = "en-w411dp-h891dp")
    fun `the gold color row says it in English`() =
        comprobar("streak-365", "You have the gold color.", "Find it in the store, under Colors.")

    @Test
    @Config(qualifiers = "en-w411dp-h891dp")
    fun `a garden special row says it in English`() = comprobar("first-week", "You have the stone path.", "It's already in your garden.")

    @Test
    fun `Primera llama shows the flower first and the lantern is planted after`() {
        montar("streak-7")
        rule.onNodeWithText("Tienes la flor.", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("logro-especial", useUnmergedTree = true).assertDoesNotExist()
        // El farolillo llega como un momento propio, después del logro (G14).
        val logro = Momento.Logro("streak-7", 1L, 1L)
        val farolillo = Momento.Especial(GardenSpecial.LANTERN, 1L)
        assertTrue(logro.orden < farolillo.orden)
    }

    @Test
    fun `the sheet plays LOGRO, CLICK and CONFIRM`() {
        val sonido = SonidoGrabado()
        val tacto = TactoGrabado()
        montar("streak-7", sonido, tacto)
        rule.mainClock.advanceTimeBy(1_500)
        assertTrue(Snd.LOGRO in sonido.snds())
        assertTrue(Haptica.CLICK in tacto.hapticas)
        rule.onNodeWithText("Seguir").performClick()
        assertTrue(Haptica.CONFIRM in tacto.hapticas)
    }

    @Test
    fun `consult mode keeps the planted line and has no reward row`() {
        montar("streak-7", modo = ModoLogro.CONSULTA)
        textoDe("fila-premio").assertDoesNotExist()
        textoDe("logro-especial").assertExists()
    }

    @Test
    fun `a badge with no prize has no row`() {
        montar("first-habit")
        textoDe("fila-premio").assertDoesNotExist()
    }

    @Test
    fun `the new sheet has the 184 dp niche, a 164 dp emblem and the Logro desbloqueado label`() {
        montar("streak-7")
        rule.onNodeWithText("Logro desbloqueado", useUnmergedTree = true).assertExists()
        val nicho = textoDe("logro-hornacina").fetchSemanticsNode().boundsInRoot
        val emblema = textoDe("logro-emblema").fetchSemanticsNode().boundsInRoot
        with(rule.density) {
            assertEquals(184.dp.toPx(), nicho.height, 1f)
            assertEquals(164.dp.toPx(), emblema.height, 1f)
            assertEquals(164.dp.toPx(), emblema.width, 1f)
        }
        val etiqueta = estiloDe("logro-etiqueta").layoutInput.style
        assertEquals(14f, etiqueta.fontSize.value, 0.01f)
        assertEquals(FontWeight.Bold, etiqueta.fontWeight)
    }

    @Test
    fun `a reward sheet only offers Seguir`() {
        montar("streak-7")
        rule.onNodeWithText("Seguir").assertExists()
        rule.onNodeWithText("Verlo en el jardín").assertDoesNotExist()
    }

    @Test
    fun `the consult sheet keeps its 124 dp emblem`() {
        montar("streak-7", modo = ModoLogro.CONSULTA)
        rule.onNodeWithText("Logro desbloqueado", useUnmergedTree = true).assertDoesNotExist()
        with(rule.density) { assertEquals(124.dp.toPx(), textoDe("logro-emblema").fetchSemanticsNode().boundsInRoot.height, 1f) }
    }
}
