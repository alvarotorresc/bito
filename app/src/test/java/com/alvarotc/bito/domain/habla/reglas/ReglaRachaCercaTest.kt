package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.HabitoHoy
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.habito
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglaRachaCercaTest {
    private fun sale(vararg hs: HabitoHoy) = ReglaRachaCerca.candidatas(hechos(habitos = hs.toList()), Memoria(), ctx())

    private fun aFaltan(
        faltan: Int?,
        id: String = "h1",
        racha: Int = 0,
    ) = habito(id, racha = racha, hitoCerca = faltan?.let { 7 }, faltanHito = faltan).copy(bitosHito = faltan?.let { 5 })

    @Test
    fun `one and two periods from the milestone come out`() {
        assertEquals(1, sale(aFaltan(1)).size)
        assertEquals(1, sale(aFaltan(2)).size)
    }

    @Test
    fun `without a milestone near it does not come out`() {
        // Los bordes de 0 y 3 periodos los decide Hechos.hitoCerca (HechosTest, `milestone near`).
        assertTrue(sale(aFaltan(null)).isEmpty())
    }

    @Test
    fun `it brings the streak, what is missing and the bitos with the hour`() {
        val c = sale(aFaltan(2, racha = 5)).single()
        assertEquals(Situacion.RACHA_CERCA, c.situacion)
        assertEquals(ValorHueco.Numero(5), c.huecos[Hueco.RACHA])
        assertEquals(ValorHueco.Numero(2), c.huecos[Hueco.FALTAN])
        assertEquals(ValorHueco.Numero(5), c.huecos[Hueco.BITOS])
        assertEquals(ValorHueco.Texto("Leer"), c.huecos[Hueco.HABITO])
        assertEquals(listOf(Respuesta.VALE, Respuesta.HOY_NO), c.acciones)
        assertEquals(14 * 60 + 30, c.hora)
        assertEquals(5, c.peso)
        assertEquals(Destino.Habito("h1"), c.destino)
    }

    @Test
    fun `without an hour the first action is Now`() {
        val c = ReglaRachaCerca.candidatas(hechos(habitos = listOf(aFaltan(1))), Memoria(), ctx(ahoraMin = 20 * 60 + 50)).single()
        assertNull(c.hora)
        assertEquals(listOf(Respuesta.AHORA, Respuesta.HOY_NO), c.acciones)
    }

    @Test
    fun `a habit done today does not come out`() = assertTrue(sale(habito(hitoCerca = 7, faltanHito = 1, hechoHoy = true)).isEmpty())
}
