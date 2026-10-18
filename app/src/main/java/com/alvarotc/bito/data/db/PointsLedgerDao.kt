package com.alvarotc.bito.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.flow.Flow

@Dao
interface PointsLedgerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: PointsLedgerEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<PointsLedgerEntity>)

    @Query("SELECT * FROM points_ledger ORDER BY logicalDay, createdAtMillis")
    fun observeAll(): Flow<List<PointsLedgerEntity>>

    /** Los últimos movimientos para «Tus bitos» (pieza 3, §9.1). Solo una consulta: el esquema no cambia. */
    @Query("SELECT * FROM points_ledger ORDER BY createdAtMillis DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PointsLedgerEntity>>

    /** The balance is always derived — never stored (tech doc §3). */
    @Query("SELECT COALESCE(SUM(delta), 0) FROM points_ledger")
    fun observeBalance(): Flow<Int>

    @Query("SELECT * FROM points_ledger")
    suspend fun all(): List<PointsLedgerEntity>

    /** Borra los asientos de [reason] con ese [refId] (el Deshacer del congelador). Devuelve cuántos. */
    @Query("DELETE FROM points_ledger WHERE reason = :reason AND refId = :refId")
    suspend fun deleteByReasonRef(
        reason: PointsReason,
        refId: String,
    ): Int

    @Query("DELETE FROM points_ledger")
    suspend fun deleteAll()
}
