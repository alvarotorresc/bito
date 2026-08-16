package com.alvarotc.bito.ui.detail

import com.alvarotc.bito.domain.HabitStats
import com.alvarotc.bito.domain.Heatmap
import com.alvarotc.bito.domain.HeatmapDay
import com.alvarotc.bito.domain.PointsEngine
import com.alvarotc.bito.domain.Streaks
import com.alvarotc.bito.domain.WindowStats
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.ui.today.CardKind
import java.time.YearMonth

/** The 7/30/365-day compliance windows the monument's "% de cumplimiento" segment renders, in that order. */
private val WINDOW_SIZES = listOf(7, 30, 365)

/** State the habit Detail screen renders, built once per (domain state, habit, month, today) tuple. */
data class DetailUiState(
    val habitId: String,
    val name: String,
    val kind: CardKind,
    val unit: String?,
    val status: HabitStatus,
    val pausedSinceDay: LogicalDay?,
    val currentStreak: Int,
    val bestStreak: Int,
    val period: Period,
    val windows: List<WindowStats>,
    val month: YearMonth,
    val heatmap: List<HeatmapDay>,
    val freezersOwned: Int,
    val freezerPrice: Int,
    val balance: Int,
    // Not in the brief's literal field list, added deliberately: the screen needs "today" both to
    // disable the heatmap's next-month chevron once `month` reaches it and to log the abstinence
    // pill's relapse on the right day when a past month is on screen — the same reason
    // ui.today.TodayUiState already carries its own `today`.
    val today: LogicalDay,
    val loading: Boolean = false,
)

/**
 * Derives the Detail screen state for [habitId] as seen on [today], or null if the habit does
 * not exist (a deleted habit navigated away from mid-viewing) — the screen navigates back on null.
 * Pure — no side effects, no storage, no clock reads.
 */
fun buildDetailUiState(
    state: DomainState,
    habitId: String,
    month: YearMonth,
    today: LogicalDay,
): DetailUiState? {
    val habit = state.habits.find { it.id == habitId } ?: return null
    val economy = EconomyConfig()
    val streaks = Streaks.streaksOf(state, habit, today)
    val openPause = state.pauseIntervals.filter { it.habitId == habitId && it.endDay == null }.maxByOrNull { it.startDay }
    // Freezers only ever protect DAY-period habits (FreezerEngine.eligibilityOf): the global
    // inventory is meaningless on the detail of a WEEK/MONTH habit, so it is zeroed here rather
    // than left for the screen to remember to ignore.
    val freezersOwned = if (habit.period == Period.DAY) PointsEngine.freezersOwned(state) else 0
    return DetailUiState(
        habitId = habit.id,
        name = habit.name,
        kind = cardKindOf(habit),
        unit = habit.unit,
        status = habit.status,
        pausedSinceDay = openPause?.startDay,
        currentStreak = streaks.current,
        bestStreak = streaks.best,
        period = habit.period,
        windows = WINDOW_SIZES.map { HabitStats.windowStats(state, habit, today, it) },
        month = month,
        heatmap = Heatmap.monthOf(state, habit, month, today),
        freezersOwned = freezersOwned,
        freezerPrice = economy.freezerPrice,
        balance = PointsEngine.balance(state.pointsLedger),
        today = today,
        loading = false,
    )
}

/**
 * Visual kind of a habit's card/monument — mirrors
 * [com.alvarotc.bito.ui.today.buildTodayUiState]'s private `cardOf` mapping. Kept as a small,
 * self-contained duplicate rather than sharing a function across packages: the rule is four
 * lines and Today's builder is out of scope for this task.
 */
private fun cardKindOf(habit: Habit): CardKind {
    val binaryLike = habit.metric == Metric.CHECK || habit.logMode == LogMode.BINARY
    return when {
        habit.direction == Direction.ZERO -> CardKind.ABSTINENCE
        binaryLike -> CardKind.CHECK
        habit.metric == Metric.COUNT -> CardKind.COUNTER
        else -> CardKind.DURATION
    }
}
