package com.alvarotc.bito.data.db

import com.alvarotc.bito.domain.model.BreathingSession
import com.alvarotc.bito.domain.model.DaySeal
import com.alvarotc.bito.domain.model.Entry
import com.alvarotc.bito.domain.model.FreezerUse
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.PauseInterval
import com.alvarotc.bito.domain.model.PointsLedgerEntry
import com.alvarotc.bito.domain.model.TargetChange
import com.alvarotc.bito.domain.model.Task
import com.alvarotc.bito.domain.model.TaskEvent

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

// doneAtMillis se queda en la entidad a proposito: el dominio solo necesita el dia. Mismo
// criterio que HabitEntity.createdAtMillis, que tampoco sube. Argumentos nombrados, porque la
// entidad lleva createdAtMillis antes de createdOnDay y el tipo del dominio al reves.
fun TaskEntity.toDomain(): Task =
    Task(
        id = id,
        title = title,
        firstStep = firstStep,
        dueKind = dueKind,
        dueDay = dueDay,
        status = status,
        createdOnDay = createdOnDay,
        createdAtMillis = createdAtMillis,
        doneOnDay = doneOnDay,
    )

fun TaskEventEntity.toDomain(): TaskEvent = TaskEvent(id, taskId, kind, logicalDay)

fun BreathingSessionEntity.toDomain(): BreathingSession = BreathingSession(id, mode, startedAtMillis, durationSeconds, completed)
