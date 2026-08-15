package com.alvarotc.bito.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DaySealDao {
    /** First seal wins: re-sealing a sealed day never rewrites sealedAt. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(seal: DaySealEntity)

    @Query("SELECT * FROM day_seals WHERE logicalDay = :day")
    suspend fun byDay(day: Int): DaySealEntity?

    @Query("SELECT * FROM day_seals")
    fun observeAll(): Flow<List<DaySealEntity>>
}
