package com.alvarotc.bito.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Append-only: no hay update — la historia no se edita (regla T4). La única baja por id es la del
 * Deshacer de «Traer a hoy», que quita el apunte recién escrito para que el viaje se deshaga entero.
 */
@Dao
interface TaskEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: TaskEventEntity)

    @Query("SELECT * FROM task_events ORDER BY logicalDay, createdAtMillis, id")
    fun observeAll(): Flow<List<TaskEventEntity>>

    @Query("SELECT * FROM task_events ORDER BY logicalDay, createdAtMillis, id")
    suspend fun all(): List<TaskEventEntity>

    @Query("DELETE FROM task_events WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM task_events")
    suspend fun deleteAll()
}
