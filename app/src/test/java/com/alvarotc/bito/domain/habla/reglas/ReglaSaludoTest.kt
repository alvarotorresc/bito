package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.HabitoHoy
import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.habito
import com.alvarotc.bito.domain.habla.hechos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglaSaludoTest {
    private fun sale(
        nombre: String? = "Álvaro",
        habitos: List<HabitoHoy> = listOf(habito("a", hechoHoy = true), habito("b")),
    ) = ReglaSaludo.candidatas(hechos(habitos = habitos), Memoria(), ctx(nombre = nombre)).single()

    @Test
    fun `it is always a candidate without actions`() {
        val c = sale()
        assertEquals(Situacion.SALUDO, c.situacion)
        assertTrue(c.acciones.isEmpty())
        assertNull(c.ref)
        assertNull(c.destino)
    }

    @Test
    fun `it brings the name, how many are done and the total`() {
        val c = sale()
        assertEquals(ValorHueco.Texto("Álvaro"), c.huecos[Hueco.NOMBRE])
        assertEquals(ValorHueco.Numero(1), c.huecos[Hueco.HECHOS])
        assertEquals(ValorHueco.Numero(2), c.huecos[Hueco.TOTAL])
    }

    @Test
    fun `without a name there is no name hole`() {
        assertNull(sale(nombre = null).huecos[Hueco.NOMBRE])
        assertNull(sale(nombre = "  ").huecos[Hueco.NOMBRE])
    }

    @Test
    fun `with no habits today it does not bring done or total`() {
        val c = sale(habitos = emptyList())
        assertNull(c.huecos[Hueco.HECHOS])
        assertNull(c.huecos[Hueco.TOTAL])
        assertEquals(ValorHueco.Texto("Álvaro"), c.huecos[Hueco.NOMBRE])
    }
}
