package com.alvarotc.bito.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.db.HabitEntity
import com.alvarotc.bito.data.habla.AvisosAceptadosRepository
import com.alvarotc.bito.data.habla.LoGordoRepository
import com.alvarotc.bito.data.habla.MemoriaRepository
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.GardenEngine
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.Movimientos
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.habla.AvisoAceptado
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoMemoria
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Efecto
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.ui.habi.HabiSound
import com.alvarotc.bito.ui.habi.HabiSounds
import com.alvarotc.bito.ui.habi.minuteTicks
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

private const val EFECTOS_EN_COLA = 8

/** Lo de [hoy] que dice si es día duro: respuestas y vigentes de `DIA_DURO` y si se llegó a mostrar uno. */
private fun Memoria.soloDiaDuro(hoy: LogicalDay): Memoria =
    Memoria(
        eventos =
            eventos.filter {
                it.tipo == TipoMemoria.RESPUESTA && it.dia == hoy && it.ref?.startsWith("${Situacion.DIA_DURO.name}:") == true
            },
        vigentes = vigentes.filter { it.situacion == Situacion.DIA_DURO && it.dia == hoy },
        mostradasHoy = mostradasHoy.filter { it.situacion == Situacion.DIA_DURO }.take(1),
    )

/** Backs the Today screen: derives its state and runs every registration action. */
class TodayViewModel(
    domainState: DomainStateRepository,
    private val habits: HabitsRepository,
    private val journal: JournalRepository,
    private val settings: SettingsRepository,
    private val reconciler: PointsReconciler,
    private val rewards: RewardsRepository,
    // Nullable so pre-existing VM tests need no fake SoundPool; the factory always passes the real one.
    private val habiSounds: HabiSounds? = null,
    private val tasks: TasksRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    // Overridable so tests can swap in their TestDispatcher — buildTodayUiState off Main (perf)
    // must not race a runTest's virtual scheduler the way the real Dispatchers.Default would.
    defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    // La fuente de las propuestas de Habi (pieza 4); null en los tests que no la usan.
    private val propuestas: FuentePropuestas? = null,
    // Un tick por minuto para que la luz del jardín siga a la hora; por defecto uno solo (los tests
    // con advanceUntilIdle no conviven con un bucle infinito). La fábrica pasa minuteTicks().
    clockTicks: Flow<Unit> = flowOf(Unit),
    private val economia: EconomyConfig = EconomyConfig(),
    // Lo gordo de cada día; null en los tests que no lo usan.
    private val gordo: LoGordoRepository? = null,
    // Los «Vale, a las…» aceptados; null en los tests que no los usan.
    private val aceptados: AvisosAceptadosRepository? = null,
    // Lo que Habi recuerda: de ahí sale que hoy es día duro aunque la propuesta cambie. null en los tests que no la usan.
    private val memoria: MemoriaRepository? = null,
) : ViewModel() {
    private val jardinForzado = MutableStateFlow<GardenState?>(null)

    private data class Entradas(
        val state: DomainState,
        val entities: List<HabitEntity>,
        val prefs: Settings,
        val owned: List<CustomizationItemEntity>,
        val saldo: Int,
    )

    // Lo gordo de hoy y de ayer, siguiendo al día lógico (el corte y el reloj).
    @OptIn(ExperimentalCoroutinesApi::class)
    private val gordoDeDosDias: Flow<Pair<List<RefGordo>, List<RefGordo>>> =
        if (gordo == null) {
            flowOf(emptyList<RefGordo>() to emptyList())
        } else {
            combine(settings.settings, clockTicks) { prefs, _ -> LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone()) }
                .distinctUntilChanged()
                .flatMapLatest { hoy ->
                    gordo.observe(listOf(hoy - 1, hoy)).map { porDia -> (porDia[hoy] ?: emptyList()) to (porDia[hoy - 1] ?: emptyList()) }
                }
        }

    /** La propuesta de Habi para Hoy; sin fuente, nunca hay. */
    val propuesta: StateFlow<Propuesta?> =
        propuestas?.propuesta(Ambito.HOY)?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
            ?: MutableStateFlow<Propuesta?>(null)

    // Los avisos aceptados de hoy, siguiendo al día lógico.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val avisosDeHoy: Flow<List<AvisoAceptado>> =
        if (aceptados == null) {
            flowOf(emptyList())
        } else {
            combine(settings.settings, clockTicks) { prefs, _ -> LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone()) }
                .distinctUntilChanged()
                .flatMapLatest { hoy -> aceptados.observeDe(hoy) }
        }

    // De la memoria de hoy, solo lo que dice si es día duro: así una escritura cualquiera no rehace Hoy.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val memoriaDiaDuro: Flow<Memoria> =
        if (memoria == null) {
            flowOf(Memoria())
        } else {
            combine(settings.settings, clockTicks) { prefs, _ -> LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone()) }
                .distinctUntilChanged()
                .flatMapLatest { hoy -> memoria.observe(flowOf(hoy)).map { it.soloDiaDuro(hoy) } }
                .distinctUntilChanged()
        }

    // El último hábito de día duro que propuso la tarjeta hoy, por si la vigente se reemplaza sin respuesta.
    @Volatile private var diaDuroVisto: Pair<LogicalDay, String>? = null

    private fun resolverDiaDuro(
        propuesta: Propuesta?,
        memoriaHoy: Memoria,
        hoy: LogicalDay,
    ): String? {
        diaDuroDe(propuesta)?.takeIf { propuesta?.id?.endsWith(":$hoy") == true }?.let { diaDuroVisto = hoy to it }
        return diaDuroDeHoy(propuesta, memoriaHoy, hoy, diaDuroVisto?.takeIf { it.first == hoy }?.second)
    }

    private data class Extras(
        val badges: List<BadgeEntity>,
        val gordos: Pair<List<RefGordo>, List<RefGordo>>,
        val avisos: List<AvisoAceptado>,
        val propuesta: Propuesta?,
        val memoria: Memoria,
    )

    val uiState: StateFlow<TodayUiState> =
        combine(
            combine(
                domainState.observe(),
                habits.observeHabits(),
                settings.settings,
                rewards.observeOwnedItems(),
                rewards.observeBalance(),
            ) { state, entities, prefs, owned, saldo -> Entradas(state, entities, prefs, owned, saldo) },
            combine(rewards.observeBadges(), gordoDeDosDias, avisosDeHoy, propuesta, memoriaDiaDuro) { badges, gordos, avisos, p, m ->
                Extras(badges, gordos, avisos, p, m)
            },
            rewards.observeRecentMovements(5),
            jardinForzado,
            clockTicks,
        ) { entradas, extras, recientes, forzado, _ ->
            val (badges, gordos) = extras.badges to extras.gordos
            val nowMillis = now()
            val zoneId = zone()
            val prefs = entradas.prefs
            val hoy = LogicalDays.logicalDayOf(nowMillis, prefs.dayCutoffMinutes, zoneId)
            val garden =
                forzado
                    ?: GardenEngine.gardenOf(
                        entradas.state,
                        hoy,
                        LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId),
                        StatsEngine.lastActivityDay(entradas.state),
                        badges.mapTo(mutableSetOf()) { it.badgeId },
                        prefs.gardenWaterHighMark,
                    )
            buildTodayUiState(
                entradas.state,
                entradas.entities.associate { it.id to it.sortOrder },
                hoy,
                prefs.personality,
                entradas.owned,
                prefs.userName,
                logHapticEnabled = prefs.logHapticEnabled,
                balance = entradas.saldo,
                garden = garden,
                movimientos = Movimientos.de(recientes, entradas.state, hoy),
                economia = economia,
                gordoHoy = gordos.first,
                gordoAyer = gordos.second,
                avisosHoy = extras.avisos,
                diaDuro = resolverDiaDuro(extras.propuesta, extras.memoria, hoy),
            )
        }.flowOn(defaultDispatcher)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private val efectosInternos = MutableSharedFlow<Efecto>(extraBufferCapacity = EFECTOS_EN_COLA)

    /** Lo que la pantalla debe hacer tras una respuesta. Se consume una vez: sin repetición para quien llega tarde. */
    val efectos: SharedFlow<Efecto> = efectosInternos.asSharedFlow()

    fun responder(
        r: Respuesta,
        hora: Int? = null,
    ) {
        val id = propuesta.value?.id ?: return
        viewModelScope.launch {
            val efecto = propuestas?.responder(id, r, hora) ?: return@launch
            if (efecto != Efecto.Ninguno) efectosInternos.emit(efecto)
        }
    }

    /** Lo gordo que el usuario guarda en la hoja. */
    fun guardarGordo(refs: List<RefGordo>) {
        viewModelScope.launch { propuestas?.guardarGordo(refs) }
    }

    /** Solo para el panel de depuración: fuerza lo que pinta el jardín; null vuelve al motor. */
    fun forzarJardin(garden: GardenState?) {
        jardinForzado.value = garden
    }

    private val habiDuermeInterno = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Se emite al tocar a Habi con el día sellado: la lista avisa de que duerme. */
    val habiDuerme: SharedFlow<Unit> = habiDuermeInterno.asSharedFlow()

    fun alTocarHabi() {
        if (uiState.value.todaySealed) {
            habiDuermeInterno.tryEmit(Unit)
        } else {
            habiSounds?.play(HabiSound.GREETING)
        }
    }

    fun alAcariciarHabi() {
        if (uiState.value.todaySealed) {
            habiDuermeInterno.tryEmit(Unit)
        } else {
            habiSounds?.play(HabiSound.HAPPY)
        }
    }

    private val loggedEntry = MutableStateFlow<String?>(null)
    val lastLogged: StateFlow<String?> = loggedEntry.asStateFlow()

    init {
        write { _, _ -> } // opening the app reconciles pending grants
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

    private fun log(
        habitId: String,
        value: Int,
    ) = write { today, nowMillis ->
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

    /** Recaída de un hábito de abstinencia: una entrada de valor 1 hoy, que «Deshacer» quita. */
    fun recaerHoy(card: HabitCardUi) {
        if (card.kind != CardKind.ABSTINENCE) return
        write { today, nowMillis ->
            val id = UUID.randomUUID().toString()
            journal.log(EntryEntity(id, card.id, today, 1, nowMillis))
            loggedEntry.value = id
            habiSounds?.play(HabiSound.SAD)
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

    fun sealPendingDays() = write { _, nowMillis -> uiState.value.pendingSealDays.forEach { journal.sealDay(it, nowMillis) } }

    private val doneTask = MutableStateFlow<String?>(null)

    /** El id de la ultima tarea marcada hecha, para el snackbar de deshacer. */
    val lastTaskDone: StateFlow<String?> = doneTask.asStateFlow()

    fun markTaskDone(id: String) =
        write { today, nowMillis ->
            tasks.markDone(id, today, nowMillis)
            doneTask.value = id
        }

    /** Devuelve la tarea a abierta. El apunte de puntos se queda: lo ganado, ganado. */
    fun undoTaskDone() {
        val id = doneTask.value ?: return
        doneTask.value = null
        write { _, _ -> tasks.reopen(id) }
    }

    fun consumeTaskDone() {
        doneTask.value = null
    }

    fun postponeTask(id: String) = write { today, nowMillis -> tasks.postpone(id, today, nowMillis) }

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
                        tasks = container.tasks,
                        propuestas = container.propuestas,
                        clockTicks = minuteTicks(),
                        gordo = container.loGordo,
                        aceptados = container.avisosAceptados,
                        memoria = container.memoria,
                    )
                }
            }
    }
}
