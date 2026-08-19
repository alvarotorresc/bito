package com.alvarotc.bito.ui.habi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.PointsEngine
import com.alvarotc.bito.domain.model.CatalogItem
import com.alvarotc.bito.domain.model.CustomizationCategory
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.HabiCatalog
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.PointsReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID

/**
 * Backs the Habi screen: derives its state from the domain engines and the rewards/settings
 * stores, and runs the store's writes. [setPersonality], [equip] and [unequipDefault]/[unequip]
 * leave no trace for the reconciler to reconcile — there is nothing derived from them to
 * re-derive. [purchase] is the same: a cosmetic buy is entirely self-contained inside
 * [RewardsRepository.purchase]'s own transaction, so it skips [write] too. [buyFreezer] is the
 * one exception — moved in from [com.alvarotc.bito.ui.detail.DetailViewModel] (docs/05 §1), it
 * keeps the reconcile-after-write invariant that screen always upheld.
 */
class HabiViewModel(
    private val domainState: DomainStateRepository,
    private val rewards: RewardsRepository,
    private val settings: SettingsRepository,
    private val reconciler: PointsReconciler,
    private val habiSounds: HabiSounds,
    private val economy: EconomyConfig = EconomyConfig(),
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // Overridable so tests can swap in their TestDispatcher — buildHabiUiState off Main (perf)
    // must not race a runTest's virtual scheduler the way the real Dispatchers.Default would.
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    // Ephemeral, UI-only: the item Habi is trying on before buying it. Combined into `uiState`
    // purely so the builder can render it live on the spec — it never reaches the DB.
    private val previewItemId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HabiUiState> =
        combine(
            domainState.observe(),
            rewards.observeOwnedItems(),
            rewards.observeBalance(),
            settings.settings,
            previewItemId,
        ) { state, owned, balance, prefs, preview ->
            val today = LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone())
            buildHabiUiState(state, owned, balance, prefs.personality, today, preview, prefs.userName, economy.freezerPrice)
        }.flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabiUiState())

    private fun todayOf(prefs: Settings) = LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone())

    /** Every mutation that needs one recomputes grants right after, against the CURRENT today. */
    private fun write(block: suspend (today: LogicalDay, nowMillis: Long) -> Unit) =
        viewModelScope.launch {
            val today = todayOf(settings.settings.first())
            val nowMillis = now()
            block(today, nowMillis)
            reconciler.reconcile(today, nowMillis)
        }

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

    /** Sets/clears what Habi is trying on in the store. Ephemeral UI state only — never touches the DB. */
    fun preview(itemId: String?) {
        previewItemId.value = itemId
    }

    /** Tapping the stage avatar (T16): Habi's greeting cue, gated on the Ajustes toggle and ringer mode. */
    fun onAvatarTap() {
        habiSounds.play(HabiSound.GREETING)
    }

    /**
     * Buys the previewed item. On success the preview clears — [RewardsRepository.purchase]
     * already equips it atomically, so there is nothing left to reconcile. This is
     * belt-and-suspenders in the wired UI: `PurchaseSheet`'s Comprar button already calls
     * `onDismiss()` (== `preview(null)`) synchronously the moment it's tapped, and it's disabled
     * whenever a refusal (already owned, unaffordable) would happen — so a real refusal is never
     * reachable through that button. The preview-on-success clearing here only matters to a
     * caller that invokes [purchase] directly without going through that gate (e.g. a test).
     */
    fun purchase(itemId: String) {
        val item = HabiCatalog.byId(itemId) ?: return
        viewModelScope.launch {
            val today = todayOf(settings.settings.first())
            if (rewards.purchase(item, today, now())) {
                preview(null)
                habiSounds.play(HabiSound.PURCHASE)
            }
        }
    }

    /**
     * Moved from [com.alvarotc.bito.ui.detail.DetailViewModel] (docs/05 §1: "la compra de
     * congeladores se muda del Detalle a la tienda de Habi"). Same body as before the move — the
     * Detail screen keeps only the inventory chip and its ⓘ.
     */
    fun buyFreezer() =
        write { today, nowMillis ->
            val ledger = domainState.snapshot().pointsLedger
            if (PointsEngine.canSpend(ledger, economy.freezerPrice)) {
                rewards.spend(-economy.freezerPrice, PointsReason.BUY_FREEZER, UUID.randomUUID().toString(), today, nowMillis)
                habiSounds.play(HabiSound.PURCHASE)
            }
        }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    HabiViewModel(
                        container.domainState,
                        container.rewards,
                        container.settings,
                        container.reconciler,
                        container.habiSounds,
                    )
                }
            }
    }
}
