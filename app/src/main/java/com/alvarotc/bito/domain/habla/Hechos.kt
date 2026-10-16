package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.Compliance
import com.alvarotc.bito.domain.ComplianceStatus
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.MoodEngine
import com.alvarotc.bito.domain.Sealing
import com.alvarotc.bito.domain.StatsEngine
import com.alvarotc.bito.domain.Streaks
import com.alvarotc.bito.domain.Tasks
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.EconomyConfig
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.HabitStatus
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.Period
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus
import java.time.DayOfWeek
import java.time.ZoneId

/** Tipo de hábito tal como lo nombra Habi. */
enum class TipoHabito { CHECK, CONTADOR, DURACION, LIMITE, DEJAR }

/** Un hábito visto desde hoy (spec §4.3): lo que las reglas necesitan saber de él. */
data class HabitoHoy(
    val id: String,
    val nombre: String,
    val tipo: TipoHabito,
    val diario: Boolean,
    val exigibleHoy: Boolean,
    val hechoHoy: Boolean,
    val falladoHoy: Boolean,
    val enPausa: Boolean,
    val pausadoDesde: LogicalDay?,
    val progreso: Int,
    val objetivo: Int,
    val paso: Int,
    val unidad: String?,
    val racha: Int,
    val mejorRacha: Int,
    val diasLimpios: Int,
    val creadoHaceDias: Int,
    val semanasHistoria: Int,
    val falladoAyer: Boolean,
    /** Cumplidos de los tres días anteriores a ayer (hoy-2..hoy-4), spec §5.1 n.º 8. */
    val cumplidosTresAnteriores: Int,
    /** Cumplimiento de los últimos 30 días juzgados (0–100). */
    val pct30: Int?,
    val juzgados30: Int,
    /** Este día de la semana, últimas 8 apariciones. */
    val pctHoySemana: Int?,
    val muestrasHoySemana: Int,
    /** §5.7 `FI_MEJOR_DIA`. */
    val mejorDiaSemana: DayOfWeek?,
    val pctMejorDiaSemana: Int?,
    val horaHabitual: Int?,
    val recordatorio: Int?,
    /** 7, 30, 100 o 365 cuando la racha está a 1 o 2 periodos. */
    val hitoCerca: Int?,
    val faltanHito: Int?,
    val bitosHito: Int?,
    val rachaHoyRedonda: Boolean,
    val rachaRotaRecuperada: Boolean,
    /** Tiene hoy un «Vale, a las…» aceptado que aún no ha sonado: no se le ofrece otra hora (§5.2). */
    val avisoPendienteHoy: Boolean = false,
)

/** Una tarea de hoy vista por Habi. */
data class TareaHoy(
    val id: String,
    val titulo: String,
    val primerPaso: String?,
    val vencidaDias: Int?,
    val venceHoy: Boolean,
    val hecha: Boolean,
    val empezada: Boolean,
)

/**
 * Lo que el motor sabe de hoy (spec §4.3). Los tres últimos campos salen de [LoGordo], para que
 * las reglas, el plan de avisos y la hoja cuenten lo mismo.
 */
