package com.alvarotc.bito.ui.sistema.sonido

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RampaTest {
    @Test
    fun `a 400 ms ramp moves in 16 ms steps without jumps and ends on target`() {
        val pasos = Rampa.pasos(0f, 0.8f, ms = 400)
        assertEquals(25, pasos.size)
        assertEquals(0.8f, pasos.last(), 0f)
        val maximo = 0.8f * 16 / 400 + 1e-6f
        assertTrue((listOf(0f) + pasos).zipWithNext().all { (a, b) -> abs(b - a) <= maximo })
    }
}
