package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.AUGUST_FIRST
import com.alvarotc.bito.domain.RealHabits
import com.alvarotc.bito.domain.THIS_MONDAY
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.archivedOn
import com.alvarotc.bito.domain.createdOn
import com.alvarotc.bito.domain.datedTask
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.entriesOn
import com.alvarotc.bito.domain.habla.reglas.ReglaGordoSinEmpezar
import com.alvarotc.bito.domain.entryOn
import com.alvarotc.bito.domain.millisOn
import com.alvarotc.bito.domain.model.DaySeal
import com.alvarotc.bito.domain.model.DomainState
import com.alvarotc.bito.domain.model.Entry
import com.alvarotc.bito.domain.model.Habit
import com.alvarotc.bito.domain.model.LogicalDay
import com.alvarotc.bito.domain.model.Mood
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.domain.pauseOn
import com.alvarotc.bito.domain.propuestas.Respuesta
import com.alvarotc.bito.domain.task
import com.alvarotc.bito.domain.taskEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

class HechosTest {
    private val leer = RealHabits.readBeforeSleep
    private val seq = AtomicInteger(0)

    private fun de(
        state: DomainState,
        memoria: Memoria = Memoria(),
        recordatorios: Map<String, Int> = emptyMap(),
    ): Hechos = Hechos.de(state, memoria, ctx(), recordatorios)

    private fun primero(state: DomainState): HabitoHoy = de(state).habitos.first()

    private fun diaSemana(dia: LogicalDay): DayOfWeek = LocalDate.ofEpochDay(dia.toLong()).dayOfWeek

    /** Un registro hecho a las [hora]:[min] de [dia], el mismo día lógico que registra. */
    private fun registro(
        habit: Habit,
        dia: LogicalDay,
        hora: Int,
        min: Int,
    ): Entry = Entry("r-${seq.incrementAndGet()}", habit.id, dia, 1, millisOn(dia, hora, min))

    private fun rachaDe(
        n: Int,
        habit: Habit = leer,
    ): DomainState = domainState(habits = listOf(habit), entries = entriesOn(habit, (TODAY - n + 1)..TODAY))

    private fun evento(
        tipo: TipoMemoria,
        dia: LogicalDay,
        ref: String? = null,
        valor: Int? = null,
    ) = EventoMemoria("ev-${seq.incrementAndGet()}", tipo, dia, ref, valor, millisOn(dia, 12))

    // ---- hora habitual -------------------------------------------------------------------

    private fun registrosDeLaTarde(): List<Entry> =
        listOf(19 to 2, 19 to 10, 19 to 20, 19 to 40, 20 to 5).mapIndexed { i, (h, m) -> registro(leer, TODAY - 1 - i, h, m) }

    @Test
    fun `habitual hour needs five records`() {
        assertNull(primero(domainState(habits = listOf(leer), entries = registrosDeLaTarde().take(4))).horaHabitual)
    }

    @Test
    fun `habitual hour is the median rounded to the quarter`() {
        assertEquals(19 * 60 + 15, primero(domainState(habits = listOf(leer), entries = registrosDeLaTarde())).horaHabitual)
    }

    @Test
    fun `a record of yesterday made today does not count`() {
        val tarde = registrosDeLaTarde().take(4)
        val deAyerHechoHoy = Entry("tarde", leer.id, TODAY - 6, 1, millisOn(TODAY, 19, 0))
        assertNull(primero(domainState(habits = listOf(leer), entries = tarde + deAyerHechoHoy)).horaHabitual)
    }

    @Test
    fun `records older than thirty days do not count`() {
        val viejos = (0 until 5).map { registro(leer, TODAY - 40 - it, 19, 0) }
        assertNull(primero(domainState(habits = listOf(leer), entries = viejos)).horaHabitual)
    }

    // ---- ausencia y ánimo ----------------------------------------------------------------

    @Test
    fun `days without activity are the days in between`() {
        assertEquals(2, de(domainState(habits = listOf(leer), entries = listOf(entryOn(leer, TODAY - 3)))).diasSinActividad)
        assertEquals(3, de(domainState(habits = listOf(leer), entries = listOf(entryOn(leer, TODAY - 4)))).diasSinActividad)
        assertEquals(0, de(domainState(habits = listOf(leer), entries = listOf(entryOn(leer, TODAY - 1)))).diasSinActividad)
        assertEquals(0, de(domainState(habits = listOf(leer), entries = listOf(entryOn(leer, TODAY)))).diasSinActividad)
        assertEquals(0, de(domainState(habits = listOf(leer))).diasSinActividad)
    }

