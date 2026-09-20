package com.alvarotc.bito.ui.tasks

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.PointsReconciler
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.repo.TasksRepository
import com.alvarotc.bito.data.settings.FocusClock
import com.alvarotc.bito.data.settings.FocusSession
import com.alvarotc.bito.data.settings.FocusStore
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.MoodEngine
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.equippedSetOf
import com.alvarotc.bito.ui.habi.HabiSpec
import com.alvarotc.bito.ui.notifications.AndroidFocusPresence
import com.alvarotc.bito.ui.notifications.FocusPresence
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/** How long "he terminado" holds the screen on the done phrase before the session actually closes. */
private const val FINISH_HOLD_MS = 1_500L

/**
 * Lo que la pantalla de foco pinta. [busyWith] es el titulo de la sesion ajena que ya estaba en
 * marcha cuando se pidio [FocusViewModel] para otra tarea — no nulo solo en ese conflicto, y la
 * pantalla ofrece entonces la hoja "seguir con la otra / dejarla y empezar esta". [justFinished]
 * es solo true durante la pausa de [FocusViewModel.finish] (todavia `running`, ya con la tarea
 * marcada hecha) — la pantalla lo usa para ensenar la frase de Habi en vez de la cuenta atras.
 */
data class FocusUiState(
    val taskId: String? = null,
    val title: String = "",
    val firstStep: String? = null,
    val spec: HabiSpec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
    val userName: String = "",
    val selectedMinutes: Int = FocusViewModel.DEFAULT_MINUTES,
    val running: Boolean = false,
    val remainingMillis: Long = 0L,
    val justFinished: Boolean = false,
    val gone: Boolean = false,
    val busyWith: String? = null,
    val loading: Boolean = true,
)

/**
 * La sesion de foco: empezarla, alargarla y sus dos salidas (terminarla o dejarla). Sin
 * [Context] — [presence] es lo unico que toca plataforma (alarma y bandeja), y los dos relojes
 * ([now], [elapsed]) son inyectables para que [FocusClock] decida en el test, no el reloj real.
 */
