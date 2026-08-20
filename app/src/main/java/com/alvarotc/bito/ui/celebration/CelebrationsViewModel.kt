package com.alvarotc.bito.ui.celebration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.ui.habi.HabiSound
import com.alvarotc.bito.ui.habi.HabiSounds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Backs the global celebration sheets (perfect day + newly unlocked badges). Read-only state plus
 * the two dismiss writes that mark a celebration seen — no reconciler here: this VM never derives
 * new grants, it only reflects what the other writers (Today, quick actions, the widget) already
 * reconciled.
 */
class CelebrationsViewModel(
    domainState: DomainStateRepository,
    private val settings: SettingsRepository,
    rewards: RewardsRepository,
    private val habiSounds: HabiSounds,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // Overridable so tests can swap in their TestDispatcher — buildCelebrationsUiState off Main
    // (perf) must not race a runTest's virtual scheduler the way the real Dispatchers.Default would.
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val uiState: StateFlow<CelebrationsUiState> =
        combine(
            domainState.observe(),
            settings.settings,
            rewards.observeBadges(),
            rewards.observeOwnedItems(),
        ) { state, prefs, badges, owned ->
            buildCelebrationsUiState(state, prefs, badges, owned, todayOf(prefs))
        }.flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CelebrationsUiState())

    private fun todayOf(prefs: Settings) = LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone())

    /** Marks today as celebrated so the perfect-day sheet does not reappear. */
    fun dismissPerfectDay() {
        viewModelScope.launch {
            val today = todayOf(settings.settings.first())
            settings.update { it.copy(perfectDayCelebratedDay = today) }
        }
    }

    /** Marks every badge unlocked up to now as seen so the badges sheet does not reappear. */
    fun dismissBadges() {
        viewModelScope.launch {
            settings.update { it.copy(badgesSeenUntilMillis = now()) }
        }
    }

    // R7: CelebrationsViewModel outlives the composition (survives Activity recreation — e.g.
    // rotation, since MainActivity has no configChanges), but BitoNavHost's cue LaunchedEffect is
    // composition-scoped and keyed on (perfectDayPending, hasNewBadges, currentRoute). Rotating
    // while a sheet is pending re-runs that effect with the same keys, which would call cue()
    // again for the very same sheet. Idempotency has to live here, in the survivor, not in the
    // effect.
    private var lastCuedSignature: String? = null

    /** Exposed for tests — [HabiSounds] has no seam to assert a sound actually played. */
    internal val lastCued: String?
        get() = lastCuedSignature

    /**
     * Habi's celebration cue for the sheet. Pure presentation — no [settings] write. Idempotent
     * per pending sheet: plays at most once for a given [CelebrationsUiState.perfectDayPending]
     * or [CelebrationsUiState.newBadges] set, no matter how many times the host effect that calls
     * this re-runs (e.g. across a rotation) while that same sheet stays pending.
     */
    fun cue() {
        val state = uiState.value
        val signature =
            when {
                state.perfectDayPending -> "perfect-day"
                state.newBadges.isNotEmpty() -> "badges:" + state.newBadges.joinToString(",") { it.id }
                else -> return
            }
        if (signature != lastCuedSignature) {
            lastCuedSignature = signature
            habiSounds.play(HabiSound.CELEBRATION)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    CelebrationsViewModel(container.domainState, container.settings, container.rewards, container.habiSounds)
                }
            }
    }
}
