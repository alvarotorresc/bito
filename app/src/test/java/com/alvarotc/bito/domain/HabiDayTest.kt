package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.HabiDayPhase
import com.alvarotc.bito.domain.model.HabiPose
import com.alvarotc.bito.domain.model.toPose
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Las siete filas de la tabla de verdad de la spec §6.2, más los bordes de la hora.
 * `REVIEW` es la hora de repaso por defecto (21:30 = 1290).
 */
class HabiDayTest {
    private val review = 21 * 60 + 30

    @Test
    fun `a sealed day sleeps whatever else is true`() {
        assertEquals(
            HabiDayPhase.ASLEEP,
            HabiDay.phaseOf(requirableCount = 3, doneCount = 0, sealed = true, minutesOfDay = 9 * 60, reviewTimeMinutes = review),
        )
        assertEquals(
            HabiDayPhase.ASLEEP,
            HabiDay.phaseOf(requirableCount = 0, doneCount = 0, sealed = true, minutesOfDay = 23 * 60, reviewTimeMinutes = review),
        )
    }

    @Test
    fun `a day with nothing requirable starts awake and waits at review time`() {
        assertEquals(
            HabiDayPhase.AWAKE,
            HabiDay.phaseOf(0, 0, sealed = false, minutesOfDay = 9 * 60, reviewTimeMinutes = review),
        )
        assertEquals(
            HabiDayPhase.WAITING,
            HabiDay.phaseOf(0, 0, sealed = false, minutesOfDay = 22 * 60, reviewTimeMinutes = review),
        )
    }

    @Test
    fun `work left before review time keeps her awake`() {
        assertEquals(
            HabiDayPhase.AWAKE,
            HabiDay.phaseOf(3, 1, sealed = false, minutesOfDay = 9 * 60, reviewTimeMinutes = review),
        )
    }

    @Test
    fun `work left at review time waits with you`() {
        assertEquals(
            HabiDayPhase.WAITING,
            HabiDay.phaseOf(3, 1, sealed = false, minutesOfDay = 22 * 60, reviewTimeMinutes = review),
        )
    }

    @Test
    fun `all done waits for the close at any hour`() {
        assertEquals(
            HabiDayPhase.WAITING,
            HabiDay.phaseOf(3, 3, sealed = false, minutesOfDay = 9 * 60, reviewTimeMinutes = review),
        )
        assertEquals(
            HabiDayPhase.WAITING,
            HabiDay.phaseOf(3, 3, sealed = false, minutesOfDay = 22 * 60, reviewTimeMinutes = review),
        )
    }

    @Test
    fun `exactly at review time already counts as review time`() {
        assertEquals(
            HabiDayPhase.WAITING,
            HabiDay.phaseOf(3, 1, sealed = false, minutesOfDay = review, reviewTimeMinutes = review),
        )
        assertEquals(
            HabiDayPhase.AWAKE,
            HabiDay.phaseOf(3, 1, sealed = false, minutesOfDay = review - 1, reviewTimeMinutes = review),
        )
    }

    @Test
    fun `a review time at midnight leaves the whole day waiting`() {
        assertEquals(
            HabiDayPhase.WAITING,
            HabiDay.phaseOf(3, 1, sealed = false, minutesOfDay = 0, reviewTimeMinutes = 0),
        )
        assertEquals(
            HabiDayPhase.WAITING,
            HabiDay.phaseOf(3, 1, sealed = false, minutesOfDay = 12 * 60, reviewTimeMinutes = 0),
        )
    }

    @Test
    fun `every phase maps to its own pose`() {
        assertEquals(HabiPose.STANDING, HabiDayPhase.AWAKE.toPose())
        assertEquals(HabiPose.WAITING, HabiDayPhase.WAITING.toPose())
        assertEquals(HabiPose.SLEEPING, HabiDayPhase.ASLEEP.toPose())
    }
}
