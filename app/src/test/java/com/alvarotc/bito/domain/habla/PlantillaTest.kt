package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.model.BreathingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

class PlantillaTest {
    private val es = Locale.forLanguageTag("es")

    private fun r(
        t: String,
        h: Map<Hueco, ValorHueco>,
        i: Idioma = Idioma.ES,
    ) = Plantilla.rellenar(t, h, i, if (i == Idioma.ES) es else Locale.ENGLISH)

    @Test
    fun `fills a named gap`() =
        assertEquals(
            "Ayer se quedó sin hacer: Leer.",
            r("Ayer se quedó sin hacer: {habito}.", mapOf(Hueco.HABITO to ValorHueco.Texto("Leer"))),
        )

    @Test
    fun `hour is formatted the same in both languages`() {
        assertEquals("a las 9:05", r("a las {hora}", mapOf(Hueco.HORA to ValorHueco.Hora(545))))
        assertEquals("at 14:30", r("at {hora}", mapOf(Hueco.HORA to ValorHueco.Hora(870)), Idioma.EN))
    }

    @Test
    fun `plurals in Spanish and English`() {
        assertEquals("1 día fuera", r("{dias_fuera|día|días} fuera", mapOf(Hueco.DIAS_FUERA to ValorHueco.Numero(1))))
        assertEquals("3 días fuera", r("{dias_fuera|día|días} fuera", mapOf(Hueco.DIAS_FUERA to ValorHueco.Numero(3))))
        assertEquals(
            "0 days away",
            r("{dias_fuera|day|days} away", mapOf(Hueco.DIAS_FUERA to ValorHueco.Numero(0)), Idioma.EN),
        )
    }

    @Test
    fun `lists join with y and and`() {
        val l = mapOf(Hueco.GORDO to ValorHueco.Lista(listOf("el informe", "Leer", "Agua")))
        assertEquals("el informe, Leer y Agua", r("{gordo}", l))
        assertEquals("el informe, Leer and Agua", r("{gordo}", l, Idioma.EN))
        assertEquals("a y b", r("{gordo}", mapOf(Hueco.GORDO to ValorHueco.Lista(listOf("a", "b")))))
        assertEquals("a and b", r("{gordo}", mapOf(Hueco.GORDO to ValorHueco.Lista(listOf("a", "b"))), Idioma.EN))
        assertEquals("a", r("{gordo}", mapOf(Hueco.GORDO to ValorHueco.Lista(listOf("a")))))
    }

    @Test
    fun `weekday comes from the locale`() {
        assertEquals("lunes", r("{dia_semana}", mapOf(Hueco.DIA_SEMANA to ValorHueco.DiaSemana(DayOfWeek.MONDAY))))
        assertEquals(
            "Monday",
            r("{dia_semana}", mapOf(Hueco.DIA_SEMANA to ValorHueco.DiaSemana(DayOfWeek.MONDAY)), Idioma.EN),
        )
    }

    @Test
    fun `date is day and month per language`() {
        val d = ValorHueco.Fecha(LocalDate.of(2026, 10, 3).toEpochDay())
        assertEquals("desde el 3 de octubre", r("desde el {fecha}", mapOf(Hueco.FECHA to d)))
        assertEquals("since October 3", r("since {fecha}", mapOf(Hueco.FECHA to d), Idioma.EN))
    }

    @Test
    fun `a number prints as is`() = assertEquals("racha de 12", r("racha de {racha}", mapOf(Hueco.RACHA to ValorHueco.Numero(12))))

    @Test
    fun `a single value is rendered without a template`() {
        assertEquals("14:30", Plantilla.valor(ValorHueco.Hora(870), Idioma.ES, es))
        assertEquals("Leer", Plantilla.valor(ValorHueco.Texto("Leer"), Idioma.ES, es))
        assertEquals("a y b", Plantilla.valor(ValorHueco.Lista(listOf("a", "b")), Idioma.ES, es))
    }

    @Test
    fun `a breathing mode prints its screen name, never its identifier`() {
        assertEquals("Calmarme", Plantilla.valor(ValorHueco.Modo(BreathingMode.CALM), Idioma.ES, es))
        assertEquals("Dormir", Plantilla.valor(ValorHueco.Modo(BreathingMode.SLEEP), Idioma.ES, es))
        assertEquals("Centrarme", Plantilla.valor(ValorHueco.Modo(BreathingMode.FOCUS), Idioma.ES, es))
        assertEquals("Calm down", Plantilla.valor(ValorHueco.Modo(BreathingMode.CALM), Idioma.EN, Locale.ENGLISH))
        assertEquals("Sleep", Plantilla.valor(ValorHueco.Modo(BreathingMode.SLEEP), Idioma.EN, Locale.ENGLISH))
        assertEquals("Focus", Plantilla.valor(ValorHueco.Modo(BreathingMode.FOCUS), Idioma.EN, Locale.ENGLISH))
    }

    @Test
    fun `inserted values are never expanded again`() =
        assertEquals(
            "Hoy: {habito} 50 % A/B.",
            r("Hoy: {habito}.", mapOf(Hueco.HABITO to ValorHueco.Texto("{habito} 50 % A/B"))),
        )

    @Test
    fun `an inserted gap does not pull in other gaps`() =
        assertEquals(
            "{tarea} y Leer",
            r(
                "{habito} y {tarea}",
                mapOf(Hueco.HABITO to ValorHueco.Texto("{tarea}"), Hueco.TAREA to ValorHueco.Texto("Leer")),
            ),
        )

    @Test
    fun `an unknown gap breaks loudly`() {
        assertThrows(IllegalArgumentException::class.java) { r("{inventado}", emptyMap()) }
    }

    @Test
    fun `a missing value breaks loudly`() {
        assertThrows(IllegalArgumentException::class.java) { r("{habito}", emptyMap()) }
    }

    @Test
    fun `a plural over a non numeric value breaks loudly`() {
        assertThrows(IllegalArgumentException::class.java) {
            r("{habito|a|b}", mapOf(Hueco.HABITO to ValorHueco.Texto("Leer")))
        }
    }

    @Test
    fun `tags are separated and removed`() {
        val (etiquetas, texto) = Plantilla.separarEtiquetas("[lunes,manana] Lunes. Hoy toca.")
        assertEquals(setOf(Etiqueta.LUNES, Etiqueta.MANANA), etiquetas)
        assertEquals("Lunes. Hoy toca.", texto)
    }

    @Test
    fun `an item without tags comes back untouched`() {
        val (etiquetas, texto) = Plantilla.separarEtiquetas("Hoy toca.")
        assertEquals(emptySet<Etiqueta>(), etiquetas)
        assertEquals("Hoy toca.", texto)
    }

    @Test
    fun `an unknown tag breaks loudly`() {
        assertThrows(IllegalArgumentException::class.java) { Plantilla.separarEtiquetas("[inventada] Hola.") }
    }

    @Test
    fun `filling drops the leading tags`() =
        assertEquals("Hola Leer.", r("[lunes] Hola {habito}.", mapOf(Hueco.HABITO to ValorHueco.Texto("Leer"))))

    @Test
    fun `gaps of a text include plural gaps`() = assertEquals(setOf(Hueco.HABITO, Hueco.N), Plantilla.huecosDe("{habito}: {n|vez|veces}."))

    @Test
    fun `join leaves one space and skips empty pieces`() =
        assertEquals("Buenas. Hoy toca. Sin prisa.", Plantilla.unir(listOf("Buenas.", "", " Hoy toca. ", "Sin prisa.")))
}
