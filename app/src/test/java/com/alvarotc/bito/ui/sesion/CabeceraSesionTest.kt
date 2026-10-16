package com.alvarotc.bito.ui.sesion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es-w412dp-h915dp")
class CabeceraSesionTest {
    @get:Rule
    val compose = createComposeRule()

    private val sonido = SonidoGrabado()
    private val tacto = TactoGrabado()
    private var musica by mutableStateOf<Boolean?>(false)
    private var atrases = 0
    private val cambios = mutableListOf<Boolean>()

    private fun montar(
        atras: AccionAtras? = AccionAtras("Volver") { atrases++ },
        saldo: Int? = 140,
    ) {
        compose.setContent {
            ConSistema(sonido, tacto) {
                CabeceraSesion(atras, musica, { on ->
                    cambios += on
                    musica = on
                }, saldo)
            }
        }
    }

    @Test
    fun `everything shows when everything is given`() {
        montar()
        compose.onNodeWithTag("ses-atras").assertIsDisplayed()
        compose.onNodeWithTag("ses-musica").assertIsDisplayed()
        compose.onNodeWithTag("ses-saldo").assertIsDisplayed()
    }

    @Test
    fun `null parameters leave their piece out`() {
        musica = null
        montar(atras = null, saldo = null)
        compose.onNodeWithTag("ses-atras").assertDoesNotExist()
        compose.onNodeWithTag("ses-musica").assertDoesNotExist()
        compose.onNodeWithTag("ses-saldo").assertDoesNotExist()
    }

    @Test
    fun `round buttons measure 44 dp, 8 dp from the top and 12 dp from the sides`() {
        montar()
        compose.onNodeWithTag("ses-atras").assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
        compose.onNodeWithTag("ses-musica").assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
        compose.onNodeWithTag("ses-saldo").assertHeightIsEqualTo(36.dp)
        val atras = compose.onNodeWithTag("ses-atras").getBoundsInRoot()
        assertEquals(12.dp, atras.left)
        // Cabecera de 64 dp a 8 dp de arriba, botones centrados: 8 + (64 − 44) / 2.
        assertEquals(18.dp, atras.top)
        val saldo = compose.onNodeWithTag("ses-saldo").getBoundsInRoot()
        assertEquals(400.dp, saldo.right)
        assertEquals(22.dp, saldo.top)
    }

    @Test
    fun `back carries its label and goes back`() {
        montar(atras = AccionAtras("Saltar y volver a preparar") { atrases++ })
        compose.onNodeWithTag("ses-atras").assertContentDescriptionEquals("Saltar y volver a preparar").performClick()
        assertEquals(1, atrases)
    }

    @Test
    fun `music is a switch with the labels of the mock and its sound`() {
        montar()
        compose
            .onNodeWithTag("ses-musica")
            .assertContentDescriptionEquals("Poner música de fondo")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .performClick()

        assertEquals(listOf(true), cambios)
        assertEquals(listOf(Snd.INTERRUPTOR_ON), sonido.snds())
        assertEquals(listOf(Haptica.TICK), tacto.hapticas)
        compose
            .onNodeWithTag("ses-musica")
            .assertContentDescriptionEquals("Quitar la música")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .performClick()

        assertEquals(listOf(true, false), cambios)
        assertEquals(listOf(Snd.INTERRUPTOR_ON, Snd.INTERRUPTOR_OFF), sonido.snds())
        assertEquals(listOf(Haptica.TICK, Haptica.TICK), tacto.hapticas)
    }

    @Test
    fun `the balance reads the bitos`() {
        montar()
        compose.onNodeWithTag("ses-saldo").assertContentDescriptionEquals("Tus bitos", "140 bitos")
    }

    @Test
    fun `the balance is labelled Tus bitos for a screen reader`() {
        montar()
        compose
            .onNodeWithTag("ses-saldo")
            .assert(
                SemanticsMatcher("lleva la etiqueta «Tus bitos»") {
                    SemanticsProperties.ContentDescription in it.config && "Tus bitos" in it.config[SemanticsProperties.ContentDescription]
                },
            )
    }

    @Test
    fun `the sky fade sits under the header`() {
        montar()
        compose.onNodeWithTag("ses-cielo").assertHeightIsEqualTo(96.dp).assertWidthIsEqualTo(412.dp)
    }
}
