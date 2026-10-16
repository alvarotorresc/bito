package com.alvarotc.bito.sesion

import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La tabla de las fronteras, que comparten el motor y el receptor (revisión final, M7). */
class SonidoFronteraTest {
    private val respirar = Tramo.Respirar()
    private val foco = Tramo.Foco(bloque = 1, minutos = 25)
    private val descanso = Tramo.Descanso(trasBloque = 1, largo = false, minutos = 5)

    @Test
    fun `breathing to focus drops away, quiets the air and brings the background in`() {
        val s = SonidoFrontera.de(respirar, foco)
        assertEquals(Snd.ALEJAR, s.snd)
        assertEquals(Haptica.TICK, s.haptica)
        assertEquals(AccionFondo.SONAR, s.fondo)
        assertTrue(s.pararAire)
    }

    @Test
    fun `break to focus sprouts and rises with the background`() {
        val s = SonidoFrontera.de(descanso, foco)
        assertEquals(Snd.BROTE, s.snd)
        assertEquals(Haptica.RISE, s.haptica)
        assertEquals(AccionFondo.SONAR, s.fondo)
        assertFalse(s.pararAire)
    }

    @Test
    fun `focus to break rings and silences the background`() {
        val s = SonidoFrontera.de(foco, descanso)
        assertEquals(Snd.CAMPANILLA, s.snd)
        assertEquals(Haptica.CLICK, s.haptica)
        assertEquals(AccionFondo.CALLAR, s.fondo)
    }

    @Test
    fun `the end rings, confirms and silences`() {
        val s = SonidoFrontera.de(foco, null)
        assertEquals(Snd.CAMPANILLA, s.snd)
        assertEquals(Haptica.CONFIRM, s.haptica)
        assertEquals(AccionFondo.CALLAR, s.fondo)
        assertTrue(SonidoFrontera.de(respirar, null).pararAire)
    }
}
