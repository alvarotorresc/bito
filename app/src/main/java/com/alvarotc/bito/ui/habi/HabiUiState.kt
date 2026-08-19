package com.alvarotc.bito.ui.habi

import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.domain.HabiEngine
import com.alvarotc.bito.domain.MoodEngine
import com.alvarotc.bito.domain.PointsEngine
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.StoreItemState
import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.equippedSetOf

/** One store card: a catalog entry paired with its derived purchase/equip state. */
data class StoreEntry(val item: CatalogItem, val state: StoreItemState)

/**
 * Snapshot the Habi screen renders: the avatar spec (mood + personality + what's worn), the points
 * balance, freezer inventory, and the store grouped by customization axis — T12 is the one that
 * actually renders [store]; T11 only derives it.
 */
data class HabiUiState(
    val spec: HabiSpec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
    val balance: Int = 0,
    val freezersOwned: Int = 0,
    val store: Map<CustomizationCategory, List<StoreEntry>> = emptyMap(),
    val loading: Boolean = true,
)

/** Axis order the store's pill row follows (GUIA: Colores · Patrones · Ojos · Arriba · Abajo). */
private val StoreAxisOrder =
    listOf(
        CustomizationCategory.BODY_COLOR,
        CustomizationCategory.PATTERN,
        CustomizationCategory.EYE_COLOR,
        CustomizationCategory.UPPER,
        CustomizationCategory.LOWER,
    )

/**
 * Derives the Habi screen state from [state] as seen on [today]. Pure — no side effects, no
 * storage, no clock reads — so it is trivially testable and safe to call on every state change.
 */
fun buildHabiUiState(
    state: DomainState,
    owned: List<CustomizationItemEntity>,
    balance: Int,
    personality: Personality,
    today: LogicalDay,
): HabiUiState {
    val lastActivityDay = StatsEngine.lastActivityDay(state)
    val mood = MoodEngine.moodOf(state, today, lastActivityDay)
    val equipped = equippedSetOf(owned.filter { it.equipped }.map { it.itemId })
    val ownedIds = owned.mapTo(mutableSetOf()) { it.itemId }

    val itemsByCategory = HabiCatalog.all.groupBy { it.category }
    val store =
        StoreAxisOrder.associateWith { category ->
            // sortedByDescending is a stable sort, so this only promotes the default item to the
            // front of its axis — the rest of the catalog's own order is left untouched.
            (itemsByCategory[category] ?: emptyList())
                .sortedByDescending { it.default }
                .map { item -> StoreEntry(item, HabiEngine.storeStateOf(item, ownedIds, equipped, balance)) }
        }

    return HabiUiState(
        spec = HabiSpec(mood, personality, equipped),
        balance = balance,
        freezersOwned = PointsEngine.freezersOwned(state),
        store = store,
        loading = false,
    )
}
