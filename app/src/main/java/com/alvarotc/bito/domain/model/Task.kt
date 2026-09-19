package com.alvarotc.bito.domain.model

/** Una tarea esta abierta o hecha; posponer es un suceso, no un estado. */
enum class TaskStatus { OPEN, DONE }

/** La historia de una tarea, append-only: traida a hoy, pospuesta, intentada. */
enum class TaskEventKind { BROUGHT, POSTPONED, ATTEMPT }

/** Los tres grados de plazo (D13): suelta, esta semana, fecha exacta. */
enum class DueKind { NONE, WEEK, DATE }

/**
 * Una tarea puntual. Tipo puro — la persistencia vive en la capa de datos.
 *
 * El plazo son DOS campos y no una jerarquia sellada: Room guarda columnas, asi que un
 * `sealed Due` habria que aplanarlo igual en [com.alvarotc.bito.data.db.TaskEntity] y escribir
 * el mapeo dos veces. Es ademas lo que ya hace Habit con (timeBucket, timeOfDayMinutes). El
 * invariante que la jerarquia daria gratis — `dueKind == NONE` si y solo si `dueDay == null` —
 * lo sostiene la disciplina de construccion (`datedTask()`, `weekTask()`, los mapeadores); los
 * mapeadores lo respetan, no lo reparan.
 *
 * [dueDay] es null si [dueKind] es NONE, el domingo de su semana natural si es WEEK
 * (materializado al crearla, nunca recalculado al vuelo) y la fecha elegida si es DATE.
 * [createdAtMillis] vive en el dominio — al reves que en Habit — porque «la mas antigua»
 * necesita desempate dentro de un mismo dia logico.
 */
data class Task(
    val id: String,
    val title: String,
    val firstStep: String?,
    val dueKind: DueKind = DueKind.NONE,
    val dueDay: LogicalDay?,
    val status: TaskStatus = TaskStatus.OPEN,
    val createdOnDay: LogicalDay,
    val createdAtMillis: Long,
    val doneOnDay: LogicalDay? = null,
)

/** Un suceso de la historia de una tarea. No se edita ni se borra (regla T4). */
data class TaskEvent(
    val id: String,
    val taskId: String,
    val kind: TaskEventKind,
    val logicalDay: LogicalDay,
)
