package com.alvarotc.bito.ui.cierre

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.db.BadgeEntity
import com.alvarotc.bito.data.db.EntryEntity
import com.alvarotc.bito.data.habla.AvisosAceptadosRepository
import com.alvarotc.bito.data.habla.LoGordoRepository
import com.alvarotc.bito.data.habla.MemoriaRepository
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.HabitsRepository
import com.alvarotc.bito.data.repo.JournalRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.PerfectDays
import com.alvarotc.bito.domain.PointsEngine
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.Tasks
import com.alvarotc.bito.domain.habla.EventoMemoria
import com.alvarotc.bito.domain.habla.LoGordo
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.TipoMemoria
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.model.BadgeCatalog
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.FuentePropuestas
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.ui.habi.HabiSound
import com.alvarotc.bito.ui.habi.HabiSounds
import com.alvarotc.bito.ui.habla.reviewRowsOf
import com.alvarotc.bito.ui.notifications.AvisosHabi
import com.alvarotc.bito.ui.review.ReviewViewModel
import com.alvarotc.bito.ui.today.HabitCardUi
import com.alvarotc.bito.ui.today.buildTodayUiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.UUID

private const val TAREAS_NOMBRADAS = 2

/**
 * El modelo del cierre del día, dentro del Inicio. Vive en la entrada de Hoy: lo decidido y el ánimo son de la
 * apertura en curso y se olvidan con [reiniciar]. Todas las escrituras corren en su propio ámbito, que no se
 * cancela al bajar la cámara: sellar con «Buenas noches» y cerrar a toda prisa no corta la memoria ni el aviso.
 *
 * [crearRepaso], [abrirRepaso] y [soltarRepaso] son transitorios: los usa el contenido provisional del cierre
 * hasta que la Tarea 27 lo sustituya.
 */
