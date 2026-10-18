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
 * engines and the rewards/settings stores, and runs the personality write. [setPersonality] leaves
 * no trace for the reconciler to reconcile: there is nothing derived from it to re-derive.
 */
class HabiViewModel(
    private val domainState: DomainStateRepository,
    private val rewards: RewardsRepository,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // Overridable so tests can swap in their TestDispatcher, since buildHabiUiState off Main (perf)
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

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    HabiViewModel(
                        container.domainState,
                        container.rewards,
                        container.settings,
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
