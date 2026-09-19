package com.alvarotc.bito.ui.today

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
import com.alvarotc.bito.domain.EyeTransition
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.HabiCue
import com.alvarotc.bito.domain.model.HabiCueEnvelope
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.ui.habi.HabiSound
import com.alvarotc.bito.ui.habi.HabiSounds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID

/** Backs the Today screen: derives its state and runs every registration action. */
class TodayViewModel(
    private val domainStateRepo: DomainStateRepository,
    private val habits: HabitsRepository,
    private val journal: JournalRepository,
    private val settings: SettingsRepository,
    private val reconciler: PointsReconciler,
    private val rewards: RewardsRepository,
    // Nullable so pre-existing VM tests need no fake SoundPool; the factory always passes the real one.
    private val habiSounds: HabiSounds? = null,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // Overridable so tests can swap in their TestDispatcher — buildTodayUiState off Main (perf)
    // must not race a runTest's virtual scheduler the way the real Dispatchers.Default would.
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    // Solo sirve para construir el valor por defecto de [ticker] de abajo.
    tickerMillis: Long = 60_000L,
    // Ticker grueso, inyectable como flujo — no como periodo — porque un generador con delay()
    // real nunca deja el reloj virtual de un test en reposo: `advanceUntilIdle()` no vacia jamas
    // su cola y el test se cuelga (comprobado: un test PREEXISTENTE que solo se suscribe a
    // uiState pasa de 90s con el bucle real). Los tests pasan su propio flujo (uno que ya emitio,
    // o uno que ellos empujan a mano) en vez de esperar un minuto real o virtual.
    //
    // Emite Unit, no la hora: el valor solo dispara la recombinacion, `now()` se relee dentro del
    // combine. Si el tic llevara la hora, un test que inyecta `flowOf(0L)` freiria cada calculo
    // (logicalDayOf incluido) con epoch 0, y con el ticker real las otras cuatro fuentes
    // recalcularian con la marca de tiempo del ULTIMO tic en vez de la hora real de su propio
    // cambio — exactamente el bug que esta tarea existe para tapar.
    private val ticker: Flow<Unit> =
        flow {
            while (true) {
                emit(Unit)
                delay(tickerMillis)
            }
        },
) : ViewModel() {
    val uiState: StateFlow<TodayUiState> =
        combine(
            domainStateRepo.observe(),
            habits.observeHabits(),
            settings.settings,
            rewards.observeOwnedItems(),
            ticker,
        ) { state, entities, prefs, owned, _ ->
            val nowMillis = now()
            val zoneNow = zone()
            val local = java.time.Instant.ofEpochMilli(nowMillis).atZone(zoneNow).toLocalTime()
            buildTodayUiState(
                state,
                entities.associate { it.id to it.sortOrder },
                LogicalDays.logicalDayOf(nowMillis, prefs.dayCutoffMinutes, zoneNow),
                prefs.personality,
                owned,
                prefs.userName,
                logHapticEnabled = prefs.logHapticEnabled,
                minutesOfDay = local.hour * 60 + local.minute,
                reviewTimeMinutes = prefs.reviewTimeMinutes,
                eyesPainted = prefs.habiEyesPainted,
            )
        }.flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private val loggedEntry = MutableStateFlow<String?>(null)
    val lastLogged: StateFlow<String?> = loggedEntry.asStateFlow()

    private val cue = MutableStateFlow<HabiCueEnvelope?>(null)
    val habiCue: StateFlow<HabiCueEnvelope?> = cue.asStateFlow()

    private val ritual = MutableStateFlow<EyeTransition?>(null)
    val eyeRitual: StateFlow<EyeTransition?> = ritual.asStateFlow()

    // Id monótono: dos LOGGED seguidos son dos reacciones, no una repetida que el
    // LaunchedEffect de la pantalla se comería por tener la misma clave.
    private var nextCueId = 0L

    private var lastRingSnapshot: Pair<Int, Int>? = null
    private var lastFailedIds: Set<String> = emptySet()

    init {
        write { _, _ -> } // opening the app reconciles pending grants
    }

    private fun todayOf(prefs: Settings) = LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone())

    private fun emitCue(next: HabiCue) {
        cue.value = HabiCueEnvelope(next, nextCueId++)
    }

    /**
     * Consume solo si el slot todavía sostiene el envelope que se lanzó (por [id]): si mientras
     * tanto ya llegó uno nuevo, este consume es un no-op y el nuevo queda intacto. Sin esta guarda
     * un cue interrumpido por otro más reciente borraba el slot al terminar de deshacerse, y eso
     * de rebote cancelaba también el efecto ya relanzado para el nuevo id (misma clave `null` que
     * su condición de salida). Sigue siendo un slot único, no una cola: bajo solape la reacción
     * vieja se corta, pero la nueva ya no se pierde con ella.
     */
    fun consumeHabiCue(id: Long) {
        cue.update { if (it?.id == id) null else it }
    }

    /** Misma guarda que [consumeHabiCue], por identidad de valor en vez de id: no hay uno propio. */
    fun consumeEyeRitual(transition: EyeTransition) {
        ritual.update { if (it == transition) null else it }
    }

    /** El sonido lo dispara la coreografía (HabiMotion), no el VM: este es el único conducto. */
    fun playHabiSound(sound: HabiSound) {
        habiSounds?.play(sound)
    }

    /** Cada mutación recalcula las concesiones justo después (append idempotente) y decide qué dice el cuerpo. */
    private fun write(
        cueOnSuccess: HabiCue? = null,
        block: suspend (today: LogicalDay, nowMillis: Long) -> Unit,
    ) = viewModelScope.launch {
        val prefs = settings.settings.first()
        val today = todayOf(prefs)
        val nowMillis = now()
        val before = snapshotOf(today)
        block(today, nowMillis)
        val result = reconciler.reconcile(today, nowMillis)
        val after = snapshotOf(today)

        result.eyeRitual?.let { ritual.value = it }
        when {
            result.reachedPerfectDay(today) -> emitCue(HabiCue.PERFECT_DAY)
            after.failedIds.any { it !in before.failedIds } -> emitCue(HabiCue.FAILED)
            after.allDone && !before.allDone -> emitCue(HabiCue.ALL_DONE)
            cueOnSuccess != null -> emitCue(cueOnSuccess)
        }
        lastRingSnapshot = after.ring
        lastFailedIds = after.failedIds
    }

    private data class WriteSnapshot(val ring: Pair<Int, Int>, val failedIds: Set<String>) {
        val allDone: Boolean get() = ring.second > 0 && ring.first >= ring.second
    }

    /**
     * Reconstruye lo justo para decidir qué dice el cuerpo, sin depender de que la UI esté
     * suscrita (`uiState` es WhileSubscribed y puede estar frío).
     *
     * Solo recibe `today` a propósito: solo lee `ringDone`/`ringTotal`/`failed`, que no dependen
     * ni de `prefs` ni de la hora ni de los ojos — pasarlos aquí ataría cada escritura a un
     * segundo `settings.settings.first()` o al reloj sin ganar nada.
     */
    private suspend fun snapshotOf(today: LogicalDay): WriteSnapshot {
        val entities = habits.observeHabits().first()
        val state = buildTodayUiState(domainStateRepo.snapshot(), entities.associate { it.id to it.sortOrder }, today)
        return WriteSnapshot(state.ringDone to state.ringTotal, state.cards.filter { it.failed }.mapTo(mutableSetOf()) { it.id })
    }

    private fun log(
        habitId: String,
        value: Int,
    ) = write(HabiCue.LOGGED) { today, nowMillis ->
        val id = UUID.randomUUID().toString()
        journal.log(EntryEntity(id, habitId, today, value, nowMillis))
        loggedEntry.value = id
    }

    fun tapPrimary(card: HabitCardUi) {
        when (card.kind) {
            CardKind.CHECK ->
                if (card.doneToday) {
                    write { today, nowMillis ->
                        journal.setDayTotal(card.id, today, 0, nowMillis)
                        loggedEntry.value = null
                    }
                } else {
                    log(card.id, 1)
                }
            CardKind.COUNTER -> log(card.id, card.step)
            CardKind.DURATION, CardKind.ABSTINENCE -> Unit
        }
    }

    fun addAmount(
        card: HabitCardUi,
        amount: Int,
    ) = log(card.id, amount)

    fun setExactToday(
        card: HabitCardUi,
        value: Int,
    ) = write { today, nowMillis ->
        journal.setDayTotal(card.id, today, value, nowMillis)
        loggedEntry.value = null
    }

    fun undo() {
        val id = loggedEntry.value ?: return
        loggedEntry.value = null
        write { _, _ -> journal.remove(id) }
    }

    fun consumeLogged() {
        loggedEntry.value = null
    }

    /** No [write] wrapper: ordering has no journal or points effect, it's pure presentation. */
    fun reorder(orderedIds: List<String>) {
        viewModelScope.launch { habits.reorder(orderedIds) }
    }

    fun sealPendingDays() =
        write(HabiCue.SEALED) { _, nowMillis -> uiState.value.pendingSealDays.forEach { journal.sealDay(it, nowMillis) } }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    TodayViewModel(
                        container.domainState,
                        container.habits,
                        container.journal,
                        container.settings,
                        container.reconciler,
                        container.rewards,
                        habiSounds = container.habiSounds,
                    )
                }
            }
    }
}
