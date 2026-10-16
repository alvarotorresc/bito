package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Bolsa
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
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RespuestasTest {
    private val informe = RefGordo(TipoRefGordo.TAREA, "t1")
    private val banco = RefGordo(TipoRefGordo.TAREA, "t2")
    private val candidata = ReglaDiaDuro.candidatas(hechos(habitos = listOf(habito())).copy(diaDuro = true), Memoria(), ctx()).single()

    private fun conGordo(
        gordo: List<RefGordo>,
        hecho: List<RefGordo> = emptyList(),
    ) = hechos(
        tareas = listOf(tarea("t1", titulo = "El informe"), tarea("t2", titulo = "Llamar al banco")),
        gordo = gordo,
        gordoHecho = hecho,
    )

    @Test
    fun `with the big thing undone the confirmation offers its session`() {
        val c = Respuestas.confirmaVale(candidata, 14 * 60 + 30, conGordo(listOf(informe, banco)))
        assertEquals(Situacion.CONFIRMA_VALE, c.situacion)
        assertEquals(Bolsa.NU_CONFIRMA_VALE_GORDO, c.nucleo)
        assertEquals(listOf(Respuesta.AHORA, Respuesta.LUEGO), c.acciones)
        assertEquals(Destino.LoGordo, c.destino)
        assertEquals(ValorHueco.Hora(14 * 60 + 30), c.huecos[Hueco.HORA])
        assertEquals(ValorHueco.Lista(listOf("El informe", "Llamar al banco")), c.huecos[Hueco.GORDO])
        assertEquals(candidata.ref, c.ref)
        assertEquals(14 * 60 + 30, c.hora)
    }

    @Test
    fun `only what is left of the big thing is named`() {
        val c = Respuestas.confirmaVale(candidata, 900, conGordo(listOf(informe, banco), hecho = listOf(informe)))
        assertEquals(ValorHueco.Lista(listOf("Llamar al banco")), c.huecos[Hueco.GORDO])
    }

    @Test
    fun `without the big thing only Another hour is offered as text`() {
        val c = Respuestas.confirmaVale(candidata, 900, conGordo(emptyList()))
        assertEquals(Bolsa.NU_CONFIRMA_VALE, c.nucleo)
        assertEquals(listOf(Respuesta.OTRA_HORA), c.acciones)
        assertNull(c.destino)
        assertNull(c.huecos[Hueco.GORDO])
    }

    @Test
    fun `with the big thing all done it is the same as without`() {
        val c = Respuestas.confirmaVale(candidata, 900, conGordo(listOf(informe), hecho = listOf(informe)))
        assertEquals(Bolsa.NU_CONFIRMA_VALE, c.nucleo)
        assertEquals(listOf(Respuesta.OTRA_HORA), c.acciones)
    }

    @Test
    fun `with the big thing left but nothing to name it is the plain confirmation`() {
        val c = Respuestas.confirmaVale(candidata, 900, conGordo(listOf(RefGordo(TipoRefGordo.TAREA, "borrada"))))
        assertEquals(Bolsa.NU_CONFIRMA_VALE, c.nucleo)
        assertEquals(listOf(Respuesta.OTRA_HORA), c.acciones)
        assertNull(c.huecos[Hueco.GORDO])
        assertEquals(ValorHueco.Hora(900), c.huecos[Hueco.HORA])
    }

    @Test
    fun `the line after Not today has no actions and no holes`() {
        val c = Respuestas.trasHoyNo(conGordo(listOf(informe)))
        assertEquals(Situacion.TRAS_HOY_NO, c.situacion)
        assertTrue(c.acciones.isEmpty())
        assertTrue(c.huecos.isEmpty())
    }

    @Test
    fun `the night line carries the first undone task of the big thing`() {
        val c = Respuestas.noche(conGordo(listOf(informe, banco), hecho = listOf(informe)))
        assertEquals(Situacion.NOCHE, c.situacion)
        assertEquals(ValorHueco.Texto("Llamar al banco"), c.huecos[Hueco.TAREA])
        assertTrue(c.acciones.isEmpty())
    }

    @Test
    fun `the night line has no task without carry over`() {
        assertTrue(Respuestas.noche(conGordo(listOf(informe), hecho = listOf(informe))).huecos.isEmpty())
        assertTrue(Respuestas.noche(conGordo(emptyList())).huecos.isEmpty())
        assertTrue(Respuestas.noche(conGordo(listOf(RefGordo(TipoRefGordo.HABITO, "h1")))).huecos.isEmpty())
    }

    @Test
    fun `the chosen big thing names all of it and the first step it has`() {
        val h =
            hechos(
                habitos = listOf(habito(id = "h1", nombre = "Correr")),
                tareas =
                    listOf(
                        tarea("t1", titulo = "El informe"),
                        tarea("t2", titulo = "Llamar al banco", primerPaso = "Buscar el número"),
                    ),
                gordo = listOf(informe, RefGordo(TipoRefGordo.HABITO, "h1"), banco),
            )
        val c = Respuestas.gordoElegido(h)!!
        assertEquals(Situacion.GORDO_ELEGIDO, c.situacion)
        assertEquals(ValorHueco.Lista(listOf("El informe", "Correr", "Llamar al banco")), c.huecos[Hueco.GORDO])
        assertEquals(ValorHueco.Texto("Buscar el número"), c.huecos[Hueco.PRIMER_PASO])
        assertEquals(listOf(Respuesta.AHORA, Respuesta.LUEGO), c.acciones)
    }

    @Test
    fun `the chosen big thing without a first step has no first step hole`() {
        val c = Respuestas.gordoElegido(conGordo(listOf(informe)))!!
        assertEquals(ValorHueco.Lista(listOf("El informe")), c.huecos[Hueco.GORDO])
        assertNull(c.huecos[Hueco.PRIMER_PASO])
    }

    @Test
    fun `with nothing to name there is no chosen big thing line`() {
        assertNull(Respuestas.gordoElegido(conGordo(emptyList())))
        assertNull(Respuestas.gordoElegido(conGordo(listOf(RefGordo(TipoRefGordo.TAREA, "borrada")))))
    }
}