data class Hechos(
    val hoy: LogicalDay,
    /** Activos y en pausa; nunca archivados. */
    val habitos: List<HabitoHoy>,
    /** `Tasks.todayTasks` más las hechas hoy. */
    val tareas: List<TareaHoy>,
    /** Lo gordo de hoy. */
    val gordo: List<RefGordo>,
    /** Solo tareas. */
    val gordoAyerSinHacer: List<RefGordo>,
    val diasSinActividad: Int,
    val primerDiaTrasAusencia: Boolean,
    /** 0 bien, 1 regular, 2 duro. */
    val animoAyer: Int?,
    /** `animoAyer == 2`. */
    val diaDuro: Boolean,
    val diaSellado: Boolean,
    val tuDiaHechos: Int,
    val tuDiaTotal: Int,
    /** Las filas que el cierre pregunta hoy. */
    val pendientesCierre: Int,
    /** Títulos. */
    val tareasPendientesHoy: List<String>,
    val animoSemana: Mood,
    val diasPerfectosMes: Int,
    val deltaSemanaPct: Int?,
    /** Cuántos candidatos tiene hoy la hoja de lo gordo ([LoGordo.candidatos]). */
    val candidatosGordo: Int = 0,
    /** La sugerencia de la hoja ([LoGordo.sugerencia]); siempre una tarea de [tareas]. */
    val sugerenciaGordo: RefGordo? = null,
    /** Lo gordo de hoy que ya está hecho ([LoGordo.hecho]). */
    val gordoHecho: List<RefGordo> = emptyList(),
    /** El título de la primera tarea de lo gordo de ayer que está hecha; null si no hay (`re_gordo_hecho`). */
    val tareaGordoAyerHecha: String? = null,
) {
    companion object {
        private const val DIAS_SEMANA = 7
        private const val ANIMO_DURO = 2
        private val ECONOMIA = EconomyConfig()

        /**
         * Los hechos de hoy, calculados desde cero. [recordatorios] es el recordatorio propio de cada hábito
         * (minutos, por id). Lo caro de cada hábito se puede recordar con [HechosCache].
         */
        fun de(
            state: DomainState,
            memoria: Memoria,
            ctx: Contexto,
            recordatorios: Map<String, Int>,
            empezadas: Set<String> = emptySet(),
        ): Hechos = deConCaros(state, memoria, ctx, recordatorios, empezadas) { habit -> CarosDeHabito.calcular(state, habit, ctx) }

        /**
         * Los días fuera si un registro en [dia] (con [state] aún sin él) es el primero tras la ausencia (spec §5.11):
         * `dia - última actividad`, con la misma cuenta que la tarjeta de vuelta. Null si no hubo ausencia o si ya hubo
         * actividad ese día.
         */
        fun diasFueraAlVolver(
            state: DomainState,
            dia: LogicalDay,
            corteDia: Int,
            zona: ZoneId,
        ): Int? {
            val ultima = ultimaActividadAntesDeVolver(state, dia, corteDia, zona) ?: return null
            return (dia - ultima).takeIf { it >= Ritmo.DIAS_AUSENCIA }
        }

        /**
         * Si queda algo pendiente hoy, con la misma cuenta que `pendienteHoy()` de las reglas (exigible, sin hacer y
         * sin fallar): un límite o un dejar limpio ya no espera nada, aunque su periodo siga PENDING hasta sellar. Es
         * lo que decide la fase (`Contextos.de`, ESPERANDO) y lo que mira «Todo hecho», así que van siempre juntas.
         */
        fun quedanPendientes(
            state: DomainState,
            hoy: LogicalDay,
        ): Boolean =
            state.habits
                .filter { it.status != HabitStatus.ARCHIVED && Compliance.isAliveOn(it, hoy) }
                .any { estadoDeHoy(state, it, hoy).pendiente }

        internal fun deConCaros(
            state: DomainState,
            memoria: Memoria,
            ctx: Contexto,
            recordatorios: Map<String, Int>,
            empezadas: Set<String> = emptySet(),
            caros: (Habit) -> CarosDeHabito,
        ): Hechos {
            val hoy = ctx.hoy
            val conAvisoPendiente = memoria.avisos.filter { it.dia == hoy && it.estado == EstadoAviso.PENDIENTE }.map { it.habitId }.toSet()
            val habitos =
                state.habits
                    .filter { it.status != HabitStatus.ARCHIVED && Compliance.isAliveOn(it, hoy) }
                    .map { habitoHoy(state, it, ctx, recordatorios[it.id], caros(it), it.id in conAvisoPendiente) }
            val tareas = tareasDeHoy(state, hoy, empezadas)
            val diaSellado = Sealing.isSealed(state, hoy)
            val gordo = memoria.gordo[hoy].orEmpty()
            val gordoAyerSinHacer =
                memoria.gordo[hoy - 1].orEmpty().filter { ref ->
                    ref.tipo == TipoRefGordo.TAREA && state.tasks.any { it.id == ref.id && it.status == TaskStatus.OPEN }
                }
            val candidatos = LoGordo.candidatos(state, hoy)
            val ultimaActividad = ultimaActividadAntesDeVolver(state, ctx.hoy, ctx.corteDia, ctx.zona)
            val diasSinActividad = ultimaActividad?.let { (hoy - it - 1).coerceAtLeast(0) } ?: 0
            val animoAyer =
                memoria.eventos
                    .filter { it.tipo == TipoMemoria.ANIMO && it.dia == hoy - 1 }
                    .maxByOrNull { it.creadoMillis }
                    ?.valor
            return Hechos(
                hoy = hoy,
                habitos = habitos,
                tareas = tareas,
                gordo = gordo,
                gordoAyerSinHacer = gordoAyerSinHacer,
                diasSinActividad = diasSinActividad,
                primerDiaTrasAusencia =
                    ultimaActividad != null && hoy - ultimaActividad >= Ritmo.DIAS_AUSENCIA && !vueltaRespondidaHoy(memoria, hoy),
                animoAyer = animoAyer,
                diaDuro = animoAyer == ANIMO_DURO,
                diaSellado = diaSellado,
                tuDiaHechos = habitos.count { it.exigibleHoy && it.hechoHoy },
                tuDiaTotal = habitos.count { it.exigibleHoy },
                pendientesCierre = habitos.count { pendienteEnElCierre(it, diaSellado) },
                tareasPendientesHoy = tareas.filterNot { it.hecha }.map { it.titulo },
                animoSemana = MoodEngine.moodOf(state, hoy, StatsEngine.lastActivityDay(state)),
                diasPerfectosMes = StatsEngine.perfectDays(state, hoy).thisMonth,
                deltaSemanaPct = StatsEngine.weekSummary(state, hoy).deltaVsLastWeek,
                candidatosGordo = candidatos.size,
                sugerenciaGordo = LoGordo.sugerencia(candidatos, gordoAyerSinHacer, state, hoy),
                gordoHecho = gordo.filter { LoGordo.hecho(it, state, hoy) },
                tareaGordoAyerHecha =
                    memoria.gordo[hoy - 1]
                        .orEmpty()
                        .filter { it.tipo == TipoRefGordo.TAREA }
                        .firstNotNullOfOrNull { ref -> state.tasks.firstOrNull { it.id == ref.id && it.status == TaskStatus.DONE }?.title },
            )
        }

        /**
         * La última actividad como la cuenta `StatsEngine.lastActivityDay` (el mismo umbral que DRAMATIC en
         * `MoodEngine`), sin los sellos de días pasados hechos hoy: «Sellar N días» de la hoja «Estuviste fuera»
         * no acaba con la ausencia que la tarjeta de vuelta tiene que nombrar (spec §5.6). Null si nunca hubo.
         */
        private fun ultimaActividadAntesDeVolver(
            state: DomainState,
            hoy: LogicalDay,
            corteDia: Int,
            zona: ZoneId,
        ): LogicalDay? {
            val sellosDeAntes =
                state.daySeals.filterNot {
                    it.logicalDay < hoy && LogicalDays.logicalDayOf(it.sealedAtMillis, corteDia, zona) >= hoy
                }
            return StatsEngine.lastActivityDay(state.copy(daySeals = sellosDeAntes))
        }

        /** Habi ya respondió a la vuelta hoy, o la dejó registrada. */
        private fun vueltaRespondidaHoy(
            memoria: Memoria,
            hoy: LogicalDay,
        ): Boolean =
            memoria.eventos.any {
                it.dia == hoy &&
                    (
                        it.tipo == TipoMemoria.VUELTA ||
                            (it.tipo == TipoMemoria.RESPUESTA && it.ref?.startsWith("${Situacion.VUELTA.name}:") == true)
                    )
            }

        /**
         * La regla de `reviewRowsOf` en el dominio: de hacer, lo no hecho; límites y dejar, limpios mientras el
         * día no está sellado; nunca los fallados.
         */
        private fun pendienteEnElCierre(
            h: HabitoHoy,
            diaSellado: Boolean,
        ): Boolean =
            h.exigibleHoy &&
                !h.falladoHoy &&
                when (h.tipo) {
                    TipoHabito.LIMITE, TipoHabito.DEJAR -> !diaSellado
                    else -> !h.hechoHoy
                }

        /**
         * `Tasks.todayTasks` y a continuación las que se hicieron hoy. «Empezada» es un intento registrado hoy o una
         * sesión de hoy ([empezadas]: la cerrada con foco y la viva, que aún no ha escrito su fila).
         */
        private fun tareasDeHoy(
            state: DomainState,
            hoy: LogicalDay,
            empezadas: Set<String>,
        ): List<TareaHoy> {
            val intentadas =
                state.taskEvents.filter { it.kind == TaskEventKind.ATTEMPT && it.logicalDay == hoy }.map { it.taskId }.toSet() + empezadas
            val abiertas =
                Tasks.todayTasks(state, hoy).map { t ->
                    TareaHoy(
                        id = t.task.id,
                        titulo = t.task.title,
                        primerPaso = t.task.firstStep,
                        vencidaDias = if (t.slot == Tasks.TodaySlot.OVERDUE) t.task.dueDay?.let { hoy - it } else null,
                        venceHoy = t.slot == Tasks.TodaySlot.DUE_TODAY,
                        hecha = false,
                        empezada = t.task.id in intentadas,
                    )
                }
            val hechas =
                state.tasks
                    .filter { it.status == TaskStatus.DONE && it.doneOnDay == hoy }
                    .sortedWith(compareBy({ it.createdAtMillis }, { it.id }))
                    .map { t ->
                        TareaHoy(
                            t.id,
                            t.title,
                            t.firstStep,
                            vencidaDias = null,
                            venceHoy = t.dueDay == hoy,
                            hecha = true,
                            empezada = t.id in intentadas,
                        )
                    }
            return abiertas + hechas
        }

        private fun tipoDe(habit: Habit): TipoHabito =
            when {
                habit.direction == Direction.ZERO -> TipoHabito.DEJAR
                habit.direction == Direction.AT_MOST -> TipoHabito.LIMITE
                habit.metric == Metric.CHECK || habit.logMode == LogMode.BINARY -> TipoHabito.CHECK
                habit.metric == Metric.COUNT -> TipoHabito.CONTADOR
                else -> TipoHabito.DURACION
            }

        /** Exigible, hecho y fallado hoy: lo que comparten [HabitoHoy] y [quedanPendientes]. */
        private class EstadoDeHoy(
            val exigible: Boolean,
            val hecho: Boolean,
            val fallado: Boolean,
        ) {
            val pendiente: Boolean get() = exigible && !hecho && !fallado
        }

        private fun estadoDeHoy(
            state: DomainState,
            habit: Habit,
            hoy: LogicalDay,
        ): EstadoDeHoy {
            val clave = LogicalDays.periodKeyOf(hoy, habit.period)
            val compliance = Compliance.complianceOf(state, habit, clave, hoy)
            val enPausa = habit.status == HabitStatus.PAUSED || Compliance.isPausedOn(state, habit.id, hoy)
            val fallado = compliance == ComplianceStatus.FAILED
            // La misma cuenta que la tarjeta de Hoy (`cardOf`): lo que no exige nada más hoy cuenta como hecho.
            val hecho =
                when (habit.direction) {
                    Direction.AT_LEAST ->
                        compliance == ComplianceStatus.FULFILLED ||
                            (habit.period != Period.DAY && state.entries.any { it.habitId == habit.id && it.logicalDay == hoy })
                    Direction.AT_MOST, Direction.ZERO -> !fallado
                }
            return EstadoDeHoy(!enPausa && Compliance.isRequirableOn(state, habit, hoy), hecho, fallado)
        }

        private fun habitoHoy(
            state: DomainState,
            habit: Habit,
            ctx: Contexto,
            recordatorio: Int?,
            caros: CarosDeHabito,
            avisoPendienteHoy: Boolean,
        ): HabitoHoy {
            val hoy = ctx.hoy
            val diario = habit.period == Period.DAY
            val clave = LogicalDays.periodKeyOf(hoy, habit.period)
            val periodo = LogicalDays.daysOf(clave, habit.period)
            val compliance = Compliance.complianceOf(state, habit, clave, hoy)
            val diasExigibles = Compliance.requirableDaysOf(state, habit, clave)
            val entradas = state.entries.filter { it.habitId == habit.id && it.logicalDay in diasExigibles }
            val enPausa = habit.status == HabitStatus.PAUSED || Compliance.isPausedOn(state, habit.id, hoy)
            val estado = estadoDeHoy(state, habit, hoy)
            val exigibleHoy = estado.exigible
            val fallado = estado.fallado
            val hecho = estado.hecho
            val racha = Streaks.streaksOf(state, habit, hoy)
            val juzgados = juzgadosRecientes(state, habit, hoy)
            val creadoHaceDias = (hoy - habit.createdOnDay).coerceAtLeast(0)
            val hito = Ritmo.HITOS.firstOrNull { it - racha.current in 1..Ritmo.HITO_A_PERIODOS }
            return HabitoHoy(
                id = habit.id,
                nombre = habit.name,
                tipo = tipoDe(habit),
                diario = diario,
                exigibleHoy = exigibleHoy,
                hechoHoy = exigibleHoy && hecho,
                falladoHoy = fallado,
                enPausa = enPausa,
                pausadoDesde = pausadoDesde(state, habit, hoy),
                progreso = Compliance.progressOf(habit, entradas),
                objetivo = Compliance.targetOf(state, habit, periodo.last),
                paso = habit.step,
                unidad = habit.unit,
                racha = racha.current,
                mejorRacha = racha.best,
                diasLimpios = diasLimpios(state, habit, hoy),
                creadoHaceDias = creadoHaceDias,
                semanasHistoria = creadoHaceDias / DIAS_SEMANA,
                falladoAyer = diario && estadoDelDia(state, habit, hoy - 1, hoy) == ComplianceStatus.FAILED,
                cumplidosTresAnteriores =
                    if (diario) (2..4).count { estadoDelDia(state, habit, hoy - it, hoy) == ComplianceStatus.FULFILLED } else 0,
                pct30 = pctDe(juzgados.first, juzgados.second),
                juzgados30 = juzgados.second,
                pctHoySemana = caros.pctHoySemana,
                muestrasHoySemana = caros.muestrasHoySemana,
                mejorDiaSemana = caros.mejorDiaSemana,
                pctMejorDiaSemana = caros.pctMejorDiaSemana,
                horaHabitual = caros.horaHabitual,
                recordatorio = recordatorio,
                hitoCerca = hito,
                faltanHito = hito?.let { it - racha.current },
                bitosHito = hito?.let { ECONOMIA.streakMilestonePoints[it] },
                rachaHoyRedonda =
                    racha.current > 0 && racha.current % Ritmo.RACHA_REDONDA_CADA == 0 && compliance == ComplianceStatus.FULFILLED,
                rachaRotaRecuperada =
                    racha.current == Ritmo.RACHA_RECUPERADA && Streaks.hasResurrected(state, habit, hoy, Ritmo.RACHA_ROTA_MIN),
                avisoPendienteHoy = avisoPendienteHoy,
            )
        }

        private fun estadoDelDia(
            state: DomainState,
            habit: Habit,
            dia: LogicalDay,
            hoy: LogicalDay,
        ): ComplianceStatus = Compliance.complianceOf(state, habit, dia, hoy)

        /** Cumplidos y juzgados de los últimos [Ritmo.PERIODOS_JUZGADOS] periodos decididos (FULFILLED o FAILED). */
        private fun juzgadosRecientes(
            state: DomainState,
            habit: Habit,
            hoy: LogicalDay,
        ): Pair<Int, Int> {
            var cumplidos = 0
            var juzgados = 0
            var clave = LogicalDays.periodKeyOf(hoy, habit.period)
            val primera = LogicalDays.periodKeyOf(habit.createdOnDay, habit.period)
            while (clave >= primera && juzgados < Ritmo.PERIODOS_JUZGADOS) {
                when (Compliance.complianceOf(state, habit, clave, hoy)) {
                    ComplianceStatus.FULFILLED -> {
                        cumplidos++
                        juzgados++
                    }
                    ComplianceStatus.FAILED -> juzgados++
                    else -> Unit
                }
                clave--
            }
            return cumplidos to juzgados
        }

        /** Desde cuándo está en pausa, si la pausa abierta cubre hoy. */
        private fun pausadoDesde(
            state: DomainState,
            habit: Habit,
            hoy: LogicalDay,
        ): LogicalDay? =
            state.pauseIntervals
                .filter { it.habitId == habit.id && it.startDay <= hoy && (it.endDay == null || it.endDay >= hoy) }
                .maxOfOrNull { it.startDay }

        /** Dejar del todo: días desde la última recaída, o desde que se creó si no hay ninguna. */
        private fun diasLimpios(
            state: DomainState,
            habit: Habit,
            hoy: LogicalDay,
        ): Int {
            if (habit.direction != Direction.ZERO) return 0
            val ultimaRecaida = state.entries.filter { it.habitId == habit.id && it.logicalDay <= hoy }.maxOfOrNull { it.logicalDay }
            val desde = ultimaRecaida ?: habit.createdOnDay
            return (hoy - desde).coerceAtLeast(0)
        }
    }
}

/**
 * Los mismos hechos con las tareas de [empezadas] (las de una sesión de hoy, cerrada o viva) marcadas como empezadas.
 * La fuente lo aplica a unos hechos ya calculados, para no depender de cómo los calcule quien se los pase.
 */
fun Hechos.conEmpezadas(empezadas: Set<String>): Hechos =
    if (empezadas.isEmpty() || tareas.none { it.id in empezadas && !it.empezada }) {
        this
    } else {
        copy(tareas = tareas.map { if (it.id in empezadas) it.copy(empezada = true) else it })
    }
