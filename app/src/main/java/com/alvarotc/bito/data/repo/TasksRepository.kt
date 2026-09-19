package com.alvarotc.bito.data.repo

import androidx.room.withTransaction
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.TaskEntity
import com.alvarotc.bito.data.db.TaskEventEntity
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Tareas puntuales: la fila y su historia. Toda escritura pasa por aqui, que es donde se hacen
 * ciertos los invariantes del dominio: status DONE si y solo si doneOnDay != null (T1), titulo
 * recortado, y en blanco se rechaza (T3 — red de seguridad: el formulario ya bloquea antes con
 * canSave, como HabitFormModel), primer paso en blanco guardado como null, y eventos que no se
 * editan ni se borran (T4) — editar toca SOLO la fila de tasks.
 */
class TasksRepository(private val db: BitoDatabase) {
    fun observeTasks(): Flow<List<TaskEntity>> = db.taskDao().observeAll()

    suspend fun task(id: String): TaskEntity? = db.taskDao().byId(id)

    suspend fun create(task: TaskEntity) = db.taskDao().upsert(task.normalized())

    /** Solo lo que la hoja de editar ofrece. Los puntos ya concedidos no se recalculan (§5.1). */
    suspend fun update(
        id: String,
        title: String,
        firstStep: String?,
        dueKind: DueKind,
        dueDay: Int?,
    ) = db.withTransaction {
        val current = db.taskDao().byId(id) ?: return@withTransaction
        db.taskDao().upsert(
            current.copy(title = title, firstStep = firstStep, dueKind = dueKind, dueDay = dueDay).normalized(),
        )
    }

    /** El ForeignKey CASCADE se lleva su historia. No descuenta puntos (regla E3). */
    suspend fun delete(id: String) = db.taskDao().delete(id)

    suspend fun markDone(
        id: String,
        today: LogicalDay,
        nowMillis: Long,
    ) = db.withTransaction {
        val current = db.taskDao().byId(id) ?: return@withTransaction
        db.taskDao().upsert(current.copy(status = TaskStatus.DONE, doneOnDay = today, doneAtMillis = nowMillis))
    }

    /** El deshacer del snackbar. El apunte de puntos se queda: lo ganado, ganado. */
    suspend fun reopen(id: String) =
        db.withTransaction {
            val current = db.taskDao().byId(id) ?: return@withTransaction
            db.taskDao().upsert(current.copy(status = TaskStatus.OPEN, doneOnDay = null, doneAtMillis = null))
        }

    suspend fun postpone(
        id: String,
        today: LogicalDay,
        nowMillis: Long,
    ) = addEvent(id, TaskEventKind.POSTPONED, today, nowMillis)

    suspend fun bringToToday(
        id: String,
        today: LogicalDay,
        nowMillis: Long,
    ) = addEvent(id, TaskEventKind.BROUGHT, today, nowMillis)

    suspend fun recordAttempt(
        id: String,
        today: LogicalDay,
        nowMillis: Long,
    ) = addEvent(id, TaskEventKind.ATTEMPT, today, nowMillis)

    private suspend fun addEvent(
        taskId: String,
        kind: TaskEventKind,
        day: LogicalDay,
        nowMillis: Long,
    ) = db.taskEventDao().insert(TaskEventEntity(UUID.randomUUID().toString(), taskId, kind, day, nowMillis))

    /** Recorta ambos campos; un titulo que queda en blanco tras recortar se rechaza (T3). */
    private fun TaskEntity.normalized(): TaskEntity {
        val trimmed = copy(title = title.trim(), firstStep = firstStep?.trim()?.ifBlank { null })
        require(trimmed.title.isNotBlank()) { "task title must not be blank" }
        return trimmed
    }
}
