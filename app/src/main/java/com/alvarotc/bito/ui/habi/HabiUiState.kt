package com.alvarotc.bito.ui.habi

import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.domain.GardenEngine
import com.alvarotc.bito.domain.MoodEngine
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.equippedSetOf
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Lo que leen los Ajustes de Habi: el espectro del avatar (ánimo, personalidad y lo puesto) y el
 * jardín de la maqueta.
 */
data class HabiUiState(
    val spec: HabiSpec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
    // Pieza 1: el jardín de Habi, deducido del historial.
    val garden: GardenState = GardenState.INITIAL,
)

/**
 * Derives the Habi state from [state] as seen on [today]. Pure: no side effects, no storage, no
 * clock reads, so it is trivially testable and safe to call on every state change.
 */
fun buildHabiUiState(
    state: DomainState,
    owned: List<CustomizationItemEntity>,
    personality: Personality,
    today: LogicalDay,
    // Pieza 1. Por defecto, mediodía del día lógico, sin logros ni marca: los tests que no miran el
    // jardín no cambian. HabiViewModel pasa el reloj, los logros y la marca de DataStore.
    now: LocalDateTime = LocalDate.ofEpochDay(today.toLong()).atTime(12, 0),
    unlockedBadges: Set<String> = emptySet(),
    waterHighMark: Double = 0.0,
): HabiUiState {
    val lastActivityDay = StatsEngine.lastActivityDay(state)
    val mood = MoodEngine.moodOf(state, today, lastActivityDay)
    val placedObjects = owned.filter { it.category == CustomizationCategory.GARDEN_OBJECT && it.equipped }.map { it.itemId }
    val garden = GardenEngine.gardenOf(state, today, now, lastActivityDay, unlockedBadges, waterHighMark, placedObjects)
    val equipped = equippedSetOf(owned.filter { it.equipped }.map { it.itemId })
    return HabiUiState(spec = HabiSpec(mood, personality, equipped), garden = garden)
}
