package com.alvarotc.bito.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PauseIntervalDao {
    @Upsert
    suspend fun upsert(pause: PauseIntervalEntity)

    @Query("SELECT * FROM pause_intervals WHERE habitId = :habitId ORDER BY startDay")
    suspend fun forHabit(habitId: String): List<PauseIntervalEntity>

    @Query("UPDATE pause_intervals SET endDay = :endDay WHERE habitId = :habitId AND endDay IS NULL")
    suspend fun closeOpen(
        habitId: String,
        endDay: Int,
    )

    @Query("SELECT * FROM pause_intervals")
    fun observeAll(): Flow<List<PauseIntervalEntity>>
}
