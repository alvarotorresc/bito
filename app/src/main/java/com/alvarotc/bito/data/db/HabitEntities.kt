package com.alvarotc.bito.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Period

/** Time-of-day slot for list views and reminders ("franja"). */
enum class TimeBucket { MORNING, AFTERNOON, EVENING }

/**
 * Storage row for a habit (tech doc §3). Carries scheduling/presentation
 * fields the pure domain Habit does not need. Logical days are stored as
 * facts captured at event time (rule E7: never recomputed from millis).
 * At most one of [timeBucket] / [timeOfDayMinutes] is set.
 */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val metric: Metric,
    val period: Period,
    val direction: Direction,
    val target: Int,
    val unit: String?,
    val logMode: LogMode,
    val step: Int,
    val timeBucket: TimeBucket?,
    val timeOfDayMinutes: Int?,
    val reminderMinutes: Int?,
    val status: HabitStatus,
    val createdAtMillis: Long,
    val createdOnDay: Int,
    val archivedAtMillis: Long?,
    val archivedOnDay: Int?,
    val sortOrder: Int,
)

/** Target history, rule E2. One row per (habit, effective-from day). */
@Entity(
    tableName = "target_changes",
    primaryKeys = ["habitId", "effectiveFromDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class TargetChangeEntity(
    val habitId: String,
    val effectiveFromDay: Int,
    val target: Int,
)

/** A pause; endDay null = still open. Both bounds inclusive. */
@Entity(
    tableName = "pause_intervals",
    primaryKeys = ["habitId", "startDay"],
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("habitId")],
)
data class PauseIntervalEntity(
    val habitId: String,
    val startDay: Int,
    val endDay: Int?,
    val note: String?,
)
