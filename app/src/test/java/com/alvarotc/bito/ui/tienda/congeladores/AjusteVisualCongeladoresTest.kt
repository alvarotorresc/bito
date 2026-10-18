package com.alvarotc.bito.ui.tienda.congeladores

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Medidas de la pestaña Congeladores contra el DOM del mock (`tienda-congeladores.html`, 412 px): el interlineado cuenta entero. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AjusteVisualCongeladoresTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `the hint keeps the full 19_6 dp line height on both lines`() {
        rule.setContent {
            ConSistema {
                Box(Modifier.width(412.dp)) {
                    CatalogoCongeladores(Personality.NEUTRA, congeladores = 2, saldo = 140, precio = 100, alComprar = {})
                }
            }
        }
        val pista = rule.onNodeWithTag("tc-pista").getBoundsInRoot()
        // 2 líneas de 19,6 (a mdpi cada línea sube a píxel entero)
        assertEquals(39.2f, pista.height.value, 1.6f)
    }
}
