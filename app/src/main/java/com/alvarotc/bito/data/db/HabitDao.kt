package com.alvarotc.bito.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Upsert
    suspend fun upsert(habit: HabitEntity)

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun byId(id: String): HabitEntity?

    @Query("SELECT * FROM habits ORDER BY sortOrder, createdAtMillis")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("UPDATE habits SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(
        id: String,
        sortOrder: Int,
    )

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM habits ORDER BY sortOrder")
    suspend fun all(): List<HabitEntity>

    @Query("DELETE FROM habits")
    suspend fun deleteAll()
}
