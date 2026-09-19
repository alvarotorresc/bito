package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.TaskEventKind

/**
 * Las reglas de las tareas: que toca hoy, cual trae Habi y de que se avisa. Objeto puro en la
 * linea de Sealing y MoodEngine — recibe el estado y el dia, no lee reloj ni almacenamiento.
 */
object Tasks {
    /** Una fecha entra en Hoy con este margen de dias por delante. */
    const val DUE_SOON_DAYS = 3

    /** Una tarea suelta avisa una sola vez, a los tantos dias de creada. */
    const val LOOSE_NOTICE_DAYS = 7

    /** Una tarea de semana avisa el viernes: domingo menos dos, el ultimo dia habil. */
    const val WEEK_NOTICE_BEFORE_END = 2

    /**
     * El ultimo dia de la semana natural que contiene [day] — el domingo ISO, reutilizando la
     * aritmetica que ya existe. Domingo y no «el fin de semana segun Locale» por tres razones:
     * la app ya celebra la semana perfecta sobre esta misma semana ISO, los habitos de periodo
     * WEEK cierran ese mismo domingo, y Locale.getDefault() dentro de una funcion pura haria
     * que el mismo estado diera dos resultados segun el idioma del movil.
     */
    fun weekDueOf(day: LogicalDay): LogicalDay = LogicalDays.daysOf(LogicalDays.periodKeyOf(day, Period.WEEK), Period.WEEK).last

    /** Si el usuario dijo «hoy no» a esa tarea ese dia. */
    fun postponedOn(
        state: DomainState,
        taskId: String,
        day: LogicalDay,
    ): Boolean = hasEvent(state, taskId, TaskEventKind.POSTPONED, day)

    /** Si el usuario la trajo a Hoy a mano ese dia. */
    fun broughtOn(
        state: DomainState,
        taskId: String,
        day: LogicalDay,
    ): Boolean = hasEvent(state, taskId, TaskEventKind.BROUGHT, day)

    /** El dia en que se pospuso por ultima vez, o null si nunca — hunde la tarea en la cola de sueltas. */
    fun lastPostponedDay(
        state: DomainState,
        taskId: String,
    ): LogicalDay? =
        state.taskEvents
            .filter { it.taskId == taskId && it.kind == TaskEventKind.POSTPONED }
            .maxOfOrNull { it.logicalDay }

    private fun hasEvent(
        state: DomainState,
        taskId: String,
        kind: TaskEventKind,
        day: LogicalDay,
    ): Boolean = state.taskEvents.any { it.taskId == taskId && it.kind == kind && it.logicalDay == day }
}
