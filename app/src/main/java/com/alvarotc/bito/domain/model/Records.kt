package com.alvarotc.bito.domain.model

/**
 * A single log record.
 *
 * [value] semantics: 1 for a check, the amount for COUNT, minutes for DURATION.
 * For [Direction.ZERO] habits an entry IS a relapse (value 1).
 * Retroactive entries are allowed without limit; everything derived (streaks,
 * stats, points) is recomputed from entries — never stored.
 */
data class Entry(
    val id: String,
    val habitId: String,
    val logicalDay: LogicalDay,
    val value: Int,
    val createdAtMillis: Long,
)

/**
 * Seals a whole logical day: confirms abstinences/limits ("I stayed clean")
 * and closes the day. Created by the nightly review, by opening the app later,
 * or by batch sealing after days away. Silence never inflates streaks: without
 * a seal, ZERO/AT_MOST periods stay PENDING, never FULFILLED.
 */
data class DaySeal(
    val logicalDay: LogicalDay,
    val sealedAtMillis: Long,
)

/**
 * A pause: the habit does not demand anything and the streak does not break
 * while paused. Visible in stats. [endDay] null = pause still open.
 * Both bounds inclusive.
 */
data class PauseInterval(
    val habitId: String,
    val startDay: LogicalDay,
    val endDay: LogicalDay? = null,
    val note: String? = null,
)

/**
 * Target history (rule E2). A change applies from [effectiveFromDay] onward;
 * the past is never rewritten. Creating a habit records its initial target as
 * a change effective from its creation day. The target in force on day D is
 * the change with the greatest effectiveFromDay <= D.
 */
data class TargetChange(
    val habitId: String,
    val effectiveFromDay: LogicalDay,
    val target: Int,
)

/**
 * A spent streak freezer: protects exactly one habit on one concrete day
 * (manual activation, bought with points). A protected FAILED day acts as a
 * bridge in the streak: it does not break it and does not count toward it.
 * Freezers only apply to [Period.DAY] habits.
 */
data class FreezerUse(
    val id: String,
    val habitId: String,
    val protectedDay: LogicalDay,
    val usedAtMillis: Long,
)
