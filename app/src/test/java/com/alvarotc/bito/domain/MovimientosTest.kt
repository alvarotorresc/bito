package com.alvarotc.bito.domain

import com.alvarotc.bito.domain.model.PointsReason
import org.junit.Assert.assertEquals
import org.junit.Test

class MovimientosTest {
    private val habitos = mapOf("h1" to "Leer")
    private val tareas = mapOf("t1" to "Llamar al banco")

    private fun etiqueta(
        reason: PointsReason,
        ref: String?,
    ) = Movimientos.etiquetaDe(ledgerEntry(1, reason, ref), habitos, tareas)

    @Test
    fun `every reason has its label`() {
        assertEquals(EtiquetaMovimiento.Habito("Leer"), etiqueta(PointsReason.HABIT_DONE, "h1:20679"))
        assertEquals(EtiquetaMovimiento.Tarea("Llamar al banco"), etiqueta(PointsReason.TASK_DONE, "task:t1"))
        assertEquals(EtiquetaMovimiento.DiaCompleto, etiqueta(PointsReason.PERFECT_DAY, "day:20679"))
        assertEquals(EtiquetaMovimiento.SemanaCompleta, etiqueta(PointsReason.PERFECT_DAY, "week:2954"))
        assertEquals(EtiquetaMovimiento.MesCompleto, etiqueta(PointsReason.PERFECT_DAY, "month:24319"))
        assertEquals(EtiquetaMovimiento.Racha(30, "Leer"), etiqueta(PointsReason.STREAK_MILESTONE, "h1:30"))
        assertEquals(EtiquetaMovimiento.Congelador, etiqueta(PointsReason.BUY_FREEZER, "uuid"))
        assertEquals(EtiquetaMovimiento.Articulo("head-gorro-lana"), etiqueta(PointsReason.BUY_ITEM, "head-gorro-lana"))
    }

    @Test
    fun `a deleted habit or task keeps a label without a name`() {
        assertEquals(EtiquetaMovimiento.Habito(null), etiqueta(PointsReason.HABIT_DONE, "borrado:20679"))
        assertEquals(EtiquetaMovimiento.Tarea(null), etiqueta(PointsReason.TASK_DONE, "task:borrada"))
        assertEquals(EtiquetaMovimiento.Racha(7, null), etiqueta(PointsReason.STREAK_MILESTONE, "borrado:7"))
    }

    @Test
    fun `dates read today, yesterday or a date`() {
        assertEquals(FechaMovimiento.HOY, Movimientos.fechaDe(TODAY, TODAY))
        assertEquals(FechaMovimiento.AYER, Movimientos.fechaDe(TODAY - 1, TODAY))
        assertEquals(FechaMovimiento.FECHA, Movimientos.fechaDe(TODAY - 2, TODAY))
    }
}
