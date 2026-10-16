package com.alvarotc.bito.domain.habla.reglas

import com.alvarotc.bito.domain.habla.Hueco
import com.alvarotc.bito.domain.habla.Memoria
import com.alvarotc.bito.domain.habla.Situacion
import com.alvarotc.bito.domain.habla.TipoHabito
import com.alvarotc.bito.domain.habla.ValorHueco
import com.alvarotc.bito.domain.habla.ctx
import com.alvarotc.bito.domain.habla.habito
import com.alvarotc.bito.domain.habla.hechos
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class ReglaVueltaTest {
    @Test
    fun `two days away is not a return`() =
        assertTrue(
            ReglaVuelta
                .candidatas(hechos(diasSinActividad = 2, primerDiaTrasAusencia = false, habitos = listOf(habito())), Memoria(), ctx())
                .isEmpty(),
        )

    @Test
    fun `three days away proposes the best kept small habit`() {
        val c =
            ReglaVuelta
                .candidatas(
                    hechos(
                        // El borde: última actividad hace 3 días lógicos.
                        diasSinActividad = 2,
                        primerDiaTrasAusencia = true,
                        habitos =
                            listOf(
                                habito("a", pct30 = 60),
                                habito("b", pct30 = 90, objetivo = 8, tipo = TipoHabito.CONTADOR),
                                habito("c", pct30 = 90),
                            ),
                    ),
                    Memoria(),
                    ctx(),
                ).single()
        assertEquals(Situacion.VUELTA, c.situacion)
        assertEquals("c", c.ref) // empate a 90: gana el de objetivo menor
        assertEquals(listOf(Respuesta.HECHO, Respuesta.HOY_NO), c.acciones)
        assertEquals(ValorHueco.Numero(3), c.huecos[Hueco.DIAS_FUERA]) // hoy menos la última actividad, no los días vacíos de en medio
        assertEquals(ValorHueco.Texto("Leer"), c.huecos[Hueco.HABITO])
        assertEquals(Destino.Habito("c"), c.destino)
        assertNull(c.hora)
    }

    @Test
    fun `the best percentage wins over a smaller target`() {
        val c =
            ReglaVuelta
                .candidatas(
                    hechos(
                        primerDiaTrasAusencia = true,
                        diasSinActividad = 4,
                        habitos =
                            listOf(
                                habito("a", pct30 = 70, objetivo = 1),
                                habito("b", pct30 = 95, objetivo = 5, tipo = TipoHabito.CONTADOR),
                            ),
                    ),
                    Memoria(),
                    ctx(),
                ).single()
        assertEquals("b", c.ref)
    }

    @Test
    fun `a full tie is broken by id`() {
        val c =
            ReglaVuelta
                .candidatas(
                    hechos(primerDiaTrasAusencia = true, diasSinActividad = 3, habitos = listOf(habito("z"), habito("m"))),
                    Memoria(),
                    ctx(),
                ).single()
        assertEquals("m", c.ref)
    }

    @Test
    fun `a habit without judged days goes last`() {
        val c =
            ReglaVuelta
                .candidatas(
                    hechos(
                        primerDiaTrasAusencia = true,
                        diasSinActividad = 3,
                        habitos = listOf(habito("a", pct30 = null), habito("b", pct30 = 10)),
                    ),
                    Memoria(),
                    ctx(),
                ).single()
        assertEquals("b", c.ref)
    }

    @Test
    fun `duration habits are not proposed on return`() =
        assertTrue(
            ReglaVuelta
                .candidatas(
                    hechos(primerDiaTrasAusencia = true, diasSinActividad = 5, habitos = listOf(habito(tipo = TipoHabito.DURACION))),
                    Memoria(),
                    ctx(),
                ).isEmpty(),
        )

    @Test
    fun `done, not requirable and quitting habits are not proposed`() {
        val c =
            ReglaVuelta
                .candidatas(
                    hechos(
                        primerDiaTrasAusencia = true,
                        diasSinActividad = 3,
                        habitos =
                            listOf(
                                habito("hecho", pct30 = 100, hechoHoy = true),
                                habito("libre", pct30 = 100, exigibleHoy = false),
                                habito("limite", pct30 = 100, tipo = TipoHabito.LIMITE),
                                habito("dejar", pct30 = 100, tipo = TipoHabito.DEJAR),
                                habito("ok", pct30 = 20),
                            ),
                    ),
                    Memoria(),
                    ctx(),
                ).single()
        assertEquals("ok", c.ref)
    }

    @Test
    fun `the return names today's day of the week`() {
        val c =
            ReglaVuelta
                .candidatas(hechos(diasSinActividad = 3, primerDiaTrasAusencia = true, habitos = listOf(habito())), Memoria(), ctx())
                .single()
        // TODAY es viernes.
        assertEquals(ValorHueco.DiaSemana(DayOfWeek.FRIDAY), c.huecos[Hueco.DIA_SEMANA])
    }
}
