package com.alvarotc.bito.data.repo

import androidx.room.withTransaction
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.HabitEntity
import com.alvarotc.bito.data.db.PauseIntervalEntity
import com.alvarotc.bito.data.db.TargetChangeEntity
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogicalDay
import kotlinx.coroutines.flow.Flow

/** Habit definitions: the habit row plus its target history and pauses. */
class HabitsRepository(private val db: BitoDatabase) {
    fun observeHabits(): Flow<List<HabitEntity>> = db.habitDao().observeAll()

    suspend fun habit(id: String): HabitEntity? = db.habitDao().byId(id)

    /** Rule E2: creating a habit records its initial target as a change. */
    suspend fun create(habit: HabitEntity) =
        db.withTransaction {
            db.habitDao().upsert(habit)
            db.targetChangeDao().upsert(TargetChangeEntity(habit.id, habit.createdOnDay, habit.target))
        }

    /** Rule E2: a target edit applies from [today]; history is never rewritten. */
    suspend fun update(
        habit: HabitEntity,
        today: LogicalDay,
    ) = db.withTransaction {
        val previous = db.habitDao().byId(habit.id)
        db.habitDao().upsert(habit)
        if (previous == null || previous.target != habit.target) {
            db.targetChangeDao().upsert(TargetChangeEntity(habit.id, today, habit.target))
        }
    }

    suspend fun archive(
        id: String,
        today: LogicalDay,
        nowMillis: Long,
    ) = db.withTransaction {
        val habit = db.habitDao().byId(id) ?: return@withTransaction
        // A pause semantically ends where the habit's own life pauses too: archiving a PAUSED
        // habit without closing its open interval leaves it stuck paused forever once
        // unarchive() brings status back to ACTIVE (isPausedOn keys off the interval, not status).
        db.pauseIntervalDao().closeOpen(id, today)
        db.habitDao().upsert(
            habit.copy(status = HabitStatus.ARCHIVED, archivedOnDay = today, archivedAtMillis = nowMillis),
        )
    }

    /** Undoes [archive]: back to ACTIVE, both archive fields cleared — no history is rewritten. */
    suspend fun unarchive(id: String) =
        db.withTransaction {
            val habit = db.habitDao().byId(id) ?: return@withTransaction
            db.habitDao().upsert(
                habit.copy(status = HabitStatus.ACTIVE, archivedAtMillis = null, archivedOnDay = null),
            )
        }

    suspend fun pause(
        habitId: String,
        startDay: LogicalDay,
        note: String?,
    ) = db.withTransaction {
        val habit = db.habitDao().byId(habitId) ?: return@withTransaction
        db.habitDao().upsert(habit.copy(status = HabitStatus.PAUSED))
        db.pauseIntervalDao().upsert(PauseIntervalEntity(habitId, startDay, endDay = null, note = note))
    }

    suspend fun resume(
        habitId: String,
        endDay: LogicalDay,
    ) = db.withTransaction {
        val habit = db.habitDao().byId(habitId) ?: return@withTransaction
        db.pauseIntervalDao().closeOpen(habitId, endDay)
        db.habitDao().upsert(habit.copy(status = HabitStatus.ACTIVE))
    }

    /** Persists a manual ordering: each habit takes its position in [orderedIds] as sortOrder. */
    suspend fun reorder(orderedIds: List<String>) =
        db.withTransaction {
            orderedIds.forEachIndexed { index, id -> db.habitDao().updateSortOrder(id, index) }
        }

    /** Rule E3: cascades wipe the history; earned points are never deducted. */
    suspend fun delete(id: String) = db.habitDao().delete(id)
}
