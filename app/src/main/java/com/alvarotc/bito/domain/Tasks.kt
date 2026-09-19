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

    /**
     * La que Habi trae hoy: de las abiertas SIN plazo (una WEEK nunca es candidata, ya sale sola
     * todos los dias), la que lleva mas tiempo sin tocarse. Posponer la hunde al final de la cola
     * — eso es lo que significa «vuelve a la lista» (D5): mañana Habi trae la siguiente.
     *
     * Dos medias reglas que hay que dejar como estan:
     * 1. Si la elegida esta pospuesta HOY, hoy no hay suelta. No hay sustituta: Habi trae una al
     *    dia, y si la contestas «hoy no», ese hueco queda vacio hasta mañana.
     * 2. Si la elegida ya entro por BROUGHT, no se duplica — eso lo resuelve el orden del `when`
     *    de [slotOf], que mira BROUGHT antes que LOOSE.
     *
     * El hundimiento por posponer solo cuenta a partir de mañana: [sunkOn] ignora un pospuesto de
     * HOY mismo (todavia compite por edad como cualquier otra), asi que hoy la elegida sigue
     * siendo ella y es la comprobacion de mas abajo la que vacia el hueco — no una reordenacion
     * que ya la hubiera descartado antes de llegar ahi.
     */
    fun looseOfTheDay(
        state: DomainState,
        today: LogicalDay,
    ): Task? {
        val chosen =
            state.tasks
                .filter { it.status == TaskStatus.OPEN && it.dueKind == DueKind.NONE }
                .minWithOrNull(
                    compareBy(
                        { sunkOn(state, it.id, today) },
                        { it.createdOnDay },
                        { it.createdAtMillis },
                        { it.id },
                    ),
                ) ?: return null
        return if (postponedOn(state, chosen.id, today)) null else chosen
    }

    /** El dia en que la tarea quedo hundida al final de la cola, o el minimo si aun no cuenta. */
    private fun sunkOn(
        state: DomainState,
        taskId: String,
        today: LogicalDay,
    ): LogicalDay {
        val lastPostponed = lastPostponedDay(state, taskId) ?: return Int.MIN_VALUE
        return if (lastPostponed < today) lastPostponed else Int.MIN_VALUE
    }

    /**
     * De que se avisa hoy a las 12:00. Como el slot dispara UNA vez al dia y el predicado solo es
     * cierto en dias concretos, el recuento esta acotado por construccion: no hace falta guardar
     * «ya avisada», y por tanto no hay marcador que sincronizar, restaurar ni migrar.
     *
     * Una vencida NO vuelve a avisar: ya esta en Hoy todos los dias, e insistir seria regañar.
     *
     * Orden: plazo mas cercano primero. La notificacion solo nombra tres titulos, asi que el
     * orden decide cuales se ven — y la que vence antes es la que hay que ver. Es una decision
     * de producto, no un efecto secundario del filtro.
     */
    fun noticesOn(
        state: DomainState,
        today: LogicalDay,
    ): List<Task> =
        state.tasks
            .filter { it.status == TaskStatus.OPEN }
            .filter { task ->
                when (task.dueKind) {
                    DueKind.NONE -> today - task.createdOnDay == LOOSE_NOTICE_DAYS
                    DueKind.WEEK -> task.dueDay!! - today == WEEK_NOTICE_BEFORE_END
                    DueKind.DATE -> task.dueDay!! - today in setOf(DUE_SOON_DAYS, 1, 0)
                }
            }
            .sortedWith(compareBy({ it.dueDay ?: Int.MAX_VALUE }, { it.createdAtMillis }, { it.id }))

    private fun hasEvent(
        state: DomainState,
        taskId: String,
        kind: TaskEventKind,
        day: LogicalDay,
    ): Boolean = state.taskEvents.any { it.taskId == taskId && it.kind == kind && it.logicalDay == day }
}
