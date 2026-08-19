package com.alvarotc.bito.domain.model

/** Why points were granted or spent. */
enum class PointsReason { HABIT_DONE, PERFECT_DAY, STREAK_MILESTONE, BUY_FREEZER, BUY_ITEM }

/**
 * One movement in the points ledger. The ledger is the stored truth; the
 * balance is always derived (sum of deltas). Points are never confiscated:
 * retroactive edits may add missing grants but never remove granted ones.
 */
data class PointsLedgerEntry(
    val id: String,
    val delta: Int,
    val reason: PointsReason,
    val refId: String? = null,
    val logicalDay: LogicalDay,
    val createdAtMillis: Long,
)

/**
 * A grant the engine derives from history. [refId] uniquely identifies the
 * grant within its reason so it is granted at most once:
 * - HABIT_DONE: "habitId:periodKey"
 * - PERFECT_DAY: "day:D" / "week:K" / "month:K"
 * - STREAK_MILESTONE: "habitId:length"
 */
data class PointsEvent(
    val reason: PointsReason,
    val refId: String,
    val logicalDay: LogicalDay,
    val delta: Int,
)

/**
 * Final numbers from the economy session (docs/05-economia-catalogo.md, 2026-08-19).
 */
data class EconomyConfig(
    val habitDonePoints: Int = 1,
    val perfectDayPoints: Int = 3,
    val perfectWeekPoints: Int = 10,
    val perfectMonthPoints: Int = 50,
    /** Streak length (in the habit's period unit) -> points. */
    val streakMilestonePoints: Map<Int, Int> = mapOf(7 to 5, 30 to 20, 100 to 75, 365 to 300),
    val freezerPrice: Int = 100,
)
