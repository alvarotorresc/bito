package com.alvarotc.bito.ui.tienda

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.tienda.habi.CabeceraRanura
import com.alvarotc.bito.ui.tienda.habi.MetaRanura
import com.alvarotc.bito.ui.tienda.habi.RanuraHabi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Medidas de la tienda contra el DOM del mock (`tienda-habi.html` en Chrome, 412 px de ancho): cada valor esperado sale de
 * `getBoundingClientRect` del mock, no de la lectura del CSS. Tolerancia de 1,6 dp: a mdpi cada línea sube a píxel entero.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AjusteVisualTiendaTest {
    @get:Rule val rule = createComposeRule()

    private fun linea(tag: String): Float {
        val r = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(r)
        return r.single().getLineBaseline(0)
    }

    @Test
    fun `the slot header is 45_8 dp high and name and meta share a baseline`() {
        rule.setContent {
            ConSistema {
                Box(Modifier.width(412.dp)) {
                    CabeceraRanura(RanuraHabi.CABEZA, MetaRanura.Piezas(7), Modifier)
                }
            }
        }
        val cabecera = rule.onNodeWithTag("ranura-cabeza").getBoundsInRoot()
        val meta = rule.onNodeWithTag("meta-cabeza").getBoundsInRoot()
        assertEquals(45.8f, cabecera.height.value, TOL)
        // línea base de la meta a 31 dp del borde de arriba, como el nombre (`.nom`/`.meta` a 31 en el mock)
        assertEquals(31f, meta.top.value - cabecera.top.value + linea("meta-cabeza") / rule.density.density, TOL)
    }

    @Test
    fun `the light labels sit with the baseline 43_5 dp under the top of the row buttons`() {
        rule.setContent {
            ConSistema {
                val estado = rememberLuzEstado(LuzTienda.DIA)
                Box(Modifier.width(412.dp)) { SelectorLuz(luz = LuzTienda.DIA, estado = estado, alElegir = {}) }
            }
        }
        rule.waitForIdle()
        val boton = rule.onNodeWithText("Día", useUnmergedTree = true).getBoundsInRoot()
        val resultados = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(
            "Día",
            useUnmergedTree = true,
        ).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        val base = boton.top.value + resultados.single().getLineBaseline(0) / rule.density.density
        val fila = rule.onNodeWithTag("tluz").getBoundsInRoot().top.value
        assertEquals(43.5f, base - fila, TOL)
    }

    @Test
    fun `the balance is the capsule of the mock 36 dp high with the 24 dp coin`() {
        rule.setContent { ConSistema { Box(Modifier.width(200.dp)) { SaldoConRodillo(140) } } }
        val saldo = rule.onNodeWithTag("saldo").getBoundsInRoot()
        assertEquals(36f, saldo.height.value, 0.5f)
        assertEquals(77f, saldo.width.value, 4f)
    }

    private companion object {
        const val TOL = 1.6f
    }
}
