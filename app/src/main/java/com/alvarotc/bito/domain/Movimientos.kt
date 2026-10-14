package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.PointsLedgerEntry
import com.alvarotc.bito.domain.model.PointsReason

/** Qué dice cada movimiento de «Tus bitos» (pieza 3, §6.5). La UI lo pasa a texto; aquí no hay recursos. */
sealed interface EtiquetaMovimiento {
    data class Habito(val nombre: String?) : EtiquetaMovimiento

    data class Tarea(val titulo: String?) : EtiquetaMovimiento

    data object DiaCompleto : EtiquetaMovimiento

    data object SemanaCompleta : EtiquetaMovimiento

    data object MesCompleto : EtiquetaMovimiento

    data class Racha(val n: Int, val nombre: String?) : EtiquetaMovimiento

    data object Congelador : EtiquetaMovimiento

    data class Articulo(val itemId: String) : EtiquetaMovimiento
}

enum class FechaMovimiento { HOY, AYER, FECHA }

data class Movimiento(
    val id: String,
    val delta: Int,
    val etiqueta: EtiquetaMovimiento,
    val fecha: FechaMovimiento,
    val dia: LogicalDay,
)

object Movimientos {
    /** Las claves de `refId` son las de [com.alvarotc.bito.domain.model.PointsEvent]. Un nombre null es un hábito o una tarea borrados. */
    fun etiquetaDe(
        e: PointsLedgerEntry,
        habitos: Map<String, String>,
        tareas: Map<String, String>,
    ): EtiquetaMovimiento {
        val ref = e.refId.orEmpty()
        return when (e.reason) {
            PointsReason.HABIT_DONE -> EtiquetaMovimiento.Habito(habitos[ref.substringBeforeLast(':')])
            PointsReason.TASK_DONE -> EtiquetaMovimiento.Tarea(tareas[ref.removePrefix("task:")])
            PointsReason.PERFECT_DAY ->
                when {
                    ref.startsWith("week:") -> EtiquetaMovimiento.SemanaCompleta
                    ref.startsWith("month:") -> EtiquetaMovimiento.MesCompleto
                    else -> EtiquetaMovimiento.DiaCompleto
                }
            PointsReason.STREAK_MILESTONE ->
                EtiquetaMovimiento.Racha(ref.substringAfterLast(':').toIntOrNull() ?: 0, habitos[ref.substringBeforeLast(':')])
            PointsReason.BUY_FREEZER -> EtiquetaMovimiento.Congelador
            PointsReason.BUY_ITEM -> EtiquetaMovimiento.Articulo(ref)
        }
    }

    fun fechaDe(
        dia: LogicalDay,
        hoy: LogicalDay,
    ): FechaMovimiento =
        when (dia) {
            hoy -> FechaMovimiento.HOY
            hoy - 1 -> FechaMovimiento.AYER
            else -> FechaMovimiento.FECHA
        }

    fun de(
        entradas: List<PointsLedgerEntry>,
        state: DomainState,
        hoy: LogicalDay,
    ): List<Movimiento> {
        val habitos = state.habits.associate { it.id to it.name }
        val tareas = state.tasks.associate { it.id to it.title }
        return entradas.map { Movimiento(it.id, it.delta, etiquetaDe(it, habitos, tareas), fechaDe(it.logicalDay, hoy), it.logicalDay) }
    }
}
