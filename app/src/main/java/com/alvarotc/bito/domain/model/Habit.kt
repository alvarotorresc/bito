package com.alvarotc.bito.domain.model

/**
 * A logical day expressed as an epoch day (days since 1970-01-01), computed
 * from a real instant and the user's configurable day-cutoff. See LogicalDays.
 */
typealias LogicalDay = Int

/** How progress is measured. */
enum class Metric { CHECK, COUNT, DURATION }

/** The period over which the target is evaluated. */
enum class Period { DAY, WEEK, MONTH }

/**
 * Direction of the goal:
 * - [AT_LEAST]: reach the target or more (positive habits).
 * - [AT_MOST]: stay at or below the target (limits, e.g. "social media <= 30 min/day").
 * - [ZERO]: full abstinence (e.g. "no smoking"). Invariant: ZERO habits always have [Period.DAY].
 */
enum class Direction { AT_LEAST, AT_MOST, ZERO }

/**
 * How the user logs progress:
 * - [COUNTER]: real values accumulate toward the target.
 * - [BINARY]: the target is a written reference; logging is a done/not-done tap.
 *   A BINARY entry on a day means that day counts as fulfilled regardless of value.
 */
enum class LogMode { COUNTER, BINARY }

enum class HabitStatus { ACTIVE, PAUSED, ARCHIVED }

/**
 * A habit definition. Pure domain type — persistence lives in the data layer.
 *
 * [target] is the CURRENT target (denormalized for display). The target in force
 * on any given date is resolved through the [TargetChange] history (rule E2:
 * editing a target applies from today onward; history keeps the target that was
 * in force on each date). Changing the metric is forbidden (archive + create).
 *
 * Periods before [createdOnDay] do not exist for this habit: they are neither
 * required nor counted. [archivedOnDay], when set, ends requirability from that
 * day (inclusive) onward.
 */
data class Habit(
    val id: String,
    val name: String,
    val metric: Metric,
    val period: Period,
    val direction: Direction,
    val target: Int,
    val unit: String? = null,
    val logMode: LogMode = LogMode.COUNTER,
    val step: Int = 1,
    val status: HabitStatus = HabitStatus.ACTIVE,
    val createdOnDay: LogicalDay,
    val archivedOnDay: LogicalDay? = null,
)
