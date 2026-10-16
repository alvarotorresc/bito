package com.alvarotc.bito.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Las sesiones no se editan: se guardan al cerrarse, se leen por dia y se borran con su objetivo. */
@Dao
interface SesionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sesion: SesionEntity)

    @Query("SELECT * FROM sesiones WHERE dia = :dia ORDER BY inicio_millis, id")
    fun observeDeDia(dia: Int): Flow<List<SesionEntity>>

    @Query("SELECT COALESCE(SUM(minutos_foco), 0) FROM sesiones WHERE objetivo_id = :objetivoId AND dia = :dia")
    suspend fun minutosDe(
        objetivoId: String,
        dia: Int,
    ): Int

    @Query("DELETE FROM sesiones WHERE objetivo_id = :objetivoId")
    suspend fun borrarDe(objetivoId: String)

    @Query("SELECT * FROM sesiones ORDER BY inicio_millis, id")
    suspend fun all(): List<SesionEntity>

    @Query("DELETE FROM sesiones")
    suspend fun deleteAll()
}
