package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Logical-day and period arithmetic. Pure JVM — no Android imports.
 *
 * The logical day honors the configurable day-cutoff: with a 03:00 cutoff
 * (cutoffMinutes = 180), reading at 01:00 counts toward "logical yesterday".
 * Rule E4: days are computed in the device's local zone, no special timezone
 * handling in v1. Rule E7: a cutoff change applies from that moment forward;
 * already-sealed days are never recomputed (callers keep stored logical days).
 *
 * Weeks are ISO weeks (Monday-first). Months are calendar months.
 */
object LogicalDays {
    private const val DAYS_PER_WEEK = 7
    private const val MONTHS_PER_YEAR = 12

    /**
     * Epoch day 0 (1970-01-01) is a Thursday, so the Monday opening the epoch
     * week — and therefore week key 0 — is epoch day -3.
     */
    private const val FIRST_MONDAY = -3

    /**
     * The logical day containing [epochMillis]: shift the local wall-clock time
     * back by [cutoffMinutes] and take that date's epoch day.
     */
    fun logicalDayOf(
        epochMillis: Long,
        cutoffMinutes: Int,
        zone: ZoneId,
    ): LogicalDay {
        val wallClock = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()
        return wallClock.minusMinutes(cutoffMinutes.toLong()).toLocalDate().toEpochDay().toInt()
    }

    /**
     * Key of the period a day belongs to. DAY -> the day itself; WEEK -> ISO
     * week index (epoch weeks, Monday-first); MONTH -> year * 12 + monthIndex.
     */
    fun periodKeyOf(
        day: LogicalDay,
        period: Period,
    ): Int =
        when (period) {
            Period.DAY -> day
            Period.WEEK -> Math.floorDiv(day - FIRST_MONDAY, DAYS_PER_WEEK)
            Period.MONTH -> {
                val date = LocalDate.ofEpochDay(day.toLong())
                date.year * MONTHS_PER_YEAR + date.monthValue - 1
            }
        }

    /** Inclusive range of logical days covered by a period key. */
    fun daysOf(
        periodKey: Int,
        period: Period,
    ): IntRange =
        when (period) {
            Period.DAY -> periodKey..periodKey
            Period.WEEK -> {
                val monday = FIRST_MONDAY + periodKey * DAYS_PER_WEEK
                monday..monday + DAYS_PER_WEEK - 1
            }
            Period.MONTH -> {
                val year = Math.floorDiv(periodKey, MONTHS_PER_YEAR)
                val month = Math.floorMod(periodKey, MONTHS_PER_YEAR) + 1
                val firstOfMonth = LocalDate.of(year, month, 1)
                val firstDay = firstOfMonth.toEpochDay().toInt()
                firstDay..firstDay + firstOfMonth.lengthOfMonth() - 1
            }
        }
}
