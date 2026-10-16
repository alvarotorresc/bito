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
        ): Hechos = deConCaros(state, memoria, ctx, recordatorios) { habit -> CarosDeHabito.calcular(state, habit, ctx) }

        internal fun deConCaros(
            state: DomainState,
            memoria: Memoria,
            ctx: Contexto,
            recordatorios: Map<String, Int>,
            caros: (Habit) -> CarosDeHabito,
        ): Hechos {
            val hoy = ctx.hoy
            val habitos =
                state.habits
                    .filter { it.status != HabitStatus.ARCHIVED && Compliance.isAliveOn(it, hoy) }
                    .map { habitoHoy(state, it, ctx, recordatorios[it.id], caros(it)) }
            val tareas = tareasDeHoy(state, hoy)
            val diaSellado = Sealing.isSealed(state, hoy)
            val gordo = memoria.gordo[hoy].orEmpty()
            val gordoAyerSinHacer =
                memoria.gordo[hoy - 1].orEmpty().filter { ref ->
                    ref.tipo == TipoRefGordo.TAREA && state.tasks.any { it.id == ref.id && it.status == TaskStatus.OPEN }
                }
            val candidatos = LoGordo.candidatos(state, hoy)
            val ultimaActividad = ultimaActividadAntesDeVolver(state, ctx)
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
            )
        }

        /**
         * La última actividad como la cuenta `StatsEngine.lastActivityDay` (el mismo umbral que DRAMATIC en
         * `MoodEngine`), sin los sellos de días pasados hechos hoy: «Sellar N días» de la hoja «Estuviste fuera»
         * no acaba con la ausencia que la tarjeta de vuelta tiene que nombrar (spec §5.6). Null si nunca hubo.
         */
        private fun ultimaActividadAntesDeVolver(
            state: DomainState,
            ctx: Contexto,
        ): LogicalDay? {
            val sellosDeAntes =
                state.daySeals.filterNot {
                    it.logicalDay < ctx.hoy && LogicalDays.logicalDayOf(it.sealedAtMillis, ctx.corteDia, ctx.zona) >= ctx.hoy
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

        /** `Tasks.todayTasks` y a continuación las que se hicieron hoy. «Empezada» es un intento registrado hoy. */
        private fun tareasDeHoy(
            state: DomainState,
            hoy: LogicalDay,
        ): List<TareaHoy> {
            val intentadas = state.taskEvents.filter { it.kind == TaskEventKind.ATTEMPT && it.logicalDay == hoy }.map { it.taskId }.toSet()
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

        private fun habitoHoy(
            state: DomainState,
            habit: Habit,
            ctx: Contexto,
            recordatorio: Int?,
            caros: CarosDeHabito,
        ): HabitoHoy {
            val hoy = ctx.hoy
            val diario = habit.period == Period.DAY
            val clave = LogicalDays.periodKeyOf(hoy, habit.period)
            val periodo = LogicalDays.daysOf(clave, habit.period)
            val compliance = Compliance.complianceOf(state, habit, clave, hoy)
            val diasExigibles = Compliance.requirableDaysOf(state, habit, clave)
            val entradas = state.entries.filter { it.habitId == habit.id && it.logicalDay in diasExigibles }
            val enPausa = habit.status == HabitStatus.PAUSED || Compliance.isPausedOn(state, habit.id, hoy)
            val exigibleHoy = !enPausa && Compliance.isRequirableOn(state, habit, hoy)
            val fallado = compliance == ComplianceStatus.FAILED
            // La misma cuenta que la tarjeta de Hoy (`cardOf`): lo que no exige nada más hoy cuenta como hecho.
            val hecho =
                when (habit.direction) {
                    Direction.AT_LEAST ->
                        compliance == ComplianceStatus.FULFILLED || (!diario && entradas.any { it.logicalDay == hoy })
                    Direction.AT_MOST, Direction.ZERO -> !fallado
                }
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
                    if (diario) (1..3).count { estadoDelDia(state, habit, hoy - it, hoy) == ComplianceStatus.FULFILLED } else 0,
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