    @Test
    fun `first day after an absence`() {
        val tras = domainState(habits = listOf(leer), entries = listOf(entryOn(leer, TODAY - 3)))
        assertTrue(de(tras).primerDiaTrasAusencia)
        assertFalse(de(domainState(habits = listOf(leer), entries = listOf(entryOn(leer, TODAY - 2)))).primerDiaTrasAusencia)
    }

    @Test
    fun `sealing the days away today does not end the absence`() {
        val fuera = ((TODAY - 3)..(TODAY - 1)).map { DaySeal(it, millisOn(TODAY, 9)) }
        val tras = domainState(habits = listOf(leer), entries = listOf(entryOn(leer, TODAY - 4))).copy(daySeals = fuera)
        assertTrue(de(tras).primerDiaTrasAusencia)
        assertEquals(3, de(tras).diasSinActividad)
        // Sellados cada uno en su día, sí son actividad: no hubo ausencia.
        val enSuDia = tras.copy(daySeals = ((TODAY - 3)..(TODAY - 1)).map { DaySeal(it, millisOn(it, 22)) })
        assertFalse(de(enSuDia).primerDiaTrasAusencia)
        assertEquals(0, de(enSuDia).diasSinActividad)
    }

    @Test
    fun `the absence threshold is the one of the dramatic mood`() {
        for ((hace, esperado) in listOf(2 to false, 3 to true)) {
            val s = domainState(habits = listOf(leer), entries = listOf(entryOn(leer, TODAY - hace)))
            assertEquals("hace $hace", esperado, de(s).primerDiaTrasAusencia)
            assertEquals("hace $hace", esperado, de(s).animoSemana == Mood.DRAMATIC)
        }
    }

    @Test
    fun `an answer to the return today closes the first day`() {
        val tras = domainState(habits = listOf(leer), entries = listOf(entryOn(leer, TODAY - 3)))
        val respondida = Memoria(eventos = listOf(respuesta(Situacion.VUELTA, "h1", Respuesta.AHORA, millisOn(TODAY, 9))))
        val vuelta = Memoria(eventos = listOf(evento(TipoMemoria.VUELTA, TODAY, valor = 4)))
        assertFalse(de(tras, respondida).primerDiaTrasAusencia)
        assertFalse(de(tras, vuelta).primerDiaTrasAusencia)
        // Una vuelta de otro día no cuenta.
        assertTrue(de(tras, Memoria(eventos = listOf(evento(TipoMemoria.VUELTA, TODAY - 30, valor = 4)))).primerDiaTrasAusencia)
    }

    @Test
    fun `mood of yesterday comes from memory`() {
        val ayer = evento(TipoMemoria.ANIMO, TODAY - 1, valor = 2)
        val anteayer = evento(TipoMemoria.ANIMO, TODAY - 2, valor = 0)
        val duro = Memoria(eventos = listOf(ayer, anteayer))
        val h = de(domainState(habits = listOf(leer)), duro)
        assertEquals(2, h.animoAyer)
        assertTrue(h.diaDuro)
        val bien = de(domainState(habits = listOf(leer)), Memoria(eventos = listOf(evento(TipoMemoria.ANIMO, TODAY - 1, valor = 0))))
        assertEquals(0, bien.animoAyer)
        assertFalse(bien.diaDuro)
        assertNull(de(domainState(habits = listOf(leer))).animoAyer)
    }

    @Test
    fun `mood of the week is the mood engine`() {
        assertEquals(Mood.NORMAL, de(domainState()).animoSemana)
        assertEquals(Mood.WILTED, de(domainState(habits = listOf(leer))).animoSemana)
    }

    // ---- el día --------------------------------------------------------------------------

    @Test
    fun `the cierre asks for what is pending`() {
        // CHECK sin hacer.
        assertEquals(1, de(domainState(habits = listOf(leer))).pendientesCierre)
        // Límite limpio sin sellar: se pregunta; sellado, ya no.
        assertEquals(1, de(domainState(habits = listOf(RealHabits.socialMedia))).pendientesCierre)
        assertEquals(0, de(domainState(habits = listOf(RealHabits.socialMedia), sealedDays = listOf(TODAY))).pendientesCierre)
        // Dejar con recaída: nunca se pregunta.
        val recaida = domainState(habits = listOf(RealHabits.noSmoking), entries = listOf(entryOn(RealHabits.noSmoking, TODAY)))
        assertEquals(0, de(recaida).pendientesCierre)
        // Un CHECK hecho tampoco.
        assertEquals(0, de(domainState(habits = listOf(leer), entries = listOf(entryOn(leer, TODAY)))).pendientesCierre)
    }

