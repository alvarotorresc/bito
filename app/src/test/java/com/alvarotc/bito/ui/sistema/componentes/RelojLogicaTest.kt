package com.alvarotc.bito.ui.sistema.componentes

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class RelojLogicaTest {
    @Test
    fun `angles start at the top and go clockwise over 24 hours`() {
        assertEquals(0f, RelojLogica.anguloDePunto(0f, -10f), 0.01f)
        assertEquals(90f, RelojLogica.anguloDePunto(10f, 0f), 0.01f)
        assertEquals(180f, RelojLogica.anguloDePunto(0f, 10f), 0.01f)
        assertEquals(360, RelojLogica.minutoDeAngulo(90f, paso = 5))
        assertEquals(90f, RelojLogica.anguloDeMinuto(360), 0.01f)
    }

    @Test
    fun `minutes snap to the step`() {
        assertEquals(390, RelojLogica.minutoDeAngulo(97.4f, paso = 30))
        assertEquals(385, RelojLogica.minutoDeAngulo(96.3f, paso = 5))
        assertEquals(0, RelojLogica.minutoDeAngulo(359.9f, paso = 30))
    }

    @Test
    fun `the short way across midnight is ten minutes, not a full turn`() {
        assertEquals(10, RelojLogica.caminoCorto(1435, 5))
        assertEquals(-10, RelojLogica.caminoCorto(5, 1435))
        assertEquals(-300, RelojLogica.caminoCorto(600, 300))
    }

    @Test
    fun `out of range snaps to the circularly nearest end`() {
        assertEquals(1410, RelojLogica.acotar(1430, 1140, 1410))
        assertEquals(1410, RelojLogica.acotar(100, 1140, 1410))
        assertEquals(1140, RelojLogica.acotar(900, 1140, 1410))
        assertEquals(1200, RelojLogica.acotar(1200, 1140, 1410))
    }

    @Test
    fun `the sky color hits the catalog stops and blends between them`() {
        assertEquals(Color(0xFF26305A), RelojLogica.colorCielo(0))
        assertEquals(Color(0xFFBFDCE0), RelojLogica.colorCielo(600))
        val mitad = RelojLogica.colorCielo(345)
        assertEquals((Color(0xFF34416F).red + Color(0xFFE9B48A).red) / 2f, mitad.red, 0.01f)
    }

    @Test
    fun `sun and moon blend from 6 to 7,30 and from 19 to 20,30`() {
        assertEquals(0f, RelojLogica.mezclaSol(300), 0f)
        assertEquals(0.5f, RelojLogica.mezclaSol(405), 0.01f)
        assertEquals(1f, RelojLogica.mezclaSol(720), 0f)
        assertEquals(0.5f, RelojLogica.mezclaSol(1185), 0.01f)
        assertEquals(0f, RelojLogica.mezclaSol(1260), 0f)
    }
}
