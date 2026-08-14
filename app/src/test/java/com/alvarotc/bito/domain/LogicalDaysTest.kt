package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Logical-day and period arithmetic.
 *
 * The period-key numbering is deliberately not asserted against literals: the
 * contract is that [LogicalDays.periodKeyOf] and [LogicalDays.daysOf] are
 * inverse to each other and that consecutive periods get consecutive keys.
 * Testing the pair pins the semantics without freezing an arbitrary offset.
 */
class LogicalDaysTest {
    private val cutoffAt3Am = 180
    private val cutoffAtMidnight = 0

    private fun millisAt(
        date: LocalDate,
        time: LocalTime,
    ): Long = ZonedDateTime.of(date, time, testZone).toInstant().toEpochMilli()

    private fun logicalDay(
        date: LocalDate,
        time: LocalTime,
        cutoffMinutes: Int,
    ): LogicalDay = LogicalDays.logicalDayOf(millisAt(date, time), cutoffMinutes, testZone)

    // -----------------------------------------------------------------------
    // Cutoff
    // -----------------------------------------------------------------------

    @Test
    fun `logging at 1am with a 3am cutoff counts toward logical yesterday`() {
        val loggedAt = logicalDay(LocalDate.of(2026, 8, 14), LocalTime.of(1, 0), cutoffAt3Am)

        assertEquals(dayOf(2026, 8, 13), loggedAt)
    }

    @Test
    fun `logging one minute before a 3am cutoff still counts toward logical yesterday`() {
        val loggedAt = logicalDay(LocalDate.of(2026, 8, 14), LocalTime.of(2, 59), cutoffAt3Am)

        assertEquals(dayOf(2026, 8, 13), loggedAt)
    }

    @Test
    fun `logging exactly at the 3am cutoff already counts toward the new logical day`() {
        val loggedAt = logicalDay(LocalDate.of(2026, 8, 14), LocalTime.of(3, 0), cutoffAt3Am)

        assertEquals(dayOf(2026, 8, 14), loggedAt)
    }

    @Test
    fun `logging one minute after the 3am cutoff counts toward the new logical day`() {
        val loggedAt = logicalDay(LocalDate.of(2026, 8, 14), LocalTime.of(3, 1), cutoffAt3Am)

        assertEquals(dayOf(2026, 8, 14), loggedAt)
    }

    @Test
    fun `logging at noon with a 3am cutoff counts toward the calendar day`() {
        val loggedAt = logicalDay(LocalDate.of(2026, 8, 14), LocalTime.of(12, 0), cutoffAt3Am)

        assertEquals(dayOf(2026, 8, 14), loggedAt)
    }

    @Test
    fun `logging just before midnight with a 3am cutoff counts toward the calendar day`() {
        val loggedAt = logicalDay(LocalDate.of(2026, 8, 14), LocalTime.of(23, 59), cutoffAt3Am)

        assertEquals(dayOf(2026, 8, 14), loggedAt)
    }

    @Test
    fun `logging at midnight with a 3am cutoff still belongs to the previous logical day`() {
        val loggedAt = logicalDay(LocalDate.of(2026, 8, 14), LocalTime.of(0, 0), cutoffAt3Am)

        assertEquals(dayOf(2026, 8, 13), loggedAt)
    }

    @Test
    fun `with a midnight cutoff the logical day is the calendar day at any hour`() {
        val date = LocalDate.of(2026, 8, 14)
        val expected = dayOf(2026, 8, 14)

        assertEquals(expected, logicalDay(date, LocalTime.of(0, 0), cutoffAtMidnight))
        assertEquals(expected, logicalDay(date, LocalTime.of(1, 0), cutoffAtMidnight))
        assertEquals(expected, logicalDay(date, LocalTime.of(12, 0), cutoffAtMidnight))
        assertEquals(expected, logicalDay(date, LocalTime.of(23, 59), cutoffAtMidnight))
    }

    @Test
    fun `the same instant can fall on different logical days depending on the cutoff`() {
        val date = LocalDate.of(2026, 8, 14)
        val time = LocalTime.of(1, 30)

        assertEquals(dayOf(2026, 8, 13), logicalDay(date, time, cutoffAt3Am))
        assertEquals(dayOf(2026, 8, 14), logicalDay(date, time, cutoffAtMidnight))
    }

