package com.alvarotc.bito.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.db.FreezerUseEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.ui.habi.HabiSound
import com.alvarotc.bito.ui.habi.HabiSounds
import com.alvarotc.bito.ui.today.CardKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** Backs the habit Detail screen: derives its state and runs every registration/lifecycle action. */
class DetailViewModel(
    private val habitId: String,
    private val domainState: DomainStateRepository,
    private val habits: HabitsRepository,
    private val journal: JournalRepository,
    private val rewards: RewardsRepository,
    private val settings: SettingsRepository,
    private val reconciler: PointsReconciler,
    private val habiSounds: HabiSounds,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // Overridable so tests can swap in their TestDispatcher — buildDetailUiState off Main (perf)
    // must not race a runTest's virtual scheduler the way the real Dispatchers.Default would.
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    // Modo lectura (la ficha abierta desde Camino): no escribe nada con el gesto de Hoy.
    private val lectura: Boolean = false,
    // La fuente de las propuestas de Habi; null en los tests que no la usan.
    private val propuestas: FuentePropuestas? = null,
) : ViewModel() {
    // Seeded with a cutoff-agnostic "today" — only used as the starting point for month
    // navigation before the first real settings emission; a few hours' drift around a cutoff
    // can, at worst, open the screen one calendar month early, which the user can page back from.
    private val _month =
        MutableStateFlow(YearMonth.from(LocalDate.ofEpochDay(LogicalDays.logicalDayOf(now(), 0, zone()).toLong())))
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    val uiState: StateFlow<DetailUiState?> =
        combine(domainState.observe(), settings.settings, rewards.observeOwnedItems(), _month) { state, prefs, owned, m ->
            buildDetailUiState(state, habitId, m, todayOf(prefs), prefs.personality, prefs.userName, owned, lectura)
        }.flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** La propuesta de Habi para esta ficha; sin fuente, nunca hay. */
    val propuesta: StateFlow<Propuesta?> =
        propuestas?.propuesta(Ambito.FICHA, habitId)?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
            ?: MutableStateFlow<Propuesta?>(null)

    fun responder(r: Respuesta) {
        val id = propuesta.value?.id ?: return
        viewModelScope.launch { propuestas?.responder(id, r) }
    }

    init {
        write { _, _ -> } // opening the screen reconciles pending grants
    }

    private fun todayOf(prefs: Settings) = LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone())

    /** Every mutation recomputes grants right after, always against the CURRENT logical today — even when [day] targets the past. */
    private fun write(block: suspend (today: LogicalDay, nowMillis: Long) -> Unit) =
        viewModelScope.launch {
            val today = todayOf(settings.settings.first())
            val nowMillis = now()
            block(today, nowMillis)
            reconciler.reconcile(today, nowMillis)
        }

    /** No baja del mes de creación del hábito (spec §8.4); sin hábito cargado, no se mueve. */
    fun previousMonth() =
        viewModelScope.launch {
            val habit = domainState.snapshot().habits.find { it.id == habitId } ?: return@launch
            val primerMes = YearMonth.from(LocalDate.ofEpochDay(habit.createdOnDay.toLong()))
            _month.update { if (it > primerMes) it.minusMonths(1) else it }
        }

    /** Never advances past the calendar month containing the current logical today. */
    fun nextMonth() =
        viewModelScope.launch {
            val currentMonth = YearMonth.from(LocalDate.ofEpochDay(todayOf(settings.settings.first()).toLong()))
            _month.update { if (it < currentMonth) it.plusMonths(1) else it }
        }

    /**
     * El gesto de Hoy desde la ficha: un CHECK alterna el día, un contador o una duración suman un
     * paso; la abstinencia no se marca (la recaída va por el pie). En lectura o archivado, nada.
     */
    fun marcarHoy() =
        write { today, nowMillis ->
            val estado = domainState.snapshot()
            val habit = estado.habits.find { it.id == habitId } ?: return@write
            if (lectura || habit.status == HabitStatus.ARCHIVED) return@write
            when (cardKindOf(habit)) {
                CardKind.CHECK -> {
                    val hecho = estado.entries.any { it.habitId == habitId && it.logicalDay == today }
                    journal.setDayTotal(habitId, today, if (hecho) 0 else 1, nowMillis)
                }
                CardKind.COUNTER, CardKind.DURATION ->
                    journal.log(EntryEntity(UUID.randomUUID().toString(), habitId, today, habit.step, nowMillis))
                CardKind.ABSTINENCE -> return@write
            }
            habiSounds.play(HabiSound.LOG)
        }

    fun setDayValue(
        day: LogicalDay,
        value: Int,
    ) = write { _, nowMillis -> journal.setDayTotal(habitId, day, value, nowMillis) }

    fun markDayDone(day: LogicalDay) = write { _, nowMillis -> journal.setDayTotal(habitId, day, 1, nowMillis) }

    fun clearDay(day: LogicalDay) = write { _, nowMillis -> journal.setDayTotal(habitId, day, 0, nowMillis) }

    fun logRelapseOn(day: LogicalDay) =
        write { _, nowMillis ->
            journal.log(EntryEntity(UUID.randomUUID().toString(), habitId, day, 1, nowMillis))
            habiSounds.play(HabiSound.SAD)
        }

    /** Batch-seal's single-day twin: clears the day's entries, then seals it — same as "todo limpio". */
    fun markCleanAndSeal(day: LogicalDay) =
        write { _, nowMillis ->
            journal.setDayTotal(habitId, day, 0, nowMillis)
            journal.sealDay(day, nowMillis)
        }

    fun applyFreezer(day: LogicalDay) =
        write { _, nowMillis -> journal.useFreezer(FreezerUseEntity(UUID.randomUUID().toString(), habitId, day, nowMillis)) }

    fun pause(note: String?) = write { today, _ -> habits.pause(habitId, today, note) }

    fun resume() = write { today, _ -> habits.resume(habitId, today) }

    fun archive() = write { today, nowMillis -> habits.archive(habitId, today, nowMillis) }

    // A mutation like any other habit lifecycle write: goes through `write` so the reconcile
    // call runs afterward, consistent with pause/resume/archive above.
    fun unarchive() = write { _, _ -> habits.unarchive(habitId) }

    companion object {
        fun factory(
            container: AppContainer,
            habitId: String,
            lectura: Boolean = false,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    DetailViewModel(
                        habitId,
                        container.domainState,
                        container.habits,
                        container.journal,
                        container.rewards,
                        container.settings,
                        container.reconciler,
                        container.habiSounds,
                        lectura = lectura,
                        propuestas = container.propuestas,
                    )
                }
            }
    }
}
