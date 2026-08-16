package com.alvarotc.bito.ui.widget

import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals

class WidgetDayAlarmTest {
    private val zone = ZoneId.of("Europe/Madrid")

    private fun millisAt(
        date: LocalDate,
        hour: Int,
        minute: Int,
    ): Long = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `a cutoff later today rotates today`() {
        val now = millisAt(LocalDate.of(2026, 8, 14), 1, 0)
        val at = WidgetDayAlarm.nextRotationAt(3 * 60, now, zone)
        assertEquals(millisAt(LocalDate.of(2026, 8, 14), 3, 0), at)
    }

    @Test
    fun `a cutoff already past today rotates tomorrow`() {
        val now = millisAt(LocalDate.of(2026, 8, 14), 4, 0)
        val at = WidgetDayAlarm.nextRotationAt(3 * 60, now, zone)
        assertEquals(millisAt(LocalDate.of(2026, 8, 15), 3, 0), at)
    }

    @Test
    fun `a cutoff of twenty-four hours normalizes to midnight`() {
        val now = millisAt(LocalDate.of(2026, 8, 14), 23, 0)
        val at = WidgetDayAlarm.nextRotationAt(24 * 60, now, zone)
        assertEquals(millisAt(LocalDate.of(2026, 8, 15), 0, 0), at)
    }
}
