package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.TODAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TiposTest {
    @Test
    fun `the time slot changes at six, twelve, fifteen and twenty`() {
        assertEquals(Franja.MADRUGADA, Franja.de(359))
        assertEquals(Franja.MANANA, Franja.de(360))
        assertEquals(Franja.MANANA, Franja.de(719))
        assertEquals(Franja.MEDIODIA, Franja.de(720))
        assertEquals(Franja.MEDIODIA, Franja.de(899))
        assertEquals(Franja.TARDE, Franja.de(900))
        assertEquals(Franja.TARDE, Franja.de(1199))
        assertEquals(Franja.NOCHE, Franja.de(1200))
        assertEquals(Franja.MADRUGADA, Franja.de(0))
    }

    @Test
    fun `seasons follow meteorological months in the north`() {
        val madrid = ZoneId.of("Europe/Madrid")
        assertEquals(Estacion.INVIERNO, Estacion.de(LocalDate.of(2026, 1, 15), madrid))
        assertEquals(Estacion.PRIMAVERA, Estacion.de(LocalDate.of(2026, 3, 1), madrid))
        assertEquals(Estacion.VERANO, Estacion.de(LocalDate.of(2026, 8, 31), madrid))
        assertEquals(Estacion.OTONO, Estacion.de(LocalDate.of(2026, 11, 30), madrid))
        assertEquals(Estacion.INVIERNO, Estacion.de(LocalDate.of(2026, 12, 1), madrid))
    }

    @Test
    fun `seasons are inverted in the southern hemisphere`() {
        assertEquals(Estacion.VERANO, Estacion.de(LocalDate.of(2026, 1, 15), ZoneId.of("Australia/Sydney")))
        assertEquals(Estacion.OTONO, Estacion.de(LocalDate.of(2026, 3, 1), ZoneId.of("America/Argentina/Buenos_Aires")))
        assertEquals(Estacion.INVIERNO, Estacion.de(LocalDate.of(2026, 7, 1), ZoneId.of("Pacific/Auckland")))
        assertEquals(Estacion.PRIMAVERA, Estacion.de(LocalDate.of(2026, 10, 1), ZoneId.of("Africa/Johannesburg")))
    }

    @Test
    fun `a silence that wraps midnight contains both ends of the night`() {
        val s = Silencio(23 * 60, 450)
        assertTrue(s.contiene(1380))
        assertTrue(s.contiene(0))
        assertTrue(s.contiene(449))
        assertFalse(s.contiene(450))
        assertFalse(s.contiene(1379))
    }

    @Test
    fun `a silence inside the day contains its start and not its end`() {
        val s = Silencio(13 * 60, 15 * 60)
        assertTrue(s.contiene(13 * 60))
        assertTrue(s.contiene(15 * 60 - 1))
        assertFalse(s.contiene(15 * 60))
        assertFalse(s.contiene(13 * 60 - 1))
    }

    @Test
    fun `a silence that starts and ends at the same minute never applies`() {
        val s = Silencio(600, 600)
        assertFalse(s.contiene(600))
        assertFalse(s.contiene(0))
        assertFalse(s.contiene(1439))
    }

    @Test
    fun `the weekend tag is Saturday or Sunday`() {
        assertFalse(ctx(hoy = TODAY).cumple(Etiqueta.FINDE)) // viernes
        assertTrue(ctx(hoy = TODAY + 1).cumple(Etiqueta.FINDE)) // sábado
        assertTrue(ctx(hoy = TODAY + 2).cumple(Etiqueta.FINDE)) // domingo
        assertFalse(ctx(hoy = TODAY + 3).cumple(Etiqueta.FINDE)) // lunes
    }

    @Test
    fun `a day tag is met only on that day of the week`() {
        assertTrue(ctx(hoy = TODAY).cumple(Etiqueta.VIERNES))
        assertFalse(ctx(hoy = TODAY).cumple(Etiqueta.JUEVES))
        assertTrue(ctx(hoy = TODAY + 3).cumple(Etiqueta.LUNES))
    }

    @Test
    fun `season, slot and phase tags follow the context`() {
        val c = ctx(ahoraMin = 21 * 60, fase = FaseHabi.ESPERANDO)
        assertTrue(c.cumple(Etiqueta.VERANO))
        assertFalse(c.cumple(Etiqueta.INVIERNO))
        assertTrue(c.cumple(Etiqueta.NOCHE))
        assertFalse(c.cumple(Etiqueta.MANANA))
        assertTrue(c.cumple(Etiqueta.ESPERANDO))
        assertFalse(c.cumple(Etiqueta.DESPIERTA))
    }

    @Test
    fun `a tag is read from its key`() {
        assertEquals(Etiqueta.MIERCOLES, Etiqueta.de("miercoles"))
        assertEquals(Etiqueta.FINDE, Etiqueta.de("finde"))
        assertNull(Etiqueta.de("feriado"))
    }
}
