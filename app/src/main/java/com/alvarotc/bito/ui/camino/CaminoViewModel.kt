package com.alvarotc.bito.ui.camino

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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

/** Respalda Camino: deriva su estado de los motores del dominio. Solo lee: no escribe ni reconcilia. */
class CaminoViewModel(
    domainState: DomainStateRepository,
    settings: SettingsRepository,
    rewards: RewardsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // Reemplazable para que los tests usen su TestDispatcher y no compitan con el Default real.
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val uiState: StateFlow<CaminoUiState> =
        combine(
            domainState.observe(),
            settings.settings,
            rewards.observeOwnedItems(),
            rewards.observeBadges(),
        ) { state, prefs, owned, badges ->
            val zoneId = zone()
            val today = LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zoneId)
            buildCaminoUiState(state, prefs.personality, today, owned, badges, prefs.dayCutoffMinutes, zoneId)
        }.flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaminoUiState())

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    CaminoViewModel(container.domainState, container.settings, container.rewards)
                }
            }
    }
}
