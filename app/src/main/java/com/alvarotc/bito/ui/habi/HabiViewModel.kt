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
import com.alvarotc.bito.domain.model.Personality
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Backs the Habi screen: derives its state from the domain engines and the rewards/settings
 * stores. Its three writes ([setPersonality], [equip], [unequipDefault]) leave no trace for the
 * reconciler to reconcile — there is nothing derived from them to re-derive.
 */
class HabiViewModel(
    domainState: DomainStateRepository,
    private val rewards: RewardsRepository,
    private val settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // Overridable so tests can swap in their TestDispatcher — buildHabiUiState off Main (perf)
    // must not race a runTest's virtual scheduler the way the real Dispatchers.Default would.
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val uiState: StateFlow<HabiUiState> =
        combine(
            domainState.observe(),
            rewards.observeOwnedItems(),
            rewards.observeBalance(),
            settings.settings,
        ) { state, owned, balance, prefs ->
            val today = LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone())
            buildHabiUiState(state, owned, balance, prefs.personality, today)
        }.flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabiUiState())

    fun setPersonality(personality: Personality) {
        viewModelScope.launch { settings.update { it.copy(personality = personality) } }
    }

    fun equip(itemId: String) {
        viewModelScope.launch { rewards.equip(itemId) }
    }

    /** Equipping a default is really un-equipping its category — defaults have no DB row to equip. */
    fun unequipDefault(item: CatalogItem) {
        viewModelScope.launch { rewards.unequip(item.category) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    HabiViewModel(container.domainState, container.rewards, container.settings)
                }
            }
    }
}
