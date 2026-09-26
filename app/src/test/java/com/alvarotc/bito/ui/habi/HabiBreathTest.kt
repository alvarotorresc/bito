package com.alvarotc.bito.ui.habi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El contrato del gancho externo (spec §6), fijado con funciones puras: lo que llega al
 * graphicsLayer (escala y elevacion) y a drawHabi (respiracion y parpadeo).
 */
class HabiBreathTest {
    private val delta = 1e-5f

    @Test
    fun `amplitude one reproduces today's breathing formula exactly`() {
        for (v in listOf(0f, 0.25f, 0.5f, 1f)) {
            val scales = breathScales(v, 1f)
            assertEquals(1f + 0.022f * v, scales.scaleY, delta)
            assertEquals(1f - 0.022f * 0.5f * v, scales.scaleX, delta)
            assertEquals(v, scales.liftFactor, delta)
        }
    }

    @Test
    fun `the guided amplitude triples the idle delta`() {
        val full = breathScales(1f, GUIDED_BREATH_AMPLITUDE)

        assertEquals(1.066f, full.scaleY, delta)
        assertEquals(0.967f, full.scaleX, delta)
        assertEquals(3f, full.liftFactor, delta)
    }

    @Test
    fun `a full breath is taller than an empty one`() {
        assertTrue(breathScales(1f, GUIDED_BREATH_AMPLITUDE).scaleY > breathScales(0f, GUIDED_BREATH_AMPLITUDE).scaleY)
        assertEquals(1f, breathScales(0f, GUIDED_BREATH_AMPLITUDE).scaleY, delta)
    }

    @Test
    fun `without a hook the idle breath and blinks pass through untouched`() {
        assertEquals(0.37f, guidedBreathValue(null, idle = 0.37f), delta)
        assertEquals(0.8f, guidedBlinkValue(null, idleBlink = 0.2f, tapBlink = 0.8f, lidRelease = 0.1f), delta)
        assertEquals(0.6f, guidedBlinkValue(null, idleBlink = 0f, tapBlink = 0f, lidRelease = 0.6f), delta)
    }

    @Test
    fun `with a hook the external phase and the held lid win over every idle channel`() {
        val breath = HabiBreath(fill = 0.42f)

        assertEquals(0.42f, guidedBreathValue(breath, idle = 0.9f), delta)
        assertEquals(GUIDED_EYE_CLOSURE, guidedBlinkValue(breath, idleBlink = 1f, tapBlink = 1f, lidRelease = 0f), delta)
    }

    @Test
    fun `the hook defaults to triple amplitude and a nine-tenths slit`() {
        val breath = HabiBreath(fill = 0f)

        assertEquals(3f, breath.amplitude, delta)
        assertEquals(0.9f, breath.eyeClosure, delta)
    }
}
