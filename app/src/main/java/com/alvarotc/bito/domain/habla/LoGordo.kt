package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.Compliance
import com.alvarotc.bito.domain.ComplianceStatus
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.Tasks
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.TaskStatus

/** Una fila de la hoja de lo gordo (spec §5.4, §7.2). */
data class CandidatoGordo(
    val ref: RefGordo,
    val nombre: String,
    val subtitulo: SubtituloGordo,
)

/** El subtítulo de una fila de la hoja: por qué está ahí. */
sealed interface SubtituloGordo {
    data object TareaHoy : SubtituloGordo

    data class TareaVencida(
        val dias: Int,
    ) : SubtituloGordo

    data object TareaSemana : SubtituloGordo

    data object Tarea : SubtituloGordo

    data object Habito : SubtituloGordo
}

/** Lo gordo de hoy (spec §5.4): candidatos, sugerencia de Habi y si ya está hecho. */
object LoGordo {
    /** De 1 a 3: así lo gordo sigue siendo gordo (D13). */
    const val MAXIMO = 3

    /**
     * Las tareas de hoy (`Tasks.todayTasks`, en su orden) y después los hábitos exigibles hoy sin
     * cumplir en su periodo, en el orden del usuario. Dejar del todo y con límite no son candidatos.
     */
    fun candidatos(
        state: DomainState,
        hoy: LogicalDay,
    ): List<CandidatoGordo> {
        val tareas =
            Tasks.todayTasks(state, hoy).map { t ->
                CandidatoGordo(RefGordo(TipoRefGordo.TAREA, t.task.id), t.task.title, subtituloDe(t, hoy))
            }
        val habitos =
            state.habits
                .filter { it.direction == Direction.AT_LEAST }
                .filter { Compliance.isRequirableOn(state, it, hoy) }
                .filter { cumplioHoy(state, it, hoy).not() }
                .map { CandidatoGordo(RefGordo(TipoRefGordo.HABITO, it.id), it.name, SubtituloGordo.Habito) }
        return tareas + habitos
    }

    /**
     * La que Habi marca al abrir la hoja, como mucho una: lo gordo de ayer sin hacer que siga entre
     * los candidatos (solo tareas); si no, la vencida más antigua; si no, la que vence hoy.
     */
    fun sugerencia(
        candidatos: List<CandidatoGordo>,
        gordoAyerSinHacer: List<RefGordo>,
        state: DomainState,
        hoy: LogicalDay,
    ): RefGordo? {
        val refs = candidatos.map { it.ref }.toSet()
        gordoAyerSinHacer.firstOrNull { it.tipo == TipoRefGordo.TAREA && it in refs }?.let { return it }
        candidatos
            .filter { it.subtitulo is SubtituloGordo.TareaVencida }
            .maxByOrNull { (it.subtitulo as SubtituloGordo.TareaVencida).dias }
            ?.let { return it.ref }
        return candidatos.firstOrNull { it.subtitulo == SubtituloGordo.TareaHoy }?.ref
    }

    /** Una tarea terminada o un hábito cumplido en el periodo de hoy. */
    fun hecho(
        ref: RefGordo,
        state: DomainState,
        hoy: LogicalDay,
    ): Boolean =
        when (ref.tipo) {
            TipoRefGordo.TAREA -> state.tasks.any { it.id == ref.id && it.status == TaskStatus.DONE }
            TipoRefGordo.HABITO -> state.habits.firstOrNull { it.id == ref.id }?.let { cumplioHoy(state, it, hoy) } ?: false
        }

    private fun cumplioHoy(
        state: DomainState,
        habit: Habit,
        hoy: LogicalDay,
    ): Boolean = Compliance.complianceOf(state, habit, LogicalDays.periodKeyOf(hoy, habit.period), hoy) == ComplianceStatus.FULFILLED

    private fun subtituloDe(
        t: Tasks.TodayTask,
        hoy: LogicalDay,
    ): SubtituloGordo =
        when (t.slot) {
            Tasks.TodaySlot.OVERDUE -> SubtituloGordo.TareaVencida(hoy - (t.task.dueDay ?: hoy))
            Tasks.TodaySlot.DUE_TODAY -> SubtituloGordo.TareaHoy
            Tasks.TodaySlot.THIS_WEEK -> SubtituloGordo.TareaSemana
            else -> SubtituloGordo.Tarea
        }
}
