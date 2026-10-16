package com.alvarotc.bito.domain.habla

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HoraPropuestaTest {
    @Test
    fun `habitual hour wins`() = assertEquals(19 * 60, HoraPropuesta.de(habito(horaHabitual = 19 * 60), ctx(ahoraMin = 10 * 60)))

    @Test
    fun `own reminder when there is no habitual hour`() =
        assertEquals(18 * 60 + 15, HoraPropuesta.de(habito(recordatorio = 18 * 60 + 15), ctx(ahoraMin = 10 * 60)))

    @Test
    fun `habitual hour wins over the own reminder`() =
        assertEquals(
            19 * 60,
            HoraPropuesta.de(habito(horaHabitual = 19 * 60, recordatorio = 18 * 60 + 15), ctx(ahoraMin = 10 * 60)),
        )

    @Test
    fun `moments of the day in order`() {
        assertEquals(14 * 60 + 30, HoraPropuesta.de(habito(), ctx(ahoraMin = 13 * 60 + 59)))
        assertEquals(18 * 60 + 30, HoraPropuesta.de(habito(), ctx(ahoraMin = 14 * 60)))
        assertEquals(21 * 60, HoraPropuesta.de(habito(), ctx(ahoraMin = 20 * 60 + 29)))
    }

    @Test
    fun `at least thirty minutes ahead`() {
        // 14:20 queda a 20 min de las 14:00: no vale y salta al siguiente momento del día.
        assertEquals(18 * 60 + 30, HoraPropuesta.de(habito(horaHabitual = 14 * 60 + 20), ctx(ahoraMin = 14 * 60)))
        // Justo a 30 min sí vale.
        assertEquals(14 * 60 + 30, HoraPropuesta.de(habito(horaHabitual = 14 * 60 + 30), ctx(ahoraMin = 14 * 60)))
        // Una hora ya pasada tampoco: se cae al momento del día.
        assertEquals(14 * 60 + 30, HoraPropuesta.de(habito(horaHabitual = 14 * 60), ctx(ahoraMin = 13 * 60 + 31)))
    }

    @Test
    fun `fifteen minutes before the night at the latest`() {
        assertNull(HoraPropuesta.de(habito(horaHabitual = 21 * 60 + 20), ctx(ahoraMin = 20 * 60 + 40, horaNoche = 21 * 60 + 30)))
        assertEquals(
            21 * 60 + 15,
            HoraPropuesta.de(habito(horaHabitual = 21 * 60 + 15), ctx(ahoraMin = 20 * 60 + 40, horaNoche = 21 * 60 + 30)),
        )
    }

    @Test
    fun `never inside the silence`() =
        assertEquals(
            14 * 60 + 30,
            HoraPropuesta.de(habito(horaHabitual = 13 * 60), ctx(ahoraMin = 11 * 60, silencio = Silencio(12 * 60 + 30, 14 * 60))),
        )

    @Test
    fun `an invalid habitual hour falls back to the own reminder`() =
        assertEquals(
            16 * 60,
            HoraPropuesta.de(
                habito(horaHabitual = 9 * 60, recordatorio = 16 * 60),
                ctx(ahoraMin = 10 * 60),
            ),
        )

    @Test
    fun `no possible hour gives null`() = assertNull(HoraPropuesta.de(habito(), ctx(ahoraMin = 20 * 60 + 45)))
}
