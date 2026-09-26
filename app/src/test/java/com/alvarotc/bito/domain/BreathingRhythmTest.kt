package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.BreathingRhythm.Step
import com.alvarotc.bito.domain.model.BreathPhase.EXHALE
import com.alvarotc.bito.domain.model.BreathPhase.HOLD
import com.alvarotc.bito.domain.model.BreathPhase.INHALE
import com.alvarotc.bito.domain.model.BreathingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * El motor de ritmo (spec §3.2). Puro y determinista: el que llama le pasa el tiempo. Regla
 * unica: nunca se corta un ciclo, asi que cada modo dura un numero entero de ciclos.
 */
class BreathingRhythmTest {
    private val delta = 1e-3f

    @Test
    fun `calm is four in and six out, twelve cycles, two minutes`() {
        assertEquals(listOf(Step(INHALE, 4), Step(EXHALE, 6)), BreathingRhythm.steps(BreathingMode.CALM))
        assertEquals(12, BreathingRhythm.cycles(BreathingMode.CALM))
        assertEquals(120_000L, BreathingRhythm.totalMillis(BreathingMode.CALM))
    }

    @Test
    fun `sleep is four seven eight, six cycles, one fifty-four`() {
        assertEquals(listOf(Step(INHALE, 4), Step(HOLD, 7), Step(EXHALE, 8)), BreathingRhythm.steps(BreathingMode.SLEEP))
        assertEquals(6, BreathingRhythm.cycles(BreathingMode.SLEEP))
        assertEquals(114_000L, BreathingRhythm.totalMillis(BreathingMode.SLEEP))
    }

    @Test
    fun `focus is the four-four-four-four box, eight cycles, two oh eight`() {
        assertEquals(
            listOf(Step(INHALE, 4), Step(HOLD, 4), Step(EXHALE, 4), Step(HOLD, 4)),
            BreathingRhythm.steps(BreathingMode.FOCUS),
        )
        assertEquals(8, BreathingRhythm.cycles(BreathingMode.FOCUS))
        assertEquals(128_000L, BreathingRhythm.totalMillis(BreathingMode.FOCUS))
    }

    @Test
    fun `every step boundary changes the step index and the phase`() {
        for (mode in BreathingMode.entries) {
            var boundary = 0L
            var index = 0
            repeat(BreathingRhythm.cycles(mode)) {
                for (step in BreathingRhythm.steps(mode)) {
                    val at = BreathingRhythm.at(mode, boundary)
                    assertEquals("$mode @ $boundary", index, at.stepIndex)
                    assertEquals("$mode @ $boundary", step.phase, at.phase)
                    if (boundary > 0) {
                        val before = BreathingRhythm.at(mode, boundary - 1)
                        assertEquals("$mode @ ${boundary - 1}", index - 1, before.stepIndex)
                        assertNotEquals("$mode @ $boundary", before.phase, at.phase)
                    }
                    boundary += step.seconds * 1_000L
                    index++
                }
            }
        }
    }

    @Test
    fun `cycles are one-based and count up`() {
        assertEquals(1, BreathingRhythm.at(BreathingMode.CALM, 0).cycle)
        assertEquals(1, BreathingRhythm.at(BreathingMode.CALM, 9_999).cycle)
        assertEquals(2, BreathingRhythm.at(BreathingMode.CALM, 10_000).cycle)
        assertEquals(12, BreathingRhythm.at(BreathingMode.CALM, 119_999).cycle)
        assertEquals(6, BreathingRhythm.at(BreathingMode.SLEEP, 0).totalCycles)
    }

    @Test
    fun `an inhale fills from zero to one`() {
        assertEquals(0f, BreathingRhythm.at(BreathingMode.CALM, 0).fill, delta)
        assertTrue(BreathingRhythm.at(BreathingMode.CALM, 3_999).fill > 0.999f)
    }

