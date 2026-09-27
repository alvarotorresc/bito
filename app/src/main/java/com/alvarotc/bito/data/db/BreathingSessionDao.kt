package com.alvarotc.bito.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * La triple que exige el sistema — observeAll para DomainStateRepository, all para el export,
 * deleteAll para el import — mas insert. Nada mas: las sesiones no se editan ni se borran una a una.
 */
@Dao
interface BreathingSessionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(session: BreathingSessionEntity)

    @Query("SELECT * FROM breathing_sessions ORDER BY startedAtMillis, id")
    fun observeAll(): Flow<List<BreathingSessionEntity>>

    @Query("SELECT * FROM breathing_sessions ORDER BY startedAtMillis, id")
    suspend fun all(): List<BreathingSessionEntity>

    @Query("DELETE FROM breathing_sessions")
    suspend fun deleteAll()
}
