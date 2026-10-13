package com.alvarotc.bito.ui.sistema

import com.alvarotc.bito.ui.sistema.sonido.Canal
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SndTest {
    @Test
    fun `there are 35 sounds with unique snd_ resource names`() {
        assertEquals(35, Snd.entries.size)
        assertEquals(35, Snd.entries.map { it.recurso }.toSet().size)
        assertTrue(Snd.entries.all { it.recurso.matches(Regex("snd_[a-z_]+")) })
    }

    @Test
    fun `only gorgoteo, llegada and vaciar obey the log sound toggle`() {
        assertEquals(setOf(Snd.GORGOTEO, Snd.LLEGADA, Snd.VACIAR), Snd.entries.filter { it.registro }.toSet())
    }

    @Test
    fun `channels follow the catalog`() {
        assertEquals(listOf(Snd.HABI), Snd.entries.filter { it.canal == Canal.HABI })
        assertEquals(
            setOf(Snd.ANOCHECER, Snd.ZOOM, Snd.AMBIENTE, Snd.FONDO_LLUVIA, Snd.FONDO_MARRON, Snd.FONDO_CAFE),
            Snd.entries.filter { it.canal == Canal.AMBIENTE }.toSet(),
        )
        assertEquals(setOf(Snd.FONDO_LLUVIA, Snd.FONDO_MARRON, Snd.FONDO_CAFE), Snd.entries.filter { it.bucle }.toSet())
    }
}
