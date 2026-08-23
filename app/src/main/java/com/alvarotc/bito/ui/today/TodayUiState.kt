package com.alvarotc.bito.ui.today

import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.domain.Compliance
import com.alvarotc.bito.domain.ComplianceStatus
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.MoodEngine
import com.alvarotc.bito.domain.Sealing
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.Streaks
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.equippedSetOf
import com.alvarotc.bito.ui.habi.HabiSpec

/** Visual treatment of a habit card, derived from its metric/log mode/direction. */
enum class CardKind { CHECK, COUNTER, DURATION, ABSTINENCE }

/** One habit's Today-screen state, ready for the UI to render without further domain lookups. */
data class HabitCardUi(
    val id: String,
    val name: String,
    val kind: CardKind,
    val progress: Int,
    val target: Int,
    val unit: String?,
    val step: Int,
    val direction: Direction,
    val period: Period,
    val doneToday: Boolean,
    val failed: Boolean,
    val streak: Int,
)

/**
 * Whether the habit name should render struck through. Only AT_LEAST cards earn this once
 * today's goal is reached — AT_MOST cards are "done" (clean) from the day's start, so a
 * strikethrough there would read as an already-completed goal instead of the honest "no
 * usage logged yet". Their state is carried by the limit caption and the failure tint instead.
 */
val HabitCardUi.nameStruckThrough: Boolean
    get() = direction == Direction.AT_LEAST && doneToday

/**
 * Whether the card should keep showing its quick-log controls (e.g. DurationBody's
 * +5/+15/+goal chips). AT_LEAST cards hide them once today's goal is reached — nothing left
 * to log. AT_MOST cards show them at all times, clean or over the limit: logging real usage
 * stays honest even after the limit is blown.
 */
val HabitCardUi.showsLoggingChips: Boolean
    get() = direction == Direction.AT_MOST || !doneToday

/** A paused habit's compact row in Today's "paused" section — no progress, just a way back in. */
data class PausedHabitUi(val id: String, val name: String)

/**
 * Snapshot the Today screen renders: the ring, the cards, and the pending-seal prompt.
 * [spec] and [userName] feed the header's corner avatar and greeting (T14) — the same
 * [HabiSpec] shape the Habi and Stats screens' own avatars use.
 */
data class TodayUiState(
    val today: LogicalDay = 0,
    val ringDone: Int = 0,
    val ringTotal: Int = 0,
    val cards: List<HabitCardUi> = emptyList(),
    val pausedHabits: List<PausedHabitUi> = emptyList(),
    val pendingSealDays: List<LogicalDay> = emptyList(),
    val todaySealed: Boolean = false,
    val spec: HabiSpec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
    val userName: String = "",
    val logHapticEnabled: Boolean = true,
    val loading: Boolean = true,
)

/**
 * Derives the Today screen state from [state] as seen on [today]. Pure — no
 * side effects, no storage, no clock reads — so it is trivially testable and
 * safe to call on every state change. [personality], [owned] and [userName] default so
 * existing positional callers (tests predating the header avatar) keep compiling unchanged.
 */
fun buildTodayUiState(
    state: DomainState,
    sortOrder: Map<String, Int>,
    today: LogicalDay,
    personality: Personality = Personality.NEUTRA,
    owned: List<CustomizationItemEntity> = emptyList(),
    userName: String = "",
    logHapticEnabled: Boolean = true,
): TodayUiState {
    val lastActivityDay = StatsEngine.lastActivityDay(state)
    val mood = MoodEngine.moodOf(state, today, lastActivityDay)
    val equippedIds = owned.filter { it.equipped }.map { it.itemId }
    val cards =
        state.habits
            .filter { Compliance.isRequirableOn(state, it, today) }
            .map { habit -> cardOf(state, habit, today) }
            .sortedBy { sortOrder[it.id] ?: Int.MAX_VALUE }
    // Paused, not archived: a habit "counts" as paused either by its own status flag or by
    // carrying an open pause interval — the two are written together by
    // HabitsRepository.pause/resume, but domain-level fixtures (this file's own `pause()`
    // helper, used by test 10 and above) can set one without the other.
    val pausedHabits =
        state.habits
            .filter { it.status != HabitStatus.ARCHIVED }
            .filter { habit ->
                habit.status == HabitStatus.PAUSED ||
                    state.pauseIntervals.any { it.habitId == habit.id && it.endDay == null }
            }
            .sortedBy { sortOrder[it.id] ?: Int.MAX_VALUE }
            .map { PausedHabitUi(it.id, it.name) }
    return TodayUiState(
        today = today,
        ringDone = cards.count { it.doneToday },
        ringTotal = cards.size,
        cards = cards,
        pausedHabits = pausedHabits,
        pendingSealDays = Sealing.pendingSealDays(state, today),
        todaySealed = Sealing.isSealed(state, today),
        spec = HabiSpec(mood, personality, equippedSetOf(equippedIds)),
        userName = userName,
        logHapticEnabled = logHapticEnabled,
        loading = false,
    )
}

internal fun cardOf(
    state: DomainState,
    habit: Habit,
    today: LogicalDay,
): HabitCardUi {
    val periodKey = LogicalDays.periodKeyOf(today, habit.period)
    val periodDays = LogicalDays.daysOf(periodKey, habit.period)
    val compliance = Compliance.complianceOf(state, habit, periodKey, today)
    val requirableDays = Compliance.requirableDaysOf(state, habit, periodKey)
    val entries = state.entries.filter { it.habitId == habit.id && it.logicalDay in requirableDays }
    val progress = Compliance.progressOf(habit, entries)
    val target = Compliance.targetOf(state, habit, periodDays.last)
    val binaryLike = habit.metric == Metric.CHECK || habit.logMode == LogMode.BINARY
    val failed = compliance == ComplianceStatus.FAILED
    val doneToday =
        when (habit.direction) {
            Direction.AT_LEAST ->
                if (habit.period == Period.DAY) {
                    compliance == ComplianceStatus.FULFILLED
                } else {
                    compliance == ComplianceStatus.FULFILLED || entries.any { it.logicalDay == today }
                }
            // "No further action needed today": clean/within limit counts even
            // unsealed — the seal (M4 review) stays the streak-truth.
            Direction.AT_MOST, Direction.ZERO -> !failed
        }
    val kind =
        when {
            habit.direction == Direction.ZERO -> CardKind.ABSTINENCE
            binaryLike -> CardKind.CHECK
            habit.metric == Metric.COUNT -> CardKind.COUNTER
            else -> CardKind.DURATION
        }
    return HabitCardUi(
        id = habit.id,
        name = habit.name,
        kind = kind,
        progress = progress,
        target = target,
        unit = habit.unit,
        step = habit.step,
        direction = habit.direction,
        period = habit.period,
        doneToday = doneToday,
        failed = failed,
        streak = Streaks.streaksOf(state, habit, today).current,
    )
}
