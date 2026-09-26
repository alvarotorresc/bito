package com.alvarotc.bito.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.alvarotc.bito.domain.model.BreathingMode

/**
 * Una sesion de respiracion ya hecha (spec S4.1). Sin indices ni claves foraneas: la tabla no
 * cuelga de nada y se lee entera. Room guarda el enum como TEXT (por nombre) y el Boolean como
 * INTEGER, sin TypeConverter, como ya hace con TaskStatus.
 *
 * El ORDEN de estos campos es el orden de las columnas del DDL que genera Room y que copia
 * MIGRATION_2_3. Reordenarlos rompe la migracion.
 */
@Entity(tableName = "breathing_sessions")
data class BreathingSessionEntity(
    @PrimaryKey val id: String,
    val mode: BreathingMode,
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val completed: Boolean,
)
