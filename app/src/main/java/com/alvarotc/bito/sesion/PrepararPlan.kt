package com.alvarotc.bito.sesion

import com.alvarotc.bito.data.settings.Settings
import com.alvarotc.bito.domain.Compliance
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.Tasks
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.PlanSesion

/** Lo que sale de abrir Preparar. */
sealed interface Preparacion {
    data class Borrador(val plan: PlanSesion) : Preparacion

    /** Un hábito de contar no prepara sesión: se suma un paso y se vuelve a Hoy (spec §21.4). */
    data class SumarPaso(val habitoId: String, val paso: Int) : Preparacion

    data object Nada : Preparacion
}

/** Lo que viene relleno en Preparar según de dónde se llega (spec §4.1). Puro. */
object PrepararPlan {
    private const val PASO_MINUTOS = 5
    private const val MINUTOS_MAXIMOS = 180

    fun de(
        desde: Destino?,
        ajustes: Settings,
        estado: DomainState,
        hoy: LogicalDay,
        gordo: List<RefGordo>,
        permisoNoMolestar: Boolean,
    ): Preparacion {
        val base = Base(ajustes, permisoNoMolestar)
        return when (desde) {
            is Destino.Tarea -> base.plan(Objetivo.Tarea(desde.id))
            is Destino.Habito -> deHabito(desde.id, estado, hoy, base)
            Destino.LoGordo -> deLoGordo(estado, hoy, gordo, base) ?: sinDestino(estado, hoy, gordo, base)
            null -> sinDestino(estado, hoy, gordo, base)
            Destino.Respirar, Destino.Cierre -> Preparacion.Nada
        }
    }

    /** La primera tarea de lo gordo sin hacer; si no hay tareas, el primer hábito. */
    private fun deLoGordo(
        estado: DomainState,
        hoy: LogicalDay,
        gordo: List<RefGordo>,
        base: Base,
    ): Preparacion? {
        primeraTareaGorda(estado, gordo)?.let { return base.plan(Objetivo.Tarea(it)) }
        val habito =
            gordo.firstOrNull { ref ->
                ref.tipo == TipoRefGordo.HABITO && estado.habits.any { it.id == ref.id && it.status == HabitStatus.ACTIVE }
            } ?: return null
        return deHabito(habito.id, estado, hoy, base)
    }

    /** + → Sesión ahora → Foco: lo gordo si hay; si no, la primera de Hoy (vencida más antigua, la de hoy…); sin tareas, libre. */
    private fun sinDestino(
        estado: DomainState,
        hoy: LogicalDay,
        gordo: List<RefGordo>,
        base: Base,
    ): Preparacion {
        val tarea = primeraTareaGorda(estado, gordo) ?: Tasks.todayTasks(estado, hoy).firstOrNull()?.task?.id
        return base.plan(tarea?.let { Objetivo.Tarea(it) } ?: Objetivo.Libre)
    }

    private fun primeraTareaGorda(
        estado: DomainState,
        gordo: List<RefGordo>,
    ): String? =
        gordo.firstOrNull { ref ->
            ref.tipo == TipoRefGordo.TAREA && estado.tasks.any { it.id == ref.id && it.status == TaskStatus.OPEN }
        }?.id

    private fun deHabito(
        id: String,
        estado: DomainState,
        hoy: LogicalDay,
        base: Base,
    ): Preparacion {
        val habito = estado.habits.firstOrNull { it.id == id } ?: return Preparacion.Nada
        return when {
            deMarcar(habito) -> base.plan(Objetivo.Habito(id))
            habito.metric == Metric.COUNT -> Preparacion.SumarPaso(id, habito.step)
            else -> base.plan(Objetivo.Habito(id), minutos = minutosQueFaltan(habito, estado, hoy), bloques = 1)
        }
    }

    /** La misma regla que la tarjeta de Hoy: un toque es el día. */
    private fun deMarcar(habito: Habit): Boolean = habito.logMode == LogMode.BINARY || habito.metric == Metric.CHECK

    /** Lo que le falta en su periodo, redondeado hacia arriba a 5 y como mínimo 5. */
    private fun minutosQueFaltan(
        habito: Habit,
        estado: DomainState,
        hoy: LogicalDay,
    ): Int {
        val dias = LogicalDays.daysOf(LogicalDays.periodKeyOf(hoy, habito.period), habito.period)
        val hecho = Compliance.progressOf(habito, estado.entries.filter { it.habitId == habito.id && it.logicalDay in dias })
        val falta = (Compliance.targetOf(estado, habito, dias.last) - hecho).coerceAtLeast(0)
        val redondeado = (falta + PASO_MINUTOS - 1) / PASO_MINUTOS * PASO_MINUTOS
        return redondeado.coerceIn(PASO_MINUTOS, MINUTOS_MAXIMOS)
    }

    private class Base(
        private val ajustes: Settings,
        private val permisoNoMolestar: Boolean,
    ) {
        fun plan(
            objetivo: Objetivo,
            minutos: Int = ajustes.pomoFocus,
            bloques: Int = ajustes.pomoRounds,
        ): Preparacion =
            Preparacion.Borrador(
                PlanSesion(
                    objetivo = objetivo,
                    minutosFoco = minutos,
                    entrada = ajustes.sessionEntry,
                    bloques = bloques,
                    descansoCorto = ajustes.pomoShort,
                    descansoLargo = ajustes.pomoLong,
                    rondas = ajustes.pomoRounds,
                    descansoSolo = ajustes.pomoAutoBreak,
                    fondo = ajustes.sessionBackground,
                    noMolestar = ajustes.sessionDnd && permisoNoMolestar,
                ),
            )
    }
}
