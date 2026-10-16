package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.Compliance
import com.alvarotc.bito.domain.ComplianceStatus
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.Direction
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.Period
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Lo caro de un hábito (spec §11.1): la hora habitual y el cumplimiento por día de la semana.
 * [HechosCache] lo recuerda por día lógico; [Hechos.de] lo calcula siempre.
 */
internal data class CarosDeHabito(
    val horaHabitual: Int?,
    val pctHoySemana: Int?,
    val muestrasHoySemana: Int,
    val mejorDiaSemana: DayOfWeek?,
    val pctMejorDiaSemana: Int?,
) {
    companion object {
        private const val MINUTOS_DIA = 24 * 60
        private const val CUARTO = 15
        private const val DIAS_SEMANA = 7

        fun calcular(
            state: DomainState,
            habit: Habit,
            ctx: Contexto,
        ): CarosDeHabito {
            val tabla = if (habit.period == Period.DAY) cumplimientoPorDiaSemana(state, habit, ctx) else emptyMap()
            val hoySemana = tabla[ctx.fecha.dayOfWeek]
            val semanas = (ctx.hoy - habit.createdOnDay) / DIAS_SEMANA
            val mejor =
                if (semanas < Ritmo.DIA_FLOJO_SEMANAS) {
                    null
                } else {
                    DayOfWeek.entries
                        .mapNotNull { dia -> tabla[dia]?.takeIf { it.juzgados >= Ritmo.DIA_FLOJO_MIN_JUZGADAS }?.let { dia to it } }
                        .maxByOrNull { (_, c) -> pctDe(c.cumplidos, c.juzgados) ?: 0 }
                }
            return CarosDeHabito(
                horaHabitual = horaHabitualDe(state, habit, ctx),
                pctHoySemana = hoySemana?.let { pctDe(it.cumplidos, it.juzgados) },
                muestrasHoySemana = hoySemana?.juzgados ?: 0,
                mejorDiaSemana = mejor?.first,
                pctMejorDiaSemana = mejor?.second?.let { pctDe(it.cumplidos, it.juzgados) },
            )
        }

        /**
         * La mediana del minuto local de los registros de los últimos [Ritmo.HORA_HABITUAL_DIAS] días hechos
         * el mismo día lógico que registran, con [Ritmo.HORA_HABITUAL_REGISTROS] o más, al cuarto de hora más
         * cercano. Solo hábitos de hacer: en los de dejar, un registro es una recaída.
         */
        private fun horaHabitualDe(
            state: DomainState,
            habit: Habit,
            ctx: Contexto,
        ): Int? {
            if (habit.direction != Direction.AT_LEAST) return null
            val minutos =
                state.entries
                    .filter { it.habitId == habit.id && it.logicalDay in (ctx.hoy - Ritmo.HORA_HABITUAL_DIAS + 1)..ctx.hoy }
                    .filter { LogicalDays.logicalDayOf(it.createdAtMillis, ctx.corteDia, ctx.zona) == it.logicalDay }
                    .map {
                        val hora = java.time.Instant.ofEpochMilli(it.createdAtMillis).atZone(ctx.zona)
                        hora.hour * 60 + hora.minute
                    }.sorted()
            if (minutos.size < Ritmo.HORA_HABITUAL_REGISTROS) return null
            val medio = minutos.size / 2
            val mediana = if (minutos.size % 2 == 1) minutos[medio] else (minutos[medio - 1] + minutos[medio]) / 2
            return ((mediana + CUARTO / 2) / CUARTO * CUARTO).coerceAtMost(MINUTOS_DIA - CUARTO)
        }

        /**
         * Cumplidos y juzgados de cada día de la semana en sus últimas [Ritmo.DIA_FLOJO_APARICIONES] apariciones
         * antes de hoy: los [Ritmo.DIA_FLOJO_APARICIONES] × 7 días anteriores. Solo cuentan los días en que el
         * hábito exigía algo y quedó decidido.
         */
        private fun cumplimientoPorDiaSemana(
            state: DomainState,
            habit: Habit,
            ctx: Contexto,
        ): Map<DayOfWeek, Cuenta> {
            val cuentas = HashMap<DayOfWeek, Cuenta>()
            for (dia in (ctx.hoy - Ritmo.DIA_FLOJO_APARICIONES * DIAS_SEMANA) until ctx.hoy) {
                if (!Compliance.isRequirableOn(state, habit, dia)) continue
                val cumplido =
                    when (Compliance.complianceOf(state, habit, dia, ctx.hoy)) {
                        ComplianceStatus.FULFILLED -> true
                        ComplianceStatus.FAILED -> false
                        else -> continue
                    }
                val semana = LocalDate.ofEpochDay(dia.toLong()).dayOfWeek
                val antes = cuentas[semana] ?: Cuenta(0, 0)
                cuentas[semana] = Cuenta(antes.cumplidos + if (cumplido) 1 else 0, antes.juzgados + 1)
            }
            return cuentas
        }
    }

    private data class Cuenta(
        val cumplidos: Int,
        val juzgados: Int,
    )
}

/** Porcentaje entero de [cumplidos] sobre [juzgados], o null sin juzgados. */
internal fun pctDe(
    cumplidos: Int,
    juzgados: Int,
): Int? = if (juzgados == 0) null else (cumplidos * 100.0 / juzgados).roundToInt()
