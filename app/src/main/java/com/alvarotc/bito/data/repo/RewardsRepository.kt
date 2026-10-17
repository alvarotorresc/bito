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
import com.alvarotc.bito.domain.model.PointsLedgerEntry
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID

/** Points ledger, badges and Habi customization inventory. */
class RewardsRepository(private val db: BitoDatabase) {
    fun observeBalance(): Flow<Int> = db.pointsLedgerDao().observeBalance()

    fun observeRecentMovements(limit: Int): Flow<List<PointsLedgerEntry>> =
        db.pointsLedgerDao().observeRecent(limit).map { filas -> filas.map { it.toDomain() } }

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
            // Los objetos de jardín conviven: poner uno no quita los demás.
            if (item.category != CustomizationCategory.GARDEN_OBJECT) db.customizationItemDao().unequipCategory(item.category)
            db.customizationItemDao().upsert(item.copy(equipped = true))
        }

    /**
     * Pone [itemId] quitando antes el disfraz, en una sola transacción: la tienda lo usa al tocar una pieza
     * de una ranura que el disfraz tapa. Lo demás que llevaba Habi se queda puesto.
     */
    suspend fun equipOverCostume(itemId: String) =
        db.withTransaction {
            val catalogo = HabiCatalog.byId(itemId) ?: return@withTransaction
            db.customizationItemDao().unequipCategory(CustomizationCategory.COSTUME)
            db.customizationItemDao().unequipCategory(catalogo.category)
            // Una pieza de serie no tiene fila: con la categoría desequipada ya es la que se ve.
            db.customizationItemDao().byId(itemId)?.let { db.customizationItemDao().upsert(it.copy(equipped = true)) }
        }

    /**
     * Compra un congelador: comprueba el saldo y escribe el asiento `BUY_FREEZER` con un [refId] propio en una
     * sola transacción. Devuelve ese `refId` (lo que luego recibe [undoFreezerPurchase]) o null, sin escribir,
     * si el saldo no llega a [price].
     */
    suspend fun buyFreezer(
        price: Int,
        day: LogicalDay,
        nowMillis: Long,
    ): String? =
        db.withTransaction {
            val ledger = db.pointsLedgerDao().all().map { it.toDomain() }
            if (!PointsEngine.canSpend(ledger, price)) return@withTransaction null
            val refId = UUID.randomUUID().toString()
            db.pointsLedgerDao().insert(
                PointsLedgerEntity(UUID.randomUUID().toString(), -price, PointsReason.BUY_FREEZER, refId, day, nowMillis),
            )
            refId
        }

    /**
     * Buys [item] atomically: ledger spend + acquired row, equipped on the spot
     * (docs/05 §4). Returns false — writing nothing — when the item is not
     * purchasable, is already owned, is out of season, or the balance does not cover it.
     */
    suspend fun purchase(
        item: CatalogItem,
        day: LogicalDay,
        nowMillis: Long,
    ): Boolean =
        db.withTransaction {
            val price = item.price ?: return@withTransaction false
            // Un disfraz solo se compra en su temporada (D5); el día lógico decide, igual que en la tienda.
            if (!HabiCatalog.isOnSale(item, LocalDate.ofEpochDay(day.toLong()))) return@withTransaction false
            if (db.customizationItemDao().byId(item.id) != null) return@withTransaction false
            val ledger = db.pointsLedgerDao().all().map { it.toDomain() }
            if (!PointsEngine.canSpend(ledger, price)) return@withTransaction false
            db.pointsLedgerDao().insert(
                PointsLedgerEntity(UUID.randomUUID().toString(), -price, PointsReason.BUY_ITEM, item.id, day, nowMillis),
            )
            // Comprar un objeto de jardín no desequipa la categoría: la regadera no quita el banco (D5).
            if (item.category != CustomizationCategory.GARDEN_OBJECT) db.customizationItemDao().unequipCategory(item.category)
            db.customizationItemDao().upsert(CustomizationItemEntity(item.id, item.category, nowMillis, equipped = true))
            true
        }

    /** Compra [item] y, en la misma transacción, quita el disfraz que tapaba su ranura. */
    suspend fun purchaseOverCostume(
        item: CatalogItem,
        day: LogicalDay,
        nowMillis: Long,
    ): Boolean =
        db.withTransaction {
            val comprada = purchase(item, day, nowMillis)
            if (comprada && item.category != CustomizationCategory.COSTUME) {
                db.customizationItemDao().unequipCategory(
                    CustomizationCategory.COSTUME,
                )
            }
            comprada
        }

    /** Los objetos de jardín no se quitan: la categoría entera no se desequipa. */
    suspend fun unequip(category: CustomizationCategory) {
        if (category != CustomizationCategory.GARDEN_OBJECT) db.customizationItemDao().unequipCategory(category)
    }

    /**
     * Deshace la compra de un congelador (el aviso «Deshacer»): borra el asiento `BUY_FREEZER` de
     * [refId] en una transacción, así el saldo y el contador (derivados del libro) vuelven solos.
     * Devuelve false, sin escribir, si no existe o si ya se gastó: con los usos registrados no quedaría
     * ningún congelador sin usar tras quitar esa compra.
     */
    suspend fun undoFreezerPurchase(refId: String): Boolean =
        db.withTransaction {
            val ledger = db.pointsLedgerDao().all()
            if (ledger.none { it.reason == PointsReason.BUY_FREEZER && it.refId == refId }) return@withTransaction false
            val compradas = ledger.count { it.reason == PointsReason.BUY_FREEZER }
            val usos = db.freezerUseDao().all().size
            if (usos > compradas - 1) return@withTransaction false
            db.pointsLedgerDao().deleteByReasonRef(PointsReason.BUY_FREEZER, refId) > 0
        }

    fun observeOwnedItems(): Flow<List<CustomizationItemEntity>> = db.customizationItemDao().observeAll()
}
