package com.alvarotc.bito.ui.detail

import com.alvarotc.bito.domain.HabitStats
import com.alvarotc.bito.domain.Heatmap
import com.alvarotc.bito.domain.RealHabits
import com.alvarotc.bito.domain.Streaks
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.entriesOn
import com.alvarotc.bito.domain.entryOn
import com.alvarotc.bito.domain.ledgerEntry
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.ui.today.CardKind
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DetailUiStateTest {
    @Test
    fun `the monument shows current and best streak in the habit's period unit`() {
        val habit = RealHabits.makeBed
        val state = domainState(habits = listOf(habit), entries = entriesOn(habit, listOf(TODAY, TODAY - 1, TODAY - 2)))

        val result = buildDetailUiState(state, habit.id, YearMonth.of(2026, 8), TODAY)!!

        val expected = Streaks.streaksOf(state, habit, TODAY)
        assertEquals(expected.current, result.currentStreak)
        assertEquals(expected.best, result.bestStreak)
        assertEquals(Period.DAY, result.period)
    }

    @Test
    fun `windows come ordered 7 30 365`() {
        val habit = RealHabits.makeBed
        val state = domainState(habits = listOf(habit), entries = entriesOn(habit, listOf(TODAY, TODAY - 1)))

        val result = buildDetailUiState(state, habit.id, YearMonth.of(2026, 8), TODAY)!!

        assertEquals(3, result.windows.size)
        assertEquals(HabitStats.windowStats(state, habit, TODAY, 7), result.windows[0])
        assertEquals(HabitStats.windowStats(state, habit, TODAY, 30), result.windows[1])
        assertEquals(HabitStats.windowStats(state, habit, TODAY, 365), result.windows[2])
    }

    @Test
    fun `a missing habit yields null`() {
        val state = domainState(habits = listOf(RealHabits.makeBed))

        val result = buildDetailUiState(state, "no-such-habit", YearMonth.of(2026, 8), TODAY)

        assertNull(result)
    }

    @Test
    fun `the freezer chip data only appears for daily habits`() {
        val weekly = RealHabits.strengthTraining
        val weeklyState =
            domainState(habits = listOf(weekly), ledger = listOf(ledgerEntry(delta = -30, reason = PointsReason.BUY_FREEZER)))
        val weeklyResult = buildDetailUiState(weeklyState, weekly.id, YearMonth.of(2026, 8), TODAY)!!
        assertEquals(0, weeklyResult.freezersOwned)

        val daily = RealHabits.makeBed
        val dailyState = domainState(habits = listOf(daily), ledger = listOf(ledgerEntry(delta = -30, reason = PointsReason.BUY_FREEZER)))
        val dailyResult = buildDetailUiState(dailyState, daily.id, YearMonth.of(2026, 8), TODAY)!!
        assertEquals(1, dailyResult.freezersOwned)
    }

    @Test
    fun `day values sum a counter habit's entries so the day sheet can prefill instead of losing history`() {
        val habit = RealHabits.water
        val entries = listOf(entryOn(habit, TODAY - 1, value = 3), entryOn(habit, TODAY - 1, value = 2))
        val state = domainState(habits = listOf(habit), entries = entries)

        val result = buildDetailUiState(state, habit.id, YearMonth.of(2026, 8), TODAY)!!

        assertEquals(5, result.dayValues[TODAY - 1])
        assertNull(result.dayValues[TODAY - 2])
    }

    @Test
    fun `the heatmap month matches the requested month`() {
        val habit = RealHabits.makeBed
        val state = domainState(habits = listOf(habit), entries = entriesOn(habit, listOf(TODAY)))
        val requestedMonth = YearMonth.of(2026, 7)

        val result = buildDetailUiState(state, habit.id, requestedMonth, TODAY)!!

        assertEquals(requestedMonth, result.month)
        assertEquals(requestedMonth.lengthOfMonth(), result.heatmap.size)
        assertEquals(Heatmap.monthOf(state, habit, requestedMonth, TODAY), result.heatmap)
        assertEquals(CardKind.CHECK, result.kind)
    }
}