class FocusViewModel(
    private val tasks: TasksRepository,
    private val focus: FocusStore,
    private val settings: SettingsRepository,
    private val domainState: DomainStateRepository,
    private val rewards: RewardsRepository,
    private val reconciler: PointsReconciler,
    private val presence: FocusPresence,
    private val requestedTaskId: String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    companion object {
        val OPTIONS = listOf(5, 10, 25)
        const val DEFAULT_MINUTES = 10
        val EXTENSIONS = listOf(5, 15, 30)

        fun factory(
            container: AppContainer,
            context: Context,
            taskId: String?,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    FocusViewModel(
                        container.tasks,
                        container.focus,
                        container.settings,
                        container.domainState,
                        container.rewards,
                        container.reconciler,
                        AndroidFocusPresence(context),
                        taskId,
                    )
                }
            }
    }

    /** Lo que sale directo de las fuentes, sin resolver aun contra [requestedTaskId]. */
    private data class Snapshot(
        val session: FocusSession?,
        val state: DomainState,
        val prefs: Settings,
        val owned: List<CustomizationItemEntity>,
    )

    /** Lo que solo vive en este ViewModel: la eleccion de minutos, si la sesion ya se cerro y si esta en la pausa de [finish]. */
    private data class Extra(val terminated: Boolean, val selectedMinutes: Int, val justFinished: Boolean)

    private val terminated = MutableStateFlow(false)
    private val selection = MutableStateFlow(DEFAULT_MINUTES)
    private val justFinishedFlag = MutableStateFlow(false)

    /** Fuerza recalcular [uiState] cada segundo mientras hay sesion — el propio valor no importa. */
    private val tick = MutableStateFlow(0L)

    /** Serializa [extend]: dos "+" seguidos no deben leer la misma sesion antes de que el primero la reescriba. */
    private val extendMutex = Mutex()

    val uiState: StateFlow<FocusUiState> =
        combine(
            combine(focus.session, domainState.observe(), settings.settings, rewards.observeOwnedItems(), ::Snapshot),
            combine(terminated, selection, justFinishedFlag, ::Extra),
            tick,
        ) { snapshot, extra, _ -> buildUiState(snapshot, extra) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    init {
        // Un "forzar detencion" mata la alarma y la notificacion, pero la sesion sigue en el
        // store: nadie la rearmaria (FocusSync solo cancela, nunca reposta ni reprograma;
        // BootReceiver solo corre al arrancar). Cualquier pantalla de foco que se construya con
        // esa sesion todavia viva la repostea — tambien si pide otra tarea y sale la hoja de
        // conflicto: esa sesion sigue corriendo y merece su alarma y su bandeja igual. Idempotente
        // por diseno (requestCode fijo en FocusAlarm, mismo FOCUS_ID en Notifier), asi que da
        // igual si varias pantallas repostean la misma sesion. Vencida (remaining == 0) no se
        // rearma nada: el 00:00 con los cinco botones ya sale de buildUiState solo.
        viewModelScope.launch {
            val session = focus.session.first() ?: return@launch
            val task = tasks.task(session.taskId) ?: return@launch
            val remaining = FocusClock.remainingMillis(session, now(), elapsed())
            if (remaining > 0) presence.show(task.title, session.endsAtMillis)
        }
        // Se relanza solo con cada sesion nueva (empezar, alargar o limpiar) — un solo delay vivo
        // a la vez, nunca uno acumulandose por cada "+". Para solo al llegar a 0: una sesion vencida
        // no necesita que la sigan despertando cada segundo para seguir enseñando el mismo 00:00.
        viewModelScope.launch {
            focus.session.collectLatest { session ->
                if (session == null) return@collectLatest
                while (FocusClock.remainingMillis(session, now(), elapsed()) > 0) {
                    delay(1_000)
                    tick.value++
                }
            }
        }
    }

    private fun buildUiState(
        snapshot: Snapshot,
        extra: Extra,
    ): FocusUiState {
        val nowMillis = now()
        val today = LogicalDays.logicalDayOf(nowMillis, snapshot.prefs.dayCutoffMinutes, zone())
        val lastActivityDay = StatsEngine.lastActivityDay(snapshot.state)
        val mood = MoodEngine.moodOf(snapshot.state, today, lastActivityDay)
        val equippedIds = snapshot.owned.filter { it.equipped }.map { it.itemId }
        val spec = HabiSpec(mood, snapshot.prefs.personality, equippedSetOf(equippedIds))
        val userName = snapshot.prefs.userName

        fun gone() = FocusUiState(spec = spec, userName = userName, gone = true, loading = false)

        if (extra.terminated) return gone()

        val session = snapshot.session
        if (session == null) {
            val taskId = requestedTaskId ?: return gone()
            val task = snapshot.state.tasks.find { it.id == taskId } ?: return gone()
            return FocusUiState(
                taskId = task.id,
                title = task.title,
                firstStep = task.firstStep,
                spec = spec,
                userName = userName,
                selectedMinutes = extra.selectedMinutes,
                loading = false,
            )
        }

        val runningTask = snapshot.state.tasks.find { it.id == session.taskId } ?: return gone()

        if (requestedTaskId != null && requestedTaskId != session.taskId) {
            val requestedTask = snapshot.state.tasks.find { it.id == requestedTaskId } ?: return gone()
            // El conflicto solo es tal si la sesion ajena sigue VIVA: una vencida no bloquea nada,
            // asi que no hay hoja que ofrecer — la pantalla se ve como si esa sesion no existiera.
            val otherAlive = FocusClock.remainingMillis(session, nowMillis, elapsed()) > 0
            return FocusUiState(
                taskId = requestedTask.id,
                title = requestedTask.title,
                firstStep = requestedTask.firstStep,
                spec = spec,
                userName = userName,
                selectedMinutes = extra.selectedMinutes,
                busyWith = if (otherAlive) runningTask.title else null,
                loading = false,
            )
        }

        return FocusUiState(
            taskId = runningTask.id,
            title = runningTask.title,
            firstStep = runningTask.firstStep,
            spec = spec,
            userName = userName,
            selectedMinutes = extra.selectedMinutes,
            running = true,
            remainingMillis = FocusClock.remainingMillis(session, nowMillis, elapsed()),
            justFinished = extra.justFinished,
            loading = false,
        )
    }

    fun select(minutes: Int) {
        selection.value = minutes
    }

    /**
     * Nunca pisa en silencio la sesion VIVA de otra tarea: eso es el conflicto, no un arranque.
     * Una sesion ajena ya vencida no bloquea — se pisa aqui mismo con [beginSession], sin escribir
     * ATTEMPT: nadie sabe si esa sesion se gano o se abandono, el mismo "no se inventa lo que paso"
     * de BootReceiver.
     */
    fun start() =
        viewModelScope.launch {
            val taskId = requestedTaskId ?: return@launch
            val liveSession = focus.session.first()
            val blockedByLiveOther =
                liveSession != null &&
                    liveSession.taskId != taskId &&
                    FocusClock.remainingMillis(liveSession, now(), elapsed()) > 0
            if (blockedByLiveOther) return@launch
            beginSession(taskId, selection.value)
        }

    /**
     * Mueve el fin y reprograma la alarma y la notificacion — no toca el dominio. Cuenta desde lo
     * que QUEDA (`FocusClock.remainingMillis`), no desde el fin crudo guardado: sobre una sesion ya
     * vencida ese fin esta en el pasado, y sumarle minutos ahi la dejaria vencida igual o programaria
     * la alarma antes de lo que la pantalla enseña. Reescribe la sesion entera para que el
     * siguiente `remainingMillis` la lea con el mismo par de relojes que la escribio.
     *
     * [extendMutex] serializa dos "+" seguidos: sin el, ambos podrian leer la sesion ANTES de que
     * el primero terminara de reescribirla, y el segundo "+" se perderia en vez de sumarse.
     */
    fun extend(minutes: Int) =
        viewModelScope.launch {
            extendMutex.withLock {
                val session = focus.session.first() ?: return@withLock
                val nowMillis = now()
                val elapsedMillis = elapsed()
                val rest = FocusClock.remainingMillis(session, nowMillis, elapsedMillis) + minutes * 60_000L
                focus.start(
                    session.copy(
                        endsAtMillis = nowMillis + rest,
                        endsAtElapsed = elapsedMillis + rest,
                        bootMillis = FocusClock.bootSignatureOf(nowMillis, elapsedMillis),
                    ),
                )
                val title = titleOf(session.taskId) ?: return@withLock
                presence.show(title, nowMillis + rest)
            }
        }

    fun finish() =
        viewModelScope.launch {
            val session = focus.session.first() ?: return@launch
            val today = currentToday()
            val nowMillis = now()
            tasks.markDone(session.taskId, today, nowMillis)
            reconciler.reconcile(today, nowMillis)
            // Habi dice la misma frase que Hoy antes de que la pantalla se cierre — la sesion se
            // limpia DESPUES de la pausa, no antes, para que uiState siga leyendo running=true
            // (con la tarea ya hecha) mientras se ve el bocadillo.
            justFinishedFlag.value = true
            delay(FINISH_HOLD_MS)
            focus.clear()
            presence.clear()
            terminated.value = true
        }

    fun giveUp() =
        viewModelScope.launch {
            val session = focus.session.first() ?: return@launch
            val today = currentToday()
            val nowMillis = now()
            tasks.recordAttempt(session.taskId, today, nowMillis)
            reconciler.reconcile(today, nowMillis)
            focus.clear()
            presence.clear()
            terminated.value = true
        }

    /** El sheet ofrece "seguir con la otra": nada que escribir, la pantalla vuelve a esa sesion. */
    fun keepOther() = Unit

    /** Hubo sesion en la tarea anterior — eso es historia — y arranca la nueva. */
    fun switchToRequested() =
        viewModelScope.launch {
            val target = requestedTaskId ?: return@launch
            val session = focus.session.first() ?: return@launch
            val today = currentToday()
            val nowMillis = now()
            tasks.recordAttempt(session.taskId, today, nowMillis)
            reconciler.reconcile(today, nowMillis)
            beginSession(target, selection.value)
        }

    private suspend fun beginSession(
        taskId: String,
        minutes: Int,
    ) {
        val nowMillis = now()
        val elapsedMillis = elapsed()
        val delta = minutes * 60_000L
        val endsAtMillis = nowMillis + delta
        focus.start(
            FocusSession(
                taskId = taskId,
                startedAtMillis = nowMillis,
                endsAtMillis = endsAtMillis,
                endsAtElapsed = elapsedMillis + delta,
                bootMillis = FocusClock.bootSignatureOf(nowMillis, elapsedMillis),
            ),
        )
        val title = titleOf(taskId) ?: return
        presence.show(title, endsAtMillis)
    }

    /** Null si la tarea ya no existe — los llamantes de [presence.show] no postean sin titulo. */
    private suspend fun titleOf(taskId: String): String? = tasks.task(taskId)?.title

    private suspend fun currentToday(): LogicalDay = LogicalDays.logicalDayOf(now(), settings.settings.first().dayCutoffMinutes, zone())
}
