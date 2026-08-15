package com.alvarotc.bito.data.repo

import androidx.room.withTransaction
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.DaySealEntity
import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.db.FreezerUseEntity
import com.alvarotc.bito.domain.model.LogicalDay
import java.util.UUID

/** Day-to-day history: entries, day seals and spent freezers. */
class JournalRepository(private val db: BitoDatabase) {
    suspend fun log(entry: EntryEntity) = db.entryDao().insert(entry)

    suspend fun remove(entryId: String) = db.entryDao().delete(entryId)

    /** Idempotent: the first seal of a day wins. */
    suspend fun sealDay(
        day: LogicalDay,
        nowMillis: Long,
    ) = db.daySealDao().insert(DaySealEntity(day, nowMillis))

    /** False when that habit-day was already protected (unique index). */
    suspend fun useFreezer(use: FreezerUseEntity): Boolean = db.freezerUseDao().insert(use) != -1L

    /** Exact-value logging: replaces the day's entries with a single total. */
    suspend fun setDayTotal(
        habitId: String,
        day: LogicalDay,
        value: Int,
        nowMillis: Long,
    ) = db.withTransaction {
        db.entryDao().forHabitInRange(habitId, day, day).forEach { db.entryDao().delete(it.id) }
        if (value > 0) {
            db.entryDao().insert(EntryEntity(UUID.randomUUID().toString(), habitId, day, value, nowMillis))
        }
    }
}
