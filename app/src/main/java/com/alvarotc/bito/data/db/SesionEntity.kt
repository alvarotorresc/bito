package com.alvarotc.bito.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room 6 (spec 14): una sesion de foco ya cerrada. El ORDEN de los campos es el orden de las
 * columnas del DDL que genera Room y que copia MIGRATION_5_6. Reordenarlos o renombrarlos rompe
 * la migracion.
 */

/** Una sesion terminada. `objetivo_id` es null cuando la sesion fue libre. */
@Entity(tableName = "sesiones", indices = [Index(value = ["objetivo_id", "dia"])])
data class SesionEntity(
    @PrimaryKey val id: String,
    /** `TAREA`, `HABITO` o `LIBRE`. */
    @ColumnInfo(name = "objetivo_tipo") val objetivoTipo: String,
    @ColumnInfo(name = "objetivo_id") val objetivoId: String?,
    val dia: Int,
    @ColumnInfo(name = "inicio_millis") val inicioMillis: Long,
    @ColumnInfo(name = "fin_millis") val finMillis: Long,
    @ColumnInfo(name = "minutos_foco") val minutosFoco: Int,
    @ColumnInfo(name = "bloques_hechos") val bloquesHechos: Int,
    @ColumnInfo(name = "bloques_plan") val bloquesPlan: Int,
    /** Un `ResultadoSesion` por nombre. */
    val resultado: String,
)
