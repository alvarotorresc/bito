package com.alvarotc.bito.ui.habi

import com.alvarotc.bito.ui.sistema.SonidoGrabado
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Test

class HabiSoundsTest {
    @Test
    fun `each Habi cue plays its catalog sound through the engine`() {
        val motor = SonidoGrabado()
        val sounds = HabiSounds(motor)
        HabiSound.entries.forEach(sounds::play)
        assertEquals(
            listOf(Snd.HABI, Snd.DIA_COMPLETO, Snd.VACIAR, Snd.GASTAR, Snd.GORGOTEO, Snd.HABI),
            motor.snds(),
        )
    }
}
