package com.alvarotc.bito.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FreezerUseDao {
    /** Returns -1 when the (habitId, protectedDay) unique index ignores the insert. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(use: FreezerUseEntity): Long

    @Query("SELECT * FROM freezer_uses")
    fun observeAll(): Flow<List<FreezerUseEntity>>

    @Query("SELECT * FROM freezer_uses")
    suspend fun all(): List<FreezerUseEntity>

    @Query("DELETE FROM freezer_uses")
    suspend fun deleteAll()
}
