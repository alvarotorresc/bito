package com.alvarotc.bito.data.repo

import androidx.room.withTransaction
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.data.db.PointsLedgerEntity
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.PointsEvent
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Points ledger, badges and Habi customization inventory. */
class RewardsRepository(private val db: BitoDatabase) {
    fun observeBalance(): Flow<Int> = db.pointsLedgerDao().observeBalance()

    /** Persists engine-derived grants as ledger movements. */
    suspend fun append(
        events: List<PointsEvent>,
        nowMillis: Long,
    ) = db.pointsLedgerDao().insertAll(
        events.map {
            PointsLedgerEntity(
                id = UUID.randomUUID().toString(),
                delta = it.delta,
                reason = it.reason,
                refId = it.refId,
                logicalDay = it.logicalDay,
                createdAtMillis = nowMillis,
            )
        },
    )

    suspend fun spend(
        delta: Int,
        reason: PointsReason,
        refId: String?,
        day: LogicalDay,
        nowMillis: Long,
    ) = db.pointsLedgerDao().insert(
        PointsLedgerEntity(UUID.randomUUID().toString(), delta, reason, refId, day, nowMillis),
    )

    suspend fun unlockBadge(
        badgeId: String,
        nowMillis: Long,
    ) = db.badgeDao().insert(BadgeEntity(badgeId, nowMillis))

    suspend fun acquire(item: CustomizationItemEntity) = db.customizationItemDao().upsert(item)

    suspend fun ownedItemIds(): Set<String> = db.customizationItemDao().all().mapTo(mutableSetOf()) { it.itemId }

    /** Achievement grants: insert-ignore so re-derivation never touches existing rows. */
    suspend fun grantItems(
        itemIds: Set<String>,
        nowMillis: Long,
    ) = db.customizationItemDao().insertAll(
        itemIds.mapNotNull { id ->
            HabiCatalog.byId(id)?.let { CustomizationItemEntity(id, it.category, nowMillis, equipped = false) }
        },
    )

    /** Equipping is exclusive per category (derived from the stored item). */
    suspend fun equip(itemId: String) =
        db.withTransaction {
            val item = db.customizationItemDao().byId(itemId) ?: return@withTransaction
            db.customizationItemDao().unequipCategory(item.category)
            db.customizationItemDao().upsert(item.copy(equipped = true))
        }
}
