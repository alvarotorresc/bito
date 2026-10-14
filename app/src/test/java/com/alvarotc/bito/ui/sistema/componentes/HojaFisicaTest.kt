package com.alvarotc.bito.ui.sistema.componentes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HojaFisicaTest {
    @Test
    fun `a flick faster than 0,11 px per ms closes even if short`() {
        assertTrue(HojaFisica.debeCerrar(desplazamientoPx = 20f, altoPx = 600f, velocidadPxPorMs = 0.2f))
    }

    @Test
    fun `a slow drag closes only past 30 percent of the height`() {
        assertFalse(HojaFisica.debeCerrar(170f, 600f, 0.05f))
        assertTrue(HojaFisica.debeCerrar(190f, 600f, 0.05f))
    }

    @Test
    fun `dragging up meets friction, dragging down does not`() {
        assertEquals(50f, HojaFisica.conFriccion(50f), 0f)
        assertEquals(-Math.pow(100.0, 0.7).toFloat(), HojaFisica.conFriccion(-100f), 0.001f)
    }
}