    @Test
    fun `half an inhale or half an exhale is exactly half full`() {
        assertEquals(0.5f, BreathingRhythm.at(BreathingMode.CALM, 2_000).fill, delta)
        assertEquals(0.5f, BreathingRhythm.at(BreathingMode.CALM, 4_000 + 3_000).fill, delta)
        assertEquals(0.5f, BreathingRhythm.at(BreathingMode.SLEEP, 11_000 + 4_000).fill, delta)
    }

    @Test
    fun `an exhale starts full`() {
        assertEquals(1f, BreathingRhythm.at(BreathingMode.CALM, 4_000).fill, delta)
    }

    @Test
    fun `the hold after an inhale stays full`() {
        for (t in listOf(4_000L, 7_000L, 10_999L)) {
            assertEquals("sleep @ $t", 1f, BreathingRhythm.at(BreathingMode.SLEEP, t).fill, delta)
        }
        assertEquals(1f, BreathingRhythm.at(BreathingMode.FOCUS, 6_000).fill, delta)
    }

    @Test
    fun `the empty hold of the box stays empty`() {
        for (t in listOf(12_000L, 14_000L, 15_999L)) {
            assertEquals("focus @ $t", 0f, BreathingRhythm.at(BreathingMode.FOCUS, t).fill, delta)
        }
    }

    @Test
    fun `fill never jumps between two samples ten millis apart`() {
        for (mode in BreathingMode.entries) {
            var t = 0L
            var previous = BreathingRhythm.at(mode, 0).fill
            while (t < BreathingRhythm.totalMillis(mode)) {
                t += 10
                val current = BreathingRhythm.at(mode, t).fill
                if (!BreathingRhythm.at(mode, t).finished) {
                    assertTrue("$mode jumps at $t: $previous -> $current", abs(current - previous) < 0.01f)
                }
                previous = current
            }
        }
    }

    @Test
    fun `finished exactly at the total and not a millisecond before`() {
        for (mode in BreathingMode.entries) {
            val total = BreathingRhythm.totalMillis(mode)
            assertFalse("$mode", BreathingRhythm.at(mode, total - 1).finished)
            val end = BreathingRhythm.at(mode, total)
            assertTrue("$mode", end.finished)
            assertEquals(0f, end.fill, delta)
            assertEquals(0L, end.remainingMillis)
            assertEquals(BreathingRhythm.cycles(mode), end.cycle)
            assertTrue(BreathingRhythm.at(mode, total + 5_000).finished)
        }
    }

    @Test
    fun `the session ends on an exhale, or on the empty hold of the box`() {
        assertEquals(EXHALE, BreathingRhythm.at(BreathingMode.CALM, 120_000).phase)
        assertEquals(EXHALE, BreathingRhythm.at(BreathingMode.SLEEP, 114_000).phase)
        assertEquals(HOLD, BreathingRhythm.at(BreathingMode.FOCUS, 128_000).phase)
    }

    @Test
    fun `negative time reads as zero`() {
        assertEquals(BreathingRhythm.at(BreathingMode.CALM, 0), BreathingRhythm.at(BreathingMode.CALM, -500))
    }

    @Test
    fun `remaining seconds round up so the clock never shows zero mid-session`() {
        assertEquals(120_000L, BreathingRhythm.at(BreathingMode.CALM, 0).remainingMillis)
        assertEquals(120, BreathingRhythm.at(BreathingMode.CALM, 0).remainingSeconds)
        assertEquals(120, BreathingRhythm.at(BreathingMode.CALM, 1).remainingSeconds)
        assertEquals(119, BreathingRhythm.at(BreathingMode.CALM, 1_000).remainingSeconds)
        assertEquals(1, BreathingRhythm.at(BreathingMode.CALM, 119_001).remainingSeconds)
        assertEquals(0, BreathingRhythm.at(BreathingMode.CALM, 120_000).remainingSeconds)
    }
}
