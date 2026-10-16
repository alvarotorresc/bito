package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.RefGordo
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoRefGordo
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.habito
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.habla.tarea
import com.alvarotc.bito.domain.millisOn
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglaGordoSinEmpezarTest {
    private val informe = RefGordo(TipoRefGordo.TAREA, "t1")
    private val banco = RefGordo(TipoRefGordo.TAREA, "t2")
    private val leer = RefGordo(TipoRefGordo.HABITO, "h1")

    private fun h(
        empezada: Boolean = false,
        gordo: List<RefGordo> = listOf(informe, banco),
        gordoHecho: List<RefGordo> = emptyList(),
        progresoLeer: Boolean = false,
    ) = hechos(
        habitos = listOf(habito("h1", nombre = "Leer", hechoHoy = progresoLeer)),
        tareas =
            listOf(
                tarea("t1", titulo = "El informe", empezada = empezada),
                tarea("t2", titulo = "Llamar al banco", primerPaso = "Buscar el número"),
            ),
        gordo = gordo,
        gordoHecho = gordoHecho,
    )

    private fun elegidoA(
        hora: Int,
        minuto: Int = 0,
    ) = Memoria(gordo = mapOf(TODAY to listOf(informe, banco)), gordoElegidoMillis = millisOn(TODAY, hora, minuto))

    private fun a(
        hora: Int,
        minuto: Int = 0,
    ) = ctx(ahoraMin = hora * 60 + minuto)

    @Test
    fun `one hour fifty-nine after choosing at ten is still early`() =
        assertTrue(ReglaGordoSinEmpezar.candidatas(h(), elegidoA(10), a(11, 59)).isEmpty())

    @Test
    fun `two hours after choosing at ten it nudges`() {
        val c = ReglaGordoSinEmpezar.candidatas(h(), elegidoA(10), a(12)).single()
        assertEquals(Situacion.GORDO_SIN_EMPEZAR, c.situacion)
        assertEquals("t1", c.ref)
        assertEquals(listOf(Respuesta.AHORA, Respuesta.LUEGO), c.acciones)
        assertEquals(ValorHueco.Lista(listOf("El informe", "Llamar al banco")), c.huecos[Hueco.GORDO])
        assertEquals(ValorHueco.Texto("Buscar el número"), c.huecos[Hueco.PRIMER_PASO])
        assertNull(c.huecos[Hueco.TAREA])
        assertEquals(Destino.Tarea("t1"), c.destino)
    }

    @Test
    fun `two hours after choosing at eight it nudges before noon`() =
        assertEquals(1, ReglaGordoSinEmpezar.candidatas(h(), elegidoA(8), a(10)).size)

    @Test
    fun `at noon sharp, ten minutes after choosing, it waits`() =
        assertTrue(ReglaGordoSinEmpezar.candidatas(h(), elegidoA(11, 50), a(12)).isEmpty())

    @Test
    fun `past noon it nudges even if chosen ten minutes ago`() =
        assertEquals(1, ReglaGordoSinEmpezar.candidatas(h(), elegidoA(11, 51), a(12, 1)).size)

    @Test
    fun `a started task keeps it quiet`() = assertTrue(ReglaGordoSinEmpezar.candidatas(h(empezada = true), elegidoA(8), a(13)).isEmpty())

    @Test
    fun `a done part keeps it quiet`() =
        assertTrue(ReglaGordoSinEmpezar.candidatas(h(gordoHecho = listOf(banco)), elegidoA(8), a(13)).isEmpty())

    @Test
    fun `without lo gordo there is nothing to start`() =
        assertTrue(ReglaGordoSinEmpezar.candidatas(h(gordo = emptyList()), Memoria(), a(13)).isEmpty())

    @Test
    fun `a habit first goes to the habit, without first step`() {
        val c =
            ReglaGordoSinEmpezar
                .candidatas(h(gordo = listOf(leer, informe)), Memoria(gordoElegidoMillis = millisOn(TODAY, 8)), a(13))
                .single()
        assertEquals("h1", c.ref)
        assertEquals(Destino.Habito("h1"), c.destino)
        assertEquals(ValorHueco.Lista(listOf("Leer", "El informe")), c.huecos[Hueco.GORDO])
        assertNull(c.huecos[Hueco.PRIMER_PASO])
    }

    @Test
    fun `a habit already done keeps it quiet`() =
        assertTrue(ReglaGordoSinEmpezar.candidatas(h(gordo = listOf(leer), progresoLeer = true), elegidoA(8), a(13)).isEmpty())
}
