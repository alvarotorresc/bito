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
import com.alvarotc.bito.domain.model.HabiCue
import com.alvarotc.bito.domain.model.HabiCueEnvelope
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.habi.HabiSound
import com.alvarotc.bito.ui.habi.HabiSounds
import com.alvarotc.bito.ui.today.HabitCardUi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID

// Justo por encima de los ~320 ms del asentimiento de HabiMotion.playLogged (el gesto CORTO,
// no el bostezo de playSealed de ~900 ms), para que dos filas seguidas del mismo lote no se
// pisen — cada emisión tiene tiempo de resolverse antes de la siguiente.
private const val SEAL_NOD_GAP_MS = 340L

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
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    /** Cards the user dismissed this session ("No lo hice" / "así se queda" / "Día limpio") — transient, never persisted. */
    private val acknowledged = MutableStateFlow<Set<String>>(emptySet())

    private val cue = MutableStateFlow<HabiCueEnvelope?>(null)
    val habiCue: StateFlow<HabiCueEnvelope?> = cue.asStateFlow()

    // Id monótono: dos SEALED seguidos (un lote) son dos reacciones, no una repetida que el
    // LaunchedEffect de la pantalla se comería por tener la misma clave.
    private var nextCueId = 0L

    val uiState: StateFlow<ReviewUiState> =
        combine(
            combine(domainState.observe(), habits.observeHabits(), acknowledged, ::Triple),
            settings.settings,
            rewards.observeOwnedItems(),
            rewards.observeBadges(),
        ) { (state, entities, acked), prefs, owned, badges ->
            val nowMillis = now()
            val local = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone()).toLocalTime()
            buildReviewUiState(
                state,
                entities.associate { it.id to it.sortOrder },
                todayOf(prefs),
                acked,
                prefs.personality,
                owned,
                prefs.userName,
                minutesOfDay = local.hour * 60 + local.minute,
                reviewTimeMinutes = prefs.reviewTimeMinutes,
                eyesPainted = prefs.habiEyesPainted,
                badges = badges,
                badgesSeenUntilMillis = prefs.badgesSeenUntilMillis,
            )
        }.flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    init {
        write { _, _ -> } // opening the review reconciles pending grants (same as Today/Detail)
    }

    private fun todayOf(prefs: Settings) = LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone())

    /** Every mutation recomputes grants right after (idempotent append). */
    private fun write(block: suspend (today: LogicalDay, nowMillis: Long) -> Unit) =
        viewModelScope.launch(defaultDispatcher) {
            // Off Main: the reconcile walk after every write was two ~600ms Choreographer stalls
            // on cold entry (QA 2026-08-24) — DB calls already hop, the engine math must too.
            val today = todayOf(settings.settings.first())
            val nowMillis = now()
            block(today, nowMillis)
            reconciler.reconcile(today, nowMillis)
        }

    private fun emit(next: HabiCue) {
        cue.value = HabiCueEnvelope(next, nextCueId++)
    }

    /** Misma guarda que [com.alvarotc.bito.ui.today.TodayViewModel.consumeHabiCue], por [id]. */
    fun consumeHabiCue(id: Long) {
        cue.update { if (it?.id == id) null else it }
    }

    /** El sonido lo dispara la coreografía (HabiMotion), no el VM: este es el único conducto. */
    fun playHabiSound(sound: HabiSound) {
        habiSounds.play(sound)
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
            habiSounds.play(HabiSound.MEEH)
        }

    /**
     * Sella TODO el lote de una vez, sin ningún `delay` de por medio — R14 (review, ronda 1): un
     * `delay` dentro de este `write` retrasaba [PointsReconciler.reconcile] 340 ms × (N-1) tras la
     * última escritura y, peor, si el usuario navegaba a mitad del lote, `viewModelScope` moría
     * con la pantalla y los días restantes NUNCA se sellaban — pérdida real de datos por un efecto
     * cosmético. El asentimiento vive aparte, en [nodBatchSeal], DESPUÉS de que esto ya haya
     * terminado.
     */
    fun sealPendingDays() {
        write { _, nowMillis -> uiState.value.pendingSealDays.forEach { journal.sealDay(it, nowMillis) } }
        nodBatchSeal(uiState.value.pendingSealDays)
    }

    /**
     * El asentimiento del lote, DESVINCULADO de la escritura de arriba: si esta corrutina se
     * cancela porque el usuario navega a mitad del lote (viewModelScope muere con la pantalla),
     * los sellos ya están en Room — nada de datos depende de que este cuerpo termine.
     */
    private fun nodBatchSeal(days: List<LogicalDay>) =
        viewModelScope.launch(defaultDispatcher) {
            days.forEach { _ ->
                // Un asentimiento por fila, no la coreografía de dormirse por lote: el cuerpo
                // acompaña la lista con el gesto CORTO (LOGGED), no con SEALED.
                emit(HabiCue.LOGGED)
                delay(SEAL_NOD_GAP_MS)
            }
            // pendingSealDays es siempre estrictamente anterior a hoy (Sealing.pendingSealDays),
            // así que esta rama no dispara en la práctica — pero si el lote alguna vez incluyera
            // el día de hoy, el cierre del lote también merece su propio SEALED: la pose dormida
            // ya llega derivada por HabiDay.phaseOf, este cue es solo el gesto.
            if (uiState.value.today in days) {
                emit(HabiCue.SEALED)
            }
        }

    fun sealToday() =
        write { today, nowMillis ->
            journal.sealDay(today, nowMillis)
            emit(HabiCue.SEALED)
        }

    fun markCelebrated() = write { today, _ -> settings.update { it.copy(perfectDayCelebratedDay = today) } }

    /**
     * Marks every badge currently shown as seen. The marker is the latest of those badges' own
     * unlock stamps, not the wall clock — same rule as
     * [com.alvarotc.bito.ui.celebration.CelebrationsViewModel.dismissBadges]. Falls back to
     * [nowMillis] only when there is nothing shown to unlock a stamp from. The floor for the
     * final write is read INSIDE [SettingsRepository.update]'s transaction, not the snapshot used
     * to pick which badges count as newly shown — a write racing this one (e.g. the celebration
     * sheet's own dismissBadges) that lands between the two must never get overwritten by a stale
     * floor.
     */
    fun markBadgesSeen() =
        write { _, nowMillis ->
            val current = settings.settings.first().badgesSeenUntilMillis
            val latestShownUnlock =
                rewards.observeBadges().first()
                    .filter { it.unlockedAtMillis > current }
                    .maxOfOrNull { it.unlockedAtMillis }
            settings.update { it.copy(badgesSeenUntilMillis = maxOf(it.badgesSeenUntilMillis, latestShownUnlock ?: nowMillis)) }
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
            habiSounds.play(HabiSound.JINGLE)
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
