package com.alvarotc.bito.domain.sesion

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatoSesionTest {
    @Test
    fun `minutes read as min, hours or both`() {
        assertEquals(Texto(TextoMinutos.H_MIN, listOf(1, 40)), FormatoSesion.minutos(100))
        assertEquals(Texto(TextoMinutos.H, listOf(1)), FormatoSesion.minutos(60))
        assertEquals(Texto(TextoMinutos.H, listOf(2)), FormatoSesion.minutos(120))
        assertEquals(Texto(TextoMinutos.MIN, listOf(25)), FormatoSesion.minutos(25))
    }

    @Test
    fun `the focus figure rounds to the minute and is at least one`() {
        assertEquals(100, FormatoSesion.minutosDeFoco(6_000_000L))
        assertEquals(25, FormatoSesion.minutosDeFoco(25 * 60_000L + 29_999L))
        assertEquals(26, FormatoSesion.minutosDeFoco(25 * 60_000L + 30_000L))
        assertEquals(1, FormatoSesion.minutosDeFoco(10_000L))
        assertEquals(1, FormatoSesion.minutosDeFoco(0L))
    }

    @Test
    fun `the countdown is m_ss and never says zero while alive`() {
        assertEquals("25:00", FormatoSesion.cuenta(25 * 60_000L))
        assertEquals("4:59", FormatoSesion.cuenta(299_000L))
        assertEquals("1:00", FormatoSesion.cuenta(59_001L))
        assertEquals("0:01", FormatoSesion.cuenta(1L))
        assertEquals("0:00", FormatoSesion.cuenta(0L))
        assertEquals("180:00", FormatoSesion.cuenta(180 * 60_000L))
    }

    @Test
    fun `the blocks subtitle changes with the number of blocks`() {
        assertEquals(Texto(TextoBloques.UNO, emptyList()), FormatoSesion.bloques(1, 25, 5, 15, 4))
        assertEquals(Texto(TextoBloques.POCOS, listOf(2, 25, 5)), FormatoSesion.bloques(2, 25, 5, 15, 4))
        assertEquals(Texto(TextoBloques.POCOS, listOf(3, 45, 10)), FormatoSesion.bloques(3, 45, 10, 15, 4))
        assertEquals(Texto(TextoBloques.MUCHOS, listOf(4, 25, 5, 15, 4)), FormatoSesion.bloques(4, 25, 5, 15, 4))
        assertEquals(Texto(TextoBloques.MUCHOS, listOf(8, 25, 5, 20, 3)), FormatoSesion.bloques(8, 25, 5, 20, 3))
    }
}
