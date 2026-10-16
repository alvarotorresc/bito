package com.alvarotc.bito.ui.today

import com.alvarotc.bito.data.db.CustomizationItemEntity
import com.alvarotc.bito.domain.Compliance
import com.alvarotc.bito.domain.ComplianceStatus
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.MoodEngine
import com.alvarotc.bito.domain.Movimiento
import com.alvarotc.bito.domain.Sealing
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.Streaks
import com.alvarotc.bito.domain.Tasks
import com.alvarotc.bito.domain.habla.AvisoAceptado
import com.alvarotc.bito.domain.habla.CandidatoGordo
import com.alvarotc.bito.domain.habla.EstadoAviso
import com.alvarotc.bito.domain.habla.LoGordo
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.SubtituloGordo
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.EquippedSet
import com.alvarotc.bito.domain.model.GardenState
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.model.equippedSetOf
import com.alvarotc.bito.domain.propuestas.Propuesta
import com.alvarotc.bito.ui.habi.HabiSpec

/** Visual treatment of a habit card, derived from its metric/log mode/direction. */
enum class CardKind { CHECK, COUNTER, DURATION, ABSTINENCE }

/** One habit's Today-screen state, ready for the UI to render without further domain lookups. */
data class HabitCardUi(
    val id: String,
    val name: String,
    val kind: CardKind,
    val progress: Int,
    val target: Int,
    val unit: String?,
    val step: Int,
    val direction: Direction,
    val period: Period,
    val doneToday: Boolean,
    val failed: Boolean,
    val streak: Int,
    // El minuto de su aviso aceptado de hoy («Vale, a las…») mientras siga pendiente; null si no hay.
    val horaAceptada: Int? = null,
)

/**
 * Whether the habit name should render struck through. Only AT_LEAST cards earn this once
 * today's goal is reached — AT_MOST cards are "done" (clean) from the day's start, so a
 * strikethrough there would read as an already-completed goal instead of the honest "no
 * usage logged yet". Their state is carried by the limit caption and the failure tint instead.
 */
val HabitCardUi.nameStruckThrough: Boolean
    get() = direction == Direction.AT_LEAST && doneToday

/**
 * Whether the card should keep showing its quick-log controls (e.g. DurationBody's
 * +5/+15/+goal chips). AT_LEAST cards hide them once today's goal is reached — nothing left
 * to log. AT_MOST cards show them at all times, clean or over the limit: logging real usage
 * stays honest even after the limit is blown.
 */
val HabitCardUi.showsLoggingChips: Boolean
    get() = direction == Direction.AT_MOST || !doneToday

/** A paused habit's compact row in Today's "paused" section — no progress, just a way back in. */
data class PausedHabitUi(val id: String, val name: String)

/** One task's Today-screen row, with the via ([slot]) that brought it in, ready for the UI. */
data class TaskRowUi(
    val id: String,
    val title: String,
    val firstStep: String?,
    val slot: Tasks.TodaySlot,
    val dueKind: DueKind,
    val dueDay: LogicalDay?,
)

/**
 * Snapshot the Today screen renders: the ring, the cards, and the pending-seal prompt.
 * [spec] and [userName] feed the header's corner avatar and greeting (T14) — the same
 * [HabiSpec] shape the Habi and Stats screens' own avatars use.
 */
data class TodayUiState(
    val today: LogicalDay = 0,
    val ringDone: Int = 0,
    val ringTotal: Int = 0,
    val cards: List<HabitCardUi> = emptyList(),
    val pausedHabits: List<PausedHabitUi> = emptyList(),
    val pendingSealDays: List<LogicalDay> = emptyList(),
    val todaySealed: Boolean = false,
    val spec: HabiSpec = HabiSpec(Mood.NORMAL, Personality.NEUTRA, EquippedSet()),
    val userName: String = "",
    val logHapticEnabled: Boolean = true,
    val tasks: List<TaskRowUi> = emptyList(),
    // From the global task list, not [tasks] (which is only what touches today, per
    // Tasks.todayTasks' six doors, spec section 4.1) — a task can exist, even be finished,
    // without touching today at all, and the section (and with it the only route into "tasks")
    // must stay up regardless.
    val hasAnyTasks: Boolean = false,
    val loading: Boolean = true,
    val saldo: Int = 0,
    val garden: GardenState = GardenState.INITIAL,
    val movimientos: List<Movimiento> = emptyList(),
    val economia: EconomyConfig = EconomyConfig(),
    // Lo gordo (spec 5.4): los candidatos de la hoja, la sugerencia de Habi y lo que ya se eligió hoy. Sin elegir, vacío.
    val candidatosGordo: List<CandidatoGordo> = emptyList(),
    val sugerenciaGordo: RefGordo? = null,
    val gordo: List<RefGordo> = emptyList(),
    // Día duro (spec 5.10): el hábito que la tarjeta propone; el resto de pendientes espera sin prisa. null = día normal.
    val diaDuro: String? = null,
)