    @Test
    fun `your day counts the habits due today`() {
        val a = RealHabits.makeBed
        val b = RealHabits.meditate
        val c = RealHabits.floss
        val h = de(domainState(habits = listOf(a, b, c), entries = listOf(entryOn(a, TODAY), entryOn(b, TODAY))))
        assertEquals(2, h.tuDiaHechos)
        assertEquals(3, h.tuDiaTotal)
    }

    @Test
    fun `an archived habit is not in the list`() {
        val h = de(domainState(habits = listOf(leer, RealHabits.meditate.archivedOn(TODAY - 5))))
        assertEquals(listOf(leer.id), h.habitos.map { it.id })
    }

    @Test
    fun `a paused habit is not due and says since when`() {
        val h = de(domainState(habits = listOf(leer), pauses = listOf(pauseOn(leer, TODAY - 4))))
        val habito = h.habitos.single()
        assertTrue(habito.enPausa)
        assertFalse(habito.exigibleHoy)
        assertEquals(TODAY - 4, habito.pausadoDesde)
        assertEquals(0, h.tuDiaTotal)
        assertNull(primero(domainState(habits = listOf(leer))).pausadoDesde)
    }

    @Test
    fun `the day is sealed when it has a seal`() {
        assertTrue(de(domainState(habits = listOf(leer), sealedDays = listOf(TODAY))).diaSellado)
        assertFalse(de(domainState(habits = listOf(leer))).diaSellado)
    }

    // ---- un hábito -----------------------------------------------------------------------

    @Test
    fun `kinds, progress and target of each habit`() {
        val agua = RealHabits.water
        val habitos =
            listOf(agua, RealHabits.socialMedia, RealHabits.noSmoking, RealHabits.steps, RealHabits.walking, RealHabits.strengthTraining)
        val s = domainState(habits = habitos, entries = listOf(entryOn(agua, TODAY, value = 3)))
        val porId = de(s).habitos.associateBy { it.id }
        val a = porId.getValue(agua.id)
        assertEquals(TipoHabito.CONTADOR, a.tipo)
        assertEquals(3, a.progreso)
        assertEquals(8, a.objetivo)
        assertEquals(1, a.paso)
        assertEquals("vasos", a.unidad)
        assertTrue(a.diario)
        assertTrue(a.exigibleHoy)
        assertFalse(a.hechoHoy)
        assertEquals(TipoHabito.LIMITE, porId.getValue("social-media").tipo)
        assertEquals(TipoHabito.DEJAR, porId.getValue("no-smoking").tipo)
        assertEquals(TipoHabito.CHECK, porId.getValue("steps").tipo)
        assertEquals(TipoHabito.DURACION, porId.getValue("walking").tipo)
        val fuerza = porId.getValue("strength")
        assertEquals(TipoHabito.CHECK, fuerza.tipo)
        assertFalse(fuerza.diario)
    }

    @Test
    fun `done today follows the card`() {
        val s = domainState(habits = listOf(leer), entries = listOf(entryOn(leer, TODAY)))
        assertTrue(primero(s).hechoHoy)
        assertFalse(primero(s).falladoHoy)
        val recaida = domainState(habits = listOf(RealHabits.noSmoking), entries = listOf(entryOn(RealHabits.noSmoking, TODAY)))
        assertTrue(primero(recaida).falladoHoy)
        assertFalse(primero(recaida).hechoHoy)
    }

    @Test
    fun `the own reminder comes apart`() {
        val h = de(domainState(habits = listOf(leer)), recordatorios = mapOf(leer.id to 8 * 60))
        assertEquals(8 * 60, h.habitos.single().recordatorio)
        assertNull(primero(domainState(habits = listOf(leer))).recordatorio)
    }

