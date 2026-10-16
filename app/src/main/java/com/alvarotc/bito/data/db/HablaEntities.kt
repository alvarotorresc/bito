package com.alvarotc.bito.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room 5 (spec 10.1): memoria de Habi, lo gordo, avisos aceptados, propuesta vigente y usos de
 * trozos. El ORDEN de los campos es el orden de las columnas del DDL que genera Room y que copia
 * MIGRATION_4_5. Reordenarlos o renombrarlos rompe la migracion.
 */

/** Un hecho que Habi recuerda (como dijiste que fue el dia, lo que aceptaste...). */
@Entity(tableName = "habi_memoria", indices = [Index(value = ["tipo", "dia"])])
data class HabiMemoriaEntity(
    @PrimaryKey val id: String,
    val tipo: String,
    val dia: Int,
    val ref: String?,
    val valor: Int?,
    @ColumnInfo(name = "creado_millis") val creadoMillis: Long,
)

/** Lo gordo de un dia: hasta tres elementos por dia, en `posicion` 0 a 2. */
@Entity(
    tableName = "lo_gordo",
    primaryKeys = ["dia", "posicion"],
    indices = [Index(value = ["ref_id"])],
)
data class LoGordoEntity(
    val dia: Int,
    val posicion: Int,
    /** `HABITO` o `TAREA`. */
    val tipo: String,
    @ColumnInfo(name = "ref_id") val refId: String,
    @ColumnInfo(name = "creado_millis") val creadoMillis: Long,
)

/** Un aviso que el usuario acepto de una propuesta. Solo vale para hoy. */
@Entity(tableName = "aviso_aceptado", indices = [Index(value = ["dia"])])
data class AvisoAceptadoEntity(
    @PrimaryKey val id: String,
    val dia: Int,
    @ColumnInfo(name = "habit_id") val habitId: String,
    val minuto: Int,
    val pospuesto: Int,
    /** `PENDIENTE`, `LANZADO`, `HECHO` o `CANCELADO`. */
    val estado: String,
    @ColumnInfo(name = "propuesta_id") val propuestaId: String,
    @ColumnInfo(name = "creado_millis") val creadoMillis: Long,
)

/** La propuesta que se esta enseñando por ambito (y por habito en la ficha). */
@Entity(tableName = "propuesta_vigente", primaryKeys = ["ambito", "habit_id"])
data class PropuestaVigenteEntity(
    val ambito: String,
    /** Vacio fuera de la ficha. */
    @ColumnInfo(name = "habit_id") val habitId: String,
    val dia: Int,
    val situacion: String,
    val ref: String?,
    /** Claves `bolsa:indice` separadas por `;`. */
    val trozos: String,
    /** JSON plano. */
    val huecos: String,
    val acciones: String,
    val hora: Int?,
    @ColumnInfo(name = "creado_millis") val creadoMillis: Long,
)

/** Un trozo de texto que Habi ya ha dicho, para no repetirlo. */
@Entity(tableName = "habla_usada", indices = [Index(value = ["voz", "bolsa", "usado_millis"])])
data class HablaUsadaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val voz: String,
    val bolsa: String,
    val indice: Int,
    val combinacion: String,
    val dia: Int,
    @ColumnInfo(name = "usado_millis") val usadoMillis: Long,
    val situacion: String?,
)
