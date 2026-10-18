package com.alvarotc.bito.ui.habi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.Personality
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Backs the Habi state read by Ajustes: derives the avatar spec and the garden from the domain
 * engines and the rewards/settings stores, and runs the equip and personality writes. [setPersonality],
 * [equip] and [unequipDefault]/[unequip] leave no trace for the reconciler to reconcile — there is
 * nothing derived from them to re-derive.
 */
class HabiViewModel(
    private val domainState: DomainStateRepository,
    private val rewards: RewardsRepository,
    private val settings: SettingsRepository,
    private val habiSounds: HabiSounds,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // Overridable so tests can swap in their TestDispatcher — buildHabiUiState off Main (perf)
    // must not race a runTest's virtual scheduler the way the real Dispatchers.Default would.
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    // Un tick por minuto para que la luz del jardín siga a la hora sin cambios en los datos. Por
    // defecto un solo tick: los tests con advanceUntilIdle no pueden convivir con un bucle infinito.
    // La fábrica de abajo pasa minuteTicks().
    clockTicks: Flow<Unit> = flowOf(Unit),
) : ViewModel() {
    val uiState: StateFlow<HabiUiState> =
        combine(
            domainState.observe(),
            rewards.observeOwnedItems(),
            settings.settings,
            rewards.observeBadges(),
            clockTicks,
        ) { state, owned, prefs, badges, _ ->
            val nowMillis = now()
            val zoneId = zone()
            val today = LogicalDays.logicalDayOf(nowMillis, prefs.dayCutoffMinutes, zoneId)
            buildHabiUiState(
                state,
                owned,
                prefs.personality,
                today,
                now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId),
                unlockedBadges = badges.mapTo(mutableSetOf()) { it.badgeId },
                waterHighMark = prefs.gardenWaterHighMark,
            )
        }.flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabiUiState())

    fun setPersonality(personality: Personality) {
        viewModelScope.launch { settings.update { it.copy(personality = personality) } }
    }

    fun equip(itemId: String) {
        viewModelScope.launch { rewards.equip(itemId) }
    }

    /** Equipping a default is really un-equipping its category — defaults have no DB row to equip. */
    fun unequipDefault(item: CatalogItem) = unequip(item.category)

    /**
     * Taking off an equipped optional (pattern/upper/lower) — the exact same write as
     * [unequipDefault], kept under its own name so call sites don't have to pretend the item they
     * tapped is a "default" when it is really just being removed.
     */
    fun unequip(category: CustomizationCategory) {
        viewModelScope.launch { rewards.unequip(category) }
    }

    /** Tapping the stage avatar (T16): Habi's greeting cue, gated on the Ajustes toggle and ringer mode. */
    fun onAvatarTap() {
        habiSounds.play(HabiSound.GREETING)
    }

    /** Three quick pets in a row: the happier double-meow (same recording, different shape). */
    fun onPetStreak() {
        habiSounds.play(HabiSound.HAPPY)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    HabiViewModel(
                        container.domainState,
                        container.rewards,
                        container.settings,
                        container.habiSounds,
                        clockTicks = minuteTicks(),
                    )
                }
            }
    }
}

/** Un tick por minuto mientras alguien mire la pantalla de Habi (el `stateIn` corta al dejar de mirar). */
internal fun minuteTicks(): Flow<Unit> =
    flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }
