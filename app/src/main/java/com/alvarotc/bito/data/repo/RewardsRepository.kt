package com.alvarotc.bito.data.repo

import androidx.room.withTransaction
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.BitoDatabase
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.data.db.PointsLedgerEntity
import com.alvarotc.bito.data.db.toDomain
import com.alvarotc.bito.domain.PointsEngine
import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.domain.model.CustomizationCategory
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

    /** Achievement grants: insert-ignore so re-derivation never touches an already-unlocked badge. */
    suspend fun unlockBadges(
        ids: Set<String>,
        nowMillis: Long,
    ) = db.badgeDao().insertAll(ids.map { BadgeEntity(it, nowMillis) })

    suspend fun unlockedBadgeIds(): Set<String> = db.badgeDao().all().mapTo(mutableSetOf()) { it.badgeId }

    fun observeBadges(): Flow<List<BadgeEntity>> = db.badgeDao().observeAll()

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

    /**
     * Buys [item] atomically: ledger spend + acquired row, equipped on the spot
     * (docs/05 §4). Returns false — writing nothing — when the item is not
     * purchasable, is already owned, or the balance does not cover it.
     */
    suspend fun purchase(
        item: CatalogItem,
        day: LogicalDay,
        nowMillis: Long,
    ): Boolean =
        db.withTransaction {
            val price = item.price ?: return@withTransaction false
            if (db.customizationItemDao().byId(item.id) != null) return@withTransaction false
            val ledger = db.pointsLedgerDao().all().map { it.toDomain() }
            if (!PointsEngine.canSpend(ledger, price)) return@withTransaction false
            db.pointsLedgerDao().insert(
                PointsLedgerEntity(UUID.randomUUID().toString(), -price, PointsReason.BUY_ITEM, item.id, day, nowMillis),
            )
            db.customizationItemDao().unequipCategory(item.category)
            db.customizationItemDao().upsert(CustomizationItemEntity(item.id, item.category, nowMillis, equipped = true))
            true
        }

    suspend fun unequip(category: CustomizationCategory) = db.customizationItemDao().unequipCategory(category)

    fun observeOwnedItems(): Flow<List<CustomizationItemEntity>> = db.customizationItemDao().observeAll()
}
