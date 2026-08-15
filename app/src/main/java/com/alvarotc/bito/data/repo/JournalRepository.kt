package com.alvarotc.bito.data.repo

import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.DaySealEntity
import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.db.FreezerUseEntity
import com.alvarotc.bito.domain.model.LogicalDay

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
}
