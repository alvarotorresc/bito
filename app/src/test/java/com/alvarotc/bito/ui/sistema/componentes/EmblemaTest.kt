package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
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
@Config(sdk = [35])
class EmblemaTest {
    @get:Rule val rule = createComposeRule()

    /** Con el reloj parado el cambio de estado no avisa solo: se envía a mano. */
    private fun celebrarYa(estado: MutableState<Boolean>) =
        rule.runOnUiThread {
            estado.value = true
            Snapshot.sendApplyNotifications()
        }

    @Test
    fun `celebrating plays the badge sound once and clicks at 600 ms`() {
        val sonido = SonidoGrabado()
        val tacto = TactoGrabado()
        val celebrar = mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent { ConSistema(sonido, tacto) { Emblema(FamiliaLogro.RACHAS, 1f, encendido = true, celebrar = celebrar.value) } }
        celebrarYa(celebrar)
        rule.mainClock.advanceTimeBy(100)
        assertEquals(listOf(Snd.LOGRO), sonido.snds())
        assertTrue(Haptica.CLICK !in tacto.hapticas)
        rule.mainClock.advanceTimeBy(600)
        assertTrue(Haptica.CLICK in tacto.hapticas)
    }

    @Test
    fun `reduced motion keeps the same sound and click`() {
        val sonido = SonidoGrabado()
        val tacto = TactoGrabado()
        val celebrar = mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            ConSistema(
                sonido,
                tacto,
                reducido = true,
            ) { Emblema(FamiliaLogro.CONSTANCIA, 0.5f, encendido = true, celebrar = celebrar.value) }
        }
        celebrarYa(celebrar)
        rule.mainClock.advanceTimeBy(700)
        assertEquals(listOf(Snd.LOGRO), sonido.snds())
        assertTrue(Haptica.CLICK in tacto.hapticas)
    }

    @Test
    fun `a badge that is not lit makes no sound`() {
        val sonido = SonidoGrabado()
        rule.setContent { ConSistema(sonido) { Emblema(FamiliaLogro.MOMENTOS, 0.3f, encendido = false) } }
        rule.waitForIdle()
        assertTrue(sonido.snds().isEmpty())
    }
}
