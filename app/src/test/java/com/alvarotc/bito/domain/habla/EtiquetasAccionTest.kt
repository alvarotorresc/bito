package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.R
import com.alvarotc.bito.domain.propuestas.Accion
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.propuestas.TextoHabi
import org.junit.Assert.assertEquals
import org.junit.Test

class EtiquetasAccionTest {
    private fun recursos(acciones: List<Accion>) = acciones.map { (it.etiqueta as TextoHabi.Recurso).res }

    @Test
    fun `asking for permission says Allow and Not now, the first one main`() {
        val a = EtiquetasAccion.de(Situacion.PERMISO_AVISOS, listOf(Respuesta.VALE, Respuesta.HOY_NO), null, emptyMap())
        assertEquals(listOf(R.string.habla_permitir, R.string.habla_ahora_no), recursos(a))
        assertEquals(listOf(true, false), a.map { it.principal })
        assertEquals(listOf(Respuesta.VALE, Respuesta.HOY_NO), a.map { it.respuesta })
    }

    @Test
    fun `OK with a time carries the time as a gap value`() {
        val a =
            EtiquetasAccion.de(Situacion.AYER_SE_ESCAPO, listOf(Respuesta.VALE, Respuesta.AHORA, Respuesta.HOY_NO), 870, emptyMap())
        assertEquals(TextoHabi.Recurso(R.string.habla_vale_a_las, listOf(ValorHueco.Hora(870))), a[0].etiqueta)
        assertEquals(listOf(R.string.habla_vale_a_las, R.string.habla_ahora, R.string.task_not_today), recursos(a))
    }

    @Test
    fun `OK without a time is a plain OK`() {
        val a = EtiquetasAccion.de(Situacion.AYER_SE_ESCAPO, listOf(Respuesta.VALE, Respuesta.HOY_NO), null, emptyMap())
        assertEquals(TextoHabi.Recurso(R.string.habla_vale), a[0].etiqueta)
    }

    @Test
    fun `on a hard day OK names the habit`() {
        val habito = ValorHueco.Texto("Leer")
        val a = EtiquetasAccion.de(Situacion.DIA_DURO, listOf(Respuesta.VALE, Respuesta.HOY_NO), null, mapOf(Hueco.HABITO to habito))
        assertEquals(TextoHabi.Recurso(R.string.habla_vale_habito, listOf(habito)), a[0].etiqueta)
        assertEquals(R.string.task_not_today, recursos(a)[1])
    }

    @Test
    fun `a confirmation without the big thing offers Another time as plain text`() {
        val a = EtiquetasAccion.de(Situacion.CONFIRMA_VALE, listOf(Respuesta.OTRA_HORA), null, emptyMap())
        assertEquals(listOf(R.string.habla_otra_hora), recursos(a))
        assertEquals(listOf(false), a.map { it.principal })
    }

    @Test
    fun `a confirmation with the big thing offers a 25 minute session`() {
        val a = EtiquetasAccion.de(Situacion.CONFIRMA_VALE, listOf(Respuesta.AHORA, Respuesta.LUEGO), null, emptyMap())
        assertEquals(listOf(R.string.habla_sesion_25, R.string.habla_luego), recursos(a))
        assertEquals(listOf(true, false), a.map { it.principal })
    }

    @Test
    fun `closing the day says Close the day and Not yet`() {
        val a = EtiquetasAccion.de(Situacion.CERRAR_DIA, listOf(Respuesta.CERRAR_DIA, Respuesta.LUEGO), null, emptyMap())
        assertEquals(listOf(R.string.habla_cerrar_dia, R.string.habla_todavia_no), recursos(a))
        val b = EtiquetasAccion.de(Situacion.TODO_HECHO, listOf(Respuesta.CERRAR_DIA, Respuesta.LUEGO), null, emptyMap())
        assertEquals(listOf(R.string.habla_cerrar_dia, R.string.habla_todavia_no), recursos(b))
    }

    @Test
    fun `Now changes with the situation`() {
        fun ahora(s: Situacion) = recursos(EtiquetasAccion.de(s, listOf(Respuesta.AHORA), null, emptyMap())).single()
        assertEquals(R.string.habla_empezar_25, ahora(Situacion.GORDO_SIN_EMPEZAR))
        assertEquals(R.string.habla_sesion_25, ahora(Situacion.GORDO_ELEGIDO))
        assertEquals(R.string.habla_empezar, ahora(Situacion.TAREA_VENCE))
        assertEquals(R.string.habla_ahora, ahora(Situacion.HORA_HABITUAL))
    }

    @Test
    fun `Not today becomes Not now or No need where it fits`() {
        fun hoyNo(s: Situacion) = recursos(EtiquetasAccion.de(s, listOf(Respuesta.VALE, Respuesta.HOY_NO), null, emptyMap()))[1]
        assertEquals(R.string.habla_ahora_no, hoyNo(Situacion.VUELTA))
        assertEquals(R.string.habla_ahora_no, hoyNo(Situacion.RACHA_CERCA))
        assertEquals(R.string.habla_no_hace_falta, hoyNo(Situacion.FI_HOY_FALTA))
        assertEquals(R.string.habla_no_hace_falta, hoyNo(Situacion.FI_OBJETIVO))
        assertEquals(R.string.task_not_today, hoyNo(Situacion.ELEGIR_GORDO))
    }

    @Test
    fun `the remaining answers have their own labels`() {
        val a =
            EtiquetasAccion.de(
                Situacion.VUELTA,
                listOf(Respuesta.HECHO, Respuesta.ELEGIR_GORDO, Respuesta.LUEGO),
                null,
                emptyMap(),
            )
        assertEquals(listOf(R.string.habla_hecho, R.string.habla_elegir, R.string.habla_luego), recursos(a))
    }

    @Test
    fun `no actions, no buttons`() = assertEquals(emptyList<Accion>(), EtiquetasAccion.de(Situacion.SALUDO, emptyList(), null, emptyMap()))
}
