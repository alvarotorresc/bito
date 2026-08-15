package com.alvarotc.bito.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetChangeDao {
    @Upsert
    suspend fun upsert(change: TargetChangeEntity)

    @Query("SELECT * FROM target_changes WHERE habitId = :habitId ORDER BY effectiveFromDay")
    suspend fun forHabit(habitId: String): List<TargetChangeEntity>

    @Query("SELECT * FROM target_changes")
    fun observeAll(): Flow<List<TargetChangeEntity>>

    @Query("SELECT * FROM target_changes")
    suspend fun all(): List<TargetChangeEntity>

    @Query("DELETE FROM target_changes")
    suspend fun deleteAll()
}
