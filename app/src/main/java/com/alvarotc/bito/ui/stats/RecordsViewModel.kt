package com.alvarotc.bito.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.repo.DomainStateRepository
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

/** Backs the Records screen: derives its state from the domain engines. Read-only, same shape as [StatsViewModel]. */
class RecordsViewModel(
    domainState: DomainStateRepository,
    settings: SettingsRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // See StatsViewModel's defaultDispatcher for why this is overridable.
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val uiState: StateFlow<RecordsUiState> =
        combine(domainState.observe(), settings.settings) { state, prefs ->
            val today = LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone())
            buildRecordsUiState(state, today)
        }.flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordsUiState())

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    RecordsViewModel(container.domainState, container.settings)
                }
            }
    }
}
