package com.alvarotc.bito.ui.habitform

import com.alvarotc.bito.data.db.HabitEntity
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period

/** The five ways a user can shape a habit in the create/edit form. */
enum class HabitPreset { DAILY_CHECK, QUANTITY, DURATION, WEEKLY_TIMES, QUIT }

/** For [HabitPreset.QUIT]: total abstinence, or a cap on a metric. */
enum class QuitMode { TOTAL, LIMIT }

/**
 * The (metric, period, direction, target, unit) an entity shape resolves to for a preset.
 * Internal to this file; [toNewEntity] builds one per preset/quitMode/limitMetric combination.
 */
private data class EntityShape(
    val metric: Metric,
    val period: Period,
    val direction: Direction,
    val target: Int,
    val unit: String?,
)

/** Form state for creating or editing a habit. One shape covers all five presets. */
data class HabitFormState(
    val editingId: String? = null,
    val name: String = "",
    val preset: HabitPreset = HabitPreset.DAILY_CHECK,
    val target: Int = 1,
    val unit: String = "",
    val period: Period = Period.DAY,
    val quitMode: QuitMode = QuitMode.TOTAL,
    val limitMetric: Metric = Metric.DURATION,
    val binaryMode: Boolean = false,
    val step: Int = 1,
) {
    val isEditing get() = editingId != null
    val canSave get() = name.isNotBlank() && (target >= 1 || (preset == HabitPreset.QUIT && quitMode == QuitMode.TOTAL))
}

/** Rule E2 (initial target) is recorded by the repository on create, not here. */
fun HabitFormState.toNewEntity(
    id: String,
    today: LogicalDay,
    nowMillis: Long,
    sortOrder: Int,
): HabitEntity {
    val trimmedUnit = unit.trim().ifBlank { null }
    val shape =
        when (preset) {
            HabitPreset.DAILY_CHECK -> EntityShape(Metric.CHECK, Period.DAY, Direction.AT_LEAST, 1, null)
            HabitPreset.QUANTITY -> EntityShape(Metric.COUNT, period, Direction.AT_LEAST, target, trimmedUnit)
            HabitPreset.DURATION -> EntityShape(Metric.DURATION, period, Direction.AT_LEAST, target, null)
            HabitPreset.WEEKLY_TIMES -> EntityShape(Metric.CHECK, Period.WEEK, Direction.AT_LEAST, target, null)
            HabitPreset.QUIT ->
                when (quitMode) {
                    QuitMode.TOTAL -> EntityShape(Metric.CHECK, Period.DAY, Direction.ZERO, 0, null)
                    QuitMode.LIMIT ->
                        when (limitMetric) {
                            Metric.DURATION -> EntityShape(Metric.DURATION, period, Direction.AT_MOST, target, null)
                            Metric.COUNT -> EntityShape(Metric.COUNT, period, Direction.AT_MOST, target, trimmedUnit)
                            Metric.CHECK -> EntityShape(Metric.CHECK, period, Direction.AT_MOST, target, null)
                        }
                }
        }
    return HabitEntity(
        id = id,
        name = name.trim(),
        metric = shape.metric,
        period = shape.period,
        direction = shape.direction,
        target = shape.target,
        unit = shape.unit,
        logMode = if (binaryMode) LogMode.BINARY else LogMode.COUNTER,
        step = step,
        timeBucket = null,
        timeOfDayMinutes = null,
        reminderMinutes = null,
        status = HabitStatus.ACTIVE,
        createdAtMillis = nowMillis,
        createdOnDay = today,
        archivedAtMillis = null,
        archivedOnDay = null,
        sortOrder = sortOrder,
    )
}

/** Recovers the form shape a stored habit was built from. Inverse of [toNewEntity]'s preset mapping. */
fun HabitEntity.toFormState(): HabitFormState {
    val preset =
        when {
            direction == Direction.ZERO || direction == Direction.AT_MOST -> HabitPreset.QUIT
            metric == Metric.CHECK && period != Period.DAY -> HabitPreset.WEEKLY_TIMES
            metric == Metric.COUNT -> HabitPreset.QUANTITY
            metric == Metric.DURATION -> HabitPreset.DURATION
            else -> HabitPreset.DAILY_CHECK
        }
    return HabitFormState(
        editingId = id,
        name = name,
        preset = preset,
        target = target,
        unit = unit.orEmpty(),
        period = period,
        quitMode = if (direction == Direction.ZERO) QuitMode.TOTAL else QuitMode.LIMIT,
        limitMetric = if (direction == Direction.AT_MOST) metric else Metric.DURATION,
        binaryMode = logMode == LogMode.BINARY,
        step = step,
    )
}

/** The sensible starting target for a preset; also what a preset/mode switch resets to. */
fun defaultTargetFor(
    preset: HabitPreset,
    quitMode: QuitMode,
    limitMetric: Metric,
): Int =
    when (preset) {
        HabitPreset.DAILY_CHECK -> 1
        HabitPreset.QUANTITY -> 8
        HabitPreset.DURATION -> 20
        HabitPreset.WEEKLY_TIMES -> 3
        HabitPreset.QUIT ->
            when (quitMode) {
                QuitMode.TOTAL -> 0
                QuitMode.LIMIT ->
                    when (limitMetric) {
                        Metric.DURATION -> 30
                        Metric.COUNT -> 1
                        Metric.CHECK -> 1
                    }
            }
    }
