package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.LogicalDay
import java.time.LocalDate

/** Store item ladder: the first matching state wins (equipped > owned > buyable > locked). */
sealed interface StoreItemState {
    data object Equipped : StoreItemState

    data object Owned : StoreItemState

    data object Affordable : StoreItemState

    data class MissingPoints(val missing: Int) : StoreItemState

    data class Locked(val requiredStreak: Int) : StoreItemState

    /** Un artículo de temporada que no se tiene y hoy no está a la venta (§7). */
    data object OutOfSeason : StoreItemState
}

/**
 * Habi's achievement exclusives and store states. Exclusives derive from streak
 * milestones any habit reached (in its own period unit); like points, grants are
 * append-only — [missingExclusives] never proposes removals (docs/05 §4).
 */
object HabiEngine {
    private val milestoneToItem: Map<Int, String> =
        HabiCatalog.exclusives.associate { it.requiredStreak!! to it.id }

    fun earnedExclusives(
        state: DomainState,
        today: LogicalDay,
    ): Set<String> =
        state.habits
            .flatMap { Streaks.reachedMilestones(state, it, today, milestoneToItem.keys) }
            .mapTo(mutableSetOf()) { milestoneToItem.getValue(it) }

    fun missingExclusives(
        earned: Set<String>,
        ownedIds: Set<String>,
    ): Set<String> = earned - ownedIds

    /** La escalera de la tienda: puesto > tenido > fuera de temporada > a la venta > bloqueado. */
    fun storeStateOf(
        item: CatalogItem,
        ownedIds: Set<String>,
        equipped: EquippedSet,
        balance: Int,
        date: LocalDate,
    ): StoreItemState {
        val owned = item.default || item.id in ownedIds
        return when {
            item.id in equipped.worn -> StoreItemState.Equipped
            owned -> StoreItemState.Owned
            !HabiCatalog.isOnSale(item, date) -> StoreItemState.OutOfSeason
            item.price != null ->
                if (balance >= item.price) StoreItemState.Affordable else StoreItemState.MissingPoints(item.price - balance)
            else -> StoreItemState.Locked(item.requiredStreak!!)
        }
    }
}
