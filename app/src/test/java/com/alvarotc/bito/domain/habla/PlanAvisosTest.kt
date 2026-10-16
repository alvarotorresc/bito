package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.millisOn
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanAvisosTest {
    private val temprano = 6 * 60
    private val conAlgo = hechos(habitos = listOf(habito()))
    private val s2300a0730 = Silencio(23 * 60, 7 * 60 + 30)

    private fun List<AvisoPlan>.de(tipo: TipoAviso): AvisoPlan? = singleOrNull { it.tipo == tipo }

    private fun aceptado(
        id: String = "a1",
        habitId: String = "h1",
        minuto: Int = 14 * 60 + 30,
        pospuesto: Int = 0,
        estado: EstadoAviso = EstadoAviso.PENDIENTE,
        dia: Int = TODAY,
    ): AvisoAceptado = AvisoAceptado(id, dia, habitId, minuto, pospuesto, estado, "p-$id", 0L)

    private fun manana(
        h: Hechos = conAlgo,
        m: Memoria = Memoria(),
        c: Contexto = ctx(ahoraMin = temprano),
    ): AvisoPlan? = PlanAvisos.de(h, m, c).de(TipoAviso.MANANA)

    @Test
    fun `morning and night at their hours`() {
        val plan = PlanAvisos.de(conAlgo, Memoria(), ctx(ahoraMin = temprano, horaManana = 8 * 60 + 30, horaNoche = 21 * 60 + 30))

        assertEquals(AvisoPlan(TipoAviso.MANANA, "manana", 8 * 60 + 30, TODAY), plan.de(TipoAviso.MANANA))
        assertEquals(AvisoPlan(TipoAviso.NOCHE, "noche", 21 * 60 + 30, TODAY), plan.de(TipoAviso.NOCHE))
    }

    @Test
    fun `a time already gone today is not planned`() {
        val m = Memoria(avisos = listOf(aceptado(id = "a1", minuto = 11 * 60), aceptado(id = "a2", minuto = 15 * 60)))
        val plan = PlanAvisos.de(conAlgo, m, ctx(ahoraMin = 12 * 60))

        assertNull(plan.de(TipoAviso.MANANA))
        assertEquals(21 * 60 + 30, plan.de(TipoAviso.NOCHE)?.minuto)
        assertEquals(listOf("a2"), plan.filter { it.tipo == TipoAviso.ACEPTADO }.map { it.clave })
    }

    @Test
    fun `after midnight the night of the same logical day is already gone`() {
        val plan = PlanAvisos.de(conAlgo, Memoria(), ctx(ahoraMin = 60, corteDia = 3 * 60))

        assertNull(plan.de(TipoAviso.NOCHE))
    }

    @Test
    fun `no morning without anything to do today`() {
        assertNull(manana(h = hechos()))
        assertNull(manana(h = hechos(habitos = listOf(habito(exigibleHoy = false)))))
        assertEquals(8 * 60 + 30, manana(h = hechos(tareas = listOf(tarea())))?.minuto)
        assertEquals(8 * 60 + 30, manana(h = hechos(habitos = listOf(habito())))?.minuto)
    }

    @Test
    fun `no morning once answered and the big thing chosen`() {
        val gordo = RefGordo(TipoRefGordo.TAREA, "t1")
        val respondida = Memoria(eventos = listOf(respuesta(Situacion.SALUDO, null, Respuesta.VALE, millisOn(TODAY, 5, 0))))
        val ayer =
            Memoria(eventos = listOf(respuesta(Situacion.SALUDO, null, Respuesta.VALE, millisOn(TODAY - 1, 20, 0), dia = TODAY - 1)))
        val conGordo = hechos(habitos = listOf(habito()), gordo = listOf(gordo), candidatosGordo = 2)
        val sinElegir = hechos(habitos = listOf(habito()), candidatosGordo = 2)
        val sinCandidatos = hechos(habitos = listOf(habito()), candidatosGordo = 0)

        assertNull(manana(h = conGordo, m = respondida))
        assertNull(manana(h = sinCandidatos, m = respondida))
        assertEquals(8 * 60 + 30, manana(h = sinElegir, m = respondida)?.minuto)
        assertEquals(8 * 60 + 30, manana(h = conGordo, m = Memoria())?.minuto)
        assertEquals(8 * 60 + 30, manana(h = conGordo, m = ayer)?.minuto)
    }

    @Test
    fun `night is cancelled when the day is sealed`() {
        val sellado = hechos(habitos = listOf(habito()), diaSellado = true)

        assertNull(PlanAvisos.de(sellado, Memoria(), ctx(ahoraMin = temprano)).de(TipoAviso.NOCHE))
        assertEquals(21 * 60 + 30, PlanAvisos.de(conAlgo, Memoria(), ctx(ahoraMin = temprano)).de(TipoAviso.NOCHE)?.minuto)
    }

    @Test
    fun `no night without a habit due today`() {
        val soloTareas = hechos(tareas = listOf(tarea()))

        assertNull(PlanAvisos.de(soloTareas, Memoria(), ctx(ahoraMin = temprano)).de(TipoAviso.NOCHE))
    }

    @Test
    fun `an accepted notice is dropped once its habit is done`() {
        val m = Memoria(avisos = listOf(aceptado(id = "a1", habitId = "h1"), aceptado(id = "a2", habitId = "h2", minuto = 17 * 60)))
        val h = hechos(habitos = listOf(habito(id = "h1", hechoHoy = true), habito(id = "h2")))

        val aceptados = PlanAvisos.de(h, m, ctx()).filter { it.tipo == TipoAviso.ACEPTADO }

        assertEquals(listOf(AvisoPlan(TipoAviso.ACEPTADO, "a2", 17 * 60, TODAY)), aceptados)
    }

    @Test
    fun `no more than two accepted notices a day, counting every state but cancelled`() {
        val m =
            Memoria(
                avisos =
                    listOf(
                        aceptado(id = "tercero", minuto = 13 * 60).copy(creadoMillis = 4L),
                        aceptado(id = "lanzado", estado = EstadoAviso.LANZADO).copy(creadoMillis = 1L),
                        aceptado(id = "cancelado", estado = EstadoAviso.CANCELADO).copy(creadoMillis = 2L),
                        aceptado(id = "segundo", minuto = 16 * 60).copy(creadoMillis = 3L),
                    ),
            )

        val claves = PlanAvisos.de(conAlgo, m, ctx()).filter { it.tipo == TipoAviso.ACEPTADO }.map { it.clave }

        assertEquals(listOf("segundo"), claves)
    }

    @Test
    fun `OK then another time twice keeps the last one planned`() {
        val m =
            Memoria(
                avisos =
                    listOf(
                        aceptado(id = "vale", estado = EstadoAviso.CANCELADO).copy(creadoMillis = 1L),
                        aceptado(id = "otra1", minuto = 15 * 60, estado = EstadoAviso.CANCELADO).copy(creadoMillis = 2L),
                        aceptado(id = "otra2", minuto = 16 * 60).copy(creadoMillis = 3L),
                    ),
            )

        val claves = PlanAvisos.de(conAlgo, m, ctx()).filter { it.tipo == TipoAviso.ACEPTADO }.map { it.clave }

        assertEquals(listOf("otra2"), claves)
    }

    @Test
    fun `only pending accepted notices of today are planned`() {
        val m =
            Memoria(
                avisos =
                    listOf(
                        aceptado(id = "a1"),
                        aceptado(id = "a2", estado = EstadoAviso.LANZADO),
                        aceptado(id = "a3", estado = EstadoAviso.CANCELADO),
                        aceptado(id = "a4", dia = TODAY - 1),
                    ),
            )

        val claves = PlanAvisos.de(conAlgo, m, ctx()).filter { it.tipo == TipoAviso.ACEPTADO }.map { it.clave }

        assertEquals(listOf("a1"), claves)
    }

    @Test
    fun `accepted notices are dropped when the day is sealed`() {
        val sellado = hechos(habitos = listOf(habito()), diaSellado = true)

        val m = Memoria(avisos = listOf(aceptado()))

        assertTrue(PlanAvisos.de(sellado, m, ctx()).none { it.tipo == TipoAviso.ACEPTADO })
        assertEquals(listOf("a1"), PlanAvisos.de(conAlgo, m, ctx()).filter { it.tipo == TipoAviso.ACEPTADO }.map { it.clave })
    }

    @Test
    fun `later moves thirty minutes twice and not a third time`() {
        val c = ctx(ahoraMin = 14 * 60 + 30)

        assertEquals(15 * 60, PlanAvisos.posponer(aceptado(minuto = 14 * 60 + 30, pospuesto = 0), c))
        assertEquals(15 * 60 + 30, PlanAvisos.posponer(aceptado(minuto = 15 * 60, pospuesto = 1), c))
        assertNull(PlanAvisos.posponer(aceptado(minuto = 15 * 60 + 30, pospuesto = 2), c))
    }

    @Test
    fun `later never crosses the silence nor the night`() {
        val noche = ctx(horaNoche = 21 * 60 + 30)
        // 21:15 es justo 15 min antes de la noche: vale; 21:16 ya no.
        assertEquals(21 * 60 + 15, PlanAvisos.posponer(aceptado(minuto = 20 * 60 + 45), noche))
        assertNull(PlanAvisos.posponer(aceptado(minuto = 20 * 60 + 46), noche))

        val silencio = ctx(horaNoche = 23 * 60, silencio = Silencio(16 * 60, 17 * 60))
        assertNull(PlanAvisos.posponer(aceptado(minuto = 15 * 60 + 45), silencio))
        assertEquals(15 * 60 + 30, PlanAvisos.posponer(aceptado(minuto = 15 * 60), silencio))
        // Un silencio corto que quedaría en medio también cuenta como cruzarlo.
        val corto = ctx(horaNoche = 23 * 60, silencio = Silencio(16 * 60, 16 * 60 + 10))
        assertNull(PlanAvisos.posponer(aceptado(minuto = 15 * 60 + 50), corto))
    }

    @Test
    fun `morning inside the silence fires at its end`() {
        val c = ctx(ahoraMin = 5 * 60, horaManana = 7 * 60, silencio = s2300a0730)

        assertEquals(7 * 60 + 30, manana(c = c)?.minuto)
    }

    @Test
    fun `morning inside a silence that ends after eleven does not fire`() {
        val c = ctx(ahoraMin = 5 * 60, horaManana = 8 * 60 + 30, silencio = Silencio(6 * 60, 11 * 60 + 30))

        assertNull(manana(c = c))
        assertEquals(11 * 60, manana(c = c.copy(silencio = Silencio(6 * 60, 11 * 60)))?.minuto)
    }

    @Test
    fun `night inside a silence that wraps midnight moves to its start`() {
        val c = ctx(ahoraMin = temprano, horaNoche = 23 * 60 + 15, corteDia = 3 * 60, silencio = s2300a0730)

        assertEquals(23 * 60, PlanAvisos.de(conAlgo, Memoria(), c).de(TipoAviso.NOCHE)?.minuto)
    }

    @Test
    fun `a slot inside the silence after the cutoff is lost`() {
        val c = ctx(corteDia = 3 * 60, silencio = s2300a0730)

        // 2:00 es todavía el día lógico de hoy; el silencio acaba a las 7:30 del día siguiente.
        assertNull(PlanAvisos.ajustarConservado(2 * 60, c))
        assertNull(PlanAvisos.ajustarConservado(23 * 60 + 30, ctx(corteDia = 0, silencio = s2300a0730)))
    }

    @Test
    fun `a slot inside the silence of the same logical day moves to its end`() {
        val c = ctx(corteDia = 0, silencio = s2300a0730)

        assertEquals(7 * 60 + 30, PlanAvisos.ajustarConservado(7 * 60, c))
        assertEquals(12 * 60, PlanAvisos.ajustarConservado(12 * 60, c))
        assertEquals(12 * 60, PlanAvisos.ajustarConservado(12 * 60, ctx()))
    }

    /** Los hechos de alguien cuyo último día activo fue hace [desdeUltimo] días lógicos (`hoy - último`). */
    private fun ausente(desdeUltimo: Int): Hechos = hechos(habitos = listOf(habito()), diasSinActividad = desdeUltimo - 1)

    /** Los valores de `hoy - último` de [rango] en que sale el aviso de la mañana. */
    private fun conManana(rango: IntRange): List<Int> = rango.filter { manana(h = ausente(it)) != null }

    @Test
    fun `away three days the morning goes every other day`() {
        // 2 es un día normal; desde 3 (el umbral de la vuelta y del ánimo DRAMATIC), en días alternos.
        assertEquals(listOf(1, 2, 3, 5, 7, 9, 11, 13), conManana(1..13))
    }

    @Test
    fun `away fourteen days the morning goes on the weekday of the last active day`() {
        assertEquals(listOf(14, 21, 28, 35), conManana(14..35))
    }

    @Test
    fun `no night while away`() {
        assertNull(PlanAvisos.de(ausente(3), Memoria(), ctx(ahoraMin = temprano)).de(TipoAviso.NOCHE))
        assertNull(PlanAvisos.de(ausente(21), Memoria(), ctx(ahoraMin = temprano)).de(TipoAviso.NOCHE))
        assertEquals(21 * 60 + 30, PlanAvisos.de(ausente(2), Memoria(), ctx(ahoraMin = temprano)).de(TipoAviso.NOCHE)?.minuto)
    }

    @Test
    fun `the night follows the single silence rule`() {
        val silencios = listOf(Silencio(21 * 60, 7 * 60 + 30), Silencio(22 * 60 + 30, 6 * 60), Silencio(13 * 60, 14 * 60))
        val minutos =
            silencios.map { s ->
                val c = ctx(ahoraMin = temprano, horaNoche = 22 * 60 + 45, corteDia = 3 * 60, silencio = s)
                val esperado = NocheEnSilencio.minuto(c.horaNoche, c.silencio, c.corteDia)
                val plan = PlanAvisos.de(conAlgo, Memoria(), c).de(TipoAviso.NOCHE)?.minuto
                assertEquals(esperado, plan)
                plan
            }

        // Los tres silencios dan minutos distintos: la prueba no compara un valor consigo mismo.
        assertEquals(listOf(21 * 60, 22 * 60 + 30, 22 * 60 + 45), minutos)
        assertNotEquals(minutos[0], minutos[1])
    }
}
