package com.alvarotc.bito.ui.breathing

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.alvarotc.bito.AppContainer
import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.data.repo.BreathingRepository
import com.alvarotc.bito.data.repo.DomainStateRepository
import com.alvarotc.bito.data.repo.RewardsRepository
import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.data.settings.SettingsRepository
import com.alvarotc.bito.domain.BreathingRhythm
import com.alvarotc.bito.domain.BreathingStats
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.MoodEngine
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.model.BreathPhase
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.domain.model.BreathingSession
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.equippedSetOf
import com.alvarotc.bito.ui.habi.HabiSpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Reposo (con Habi despierto y «Empezar»), en marcha (Habi guia) y final (frase y contador). */
enum class BreathingStage { IDLE, RUNNING, FINISHED }

/**
 * Lo que pinta la pantalla de respirar. [fill] es el valor grueso del ultimo tick: la pantalla
 * real lo recalcula por fotograma desde [anchorElapsed] para que Habi no respire a saltos, y los
 * tests (animated = false) usan este. [gone] se pone a true solo cuando la ultima sesion ya esta
 * escrita en Room: la pantalla se cierra al verlo, nunca antes (Review Focus 1).
 */
data class BreathingUiState(
    val stage: BreathingStage = BreathingStage.IDLE,
    val mode: BreathingMode = BreathingMode.CALM,
    val phase: BreathPhase = BreathPhase.INHALE,
    val fill: Float = 0f,
    val cycle: Int = 1,
    val totalCycles: Int = BreathingRhythm.cycles(BreathingMode.CALM),
    val remainingSeconds: Int = BreathingRhythm.at(BreathingMode.CALM, 0).remainingSeconds,
    val anchorElapsed: Long = 0L,
    val musicEnabled: Boolean = false,
    val hapticEnabled: Boolean = true,
    val spec: HabiSpec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
    val userName: String = "",
    val week: BreathingStats.Tally = BreathingStats.Tally(0, 0),
    val allTime: BreathingStats.Tally = BreathingStats.Tally(0, 0),
    val gone: Boolean = false,
    val loading: Boolean = true,
)

/**
 * La sesion de respiracion (spec §5.4). Sin Context: [music] es lo unico que toca plataforma, y
 * los tres relojes son inyectables. El ritmo se temporiza AQUI, en viewModelScope, y no en un
 * LaunchedEffect: bajo Robolectric un delay() de LaunchedEffect no obedece a advanceTimeBy. Cada
 * tick recalcula desde el ancla de elapsedRealtime con BreathingRhythm.at, nunca resta a un
 * contador, asi que cambiar la hora del movil no mueve nada.
 *
 * Todas las entradas publicas corren en el hilo principal: los guardias de etapa (`stage`) bastan
 * para que un doble toque no arranque dos bucles ni guarde dos filas (Review Focus 2 y 3).
 */
