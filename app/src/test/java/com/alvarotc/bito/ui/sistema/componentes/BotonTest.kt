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

    private fun estiloDelTexto(texto: String): androidx.compose.ui.text.TextStyle {
        val resultado = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        rule.onNodeWithText(texto).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult]
            .action?.invoke(resultado)
        return resultado.single().layoutInput.style
    }

    @Test
    fun `the mock pill is dark with light letters at 14 sp and 16 sp when it fills the row`() {
        lateinit var colores: com.alvarotc.bito.ui.sistema.BitoColores
        rule.setContent {
            ConSistema {
                colores = com.alvarotc.bito.ui.sistema.BitoTema.colores
                Boton("Normal", {}, estilo = EstiloBoton.PRINCIPAL)
                Boton("Pildora", {}, estilo = EstiloBoton.PRINCIPAL, pildoraMock = true)
                Boton("Llena", {}, estilo = EstiloBoton.PRINCIPAL, llena = true, pildoraMock = true)
                Boton("Fantasma", {}, estilo = EstiloBoton.FANTASMA, pildoraMock = true)
            }
        }
        assertEquals(colores.tinta, estiloDelTexto("Normal").color)
        assertEquals(16f, estiloDelTexto("Normal").fontSize.value, 0f)
        assertEquals(colores.tintaClara, estiloDelTexto("Pildora").color)
        assertEquals(14f, estiloDelTexto("Pildora").fontSize.value, 0f)
        assertEquals(colores.tintaClara, estiloDelTexto("Llena").color)
        assertEquals(16f, estiloDelTexto("Llena").fontSize.value, 0f)
        assertEquals(colores.tinta, estiloDelTexto("Fantasma").color)
        assertEquals(14f, estiloDelTexto("Fantasma").fontSize.value, 0f)
    }
}
