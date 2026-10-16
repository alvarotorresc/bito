package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.millisOn
import com.alvarotc.bito.domain.model.Personality
import com.alvarotc.bito.domain.testZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ContextosTest {
    private fun de(
        ahoraMillis: Long = millisOn(TODAY, 10, 0),
        corteDia: Int = 0,
        horaNoche: Int = 21 * 60 + 30,
        dndActivo: Boolean = true,
        nombre: String? = "Álvaro",
        diaSellado: Boolean = false,
        quedanExigibles: Boolean = true,
    ) = Contextos.de(
        ahoraMillis = ahoraMillis,
        zona = testZone,
        corteDia = corteDia,
        horaManana = 8 * 60 + 30,
        horaNoche = horaNoche,
        dndActivo = dndActivo,
        dndDesde = 23 * 60,
        dndHasta = 7 * 60 + 30,
        voz = Personality.SARGENTO,
        nombre = nombre,
        permisoAvisos = true,
        alarmasExactas = false,
        diaSellado = diaSellado,
        quedanExigibles = quedanExigibles,
    )

    @Test
    fun `near the night starts thirty minutes before it and lasts past midnight until the cutoff`() {
        assertEquals(false, de(ahoraMillis = millisOn(TODAY, 20, 59)).cercaDeLaNoche())
        assertEquals(true, de(ahoraMillis = millisOn(TODAY, 21, 0)).cercaDeLaNoche())
        val madrugada = de(ahoraMillis = millisOn(TODAY + 1, 0, 30), corteDia = 3 * 60)
        assertEquals(TODAY, madrugada.hoy)
        assertEquals(true, madrugada.cercaDeLaNoche())
    }

    @Test
    fun `a morning instant gives today, its date, its weekday, its season and its slot`() {
        val c = de()
        assertEquals(TODAY, c.hoy)
        assertEquals(10 * 60, c.ahoraMin)
        assertEquals(millisOn(TODAY, 10, 0), c.ahoraMillis)
        assertEquals(LocalDate.of(2026, 8, 14), c.fecha)
        assertEquals(DayOfWeek.FRIDAY, c.diaSemana)
        assertEquals(Estacion.VERANO, c.estacion)
        assertEquals(Franja.MANANA, c.franja)
        assertEquals(FaseHabi.DESPIERTA, c.fase)
        assertEquals(Personality.SARGENTO, c.voz)
        assertEquals(false, c.alarmasExactas)
        assertEquals(testZone, c.zona)
    }

    @Test
    fun `after midnight and before the cutoff it is still yesterday`() {
        val c = de(ahoraMillis = millisOn(TODAY + 1, 1, 0), corteDia = 3 * 60)
        assertEquals(TODAY, c.hoy)
        assertEquals(60, c.ahoraMin)
        assertEquals(DayOfWeek.FRIDAY, c.diaSemana)
        assertEquals(Franja.MADRUGADA, c.franja)
        assertEquals(3 * 60, c.corteDia)
    }

    @Test
    fun `after midnight inside the same logical day it is already night for Habi`() =
        assertEquals(FaseHabi.ESPERANDO, de(ahoraMillis = millisOn(TODAY + 1, 1, 0), corteDia = 3 * 60).fase)

    @Test
    fun `the silence is the do not disturb window when it is on`() {
        assertEquals(Silencio(23 * 60, 7 * 60 + 30), de().silencio)
        assertNull(de(dndActivo = false).silencio)
    }

    @Test
    fun `a blank name is no name`() {
        assertNull(de(nombre = "  ").nombre)
        assertNull(de(nombre = null).nombre)
        assertEquals("Álvaro", de(nombre = "Álvaro").nombre)
        assertEquals("Álvaro", de(nombre = "  Álvaro ").nombre)
    }

    @Test
    fun `a sealed day puts Habi to sleep`() = assertEquals(FaseHabi.DURMIENDO, de(diaSellado = true, quedanExigibles = false).fase)

    @Test
    fun `nothing left to do makes Habi wait`() = assertEquals(FaseHabi.ESPERANDO, de(quedanExigibles = false).fase)

    @Test
    fun `from the night hour Habi waits`() {
        assertEquals(FaseHabi.ESPERANDO, de(ahoraMillis = millisOn(TODAY, 21, 30)).fase)
        assertEquals(FaseHabi.DESPIERTA, de(ahoraMillis = millisOn(TODAY, 21, 29)).fase)
    }
}