class BreathingViewModel(
    private val breathing: BreathingRepository,
    private val settings: SettingsRepository,
    private val domainState: DomainStateRepository,
    private val rewards: RewardsRepository,
    private val music: BreathingMusic,
    private val now: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    companion object {
        const val TICK_MS = 100L

        /** Menos de esto no se guarda: no fue una sesion, fue un toque. */
        const val MIN_SAVED_SECONDS = 10

        /** [music] se llama una vez por ViewModel: cada pantalla tiene su propio reproductor. */
        fun factory(
            container: AppContainer,
            music: () -> BreathingMusic,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    BreathingViewModel(container.breathing, container.settings, container.domainState, container.rewards, music())
                }
            }
    }

    /**
     * Lo que solo vive en este ViewModel. [musicOverride] sustituye al ajuste de DataStore en
     * cuanto se toca el interruptor y ya no se limpia: mientras viva el ViewModel manda sobre
     * `prefs.breathingMusicEnabled`, lo cual es coherente porque este mismo ViewModel es quien
     * escribe ese ajuste. [pending] es la sesion que [close] acaba de decidir guardar, todavia sin
     * confirmar por Room: sin ella `uiState` pasaria a FINISHED enseñando el contador de ANTES de
     * la sesion (Review Focus 5). [pendingSizeAtClose] es cuantas filas de `breathingSessions` vio
     * el ULTIMO `combine` de [uiState] al crear [pending] (ver [lastCombinedSessionCount]);
     * `buildUiState` deja de sumar [pending] en cuanto el `DomainState` que recibe ya trae una fila
     * mas que esa, asi que no hace falta un segundo suscriptor a Room para retirarlo (evita la
     * carrera entre dos flujos independientes que verian N+1 en ordenes distintos en un dispositivo
     * real, donde el executor de Room es un pool).
     */
    private data class Live(
        val stage: BreathingStage = BreathingStage.IDLE,
        val selectedMode: BreathingMode? = null,
        val point: BreathingRhythm.Point? = null,
        val anchorElapsed: Long = 0L,
        val musicOverride: Boolean? = null,
        val gone: Boolean = false,
        val pending: BreathingSession? = null,
        val pendingSizeAtClose: Int? = null,
        /**
         * Salida en curso ([leave]): la etapa se queda donde estaba (nada de FINISHED, ni un
         * fotograma de la burbuja de cierre), pero ningun cierre, arranque ni otra salida vuelve a
         * actuar. Sin esto, con la etapa aun en RUNNING, un doble atras o el ON_STOP del
         * desapilado volvian a entrar en [finishRunning] y guardaban la misma sesion dos veces.
         */
        val leaving: Boolean = false,
    )

    private val live = MutableStateFlow(Live())
    private var prefs = Settings()
    private var loop: Job? = null
    private var saveJob: Job? = null
    private var runningMode = BreathingMode.CALM
    private var startedAtMillis = 0L
    private var lastStepIndex = -1

    /**
     * Tamano de `state.breathingSessions` en la ULTIMA muestra que [buildUiState] recibio del mismo
     * `combine` que alimenta [uiState] — nunca de un segundo suscriptor a Room, para no arriesgar
     * una carrera entre dos flujos que un dispositivo real (executor en pool) puede entregar en
     * cualquier orden. [close] lo usa como base de [Live.pendingSizeAtClose].
     */
    private var lastCombinedSessionCount = 0

    /**
     * `breathingLastMode` en la ULTIMA muestra que [buildUiState] recibio del mismo `combine` que
     * alimenta [uiState] — no el `prefs` recogido aparte en [init], que puede tardar unos
     * milisegundos mas en enterarse del mismo cambio de ajustes. [start] lo usa como base de
     * modo por defecto y de la comparacion «solo si cambio», para que sea siempre el mismo valor
     * que la pantalla esta mostrando.
     */
    private var lastPersistedMode = BreathingMode.CALM

    private val phaseCount = MutableStateFlow(0)

    /**
     * Sube una vez por cambio de paso (incluido el primer «Inhala»). La pantalla lo observa con el
     * mismo guardia que `nudge` en HabiAvatar y vibra una vez por subida si la vibracion esta activa.
     */
    val phaseChanges: StateFlow<Int> = phaseCount.asStateFlow()

    val uiState: StateFlow<BreathingUiState> =
        combine(live, domainState.observe(), settings.settings, rewards.observeOwnedItems()) { l, state, p, owned ->
            buildUiState(l, state, p, owned)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BreathingUiState())

    init {
        viewModelScope.launch { settings.settings.collect { prefs = it } }
    }

    /** Solo en memoria: elegir chip no escribe ajustes (§4.6). En el final, vuelve a reposo con ese modo. */
    fun selectMode(mode: BreathingMode) {
        if (live.value.stage == BreathingStage.RUNNING || live.value.leaving) return
        // Ver un pending de la sesion anterior aqui tampoco tiene sentido: mismo motivo que en start().
        live.update {
            it.copy(selectedMode = mode, stage = BreathingStage.IDLE, point = null, pending = null, pendingSizeAtClose = null)
        }
    }

    fun start() {
        val current = live.value
        if (current.stage == BreathingStage.RUNNING || current.leaving) return
        // El modo por defecto viene de [lastPersistedMode] (mismo combine que uiState), no del
        // `prefs` recogido aparte: los dos vienen del mismo settings.settings pero por vias
        // distintas, y en los primeros milisegundos tras abrir la pantalla pueden no coincidir
        // todavia. La comparacion de abajo usa la misma base, para que sea justo lo que cambio.
        val mode = current.selectedMode ?: lastPersistedMode
        runningMode = mode
        startedAtMillis = now()
        lastStepIndex = -1
        live.value =
            current.copy(
                stage = BreathingStage.RUNNING,
                selectedMode = mode,
                point = BreathingRhythm.at(mode, 0),
                anchorElapsed = elapsed(),
                // Un pending de la sesion anterior ya no tiene sentido al empezar otra: sin esto,
                // un restore que reviviera este ViewModel arrancaria con un pending viejo (T10).
                pending = null,
                pendingSizeAtClose = null,
            )
        // La unica escritura del modo: al empezar y solo si cambio (cada escritura repinta los widgets).
        if (lastPersistedMode != mode) {
            viewModelScope.launch { settings.update { it.copy(breathingLastMode = mode) } }
        }
        if (musicOn()) music.start()
        loop =
            viewModelScope.launch {
                while (isActive) {
                    tick()
                    delay(TICK_MS)
                }
            }
    }

    fun stop() = finishRunning(showFinished = true)

    /**
     * Calcula lo mismo que veria el siguiente tick (completada o no, y cuantos segundos) y cierra
     * la sesion. [showFinished] decide si la pantalla pasa a FINISHED (parada manual) o se queda
     * donde estaba (abandono via [leave], que no debe ensenar nunca la burbuja de cierre).
     */
    private fun finishRunning(showFinished: Boolean) {
        val current = live.value
        if (current.stage != BreathingStage.RUNNING || current.leaving) return
        // Entre que el ritmo llega al final y el siguiente tick lo ve, un cierre en ese hueco no
        // debe grabar una sesion incompleta de mas de 2 minutos: si el ritmo ya esta acabado,
        // cuenta como si lo hubiera visto el tick.
        val elapsedNow = elapsed()
        val point = BreathingRhythm.at(runningMode, elapsedNow - current.anchorElapsed)
        if (point.finished) {
            close(
                completed = true,
                seconds = (BreathingRhythm.totalMillis(runningMode) / 1_000L).toInt(),
                showFinished = showFinished,
            )
        } else {
            close(
                completed = false,
                seconds = ((elapsedNow - current.anchorElapsed) / 1_000L).toInt(),
                showFinished = showFinished,
            )
        }
    }

    /** La pantalla deja de verse (segundo plano, pantalla apagada): lo mismo que «Parar». */
    fun onBackgrounded() = stop()

    /** «Otra vez»: sesion nueva e independiente del mismo modo, sin pasar por reposo. */
    fun again() {
        if (live.value.stage != BreathingStage.FINISHED) return
        start()
    }

    /**
     * Salir (flecha, atras del sistema, «Listo»): para si hacia falta sin pasar por FINISHED (Review
     * Final M11, Minor 3: abandonar a mitad de sesion no debe ensenar ni un fotograma de la burbuja
     * «Terminado»), guarda si toca, y solo entonces marca [BreathingUiState.gone]. Idempotente: tras
     * la primera llamada, [Live.leaving] corta cualquier otra salida, parada u [onBackgrounded].
     */
    fun leave() {
        if (live.value.leaving) return
        finishRunning(showFinished = false)
        live.update { it.copy(leaving = true) }
        viewModelScope.launch {
            saveJob?.join()
            live.update { it.copy(gone = true) }
        }
    }

    fun toggleMusic() {
        val on = !musicOn()
        live.update { it.copy(musicOverride = on) }
        viewModelScope.launch { settings.update { it.copy(breathingMusicEnabled = on) } }
        if (live.value.stage == BreathingStage.RUNNING && !live.value.leaving) {
            if (on) music.start() else music.stop()
        }
    }

    /** No guarda: a esta altura ya lo hizo stop() u onBackgrounded(). */
    override fun onCleared() {
        loop?.cancel()
        music.stop()
    }

    private fun musicOn(): Boolean = live.value.musicOverride ?: prefs.breathingMusicEnabled

    private fun clearPending() {
        viewModelScope.launch { live.update { it.copy(pending = null, pendingSizeAtClose = null) } }
    }

    private fun tick() {
        val current = live.value
        if (current.stage != BreathingStage.RUNNING) return
        val point = BreathingRhythm.at(runningMode, elapsed() - current.anchorElapsed)
        if (!point.finished && point.stepIndex != lastStepIndex) {
            lastStepIndex = point.stepIndex
            phaseCount.value++
        }
        live.value = current.copy(point = point)
        if (point.finished) {
            close(completed = true, seconds = (BreathingRhythm.totalMillis(runningMode) / 1_000L).toInt())
        }
    }

    /**
     * [showFinished] = false es lo que usa [leave] para abandonar a mitad de sesion sin pasar nunca
     * por BreathingStage.FINISHED (Review Final M11, Minor 3): se guarda igual si toca, pero la
     * pantalla no llega a ensenar la burbuja de cierre porque [leave] la desapila justo despues.
     */
    private fun close(
        completed: Boolean,
        seconds: Int,
        showFinished: Boolean = true,
    ) {
        loop?.cancel()
        loop = null
        music.stop()
        if (!completed && seconds < MIN_SAVED_SECONDS) {
            // Nada guardado, el contador no se ha movido: una frase de cierre seria mentira (§5.3).
            if (showFinished) live.update { it.copy(stage = BreathingStage.IDLE, point = null) }
            return
        }
        val mode = runningMode
        val startedAt = startedAtMillis
        // El id es un marcador: la reconciliacion es por tamano (ver Live.pendingSizeAtClose),
        // porque BreathingRepository.save no devuelve el id que genera.
        val pendingSession =
            BreathingSession(
                id = "pending",
                mode = mode,
                startedAtMillis = startedAt,
                durationSeconds = seconds,
                completed = completed,
            )
        // stage y pending cambian en el MISMO update: uiState nunca emite FINISHED con el contador
        // de antes de esta sesion (Review Focus 5).
        live.update {
            val withPending = it.copy(pending = pendingSession, pendingSizeAtClose = lastCombinedSessionCount)
            if (showFinished) withPending.copy(stage = BreathingStage.FINISHED) else withPending
        }
        // NonCancellable: la fila tiene que llegar a Room aunque la pantalla se desapile en este
        // mismo fotograma y el viewModelScope muera con ella.
        saveJob = viewModelScope.launch(NonCancellable) { breathing.save(mode, startedAt, seconds, completed) }
    }

    private fun buildUiState(
        l: Live,
        state: DomainState,
        p: Settings,
        owned: List<CustomizationItemEntity>,
    ): BreathingUiState {
        // Misma muestra que usara [close] como base de Live.pendingSizeAtClose: un solo lector de
        // Room, nunca un segundo suscriptor que pueda ver N+1 en un orden distinto (ver KDoc de
        // [lastCombinedSessionCount]).
        lastCombinedSessionCount = state.breathingSessions.size
        lastPersistedMode = p.breathingLastMode
        val zoneId = zone()
        val today = LogicalDays.logicalDayOf(now(), p.dayCutoffMinutes, zoneId)
        val mood = MoodEngine.moodOf(state, today, StatsEngine.lastActivityDay(state))
        val spec = HabiSpec(mood, p.personality, equippedSetOf(owned.filter { it.equipped }.map { it.itemId }))
        val mode = l.selectedMode ?: p.breathingLastMode
        val point = l.point ?: BreathingRhythm.at(mode, 0)
        // Con la sesion recien guardada aun sin llegar por Room, el contador la cuenta igual
        // (Review Focus 5): nunca un fotograma en FINISHED con la tabla de antes de empezar. En
        // cuanto ESTA MISMA muestra ya trae una fila mas que cuando se creo pending, la fila real
        // la sustituye sola y pending deja de sumarse (sin necesidad de limpiarlo aparte).
        val sessions =
            if (l.pending != null && state.breathingSessions.size <= (l.pendingSizeAtClose ?: -1)) {
                state.breathingSessions + l.pending
            } else {
                // La fila real ya llego (o no habia pending): se limpia para que un pending
                // consumido no siga vivo el resto de la vida del ViewModel (T10, edge case de
                // restore). No cambia lo que ve esta emision: [sessions] es igual con o sin el.
                if (l.pending != null) clearPending()
                state.breathingSessions
            }
        return BreathingUiState(
            stage = l.stage,
            mode = mode,
            phase = point.phase,
            fill = point.fill,
            cycle = point.cycle,
            totalCycles = point.totalCycles,
            remainingSeconds = point.remainingSeconds,
            anchorElapsed = l.anchorElapsed,
            musicEnabled = l.musicOverride ?: p.breathingMusicEnabled,
            hapticEnabled = p.logHapticEnabled,
            spec = spec,
            userName = p.userName,
            week = BreathingStats.thisWeek(sessions, today, p.dayCutoffMinutes, zoneId),
            allTime = BreathingStats.allTime(sessions),
            gone = l.gone,
            loading = false,
        )
    }
}
