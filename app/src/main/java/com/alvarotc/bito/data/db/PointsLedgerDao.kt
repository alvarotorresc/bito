package com.alvarotc.bito.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PointsLedgerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: PointsLedgerEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<PointsLedgerEntity>)

    @Query("SELECT * FROM points_ledger ORDER BY logicalDay, createdAtMillis")
    fun observeAll(): Flow<List<PointsLedgerEntity>>

    /** The balance is always derived — never stored (tech doc §3). */
    @Query("SELECT COALESCE(SUM(delta), 0) FROM points_ledger")
    fun observeBalance(): Flow<Int>

    @Query("SELECT * FROM points_ledger")
    suspend fun all(): List<PointsLedgerEntity>

    @Query("DELETE FROM points_ledger")
    suspend fun deleteAll()
}