class CierreViewModel internal constructor(
    private val domainState: DomainStateRepository,
    private val habits: HabitsRepository,
    private val journal: JournalRepository,
    private val settings: SettingsRepository,
    private val reconciler: PointsReconciler,
    private val rewards: RewardsRepository,
    private val habiSounds: HabiSounds,
    private val memoria: MemoriaRepository,
    private val loGordo: LoGordoRepository,
    private val aceptados: AvisosAceptadosRepository,
    private val avisos: AvisosHabi,
    private val propuestas: FuentePropuestas,
    private val crearRepaso: () -> ReviewViewModel,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val decididas = MutableStateFlow<Set<String>>(emptySet())
    private val animo = MutableStateFlow<Int?>(null)

    /** El día que ya se está sellando desde aquí: un segundo «Buenas noches» no repite las escrituras. */
    private var selladoEnCurso: LogicalDay? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<CierreUiState> =
        run {
            val dia = settings.settings.map(::hoyDe).distinctUntilChanged()
            val datos =
                combine(
                    combine(domainState.observe(), habits.observeHabits(), settings.settings, ::Triple),
                    rewards.observeBadges(),
                    dia.flatMapLatest { loGordo.observe(listOf(it)) },
                    propuestas.propuesta(Ambito.CIERRE),
                ) { (estado, entidades, ajustes), insignias, gordo, comentario ->
                    val hoy = hoyDe(ajustes)
                    construir(
                        estado,
                        entidades.associate { it.id to it.sortOrder },
                        hoy,
                        ajustes,
                        insignias,
                        gordo[hoy].orEmpty(),
                        comentario,
                    )
                }
            combine(datos, decididas, animo) { base, hechas, estadoAnimo ->
                base.copy(decididas = hechas, animo = estadoAnimo)
            }.flowOn(defaultDispatcher)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CierreUiState())
        }

    private fun hoyDe(prefs: Settings) = LogicalDays.logicalDayOf(now(), prefs.dayCutoffMinutes, zone())

    private fun construir(
        estado: DomainState,
        orden: Map<String, Int>,
        hoy: LogicalDay,
        ajustes: Settings,
        insignias: List<BadgeEntity>,
        gordo: List<RefGordo>,
        comentario: Propuesta?,
    ): CierreUiState {
        val delDia = buildTodayUiState(estado, orden, hoy, ajustes.personality, emptyList(), ajustes.userName)
        val tareas = Tasks.todayTasks(estado, hoy).map { it.task.title }
        return CierreUiState(
            hoy = hoy,
            filas = reviewRowsOf(delDia.cards, delDia.todaySealed),
            tareasManana = tareas.take(TAREAS_NOMBRADAS),
            tareasMas = (tareas.size - TAREAS_NOMBRADAS).coerceAtLeast(0),
            hechos = delDia.ringDone,
            total = delDia.ringTotal,
            comentario = comentario,
            sellado = delDia.todaySealed,
            perfecto = PerfectDays.isPerfectDay(estado, hoy, hoy),
            bitosHoy = PointsEngine.pointsEarnedOn(estado.pointsLedger, hoy),
            rachasAvanzan = StatsEngine.streaksAdvancedToday(estado, hoy),
            logrosNuevos =
                BadgeCatalog.all.filter { def ->
                    insignias.any { it.badgeId == def.id && it.unlockedAtMillis > ajustes.badgesSeenUntilMillis }
                },
            gordoSinHacer = gordo.filterNot { LoGordo.hecho(it, estado, hoy) }.map { nombreDe(it, estado) },
            loading = false,
            diasPendientes = delDia.pendingSealDays,
        )
    }

    private fun nombreDe(
        ref: RefGordo,
        estado: DomainState,
    ): String =
        when (ref.tipo) {
            TipoRefGordo.TAREA -> estado.tasks.firstOrNull { it.id == ref.id }?.title
            TipoRefGordo.HABITO -> estado.habits.firstOrNull { it.id == ref.id }?.name
        } ?: ref.id

    /** Cada apertura empieza limpia: ni lo decidido ni el ánimo pasan de una vez a otra. Reconcilia lo pendiente, como Hoy. */
    fun reiniciar() {
        decididas.value = emptySet()
        animo.value = null
        escribir { _, _ -> }
    }

    /** Toda mutación recalcula los premios justo después (añadido idempotente). */
    private fun escribir(bloque: suspend (hoy: LogicalDay, ahora: Long) -> Unit): Job =
        viewModelScope.launch(defaultDispatcher) {
            val hoy = hoyDe(settings.settings.first())
            val ahora = now()
            bloque(hoy, ahora)
            reconciler.reconcile(hoy, ahora)
        }

    fun markDone(card: HabitCardUi) =
        escribir { hoy, ahora ->
            journal.log(EntryEntity(UUID.randomUUID().toString(), card.id, hoy, 1, ahora))
        }

    fun addAmount(
        card: HabitCardUi,
        amount: Int,
    ) = escribir { hoy, ahora ->
        journal.log(EntryEntity(UUID.randomUUID().toString(), card.id, hoy, amount, ahora))
    }

    fun setExact(
        card: HabitCardUi,
        value: Int,
    ) = escribir { hoy, ahora -> journal.setDayTotal(card.id, hoy, value, ahora) }

    /** «No lo hice», «Así se queda» o «Día limpio»: atenúa la fila mientras el cierre está abierto; no se escribe nada. */
    fun decidir(cardId: String) {
        decididas.update { it + cardId }
    }

    fun deshacer(cardId: String) {
        decididas.update { it - cardId }
    }

    fun logRelapse(card: HabitCardUi) =
        escribir { hoy, ahora ->
            journal.log(EntryEntity(UUID.randomUUID().toString(), card.id, hoy, 1, ahora))
            habiSounds.play(HabiSound.SAD)
        }

    /** 0 bien, 1 regular, 2 duro. Se puede cambiar hasta sellar. */
    fun elegirAnimo(valor: Int) {
        require(valor in 0..2) { "El ánimo es 0, 1 o 2" }
        if (!uiState.value.sellado) animo.value = valor
    }

    /**
     * Sella el día con lo decidido y escribe la memoria: `ANIMO` si lo hay, `GORDO_HECHO` si lo gordo se hizo y
     * `ACEPTADO_CUMPLIDO` por cada aviso aceptado cuyo hábito se cumplió. Después replanifica los avisos. Con el
     * estado aún en carga no hace nada. La escritura no se cancela.
     */
    fun buenasNoches(): Job? {
        val visto = uiState.value
        if (visto.loading || visto.sellado || selladoEnCurso == visto.hoy) return null
        selladoEnCurso = visto.hoy
        val estadoAnimo = animo.value
        return escribir { hoy, ahora ->
            withContext(NonCancellable) {
                val estado = domainState.observe().first()
                journal.sealDay(hoy, ahora)
                estadoAnimo?.let { memoria.anotar(evento(TipoMemoria.ANIMO, hoy, null, it, ahora)) }
                val gordo = loGordo.observe(listOf(hoy)).first()[hoy].orEmpty()
                val hechos = gordo.count { LoGordo.hecho(it, estado, hoy) }
                if (gordo.isNotEmpty() && hechos == gordo.size) {
                    memoria.anotar(evento(TipoMemoria.GORDO_HECHO, hoy, null, hechos * 10 + gordo.size, ahora))
                }
                aceptados.observeDe(hoy).first()
                    .filter { LoGordo.hecho(RefGordo(TipoRefGordo.HABITO, it.habitId), estado, hoy) }
                    .forEach { memoria.anotar(evento(TipoMemoria.ACEPTADO_CUMPLIDO, hoy, it.habitId, it.minuto, ahora)) }
                avisos.replanificar()
            }
        }
    }

    private fun evento(
        tipo: TipoMemoria,
        hoy: LogicalDay,
        ref: String?,
        valor: Int,
        ahora: Long,
    ) = EventoMemoria(UUID.randomUUID().toString(), tipo, hoy, ref, valor, ahora)

    /** Sella los días anteriores que quedaron abiertos. */
    fun sealPendingDays() =
        escribir { hoy, ahora ->
            val estado = domainState.observe().first()
            buildTodayUiState(estado, emptyMap(), hoy).pendingSealDays.forEach { journal.sealDay(it, ahora) }
        }

    fun markCelebrated() = escribir { hoy, _ -> settings.update { it.copy(perfectDayCelebratedDay = hoy) } }

    /**
     * Marca como vistos los logros que se enseñan. La marca es el último desbloqueo mostrado, no el reloj, y el
     * suelo se lee dentro de la transacción de `update` para no pisar una marca más alta que llegue a la vez.
     */
    fun markBadgesSeen() =
        escribir { _, ahora ->
            val actual = settings.settings.first().badgesSeenUntilMillis
            val ultimo =
                rewards.observeBadges().first()
                    .filter { it.unlockedAtMillis > actual }
                    .maxOfOrNull { it.unlockedAtMillis }
            settings.update { it.copy(badgesSeenUntilMillis = maxOf(it.badgesSeenUntilMillis, ultimo ?: ahora)) }
        }

    // Transitorio (Tarea 27): el repaso provisional que monta el contenido del cierre.

    /** El repaso de la apertura en curso, o null con el cierre cerrado. */
    internal var repasoActual: ReviewViewModel? = null
        private set

    init {
        addCloseable { repasoActual?.viewModelScope?.cancel() }
    }

    /** Un repaso nuevo para esta apertura; el anterior, si lo hubiera, deja de escuchar. Reinicia también lo decidido. */
    internal fun abrirRepaso(): ReviewViewModel {
        reiniciar()
        repasoActual?.viewModelScope?.cancel()
        return crearRepaso().also { repasoActual = it }
    }

    /** Al cerrar: el repaso de esa apertura deja de escuchar. */
    internal fun soltarRepaso(repaso: ReviewViewModel) {
        repaso.viewModelScope.cancel()
        if (repasoActual === repaso) repasoActual = null
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    CierreViewModel(
                        container.domainState,
                        container.habits,
                        container.journal,
                        container.settings,
                        container.reconciler,
                        container.rewards,
                        container.habiSounds,
                        container.memoria,
                        container.loGordo,
                        container.avisosAceptados,
                        container.avisosHabi,
                        container.propuestas,
                        crearRepaso = {
                            ReviewViewModel(
                                container.domainState,
                                container.habits,
                                container.journal,
                                container.settings,
                                container.reconciler,
                                container.rewards,
                                container.habiSounds,
                            )
                        },
                    )
                }
            }
    }
}
