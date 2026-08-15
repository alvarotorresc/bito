package com.alvarotc.bito.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One log record. value: 1 for checks, amount for COUNT, minutes for DURATION. */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId", "logicalDay")],
)
data class EntryEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val logicalDay: Int,
    val value: Int,
    val createdAtMillis: Long,
)

/** Seals a whole logical day. First seal wins (INSERT OR IGNORE in the DAO). */
@Entity(tableName = "day_seals")
data class DaySealEntity(
    @PrimaryKey val logicalDay: Int,
    val sealedAtMillis: Long,
)

/** A spent streak freezer: one habit, one concrete protected day. */
@Entity(
    tableName = "freezer_uses",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId", "protectedDay", unique = true)],
)
data class FreezerUseEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val protectedDay: Int,
    val usedAtMillis: Long,
)
