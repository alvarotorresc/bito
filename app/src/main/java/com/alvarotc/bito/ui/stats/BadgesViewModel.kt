package com.alvarotc.bito.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId

/**
 * Backs the full Badges list screen: derives its state from the unlocked badges plus the
 * configured day cutoff (unlock dates must honor it, same as [RecordsViewModel]). Read-only.
 */
class BadgesViewModel(
    rewards: RewardsRepository,
    settings: SettingsRepository,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // See StatsViewModel's defaultDispatcher for why this is overridable.
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val uiState: StateFlow<BadgesUiState> =
        combine(rewards.observeBadges(), settings.settings) { badges, prefs ->
            buildBadgesUiState(badges, prefs.dayCutoffMinutes, zone())
        }.flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BadgesUiState())

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    BadgesViewModel(container.rewards, container.settings)
                }
            }
    }
}
