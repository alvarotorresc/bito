package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.ui.sistema.ConSistema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BotonTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `a switched off button still answers and explains`() {
        var principal = 0
        var explica = 0
        rule.setContent { ConSistema { Boton("Crear hábito", { principal++ }, apagado = true, alPulsarApagado = { explica++ }) } }
        rule.onNodeWithText("Crear hábito").performClick()
        assertEquals(0, principal)
        assertEquals(1, explica)
    }

    @Test
    fun `sheet buttons follow D12, soft exit above, main below and wide`() {
        rule.setContent { ConSistema { BotonesHoja(principal = "Guardar", alPrincipal = {}, suave = "Ahora no") } }
        val suave = rule.onNodeWithText("Ahora no").getBoundsInRoot()
        val principal = rule.onNodeWithText("Guardar").getBoundsInRoot()
        assertTrue(suave.bottom <= principal.top)
    }
}
