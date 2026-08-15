package com.alvarotc.bito.data

import com.alvarotc.bito.data.db.CustomizationCategory
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.data.db.DaySealEntity
import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.db.FreezerUseEntity
import com.alvarotc.bito.data.db.HabitEntity
import com.alvarotc.bito.data.db.PauseIntervalEntity
import com.alvarotc.bito.data.db.PointsLedgerEntity
import com.alvarotc.bito.data.db.TargetChangeEntity
import com.alvarotc.bito.data.db.TimeBucket
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.PointsReason

const val DAY_ZERO = 20_000

fun habitEntity(
    id: String = "h1",
    name: String = "Agua",
    metric: Metric = Metric.COUNT,
    period: Period = Period.DAY,
    direction: Direction = Direction.AT_LEAST,
    target: Int = 8,
    unit: String? = "vasos",
    logMode: LogMode = LogMode.COUNTER,
    step: Int = 1,
    timeBucket: TimeBucket? = null,
    timeOfDayMinutes: Int? = null,
    reminderMinutes: Int? = null,
    status: HabitStatus = HabitStatus.ACTIVE,
    createdAtMillis: Long = 1_000L,
    createdOnDay: Int = DAY_ZERO,
    archivedAtMillis: Long? = null,
    archivedOnDay: Int? = null,
    sortOrder: Int = 0,
) = HabitEntity(
    id, name, metric, period, direction, target, unit, logMode, step, timeBucket,
    timeOfDayMinutes, reminderMinutes, status, createdAtMillis, createdOnDay,
    archivedAtMillis, archivedOnDay, sortOrder,
)

fun targetChangeEntity(
    habitId: String = "h1",
    effectiveFromDay: Int = DAY_ZERO,
    target: Int = 8,
) = TargetChangeEntity(habitId, effectiveFromDay, target)

fun pauseIntervalEntity(
    habitId: String = "h1",
    startDay: Int = DAY_ZERO,
    endDay: Int? = null,
    note: String? = null,
) = PauseIntervalEntity(habitId, startDay, endDay, note)

fun entryEntity(
    id: String = "e1",
    habitId: String = "h1",
    logicalDay: Int = DAY_ZERO,
    value: Int = 1,
    createdAtMillis: Long = 1_000L,
) = EntryEntity(id, habitId, logicalDay, value, createdAtMillis)

fun daySealEntity(
    logicalDay: Int = DAY_ZERO,
    sealedAtMillis: Long = 1_000L,
) = DaySealEntity(logicalDay, sealedAtMillis)

fun freezerUseEntity(
    id: String = "f1",
    habitId: String = "h1",
    protectedDay: Int = DAY_ZERO,
    usedAtMillis: Long = 1_000L,
) = FreezerUseEntity(id, habitId, protectedDay, usedAtMillis)

fun pointsLedgerEntity(
    id: String = "p1",
    delta: Int = 1,
    reason: PointsReason = PointsReason.HABIT_DONE,
    refId: String? = "h1:$DAY_ZERO",
    logicalDay: Int = DAY_ZERO,
    createdAtMillis: Long = 1_000L,
) = PointsLedgerEntity(id, delta, reason, refId, logicalDay, createdAtMillis)

fun customizationItemEntity(
    itemId: String = "hat-basic",
    category: CustomizationCategory = CustomizationCategory.UPPER,
    acquiredAtMillis: Long = 1_000L,
    equipped: Boolean = false,
) = CustomizationItemEntity(itemId, category, acquiredAtMillis, equipped)
