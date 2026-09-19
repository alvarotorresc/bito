package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.DueKind
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.Task
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus

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

    /**
     * Por que via entra una tarea en Hoy. El orden del enum ES el orden de la pantalla, y es el
     * coste de no hacerla hoy: vencida ya cuesta; hoy es hoy o nunca; despues viene la unica que
     * no tiene dia (una de semana no se defiende sola, cada dia le quita un septimo); una fecha
     * proxima va debajo porque la fecha la defiende; luego lo que trajiste a mano; y al final la
     * que trae Habi.
     *
     * THIS_WEEK entra EN MEDIO del enum, al reves que PointsReason (que se añade siempre al
     * final): este es derivado y no se guarda en ningun sitio, asi que reordenarlo solo cambia
     * una lista que se recalcula entera en cada composicion.
     */
    enum class TodaySlot { OVERDUE, DUE_TODAY, THIS_WEEK, DUE_SOON, BROUGHT, LOOSE }

    /** Una tarea de Hoy con la via por la que llego. */
    data class TodayTask(val task: Task, val slot: TodaySlot)

    /**
     * Lo que toca hoy: abierta, no pospuesta hoy, y que cumpla al menos una de las seis vias
     * (spec §4.1). Si entra por dos, se queda con la primera del orden del enum.
     */
    fun todayTasks(
        state: DomainState,
        today: LogicalDay,
    ): List<TodayTask> {
        val loose = looseOfTheDay(state, today)
        val candidates =
            state.tasks
                .filter { it.status == TaskStatus.OPEN }
                .filter { !postponedOn(state, it.id, today) }
                .mapNotNull { taskOf ->
                    slotOf(state, taskOf, today, loose)?.let { TodayTask(taskOf, it) }
                }
        return candidates.sortedWith(
            compareBy(
                { it.slot.ordinal },
                { it.task.dueDay ?: Int.MAX_VALUE },
                { it.task.createdAtMillis },
                { it.task.id },
            ),
        )
    }

    private fun slotOf(
        state: DomainState,
        task: Task,
        today: LogicalDay,
        loose: Task?,
    ): TodaySlot? {
        val due = task.dueDay
        return when {
            due != null && due < today -> TodaySlot.OVERDUE
            due != null && due == today -> TodaySlot.DUE_TODAY
            task.dueKind == DueKind.WEEK -> TodaySlot.THIS_WEEK
            due != null && due - today <= DUE_SOON_DAYS -> TodaySlot.DUE_SOON
            broughtOn(state, task.id, today) -> TodaySlot.BROUGHT
            loose != null && loose.id == task.id -> TodaySlot.LOOSE
            else -> null
        }
    }

    /** La suelta del dia — la Tarea 3 la implementa; aqui todavia no hay ninguna. */
    fun looseOfTheDay(
        state: DomainState,
        today: LogicalDay,
    ): Task? = null

    private fun hasEvent(
        state: DomainState,
        taskId: String,
        kind: TaskEventKind,
        day: LogicalDay,
    ): Boolean = state.taskEvents.any { it.taskId == taskId && it.kind == kind && it.logicalDay == day }
}
