package com.alvarotc.bito.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Append-only: no hay update ni delete por id — la historia no se edita (regla T4). */
@Dao
interface TaskEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: TaskEventEntity)

    @Query("SELECT * FROM task_events ORDER BY logicalDay, createdAtMillis, id")
    fun observeAll(): Flow<List<TaskEventEntity>>

    @Query("SELECT * FROM task_events ORDER BY logicalDay, createdAtMillis, id")
    suspend fun all(): List<TaskEventEntity>

    @Query("DELETE FROM task_events")
    suspend fun deleteAll()
}
