package com.alvarotc.bito.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus

/**
 * Fila de una tarea puntual (spec §7.1). Las nueve tablas del doc tecnico §3 pasan a once con esta
 * y con [TaskEventEntity]. Los dias logicos se guardan como hechos capturados en su momento, nunca
 * recalculados desde millis (regla E7, misma disciplina que [HabitEntity]).
 *
 * El ORDEN de estos campos es el orden de las columnas del DDL que genera Room y que copia
 * MIGRATION_1_2. Reordenarlos rompe la migracion.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val firstStep: String?,
    val dueKind: DueKind,
    val dueDay: Int?,
    val status: TaskStatus,
    val createdAtMillis: Long,
    val createdOnDay: Int,
    val doneAtMillis: Long?,
    val doneOnDay: Int?,
)

/** La historia de una tarea: append-only, y se va con ella por CASCADE (regla T4). */
@Entity(
    tableName = "task_events",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId", "logicalDay")],
)
data class TaskEventEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val kind: TaskEventKind,
    val logicalDay: Int,
    val createdAtMillis: Long,
)
