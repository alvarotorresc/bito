package com.alvarotc.bito.domain

import java.time.Instant
import java.time.ZoneId

/**
 * Wall-clock scheduling math shared by reminders and the widget day-rotation
 * alarm. Like the rest of the domain: no clock of its own.
 */
object ClockTimes {
    private const val MINUTES_PER_DAY = 24 * 60

    /**
     * First instant strictly after [nowMillis] whose local wall-clock time in
     * [zone] reads [minutesOfDay] (0..1439). DST gaps resolve forward.
     */
    fun nextOccurrence(
        minutesOfDay: Int,
        nowMillis: Long,
        zone: ZoneId,
    ): Long {
        require(minutesOfDay in 0 until MINUTES_PER_DAY) { "minutesOfDay out of range: $minutesOfDay" }
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val candidate = today.atTime(minutesOfDay / 60, minutesOfDay % 60).atZone(zone)
        val next = if (candidate.toInstant().toEpochMilli() > nowMillis) candidate else candidate.plusDays(1)
        return next.toInstant().toEpochMilli()
    }
}
