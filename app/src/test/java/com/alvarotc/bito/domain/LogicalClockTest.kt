package com.alvarotc.bito.domain

import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

class LogicalClockTest {
    @Test
    fun `the logical clock takes the logical day and the minute of day from the same instant`() {
        val zone = ZoneId.of("UTC")
        val cutoffMinutes = 4 * 60
        val justAfterCutoff = LocalDateTime.of(2026, 1, 2, 4, 5).atZone(zone).toInstant().toEpochMilli()

        val clock = logicalClockAt(justAfterCutoff, cutoffMinutes, zone)

        // Ya pasado el corte de las 4: el dia logico es el nuevo, 2 de enero, no el 1.
        assertEquals(LocalDate.of(2026, 1, 2).toEpochDay().toInt(), clock.today)
        // El minuto del dia es el reloj de pared tal cual, sin el ajuste del corte.
        assertEquals(4 * 60 + 5, clock.minutesOfDay)
    }
}
