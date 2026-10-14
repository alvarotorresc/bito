package com.alvarotc.bito.ui.sistema.componentes

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** El calendario hacia delante (G30): de este mes a +11. */
object CalendarioLogica {
    const val MESES = 12

    fun meses(hoy: LocalDate): List<YearMonth> = (0 until MESES).map { YearMonth.from(hoy).plusMonths(it.toLong()) }

    fun elegible(
        dia: LocalDate,
        hoy: LocalDate,
    ): Boolean = !dia.isBefore(hoy) && !YearMonth.from(dia).isAfter(YearMonth.from(hoy).plusMonths((MESES - 1).toLong()))

    /** Las celdas del mes con lunes primero; null = hueco. Siempre múltiplo de 7. */
    fun celdas(mes: YearMonth): List<LocalDate?> {
        val huecos = mes.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
        val dias = (1..mes.lengthOfMonth()).map { mes.atDay(it) }
        val antes = List(huecos) { null }
        val despues = List((7 - (huecos + dias.size) % 7) % 7) { null }
        return antes + dias + despues
    }
}
