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
import com.alvarotc.bito.domain.propuestas.Efecto
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.ui.habi.HabiSound
import com.alvarotc.bito.ui.habi.HabiSounds
import com.alvarotc.bito.ui.today.CardKind
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
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

    /** LIBRE: se pinta lo que diga la fuente. OCULTA: se fue a Editar. ESPERA: de vuelta, hasta que conteste (o el tope). */
    private enum class Fase { LIBRE, OCULTA, ESPERA }

    // Tras mandar a Editar, la nota se calla hasta que la ficha vuelva y la fuente conteste con el estado nuevo.
    private val fase = MutableStateFlow(Fase.LIBRE)

    // Lo último que dijo la fuente; es lo que se enseña si tras el tope no ha contestado nada nuevo.
    private var ultima: Propuesta? = null

    /** La propuesta de Habi para esta ficha; sin fuente o en modo lectura, nunca hay. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val propuesta: StateFlow<Propuesta?> =
        if (propuestas == null || lectura) {
            MutableStateFlow<Propuesta?>(null)
        } else {
            // Una sola colección de la fuente por fase: lo que se pinta y lo que se espera es lo mismo.
            fase
                .flatMapLatest { f ->
                    when (f) {
                        Fase.LIBRE -> propuestas.propuesta(Ambito.FICHA, habitId).onEach { ultima = it }
                        Fase.OCULTA -> flowOf(null)
                        Fase.ESPERA ->
                            channelFlow {
                                send(null)
                                var llego = false
                                val tope =
                                    launch {
                                        delay(TOPE_ESPERA_MS)
                                        if (!llego) {
                                            llego = true
                                            send(ultima)
                                        }
                                    }
                                propuestas.propuesta(Ambito.FICHA, habitId).collect {
                                    llego = true
                                    tope.cancel()
                                    ultima = it
                                    send(it)
                                }
                            }
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
        }

    private val _efectos = MutableSharedFlow<Efecto>(extraBufferCapacity = 4)

    /** Lo que la pantalla debe hacer tras una respuesta (solo `EditarHabito` sale de la ficha). */
    val efectos: SharedFlow<Efecto> = _efectos.asSharedFlow()

    fun responder(r: Respuesta) {
        val id = propuesta.value?.id ?: return
        viewModelScope.launch {
            val efecto = propuestas?.responder(id, r) ?: return@launch
            if (efecto is Efecto.EditarHabito) fase.value = Fase.OCULTA
            if (efecto != Efecto.Ninguno) _efectos.emit(efecto)
        }
    }

    /**
     * La ficha vuelve a primer plano. Si venía de Editar, la fuente se pide otra vez y la nota sigue oculta hasta
     * su primera respuesta o hasta [TOPE_ESPERA_MS]; con `WhileSubscribed(5_000)` lo guardado puede ser de antes de editar.
     */
    fun alVolver() {
        fase.compareAndSet(Fase.OCULTA, Fase.ESPERA)
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
     * paso; la abstinencia no se marca (la recaída va por el pie). En lectura o archivado, nada. No suena
     * nada aquí: el sonido del gesto es del catálogo y lo pone la pantalla.
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
        const val TOPE_ESPERA_MS = 2_000L

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
