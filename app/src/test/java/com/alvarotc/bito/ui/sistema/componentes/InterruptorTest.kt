package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.alvarotc.bito.ui.sistema.ConSistema
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.TactoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InterruptorTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `toggling plays on and off with a tick and exposes a switch`() {
        val sonido = SonidoGrabado()
        val tacto = TactoGrabado()
        var on by mutableStateOf(false)
        rule.setContent { ConSistema(sonido, tacto) { Interruptor(on, { on = it }, descripcion = "Sonido") } }
        rule.onNodeWithContentDescription("Sonido").assertIsOff().performClick()
        rule.onNodeWithContentDescription("Sonido").assertIsOn()
        rule.onNodeWithContentDescription("Sonido").performClick()
        assertEquals(listOf(Snd.INTERRUPTOR_ON, Snd.INTERRUPTOR_OFF), sonido.snds())
        assertEquals(listOf(Haptica.TICK, Haptica.TICK), tacto.hapticas)
    }

    @Test
    fun `a disabled switch does not answer`() {
        var on by mutableStateOf(false)
        rule.setContent { ConSistema { Interruptor(on, { on = it }, habilitado = false, descripcion = "Sonido") } }
        rule.onNodeWithContentDescription("Sonido").performClick()
        assertFalse(on)
    }
}
