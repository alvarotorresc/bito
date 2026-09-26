package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.BreathingStats.Tally
import com.alvarotc.bito.domain.model.BreathingSession
import com.alvarotc.bito.domain.model.LogicalDay
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El contador del final (spec §3.3). La semana es la de las tareas: ISO de lunes a domingo sobre
 * dias logicos. El dia de cada sesion se DERIVA de startedAtMillis con el corte y la zona que se
 * le pasan — desviacion acotada de E7, aceptada porque el contador no decide nada.
 */
class BreathingStatsTest {
    private fun week(
        sessions: List<BreathingSession>,
        today: LogicalDay = TODAY,
        cutoffMinutes: Int = 0,
    ): Tally = BreathingStats.thisWeek(sessions, today, cutoffMinutes, testZone)

    @Test
    fun `every day from monday to sunday of today's week counts, the sunday before does not`() {
        val sessions =
            (THIS_MONDAY..THIS_SUNDAY).map { breathingSession(it, durationSeconds = 10) } +
                breathingSession(LAST_SUNDAY, durationSeconds = 10)

        assertEquals(Tally(sessions = 7, seconds = 70), week(sessions))
    }

    @Test
    fun `a sunday session counts in its week and the next monday does not`() {
        val sessions =
            listOf(
                breathingSession(THIS_SUNDAY, durationSeconds = 60),
                breathingSession(THIS_SUNDAY + 1, durationSeconds = 60),
            )

        assertEquals(Tally(sessions = 1, seconds = 60), week(sessions, today = THIS_SUNDAY))
    }

    @Test
    fun `the day cutoff moves a monday 01,00 session back to the previous sunday`() {
        val lateNight = listOf(breathingSession(THIS_SUNDAY + 1, hour = 1, durationSeconds = 60))

        assertEquals(1, week(lateNight, today = THIS_SUNDAY, cutoffMinutes = 180).sessions)
        assertEquals(0, week(lateNight, today = THIS_SUNDAY, cutoffMinutes = 0).sessions)
    }

    @Test
    fun `complete and incomplete sessions count alike`() {
        val sessions =
            listOf(
                breathingSession(TODAY, durationSeconds = 120, completed = true),
                breathingSession(TODAY, durationSeconds = 25, completed = false),
            )

        assertEquals(Tally(sessions = 2, seconds = 145), week(sessions))
    }

    @Test
    fun `minutes round to the nearest with a floor of one when there was anything`() {
        assertEquals(0, Tally(sessions = 0, seconds = 0).minutes)
        assertEquals(1, Tally(sessions = 1, seconds = 40).minutes)
        assertEquals(1, Tally(sessions = 1, seconds = 89).minutes)
        assertEquals(2, Tally(sessions = 1, seconds = 90).minutes)
        assertEquals(2, Tally(sessions = 2, seconds = 149).minutes)
    }

    @Test
    fun `allTime sums every stored session whatever its week`() {
        val sessions =
            listOf(
                breathingSession(THREE_WEEKS_AGO_MONDAY, durationSeconds = 114),
                breathingSession(LAST_WEDNESDAY, durationSeconds = 30, completed = false),
                breathingSession(TODAY, durationSeconds = 128),
            )

        assertEquals(Tally(sessions = 3, seconds = 272), BreathingStats.allTime(sessions))
    }

    @Test
    fun `an empty history is zero and zero`() {
        assertEquals(Tally(0, 0), week(emptyList()))
        assertEquals(Tally(0, 0), BreathingStats.allTime(emptyList()))
    }
}