/**
 * Derives the Today screen state from [state] as seen on [today]. Pure — no
 * side effects, no storage, no clock reads — so it is trivially testable and
 * safe to call on every state change. [personality], [owned] and [userName] default so
 * existing positional callers (tests predating the header avatar) keep compiling unchanged.
 */
fun buildTodayUiState(
    state: DomainState,
    sortOrder: Map<String, Int>,
    today: LogicalDay,
    personality: Personality = Personality.NEUTRA,
    owned: List<CustomizationItemEntity> = emptyList(),
    userName: String = "",
    logHapticEnabled: Boolean = true,
    balance: Int = 0,
    garden: GardenState = GardenState.INITIAL,
    movimientos: List<Movimiento> = emptyList(),
    economia: EconomyConfig = EconomyConfig(),
    gordoHoy: List<RefGordo> = emptyList(),
    gordoAyer: List<RefGordo> = emptyList(),
    avisosHoy: List<AvisoAceptado> = emptyList(),
    diaDuro: String? = null,
): TodayUiState {
    val lastActivityDay = StatsEngine.lastActivityDay(state)
    val mood = MoodEngine.moodOf(state, today, lastActivityDay)
    val equippedIds = owned.filter { it.equipped }.map { it.itemId }
    val cards =
        state.habits
            .filter { Compliance.isRequirableOn(state, it, today) }
            .map { habit -> cardOf(state, habit, today).conHoraDe(avisosHoy, today) }
            .sortedBy { sortOrder[it.id] ?: Int.MAX_VALUE }
    // Paused, not archived: a habit "counts" as paused either by its own status flag or by
    // carrying an open pause interval — the two are written together by
    // HabitsRepository.pause/resume, but domain-level fixtures (this file's own `pause()`
    // helper, used by test 10 and above) can set one without the other.
    val pausedHabits =
        state.habits
            .filter { it.status != HabitStatus.ARCHIVED }
            .filter { habit ->
                habit.status == HabitStatus.PAUSED ||
                    state.pauseIntervals.any { it.habitId == habit.id && it.endDay == null }
            }
            .sortedBy { sortOrder[it.id] ?: Int.MAX_VALUE }
            .map { PausedHabitUi(it.id, it.name) }
    val sellado = Sealing.isSealed(state, today)
    val taskRows =
        Tasks.todayTasks(state, today)
            .sortedBy { it.slot.ordinal } // estable: dentro de cada franja queda el orden de Tasks.todayTasks
            .map { todayTask ->
                TaskRowUi(
                    id = todayTask.task.id,
                    title = todayTask.task.title,
                    firstStep = todayTask.task.firstStep,
                    slot = todayTask.slot,
                    dueKind = todayTask.task.dueKind,
                    dueDay = todayTask.task.dueDay,
                )
            }
    val candidatosGordo = LoGordo.candidatos(state, today)
    val gordoAyerSinHacer = gordoAyer.filterNot { LoGordo.hecho(it, state, today) }
    return TodayUiState(
        today = today,
        ringDone = cards.count { it.doneToday },
        ringTotal = cards.size,
        cards = cards,
        pausedHabits = pausedHabits,
        pendingSealDays = Sealing.pendingSealDays(state, today),
        todaySealed = sellado,
        // Con el día sellado Habi duerme: ojos cerrados y gorro.
        spec = HabiSpec(mood, personality, equippedSetOf(equippedIds), closedEyes = sellado, sleepCap = sellado),
        userName = userName,
        logHapticEnabled = logHapticEnabled,
        tasks = taskRows,
        hasAnyTasks = state.tasks.isNotEmpty(),
        loading = false,
        saldo = balance,
        garden = garden,
        movimientos = movimientos,
        economia = economia,
        candidatosGordo = elegidosFueraDeCandidatos(state, gordoHoy, candidatosGordo) + candidatosGordo,
        sugerenciaGordo = LoGordo.sugerencia(candidatosGordo, gordoAyerSinHacer, state, today),
        gordo = gordoHoy,
        diaDuro = diaDuro,
    )
}