    @Test
    fun `a cutoff crossing a month boundary lands on the last day of the previous month`() {
        val loggedAt = logicalDay(LocalDate.of(2026, 8, 1), LocalTime.of(2, 0), cutoffAt3Am)

        assertEquals(dayOf(2026, 7, 31), loggedAt)
    }

    @Test
    fun `a cutoff crossing a year boundary lands on the last day of the previous year`() {
        val loggedAt = logicalDay(LocalDate.of(2026, 1, 1), LocalTime.of(2, 0), cutoffAt3Am)

        assertEquals(dayOf(2025, 12, 31), loggedAt)
    }

    @Test
    fun `consecutive nights with a 3am cutoff produce consecutive logical days`() {
        val firstNight = logicalDay(LocalDate.of(2026, 8, 13), LocalTime.of(1, 0), cutoffAt3Am)
        val secondNight = logicalDay(LocalDate.of(2026, 8, 14), LocalTime.of(1, 0), cutoffAt3Am)

        assertEquals(1, secondNight - firstNight)
    }

    // -----------------------------------------------------------------------
    // Period keys — DAY
    // -----------------------------------------------------------------------

    @Test
    fun `the period key of a daily habit is the day itself`() {
        assertEquals(TODAY, LogicalDays.periodKeyOf(TODAY, Period.DAY))
        assertEquals(HABIT_BIRTH, LogicalDays.periodKeyOf(HABIT_BIRTH, Period.DAY))
    }

    @Test
    fun `a day period covers exactly one day`() {
        assertEquals(TODAY..TODAY, LogicalDays.daysOf(TODAY, Period.DAY))
    }

    // -----------------------------------------------------------------------
    // Period keys — WEEK (ISO, Monday-first)
    // -----------------------------------------------------------------------

    @Test
    fun `every day from monday to sunday shares the same week key`() {
        val keys = (THIS_MONDAY..THIS_SUNDAY).map { weekKey(it) }.toSet()

        assertEquals(1, keys.size)
        assertEquals(7, (THIS_MONDAY..THIS_SUNDAY).count())
    }

    @Test
    fun `sunday and the following monday belong to consecutive weeks`() {
        assertEquals(weekKey(LAST_SUNDAY) + 1, weekKey(THIS_MONDAY))
    }

    @Test
    fun `a week period spans monday to sunday`() {
        assertEquals(THIS_MONDAY..THIS_SUNDAY, LogicalDays.daysOf(weekKey(TODAY), Period.WEEK))
        assertEquals(LAST_MONDAY..LAST_SUNDAY, LogicalDays.daysOf(weekKey(LAST_SUNDAY), Period.WEEK))
    }

    @Test
    fun `weeks are contiguous across a year boundary`() {
        val lastMondayOf2025 = dayOf(2025, 12, 29)
        val firstSundayOf2026 = dayOf(2026, 1, 4)
        val firstFullMondayOf2026 = dayOf(2026, 1, 5)

        assertEquals(weekKey(lastMondayOf2025), weekKey(firstSundayOf2026))
        assertEquals(weekKey(lastMondayOf2025) + 1, weekKey(firstFullMondayOf2026))
        assertEquals(lastMondayOf2025..firstSundayOf2026, LogicalDays.daysOf(weekKey(firstSundayOf2026), Period.WEEK))
    }

    @Test
    fun `weeks of different years never share a key`() {
        assertNotEquals(weekKey(dayOf(2025, 8, 11)), weekKey(dayOf(2026, 8, 10)))
    }

    // -----------------------------------------------------------------------
    // Period keys — MONTH (calendar months)
    // -----------------------------------------------------------------------

    @Test
    fun `every day of a calendar month shares the same month key`() {
        val keys = (AUGUST_FIRST..AUGUST_LAST).map { monthKey(it) }.toSet()

        assertEquals(1, keys.size)
    }

    @Test
    fun `consecutive calendar months get consecutive keys`() {
        assertEquals(monthKey(JULY_FIRST) + 1, monthKey(AUGUST_FIRST))
    }

