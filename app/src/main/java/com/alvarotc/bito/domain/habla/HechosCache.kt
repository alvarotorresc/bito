package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.model.DomainState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Calcula los [Hechos] recordando por día lógico lo caro (spec §11.1): la mediana de la hora habitual
 * y el cumplimiento por día de la semana de cada hábito. Lo recordado de un hábito vale mientras sea el
 * mismo día lógico y el hábito tenga el mismo número de entradas; una entrada nueva lo invalida.
 */
class HechosCache {
    private class Guardado(
        val dia: Int,
        val entradas: Int,
        val zona: java.time.ZoneId,
        val corteDia: Int,
        val caros: CarosDeHabito,
    )

    private val recuerdos = ConcurrentHashMap<String, Guardado>()
    private val contador = AtomicInteger(0)

    /** Cuántas veces se ha calculado lo caro de un hábito. Para los tests. */
    internal val calculos: Int get() = contador.get()

    fun hechos(
        state: DomainState,
        memoria: Memoria,
        ctx: Contexto,
        recordatorios: Map<String, Int>,
    ): Hechos {
        val entradasPorHabito = state.entries.groupingBy { it.habitId }.eachCount()
        return Hechos.deConCaros(state, memoria, ctx, recordatorios) { habit ->
            val entradas = entradasPorHabito[habit.id] ?: 0
            val guardado = recuerdos[habit.id]
            if (guardado != null && guardado.vale(ctx, entradas)) {
                guardado.caros
            } else {
                contador.incrementAndGet()
                CarosDeHabito.calcular(state, habit, ctx).also {
                    recuerdos[habit.id] = Guardado(ctx.hoy, entradas, ctx.zona, ctx.corteDia, it)
                }
            }
        }
    }

    private fun Guardado.vale(
        ctx: Contexto,
        entradas: Int,
    ): Boolean = dia == ctx.hoy && this.entradas == entradas && zona == ctx.zona && corteDia == ctx.corteDia
}