internal fun cardOf(
    state: DomainState,
    habit: Habit,
    today: LogicalDay,
): HabitCardUi {
    val periodKey = LogicalDays.periodKeyOf(today, habit.period)
    val periodDays = LogicalDays.daysOf(periodKey, habit.period)
    val compliance = Compliance.complianceOf(state, habit, periodKey, today)
    val requirableDays = Compliance.requirableDaysOf(state, habit, periodKey)
    val entries = state.entries.filter { it.habitId == habit.id && it.logicalDay in requirableDays }
    val progress = Compliance.progressOf(habit, entries)
    val target = Compliance.targetOf(state, habit, periodDays.last)
    val binaryLike = habit.metric == Metric.CHECK || habit.logMode == LogMode.BINARY
    val failed = compliance == ComplianceStatus.FAILED
    val doneToday =
        when (habit.direction) {
            Direction.AT_LEAST ->
                if (habit.period == Period.DAY) {
                    compliance == ComplianceStatus.FULFILLED
                } else {
                    compliance == ComplianceStatus.FULFILLED || entries.any { it.logicalDay == today }
                }
            // "No further action needed today": clean/within limit counts even
            // unsealed — the seal (M4 review) stays the streak-truth.
            Direction.AT_MOST, Direction.ZERO -> !failed
        }
    val kind =
        when {
            habit.direction == Direction.ZERO -> CardKind.ABSTINENCE
            binaryLike -> CardKind.CHECK
            habit.metric == Metric.COUNT -> CardKind.COUNTER
            else -> CardKind.DURATION
        }
    return HabitCardUi(
        id = habit.id,
        name = habit.name,
        kind = kind,
        progress = progress,
        target = target,
        unit = habit.unit,
        step = habit.step,
        direction = habit.direction,
        period = habit.period,
        doneToday = doneToday,
        failed = failed,
        streak = Streaks.streaksOf(state, habit, today).current,
    )
}

/**
 * Lo gordo elegido hoy que ya no es candidato (hecho, o fuera de las puertas de hoy): sigue siendo fila de la hoja,
 * para que reabrir y guardar no lo pierda. Va al principio, marcado como el resto de lo elegido.
 */
private fun elegidosFueraDeCandidatos(
    state: DomainState,
    elegidos: List<RefGordo>,
    candidatos: List<CandidatoGordo>,
): List<CandidatoGordo> {
    val presentes = candidatos.map { it.ref }.toSet()
    return elegidos.filter { it !in presentes }.mapNotNull { ref ->
        when (ref.tipo) {
            TipoRefGordo.TAREA ->
                state.tasks.firstOrNull { it.id == ref.id }?.let { CandidatoGordo(ref, it.title, SubtituloGordo.Tarea) }
            TipoRefGordo.HABITO ->
                state.habits.firstOrNull { it.id == ref.id }?.let { CandidatoGordo(ref, it.name, SubtituloGordo.Habito) }
        }
    }
}

/** Su aviso aceptado de hoy que sigue pendiente, si lo hay. */
private fun HabitCardUi.conHoraDe(
    avisos: List<AvisoAceptado>,
    hoy: LogicalDay,
): HabitCardUi {
    val aviso = avisos.firstOrNull { it.habitId == id && it.dia == hoy && it.estado == EstadoAviso.PENDIENTE }
    return if (aviso == null) this else copy(horaAceptada = aviso.minuto)
}

/**
 * El hábito que propone la tarjeta en un día duro: el id de la propuesta es `DIA_DURO:<hábito>:<día>`.
 * null si la propuesta no es de día duro o no nombra ningún hábito.
 */
fun diaDuroDe(propuesta: Propuesta?): String? {
    val prefijo = "${Situacion.DIA_DURO.name}:"
    val id = propuesta?.id ?: return null
    if (!id.startsWith(prefijo)) return null
    return id.removePrefix(prefijo).substringBeforeLast(':', "").ifBlank { null }
}