    @Test
    fun `yesterday failed and the three before`() {
        val s = domainState(habits = listOf(leer), entries = listOf(entryOn(leer, TODAY - 3), entryOn(leer, TODAY - 2)))
        val h = primero(s)
        assertTrue(h.falladoAyer)
        assertEquals(2, h.cumplidosTresAnteriores)
        val bien = primero(domainState(habits = listOf(leer), entries = entriesOn(leer, (TODAY - 3)..(TODAY - 1))))
        assertFalse(bien.falladoAyer)
        // Los tres días anteriores a ayer: hoy-2..hoy-4. Ayer no entra.
        assertEquals(2, bien.cumplidosTresAnteriores)
        val soloHoyMenos4 = primero(domainState(habits = listOf(leer), entries = listOf(entryOn(leer, TODAY - 4))))
        assertTrue(soloHoyMenos4.falladoAyer)
        assertEquals(1, soloHoyMenos4.cumplidosTresAnteriores)
    }

    @Test
    fun `history length`() {
        val h = primero(domainState(habits = listOf(leer.createdOn(TODAY - 20))))
        assertEquals(20, h.creadoHaceDias)
        assertEquals(2, h.semanasHistoria)
    }

    // ---- cumplimiento --------------------------------------------------------------------

    @Test
    fun `thirty judged periods give the percentage`() {
        val s = domainState(habits = listOf(leer), entries = entriesOn(leer, (TODAY - 29)..(TODAY - 15)))
        val h = primero(s)
        assertEquals(50, h.pct30)
        assertEquals(30, h.juzgados30)
    }

    @Test
    fun `no judged periods give no percentage`() {
        val h = primero(domainState(habits = listOf(leer.createdOn(TODAY))))
        assertNull(h.pct30)
        assertEquals(0, h.juzgados30)
    }

    @Test
    fun `this weekday over the last eight appearances`() {
        val viernes = listOf(1, 3, 5, 7).map { TODAY - 7 * it }
        val h = primero(domainState(habits = listOf(leer), entries = entriesOn(leer, viernes)))
        assertEquals(50, h.pctHoySemana)
        assertEquals(8, h.muestrasHoySemana)
    }

    @Test
    fun `a habit that is not daily has no weekday numbers`() {
        val h = primero(domainState(habits = listOf(RealHabits.strengthTraining)))
        assertNull(h.pctHoySemana)
        assertEquals(0, h.muestrasHoySemana)
        assertNull(h.mejorDiaSemana)
    }

    @Test
    fun `the best weekday`() {
        val hechos = ((TODAY - 56)..(TODAY - 1)).filter { diaSemana(it) == DayOfWeek.TUESDAY || it % 4 == 0 }
        val h = primero(domainState(habits = listOf(leer), entries = entriesOn(leer, hechos)))
        assertEquals(DayOfWeek.TUESDAY, h.mejorDiaSemana)
        assertEquals(100, h.pctMejorDiaSemana)
    }

    @Test
    fun `no best weekday before six weeks of history`() {
        val hechos = ((TODAY - 30)..(TODAY - 1)).filter { diaSemana(it) == DayOfWeek.TUESDAY }
        val h = primero(domainState(habits = listOf(leer.createdOn(TODAY - 30)), entries = entriesOn(leer, hechos)))
        assertNull(h.mejorDiaSemana)
        assertNull(h.pctMejorDiaSemana)
    }

    // ---- rachas --------------------------------------------------------------------------

    @Test
    fun `milestone near`() {
        assertEquals(7, primero(rachaDe(5)).hitoCerca)
        assertEquals(7, primero(rachaDe(6)).hitoCerca)
        assertNull(primero(rachaDe(4)).hitoCerca)
        // A 0 periodos el hito ya se alcanzó: no está cerca.
        assertNull(primero(rachaDe(7)).hitoCerca)
        assertEquals(30, primero(rachaDe(28)).hitoCerca)
    }

    @Test
    fun `how many are missing and what the milestone pays`() {
        val h = primero(rachaDe(5))
        assertEquals(2, h.faltanHito)
        assertEquals(5, h.bitosHito)
        assertEquals(20, primero(rachaDe(29)).bitosHito)
        assertNull(primero(rachaDe(4)).faltanHito)
        assertNull(primero(rachaDe(4)).bitosHito)
    }

    @Test
    fun `round streak only when it advanced today`() {
        assertTrue(primero(rachaDe(5)).rachaHoyRedonda)
        assertFalse(primero(rachaDe(6)).rachaHoyRedonda)
        val hastaAyer = domainState(habits = listOf(leer), entries = entriesOn(leer, (TODAY - 5)..(TODAY - 1)))
        val h = primero(hastaAyer)
        assertEquals(5, h.racha)
        assertFalse(h.rachaHoyRedonda)
        assertFalse(primero(domainState(habits = listOf(leer))).rachaHoyRedonda)
    }

