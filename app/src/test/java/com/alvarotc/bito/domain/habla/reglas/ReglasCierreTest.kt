package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Candidata
import com.alvarotc.bito.domain.habla.HabitoHoy
import com.alvarotc.bito.domain.habla.Hechos
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Motor
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.habito
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.habla.tarea
import com.alvarotc.bito.domain.propuestas.Ambito
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasCierreTest {
    private fun cierra(h: Hechos): Candidata = Motor.elegir(Ambito.CIERRE, h, Memoria(), ctx())!!

    private fun conGordoHecho(vararg extra: HabitoHoy): Hechos {
        val ref = RefGordo(TipoRefGordo.TAREA, "t1")
        return hechos(
            habitos = extra.toList(),
            tareas = listOf(tarea("t1", "El informe", hecha = true)),
            gordo = listOf(ref),
            gordoHecho = listOf(ref),
        )
    }

    @Test
    fun `the default is the simple comment`() {
        val c = cierra(hechos(habitos = listOf(habito(hechoHoy = true)), pendientesCierre = 2))
        assertEquals(Situacion.CIE_SENCILLO, c.situacion)
        assertTrue(c.acciones.isEmpty())
    }

    @Test
    fun `nothing left to decide comes out when no row is pending`() {
        assertEquals(Situacion.CIE_NADA_PENDIENTE, cierra(hechos(pendientesCierre = 0)).situacion)
        assertEquals(Situacion.CIE_SENCILLO, cierra(hechos(pendientesCierre = 1)).situacion)
    }

    @Test
    fun `a streak on a round number comes out with habit and streak`() {
        val racha = habito(hechoHoy = true, racha = 10).copy(rachaHoyRedonda = true)
        val c = cierra(hechos(habitos = listOf(racha), pendientesCierre = 0))
        assertEquals(Situacion.CIE_RACHA_AVANZA, c.situacion)
        assertEquals(ValorHueco.Numero(10), c.huecos[Hueco.RACHA])
        assertEquals(ValorHueco.Texto("Leer"), c.huecos[Hueco.HABITO])
    }

    @Test
    fun `a habit missed yesterday and done today is an achievement`() {
        val logro = habito(hechoHoy = true, falladoAyer = true)
        val c = cierra(hechos(habitos = listOf(logro), pendientesCierre = 0))
        assertEquals(Situacion.CIE_LOGRO, c.situacion)
        assertEquals(ValorHueco.Texto("Leer"), c.huecos[Hueco.HABITO])
        assertEquals(Situacion.CIE_NADA_PENDIENTE, cierra(hechos(habitos = listOf(logro.copy(hechoHoy = false)))).situacion)
    }

    @Test
    fun `the big thing done wins over the rest and names it`() {
        val logro = habito(hechoHoy = true, falladoAyer = true)
        val c = cierra(conGordoHecho(logro))
        assertEquals(Situacion.CIE_GORDO_HECHO, c.situacion)
        assertEquals(ValorHueco.Lista(listOf("El informe")), c.huecos[Hueco.GORDO])
    }

    @Test
    fun `the big thing needs all of it done`() {
        val a = RefGordo(TipoRefGordo.TAREA, "t1")
        val b = RefGordo(TipoRefGordo.TAREA, "t2")
        val parcial = hechos(tareas = listOf(tarea("t1", hecha = true), tarea("t2")), gordo = listOf(a, b), gordoHecho = listOf(a))
        assertEquals(Situacion.CIE_NADA_PENDIENTE, cierra(parcial).situacion)
        assertEquals(Situacion.CIE_NADA_PENDIENTE, cierra(hechos()).situacion)
    }

    @Test
    fun `achievement beats round streak, and the stronger streak wins inside one`() {
        val logro = habito("a", hechoHoy = true, falladoAyer = true, racha = 1)
        val redonda = habito("b", hechoHoy = true, racha = 5).copy(rachaHoyRedonda = true)
        assertEquals(Situacion.CIE_LOGRO, cierra(hechos(habitos = listOf(redonda, logro))).situacion)
        val otra = habito("c", hechoHoy = true, racha = 10).copy(rachaHoyRedonda = true)
        assertEquals(ValorHueco.Numero(10), cierra(hechos(habitos = listOf(redonda, otra))).huecos[Hueco.RACHA])
    }

    @Test
    fun `the name is passed when there is one`() {
        assertEquals(ValorHueco.Texto("Álvaro"), cierra(hechos()).huecos[Hueco.NOMBRE])
    }
}
