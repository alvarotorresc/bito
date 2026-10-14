package com.alvarotc.bito.ui.sistema.sonido

import org.junit.Assert.assertEquals
import org.junit.Test

class GananciaTest {
    private val todo = AjustesSonido(general = true, habi = true, registro = true, volHabi = 8, volEfectos = 7, volAmbiente = 6)

    @Test
    fun `the perceptual curve caps at one`() {
        assertEquals(0f, Ganancia.deNivel(0), 0f)
        assertEquals(0.9797f, Ganancia.deNivel(8), 0.001f)
        assertEquals(1f, Ganancia.deNivel(9), 0f)
        assertEquals(1f, Ganancia.deNivel(10), 0f)
    }

    @Test
    fun `the general switch silences everything first`() {
        Snd.entries.forEach { assertEquals(0f, Ganancia.de(it, 1f, todo.copy(general = false), timbreNormal = true), 0f) }
    }

    @Test
    fun `vibrate or silent ringer silences every channel even with sound on`() {
        Snd.entries.forEach { assertEquals(0f, Ganancia.de(it, 1f, todo, timbreNormal = false), 0f) }
    }

    @Test
    fun `Habi sounds off silences only the Habi channel`() {
        val a = todo.copy(habi = false)
        assertEquals(0f, Ganancia.de(Snd.HABI, 1f, a, true), 0f)
        assertEquals(Ganancia.deNivel(7), Ganancia.de(Snd.BROTE, 1f, a, true), 0f)
    }

    @Test
    fun `log sound off silences gorgoteo, llegada and vaciar only`() {
        val a = todo.copy(registro = false)
        listOf(Snd.GORGOTEO, Snd.LLEGADA, Snd.VACIAR).forEach { assertEquals(0f, Ganancia.de(it, 1f, a, true), 0f) }
        assertEquals(Ganancia.deNivel(7), Ganancia.de(Snd.BROTE, 1f, a, true), 0f)
    }

    @Test
    fun `a channel at zero is silent and intensity scales`() {
        assertEquals(0f, Ganancia.de(Snd.AMBIENTE, 1f, todo.copy(volAmbiente = 0), true), 0f)
        assertEquals(Ganancia.deNivel(7) * 0.6f, Ganancia.de(Snd.MONEDA, 0.6f, todo, true), 0.0001f)
    }
}