    @Test
    fun `recovered streak after a long one broke`() {
        val antes = entriesOn(leer, (TODAY - 16)..(TODAY - 9))
        val ahora = entriesOn(leer, (TODAY - 7)..TODAY)
        val h = primero(domainState(habits = listOf(leer), entries = antes + ahora))
        assertEquals(8, h.racha)
        assertTrue(h.rachaRotaRecuperada)
        assertEquals(8, h.mejorRacha)
        assertFalse(primero(rachaDe(8)).rachaRotaRecuperada)
        val sieteDespues = domainState(habits = listOf(leer), entries = antes + entriesOn(leer, (TODAY - 6)..TODAY))
        assertFalse(primero(sieteDespues).rachaRotaRecuperada)
    }

    @Test
    fun `clean days of a habit to quit`() {
        val dejar = RealHabits.noSmoking
        val recaida = primero(domainState(habits = listOf(dejar), entries = listOf(entryOn(dejar, TODAY - 3))))
        assertEquals(3, recaida.diasLimpios)
        val nuevo = primero(domainState(habits = listOf(dejar.createdOn(TODAY - 10))))
        assertEquals(10, nuevo.diasLimpios)
        assertEquals(0, primero(domainState(habits = listOf(leer))).diasLimpios)
    }

    // ---- tareas --------------------------------------------------------------------------

    @Test
    fun `tasks of today with the ones done today`() {
        val vencida = datedTask("t1", TODAY - 2).copy(title = "Vencida")
        val hoy = datedTask("t2", TODAY).copy(title = "De hoy")
        val hecha = task(id = "t3", title = "Hecha", status = TaskStatus.DONE, doneOnDay = TODAY)
        val s =
            domainState(
                tasks = listOf(vencida, hoy, hecha),
                taskEvents = listOf(taskEvent("t2", TaskEventKind.ATTEMPT, TODAY), taskEvent("t1", TaskEventKind.ATTEMPT, TODAY - 1)),
            )
        val h = de(s)
        assertEquals(listOf("t1", "t2", "t3"), h.tareas.map { it.id })
        assertEquals(listOf(2, null, null), h.tareas.map { it.vencidaDias })
        assertEquals(listOf(false, true, false), h.tareas.map { it.venceHoy })
        assertEquals(listOf(false, false, true), h.tareas.map { it.hecha })
        assertEquals(listOf(false, true, false), h.tareas.map { it.empezada })
        assertEquals(listOf("Vencida", "De hoy"), h.tareasPendientesHoy)
    }

    @Test
    fun `started means an attempt today`() {
        val t = datedTask("t1", TODAY)
        val ayer = domainState(tasks = listOf(t), taskEvents = listOf(taskEvent("t1", TaskEventKind.ATTEMPT, TODAY - 1)))
        assertFalse(de(ayer).tareas.single().empezada)
        val hoy = domainState(tasks = listOf(t), taskEvents = listOf(taskEvent("t1", TaskEventKind.ATTEMPT, TODAY)))
        assertTrue(de(hoy).tareas.single().empezada)
        val traida = domainState(tasks = listOf(t), taskEvents = listOf(taskEvent("t1", TaskEventKind.BROUGHT, TODAY)))
        assertFalse(de(traida).tareas.single().empezada)
    }

    // ---- lo gordo ------------------------------------------------------------------------

    private fun tareaRef(id: String) = RefGordo(TipoRefGordo.TAREA, id)

    private fun estadoGordo() =
        domainState(
            tasks =
                listOf(
                    datedTask("a", TODAY - 1),
                    datedTask("d", TODAY - 5),
                    task(id = "b", status = TaskStatus.DONE, doneOnDay = TODAY - 1),
                    task(id = "c", status = TaskStatus.DONE, doneOnDay = TODAY),
                ),
        )

    private fun memoriaGordo() =
        Memoria(
            gordo =
                mapOf(
                    TODAY - 1 to listOf(tareaRef("a"), tareaRef("b"), RefGordo(TipoRefGordo.HABITO, leer.id)),
                    TODAY to listOf(tareaRef("c")),
                ),
        )

