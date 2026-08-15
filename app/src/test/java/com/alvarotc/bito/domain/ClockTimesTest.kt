package com.alvarotc.bito.domain

import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

class ClockTimesTest {
    private val zone = ZoneId.of("Europe/Madrid")

    private fun millisAt(
        date: LocalDate,
        hour: Int,
        minute: Int,
    ): Long = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `a slot later today fires today`() {
        val now = millisAt(LocalDate.of(2026, 8, 14), 10, 0)
        val at = ClockTimes.nextOccurrence(20 * 60, now, zone) // 20:00
        assertEquals(millisAt(LocalDate.of(2026, 8, 14), 20, 0), at)
    }

    @Test
    fun `a slot already past fires tomorrow`() {
        val now = millisAt(LocalDate.of(2026, 8, 14), 21, 0)
        val at = ClockTimes.nextOccurrence(9 * 60, now, zone) // 09:00
        assertEquals(millisAt(LocalDate.of(2026, 8, 15), 9, 0), at)
    }

    @Test
    fun `now exactly on the slot fires tomorrow — strictly after now`() {
        val now = millisAt(LocalDate.of(2026, 8, 14), 9, 0)
        val at = ClockTimes.nextOccurrence(9 * 60, now, zone)
        assertEquals(millisAt(LocalDate.of(2026, 8, 15), 9, 0), at)
    }

    @Test
    fun `midnight slot rotates the widget day`() {
        val now = millisAt(LocalDate.of(2026, 8, 14), 23, 59)
        val at = ClockTimes.nextOccurrence(0, now, zone)
        assertEquals(millisAt(LocalDate.of(2026, 8, 15), 0, 0), at)
    }
}