    @Test
    fun `months are contiguous across a year boundary`() {
        assertEquals(monthKey(dayOf(2025, 12, 15)) + 1, monthKey(dayOf(2026, 1, 15)))
    }

    @Test
    fun `a month period spans the whole natural month`() {
        assertEquals(AUGUST_FIRST..AUGUST_LAST, LogicalDays.daysOf(monthKey(TODAY), Period.MONTH))
        assertEquals(JULY_FIRST..JULY_LAST, LogicalDays.daysOf(monthKey(JULY_LAST), Period.MONTH))
    }

    @Test
    fun `a month period respects short and leap months`() {
        val february2026 = LogicalDays.daysOf(monthKey(dayOf(2026, 2, 10)), Period.MONTH)
        val february2028 = LogicalDays.daysOf(monthKey(dayOf(2028, 2, 10)), Period.MONTH)

        assertEquals(dayOf(2026, 2, 1)..dayOf(2026, 2, 28), february2026)
        assertEquals(dayOf(2028, 2, 1)..dayOf(2028, 2, 29), february2028)
    }

    // -----------------------------------------------------------------------
    // Coherence between periodKeyOf and daysOf
    // -----------------------------------------------------------------------

    @Test
    fun `a day always belongs to the period its key resolves to`() {
        for (period in Period.entries) {
            for (day in JUNE_FIRST..AUGUST_LAST) {
                val range = LogicalDays.daysOf(LogicalDays.periodKeyOf(day, period), period)
                assertTrue(day in range, "day $day missing from its own $period range $range")
            }
        }
    }

    @Test
    fun `every day of a period resolves back to the same key`() {
        for (period in Period.entries) {
            for (day in JUNE_FIRST..AUGUST_LAST) {
                val key = LogicalDays.periodKeyOf(day, period)
                val keysInside = LogicalDays.daysOf(key, period).map { LogicalDays.periodKeyOf(it, period) }.toSet()
                assertEquals(setOf(key), keysInside, "period $key ($period) is not self-consistent")
            }
        }
    }

    @Test
    fun `consecutive period keys cover consecutive day ranges without gaps or overlaps`() {
        for (period in Period.entries) {
            var key = LogicalDays.periodKeyOf(JUNE_FIRST, period)
            var range = LogicalDays.daysOf(key, period)
            repeat(12) {
                val next = LogicalDays.daysOf(key + 1, period)
                assertEquals(range.last + 1, next.first, "$period periods $key and ${key + 1} are not contiguous")
                key += 1
                range = next
            }
        }
    }

    // -----------------------------------------------------------------------
    // The calendar constants the whole suite is built on
    // -----------------------------------------------------------------------

    @Test
    fun `the fixture calendar constants match the real calendar`() {
        assertEquals(dayOf(2026, 8, 14), TODAY)
        assertEquals(dayOf(2026, 8, 10), THIS_MONDAY)
        assertEquals(dayOf(2026, 8, 16), THIS_SUNDAY)
        assertEquals(dayOf(2026, 8, 3), LAST_MONDAY)
        assertEquals(dayOf(2026, 8, 5), LAST_WEDNESDAY)
        assertEquals(dayOf(2026, 8, 7), LAST_FRIDAY)
        assertEquals(dayOf(2026, 8, 9), LAST_SUNDAY)
        assertEquals(dayOf(2026, 7, 27), TWO_WEEKS_AGO_MONDAY)
        assertEquals(dayOf(2026, 8, 2), TWO_WEEKS_AGO_SUNDAY)
        assertEquals(dayOf(2026, 7, 20), THREE_WEEKS_AGO_MONDAY)
        assertEquals(dayOf(2026, 7, 26), THREE_WEEKS_AGO_SUNDAY)
        assertEquals(dayOf(2026, 7, 1), JULY_FIRST)
        assertEquals(dayOf(2026, 7, 31), JULY_LAST)
        assertEquals(dayOf(2026, 8, 1), AUGUST_FIRST)
        assertEquals(dayOf(2026, 8, 31), AUGUST_LAST)
        assertEquals(dayOf(2026, 6, 1), JUNE_FIRST)
        assertEquals(dayOf(2026, 1, 1), HABIT_BIRTH)
    }
}
