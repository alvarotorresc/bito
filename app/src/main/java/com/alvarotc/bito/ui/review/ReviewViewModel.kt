package com.alvarotc.bito.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.habi.HabiSound
import com.alvarotc.bito.ui.habi.HabiSounds
import com.alvarotc.bito.ui.today.HabitCardUi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID

/** Backs the nightly review screen: derives its state and runs every registration action. */
class ReviewViewModel(
    domainState: DomainStateRepository,
    private val habits: HabitsRepository,
    private val journal: JournalRepository,
    private val settings: SettingsRepository,
    private val reconciler: PointsReconciler,
    private val rewards: RewardsRepository,
    private val habiSounds: HabiSounds,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // Overridable so tests can swap in their TestDispatcher — buildReviewUiState off Main (perf)
    // must not race a runTest's virtual scheduler the way the real Dispatchers.Default would.
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    /** Cards the user dismissed this session ("No lo hice" / "así se queda" / "Día limpio") — transient, never persisted. */
    private val acknowledged = MutableStateFlow<Set<String>>(emptySet())

    val uiState: StateFlow<ReviewUiState> =
        combine(
            combine(domainState.observe(), habits.observeHabits(), acknowledged, ::Triple),
            settings.settings,
            rewards.observeOwnedItems(),
            rewards.observeBadges(),
        ) { (state, entities, acked), prefs, owned, badges ->
            buildReviewUiState(
                state,
                entities.associate { it.id to it.sortOrder },
                todayOf(prefs),
                acked,
                prefs.personality,
                owned,
                prefs.userName,
                badges,
                prefs.badgesSeenUntilMillis,
            )
        }.flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    init {
        write { _, _ -> } // opening the review reconciles pending grants (same as Today/Detail)
    }

    private fun todayOf(prefs: Settings) = LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone())

    /** Every mutation recomputes grants right after (idempotent append). */
    private fun write(block: suspend (today: LogicalDay, nowMillis: Long) -> Unit) =
        viewModelScope.launch {
            val today = todayOf(settings.settings.first())
            val nowMillis = now()
            block(today, nowMillis)
            reconciler.reconcile(today, nowMillis)
        }

    /** CHECK/BINARY card: logs today's completion. */
    fun markDone(card: HabitCardUi) =
        write { today, nowMillis ->
            journal.log(EntryEntity(UUID.randomUUID().toString(), card.id, today, 1, nowMillis))
        }

    fun addAmount(
        card: HabitCardUi,
        amount: Int,
    ) = write { today, nowMillis ->
        journal.log(EntryEntity(UUID.randomUUID().toString(), card.id, today, amount, nowMillis))
    }

    fun setExact(
        card: HabitCardUi,
        value: Int,
    ) = write { today, nowMillis -> journal.setDayTotal(card.id, today, value, nowMillis) }

    /** "No lo hice" / "así se queda" / "Día limpio": hides the row this session — nothing is written. */
    fun acknowledge(cardId: String) {
        acknowledged.update { it + cardId }
    }

    /** Same write + cue as [com.alvarotc.bito.ui.detail.DetailViewModel.logRelapseOn]. */
    fun logRelapse(card: HabitCardUi) =
        write { today, nowMillis ->
            journal.log(EntryEntity(UUID.randomUUID().toString(), card.id, today, 1, nowMillis))
            habiSounds.play(HabiSound.SAD)
        }

    fun sealPendingDays() = write { _, nowMillis -> uiState.value.pendingSealDays.forEach { journal.sealDay(it, nowMillis) } }

    fun sealToday() = write { today, nowMillis -> journal.sealDay(today, nowMillis) }

    fun markCelebrated() = write { today, _ -> settings.update { it.copy(perfectDayCelebratedDay = today) } }

    /**
     * Marks every badge currently shown as seen. The marker is the latest of those badges' own
     * unlock stamps, not the wall clock — same rule as
     * [com.alvarotc.bito.ui.celebration.CelebrationsViewModel.dismissBadges]. Falls back to
     * [nowMillis] only when there is nothing shown to unlock a stamp from.
     */
    fun markBadgesSeen() =
        write { _, nowMillis ->
            val current = settings.settings.first().badgesSeenUntilMillis
            val latestShownUnlock =
                rewards.observeBadges().first()
                    .filter { it.unlockedAtMillis > current }
                    .maxOfOrNull { it.unlockedAtMillis }
            settings.update { it.copy(badgesSeenUntilMillis = maxOf(current, latestShownUnlock ?: nowMillis)) }
        }

    // The screen's LaunchedEffect(state.todaySealed, state.perfectToday) re-runs on every
    // recomposition that keeps both keys true (e.g. an activity recreation with no configChanges
    // exemption), so this VM instance guards against replaying the sound for the same logical
    // day. Once per day, per VM instance — a rotation gets a fresh VM only if the OS actually
    // tears the process down; whether the cue should ALSO stay silent once the global celebration
    // sheet (CelebrationsViewModel) has already played it for the same perfect day is a product
    // call left to the architect, out of this fix's scope.
    private var cuedForDay: LogicalDay? = null

    /** Exposed for tests — [HabiSounds] has no seam to assert a sound actually played. */
    internal val cuedDay: LogicalDay?
        get() = cuedForDay

    /** The perfect-day celebration cue (E2), fired once per logical day by the screen. Pure presentation — no [write]. */
    fun cue() {
        val day = uiState.value.today
        if (cuedForDay != day) {
            cuedForDay = day
            habiSounds.play(HabiSound.CELEBRATION)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ReviewViewModel(
                        container.domainState,
                        container.habits,
                        container.journal,
                        container.settings,
                        container.reconciler,
                        container.rewards,
                        container.habiSounds,
                    )
                }
            }
    }
}