    @Test
    fun `yesterday's big thing left undone is only tasks still open`() {
        val h = de(estadoGordo(), memoriaGordo())
        assertEquals(listOf(tareaRef("a")), h.gordoAyerSinHacer)
        assertEquals(listOf(tareaRef("c")), h.gordo)
        assertEquals(listOf(tareaRef("c")), h.gordoHecho)
        assertEquals(emptyList<RefGordo>(), de(estadoGordo()).gordo)
    }

    @Test
    fun `the first task of yesterday's big thing that is done is named`() {
        assertEquals("Llamar al banco", de(estadoGordo(), memoriaGordo()).tareaGordoAyerHecha)
        assertNull(de(estadoGordo()).tareaGordoAyerHecha)
        val soloHabito = Memoria(gordo = mapOf(TODAY - 1 to listOf(RefGordo(TipoRefGordo.HABITO, leer.id))))
        assertNull(de(estadoGordo(), soloHabito).tareaGordoAyerHecha)
    }

    @Test
    fun `the big thing sheet is counted once`() {
        val h = de(estadoGordo(), memoriaGordo())
        assertEquals(2, h.candidatosGordo)
        assertEquals(tareaRef("a"), h.sugerenciaGordo)
        // Sin lo de ayer, la vencida más antigua.
        val sinAyer = de(estadoGordo())
        assertEquals(2, sinAyer.candidatosGordo)
        assertEquals(tareaRef("d"), sinAyer.sugerenciaGordo)
        assertNull(de(domainState()).sugerenciaGordo)
    }

    // ---- Camino --------------------------------------------------------------------------

    @Test
    fun `perfect days of the month`() {
        val s =
            domainState(
                habits = listOf(leer.createdOn(AUGUST_FIRST)),
                entries = listOf(entryOn(leer, AUGUST_FIRST), entryOn(leer, AUGUST_FIRST + 1)),
            )
        assertEquals(2, de(s).diasPerfectosMes)
        assertEquals(0, de(domainState()).diasPerfectosMes)
    }

    @Test
    fun `delta of the week against the last`() {
        val estaSemana = entriesOn(leer, THIS_MONDAY..(THIS_MONDAY + 3))
        assertEquals(100, de(domainState(habits = listOf(leer), entries = estaSemana)).deltaSemanaPct)
        assertNull(de(domainState()).deltaSemanaPct)
    }

    @Test
    fun `what is left today counts like pending in the rules`() {
        // Un límite y un dejar limpios ya no esperan nada (la fase pasa a ESPERANDO como «Todo hecho»).
        assertFalse(Hechos.quedanPendientes(domainState(habits = listOf(RealHabits.socialMedia, RealHabits.noSmoking)), TODAY))
        assertTrue(Hechos.quedanPendientes(domainState(habits = listOf(RealHabits.socialMedia, RealHabits.meditate)), TODAY))
        val hecho = domainState(habits = listOf(RealHabits.meditate), entries = listOf(entryOn(RealHabits.meditate, TODAY)))
        assertFalse(Hechos.quedanPendientes(hecho, TODAY))
    }

    @Test
    fun `a closed session today counts as started`() {
        val s = domainState(tasks = listOf(datedTask("t1", TODAY), datedTask("t2", TODAY)))
        val h = Hechos.de(s, Memoria(), ctx(), emptyMap(), empezadas = setOf("t1"))
        assertEquals(listOf(true, false), h.tareas.map { it.empezada })
        assertFalse(de(s).tareas.first().empezada)
    }

    @Test
    fun `a live session counts as started for the big thing`() {
        val s = domainState(tasks = listOf(datedTask("t1", TODAY)))
        val gordo = listOf(RefGordo(TipoRefGordo.TAREA, "t1"))
        val m = Memoria(gordo = mapOf(TODAY to gordo), gordoElegidoMillis = ctx().ahoraMillis - 3 * 60 * 60_000L)
        val sin = Hechos.de(s, m, ctx(), emptyMap())
        // Sin sesión, lo gordo sigue sin empezar y Habi lo diría.
        assertEquals(1, ReglaGordoSinEmpezar.candidatas(sin, m, ctx()).size)
        // Con la sesión viva de esa tarea, la misma cuenta que la fuente aplica a sus hechos, ya no.
        val con = sin.conEmpezadas(setOf("t1"))
        assertTrue(con.tareas.single().empezada)
        assertTrue(ReglaGordoSinEmpezar.candidatas(con, m, ctx()).isEmpty())
    }
}
