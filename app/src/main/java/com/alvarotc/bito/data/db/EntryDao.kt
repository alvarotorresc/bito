package com.alvarotc.bito.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Insert
    suspend fun insert(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: String)

    @Query(
        "SELECT * FROM entries WHERE habitId = :habitId AND logicalDay BETWEEN :from AND :to " +
            "ORDER BY logicalDay, createdAtMillis",
    )
    suspend fun forHabitInRange(
        habitId: String,
        from: Int,
        to: Int,
    ): List<EntryEntity>

    @Query("SELECT * FROM entries")
    fun observeAll(): Flow<List<EntryEntity>>
}
