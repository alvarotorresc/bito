package com.alvarotc.bito.ui.tienda

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LuzTest {
    private fun hex(c: Color): String =
        "#%02X%02X%02X".format(
            Math.round(c.red * 255f),
            Math.round(c.green * 255f),
            Math.round(c.blue * 255f),
        )

    @Test
    fun `the three stops are exactly the mock colors`() {
        assertEquals("#FFFFFF", hex(LuzArmario.arriba(0f)))
        assertEquals("#FFFFFF", hex(LuzArmario.abajo(0f)))
        assertEquals("#FFCF9A", hex(LuzArmario.arriba(0.5f)))
        assertEquals("#FFDDB8", hex(LuzArmario.abajo(0.5f)))
        assertEquals("#31406E", hex(LuzArmario.arriba(1f)))
        assertEquals("#4B5A8A", hex(LuzArmario.abajo(1f)))
    }

    @Test
    fun `stopAt interpolates between stops`() {
        // Un cuarto de recorrido es la mitad entre Día y Tarde; el mock redondea el .5 hacia arriba (204,5 pasa a 205).
        assertEquals("#FFE7CD", hex(LuzArmario.arriba(0.25f)))
        // Tres cuartos es la mitad entre Tarde y Noche.
        assertEquals("#988884", hex(LuzArmario.arriba(0.75f)))
        assertEquals("#A59CA1", hex(LuzArmario.abajo(0.75f)))
    }

    @Test
    fun `a value outside the track is held at the nearest end`() {
        assertEquals("#FFFFFF", hex(LuzArmario.arriba(-0.3f)))
        assertEquals("#31406E", hex(LuzArmario.arriba(1.4f)))
    }

    @Test
    fun `the astro turns into the moon only late in the track`() {
        val oro = Color(0xFFE2B04A)
        assertEquals("#E2B04A", hex(LuzArmario.astro(0f, oro)))
        assertEquals("#E2B04A", hex(LuzArmario.astro(0.5f, oro)))
        assertEquals("#E4E0F2", hex(LuzArmario.astro(1f, oro)))
        val medio = LuzArmario.astro(0.8f, oro)
        assertNotEquals(oro, medio)
        assertNotEquals(LuzArmario.LUNA, medio)
    }

    @Test
    fun `release snaps to the nearest stop after projecting 120 ms of velocity`() {
        // Parado en 0,40 sin velocidad: la parada más cercana es la Tarde.
        assertEquals(LuzTienda.TARDE, LuzArmario.alSoltar(0.40f, 0f))
        // Con 1 luz/s hacia la noche, 120 ms proyectan 0,12 y 0,40 llega a 0,52: sigue en la Tarde...
        assertEquals(LuzTienda.TARDE, LuzArmario.alSoltar(0.40f, 1f))
        // ...pero con 3 luz/s proyecta 0,36 (0,76 en total) y salta a la Noche.
        assertEquals(LuzTienda.NOCHE, LuzArmario.alSoltar(0.40f, 3f))
        // Y hacia atrás.
        assertEquals(LuzTienda.DIA, LuzArmario.alSoltar(0.60f, -3f))
        // Sin salirse del surco.
        assertEquals(LuzTienda.NOCHE, LuzArmario.alSoltar(1f, 9f))
        assertEquals(LuzTienda.DIA, LuzArmario.alSoltar(0f, -9f))
    }

    @Test
    fun `reduced motion does not project the velocity`() {
        assertEquals(LuzTienda.TARDE, LuzArmario.alSoltar(0.40f, 3f, reducido = true))
    }

    @Test
    fun `the nearest stop of a position`() {
        assertEquals(LuzTienda.DIA, LuzArmario.masCercana(0.2f))
        assertEquals(LuzTienda.TARDE, LuzArmario.masCercana(0.3f))
        assertEquals(LuzTienda.TARDE, LuzArmario.masCercana(0.7f))
        assertEquals(LuzTienda.NOCHE, LuzArmario.masCercana(0.8f))
    }

    @Test
    fun `the stops are 0, 0,5 and 1`() {
        assertEquals(listOf(0f, 0.5f, 1f), LuzTienda.entries.map { it.parada })
        assertTrue(LuzTienda.entries.size == 3)
    }
}
