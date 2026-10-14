package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "es")
class ElegirConAguaTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `choosing fills the ring, ticks, and is a radio`() {
        val sonido = SonidoGrabado()
        var elegida by mutableStateOf("neutra")
        rule.setContent {
            ConSistema(sonido) {
                ElegirConAgua(listOf(OpcionAgua("neutra", "Neutra"), OpcionAgua("sargento", "Sargento")), elegida, { elegida = it })
            }
        }
        rule.onNodeWithText("Sargento").performClick()
        rule.onNodeWithText("Sargento").assertIsSelected()
        rule.onNodeWithText("Neutra").assertIsNotSelected()
        assertEquals(listOf(Snd.TIC), sonido.snds())
    }
}
