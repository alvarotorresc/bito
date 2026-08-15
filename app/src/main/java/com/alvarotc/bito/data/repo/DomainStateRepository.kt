package com.alvarotc.bito.data.repo

import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.DaySealEntity
import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.db.FreezerUseEntity
import com.alvarotc.bito.data.db.HabitEntity
import com.alvarotc.bito.data.db.PauseIntervalEntity
import com.alvarotc.bito.data.db.PointsLedgerEntity
import com.alvarotc.bito.data.db.TargetChangeEntity
import com.alvarotc.bito.data.db.toDomain
import com.alvarotc.bito.domain.model.DomainState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/**
 * Assembles the immutable [DomainState] snapshot the engine consumes. The
 * engine never talks to storage: callers observe this and pass now/today in.
 */
class DomainStateRepository(private val db: BitoDatabase) {
    @Suppress("UNCHECKED_CAST")
    fun observe(): Flow<DomainState> =
        combine(
            db.habitDao().observeAll(),
            db.targetChangeDao().observeAll(),
            db.entryDao().observeAll(),
            db.daySealDao().observeAll(),
            db.pauseIntervalDao().observeAll(),
            db.freezerUseDao().observeAll(),
            db.pointsLedgerDao().observeAll(),
        ) { parts ->
            DomainState(
                habits = (parts[0] as List<HabitEntity>).map { it.toDomain() },
                targetChanges = (parts[1] as List<TargetChangeEntity>).map { it.toDomain() },
                entries = (parts[2] as List<EntryEntity>).map { it.toDomain() },
                daySeals = (parts[3] as List<DaySealEntity>).map { it.toDomain() },
                pauseIntervals = (parts[4] as List<PauseIntervalEntity>).map { it.toDomain() },
                freezerUses = (parts[5] as List<FreezerUseEntity>).map { it.toDomain() },
                pointsLedger = (parts[6] as List<PointsLedgerEntity>).map { it.toDomain() },
            )
        }

    suspend fun snapshot(): DomainState = observe().first()
}
