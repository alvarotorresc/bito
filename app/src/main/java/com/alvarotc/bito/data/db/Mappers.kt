package com.alvarotc.bito.data.db

import com.alvarotc.bito.domain.model.DaySeal
import com.alvarotc.bito.domain.model.Entry
import com.alvarotc.bito.domain.model.FreezerUse
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.PauseInterval
import com.alvarotc.bito.domain.model.PointsLedgerEntry
import com.alvarotc.bito.domain.model.TargetChange

fun HabitEntity.toDomain(): Habit =
    Habit(
        id = id,
        name = name,
        metric = metric,
        period = period,
        direction = direction,
        target = target,
        unit = unit,
        logMode = logMode,
        step = step,
        status = status,
        createdOnDay = createdOnDay,
        archivedOnDay = archivedOnDay,
    )

fun TargetChangeEntity.toDomain(): TargetChange = TargetChange(habitId, effectiveFromDay, target)

fun PauseIntervalEntity.toDomain(): PauseInterval = PauseInterval(habitId, startDay, endDay, note)

fun EntryEntity.toDomain(): Entry = Entry(id, habitId, logicalDay, value, createdAtMillis)

fun DaySealEntity.toDomain(): DaySeal = DaySeal(logicalDay, sealedAtMillis)

fun FreezerUseEntity.toDomain(): FreezerUse = FreezerUse(id, habitId, protectedDay, usedAtMillis)

fun PointsLedgerEntity.toDomain(): PointsLedgerEntry = PointsLedgerEntry(id, delta, reason, refId, logicalDay, createdAtMillis)
