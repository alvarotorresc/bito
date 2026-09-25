package com.alvarotc.bito.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: String): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY createdAtMillis, id")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY createdAtMillis, id")
    suspend fun all(): List<TaskEntity>

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}
