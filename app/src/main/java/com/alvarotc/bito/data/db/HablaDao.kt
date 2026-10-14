package com.alvarotc.bito.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HablaDao {
    // Memoria de Habi

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarEvento(evento: HabiMemoriaEntity)

    @Query("SELECT * FROM habi_memoria WHERE dia >= :dia ORDER BY dia, creado_millis, id")
    fun eventosDesde(dia: Int): Flow<List<HabiMemoriaEntity>>

    @Query("DELETE FROM habi_memoria WHERE dia < :dia")
    suspend fun borrarEventosAntesDe(dia: Int)

    @Query("SELECT * FROM habi_memoria ORDER BY dia, creado_millis, id")
    suspend fun todosEventos(): List<HabiMemoriaEntity>

    // Lo gordo

    @Query("DELETE FROM lo_gordo WHERE dia = :dia")
    suspend fun borrarGordoDelDia(dia: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarGordo(filas: List<LoGordoEntity>)

    @Transaction
    suspend fun reemplazarGordo(
        dia: Int,
        filas: List<LoGordoEntity>,
    ) {
        borrarGordoDelDia(dia)
        insertarGordo(filas)
    }

    @Query("SELECT * FROM lo_gordo WHERE dia IN (:dias) ORDER BY dia, posicion")
    fun gordoDe(dias: List<Int>): Flow<List<LoGordoEntity>>

    @Query("DELETE FROM lo_gordo WHERE ref_id = :refId")
    suspend fun borrarGordoDeRef(refId: String)

    @Query("SELECT * FROM lo_gordo ORDER BY dia, posicion")
    suspend fun todoGordo(): List<LoGordoEntity>

    @Query("DELETE FROM habi_memoria")
    suspend fun vaciarMemoria()

    @Query("DELETE FROM lo_gordo")
    suspend fun vaciarGordo()

    @Transaction
    suspend fun reemplazarMemoriaYGordo(
        eventos: List<HabiMemoriaEntity>,
        gordo: List<LoGordoEntity>,
    ) {
        vaciarMemoria()
        vaciarGordo()
        eventos.forEach { insertarEvento(it) }
        insertarGordo(gordo)
    }

    // Avisos aceptados

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertarAviso(aviso: AvisoAceptadoEntity)

    @Update
    suspend fun actualizarAviso(aviso: AvisoAceptadoEntity)

    @Query("SELECT * FROM aviso_aceptado WHERE id = :id")
    suspend fun aviso(id: String): AvisoAceptadoEntity?

    @Query("SELECT * FROM aviso_aceptado WHERE dia = :dia ORDER BY minuto, id")
    fun avisosDe(dia: Int): Flow<List<AvisoAceptadoEntity>>

    @Query("SELECT * FROM aviso_aceptado WHERE estado = 'PENDIENTE' ORDER BY dia, minuto, id")
    suspend fun avisosPendientes(): List<AvisoAceptadoEntity>

    @Query("DELETE FROM aviso_aceptado WHERE habit_id = :habitId")
    suspend fun borrarAvisosDeHabito(habitId: String)

    @Query("DELETE FROM aviso_aceptado")
    suspend fun vaciarAvisos()

    // Propuesta vigente

    @Query("SELECT * FROM propuesta_vigente WHERE ambito = :ambito AND habit_id = :habitId")
    suspend fun vigente(
        ambito: String,
        habitId: String,
    ): PropuestaVigenteEntity?

    @Query("SELECT * FROM propuesta_vigente ORDER BY ambito, habit_id")
    fun vigentes(): Flow<List<PropuestaVigenteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarVigente(vigente: PropuestaVigenteEntity)

    @Query("DELETE FROM propuesta_vigente WHERE ambito = :ambito AND habit_id = :habitId")
    suspend fun borrarVigente(
        ambito: String,
        habitId: String,
    )

    @Query("DELETE FROM propuesta_vigente")
    suspend fun vaciarVigentes()

    // Usos de trozos

    @Insert
    suspend fun insertarUsos(usos: List<HablaUsadaEntity>)

    @Query("SELECT * FROM habla_usada WHERE usado_millis >= :millis ORDER BY usado_millis, id")
    fun usosDesde(millis: Long): Flow<List<HablaUsadaEntity>>

    @Query("DELETE FROM habla_usada WHERE usado_millis < :millis")
    suspend fun borrarUsosAntesDe(millis: Long)
}
