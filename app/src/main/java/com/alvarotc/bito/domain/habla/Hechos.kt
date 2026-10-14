package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Mood
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
        /** Provisional: listas vacías y ceros. La Tarea 12 escribe el cálculo. */
        fun de(
            state: DomainState,
            memoria: Memoria,
            ctx: Contexto,
            recordatorios: Map<String, Int>,
        ): Hechos =
            Hechos(
                hoy = ctx.hoy,
                habitos = emptyList(),
                tareas = emptyList(),
                gordo = emptyList(),
                gordoAyerSinHacer = emptyList(),
                diasSinActividad = 0,
                primerDiaTrasAusencia = false,
                animoAyer = null,
                diaDuro = false,
                diaSellado = false,
                tuDiaHechos = 0,
                tuDiaTotal = 0,
                pendientesCierre = 0,
                tareasPendientesHoy = emptyList(),
                animoSemana = Mood.NORMAL,
                diasPerfectosMes = 0,
                deltaSemanaPct = null,
            )
    }
}
