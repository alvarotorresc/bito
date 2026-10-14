package com.alvarotc.bito.ui.sistema.componentes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarioLogicaTest {
    private val hoy = LocalDate.of(2026, 10, 14)

    @Test
    fun `twelve months from this one`() {
        val meses = CalendarioLogica.meses(hoy)
        assertEquals(12, meses.size)
        assertEquals(YearMonth.of(2026, 10), meses.first())
        assertEquals(YearMonth.of(2027, 9), meses.last())
    }

    @Test
    fun `past days and days after the last month are not choosable`() {
        assertFalse(CalendarioLogica.elegible(LocalDate.of(2026, 10, 13), hoy))
        assertTrue(CalendarioLogica.elegible(hoy, hoy))
        assertTrue(CalendarioLogica.elegible(LocalDate.of(2027, 9, 30), hoy))
        assertFalse(CalendarioLogica.elegible(LocalDate.of(2027, 10, 1), hoy))
    }

    @Test
    fun `cells start on monday`() {
        val celdas = CalendarioLogica.celdas(YearMonth.of(2026, 10)) // 1 de octubre de 2026 es jueves
        assertEquals(listOf(null, null, null), celdas.take(3))
        assertEquals(LocalDate.of(2026, 10, 1), celdas[3])
        assertEquals(0, celdas.size % 7)
    }
}
