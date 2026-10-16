package com.alvarotc.bito.domain.sesion

import com.alvarotc.bito.domain.BreathingRhythm
import com.alvarotc.bito.domain.LogicalDays
import com.alvarotc.bito.domain.model.BreathPhase
import com.alvarotc.bito.domain.model.BreathingMode
import com.alvarotc.bito.domain.sesion.Tramo.Descanso
import com.alvarotc.bito.domain.sesion.Tramo.Foco
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** El tiempo de la sesión como función pura de su plan y sus eventos (spec §6, §11.5). */
class RelojSesionTest {
    private val min = 60_000L

    /** Los dos relojes cuando han pasado [ms] de sesión, sin tocar la hora ni reiniciar. */
    private fun ahoraTras(ms: Long) = Ahora(INICIO_PARED + ms, ANCLA_ELAPSED + ms)

    @Test
    fun `every boundary falls on its millisecond`() {
        val r = registroSesion()
        val duraciones = listOf(60_000L, 25 * min, 5 * min, 25 * min, 5 * min, 25 * min, 5 * min, 25 * min, 15 * min)
        var frontera = 0L
        duraciones.forEachIndexed { i, d ->
            frontera += d
            assertEquals("antes de la frontera $i", i, RelojSesion.enSesion(r, frontera - 1).tramo)
            assertEquals("justo en la frontera $i", i + 1, RelojSesion.enSesion(r, frontera).tramo)
        }
        val fin = RelojSesion.enSesion(r, frontera)
        assertNull(fin.actual)
        assertEquals(9, fin.tramo)
        assertEquals(0L, fin.restaMillis)
        assertNull(fin.finTramoEnSesion)
        assertEquals(7_860_000L, frontera)
    }

    @Test
    fun `pause freezes the block and focus does not count`() {
        val plan = planSesion(bloques = 1, entrada = Entrada.DIRECTO)
        val r = registroSesion(plan, listOf(Evento.Pausa(10 * min), Evento.Seguir(15 * min)))

        val enPausa = RelojSesion.enSesion(r, 12 * min)
        assertTrue(enPausa.pausado)
        assertEquals(15 * min, enPausa.restaMillis)
        assertEquals(10 * min, enPausa.focoMillis)
        assertNull(enPausa.finTramoEnSesion)

        val despues = RelojSesion.enSesion(r, 20 * min)
        assertFalse(despues.pausado)
        assertEquals(10 * min, despues.restaMillis)
        assertEquals(15 * min, despues.focoMillis)
        assertEquals(30 * min, despues.finTramoEnSesion)
        assertEquals(20 * min, despues.transcurridoMillis)
    }

    @Test
    fun `adding minutes extends the current focus only`() {
        val plan = planSesion(bloques = 2, entrada = Entrada.DIRECTO)
        val r = registroSesion(plan, listOf(Evento.Sumar(2 * min, 5)))

        val foco = RelojSesion.enSesion(r, 3 * min)
        assertEquals(30 * min, foco.duracionTramoMillis)
        assertEquals(27 * min, foco.restaMillis)
        assertEquals(Foco(1, 25), RelojSesion.enSesion(r, 30 * min - 1).actual)

        val descanso = RelojSesion.enSesion(r, 30 * min)
        assertEquals(Descanso(1, false, 5), descanso.actual)
        assertEquals(5 * min, descanso.duracionTramoMillis)

        val segundo = RelojSesion.enSesion(r, 35 * min)
        assertEquals(Foco(2, 25), segundo.actual)
        assertEquals(25 * min, segundo.duracionTramoMillis)
    }

    @Test
    fun `adding minutes outside a focus does nothing`() {
        val plan = planSesion(bloques = 2, entrada = Entrada.DIRECTO)
        val r = registroSesion(plan, listOf(Evento.Sumar(26 * min, 5)))
        assertEquals(Foco(2, 25), RelojSesion.enSesion(r, 30 * min).actual)
        assertEquals(25 * min, RelojSesion.enSesion(r, 30 * min).duracionTramoMillis)
    }

    @Test
    fun `skip ends breathing and break at that instant`() {
        val conRespirar = registroSesion(planSesion(bloques = 2), listOf(Evento.Saltar(10_000L)))
        val tras = RelojSesion.enSesion(conRespirar, 10_000L)
        assertEquals(Foco(1, 25), tras.actual)
        assertEquals(25 * min, tras.restaMillis)
        assertEquals(10_000L + 25 * min, tras.finTramoEnSesion)

        val plan = planSesion(bloques = 2, entrada = Entrada.DIRECTO)
        val sinDescanso = registroSesion(plan, listOf(Evento.Saltar(26 * min)))
        val foco2 = RelojSesion.enSesion(sinDescanso, 26 * min)
        assertEquals(Foco(2, 25), foco2.actual)
        assertEquals(25 * min, foco2.restaMillis)
        assertEquals(25 * min, foco2.focoMillis)
    }

    @Test
    fun `a late skip never eats the next focus`() {
        val plan = planSesion(bloques = 2, entrada = Entrada.DIRECTO)
        // El descanso acabó solo a los 30 min; el Saltar de 31 llega tarde y no cuenta.
        val r = registroSesion(plan, listOf(Evento.Saltar(31 * min), Evento.Saltar(5 * min)))
        val i = RelojSesion.enSesion(r, 32 * min)
        assertEquals(Foco(2, 25), i.actual)
        assertEquals(23 * min, i.restaMillis)
    }

    @Test
    fun `with auto break off the break waits with a still clock`() {
        val plan = planSesion(bloques = 2, entrada = Entrada.DIRECTO, descansoSolo = false)
        val esperando = RelojSesion.enSesion(registroSesion(plan), 40 * min)
        assertEquals(Descanso(1, false, 5), esperando.actual)
        assertTrue(esperando.esperandoDescanso)
        assertEquals(5 * min, esperando.restaMillis)
        assertNull(esperando.finTramoEnSesion)
        assertEquals(25 * min, esperando.focoMillis)

        val r = registroSesion(plan, listOf(Evento.EmpezarDescanso(40 * min)))
        val enDescanso = RelojSesion.enSesion(r, 42 * min)
        assertFalse(enDescanso.esperandoDescanso)
        assertEquals(3 * min, enDescanso.restaMillis)
        assertEquals(45 * min, enDescanso.finTramoEnSesion)

        val foco2 = RelojSesion.enSesion(r, 45 * min)
        assertEquals(Foco(2, 25), foco2.actual)
        assertFalse(foco2.esperandoDescanso)
        assertEquals(70 * min, foco2.finTramoEnSesion)
    }

    @Test
    fun `a pause just after the boundary does not freeze the break`() {
        val plan = planSesion(bloques = 2, entrada = Entrada.DIRECTO)
        // La pantalla aún enseña el foco; la Pausa llega 100 ms dentro del descanso.
        val r = registroSesion(plan, listOf(Evento.Pausa(25 * min + 100)))
        val descanso = RelojSesion.enSesion(r, 27 * min)
        assertEquals(Descanso(1, false, 5), descanso.actual)
        assertFalse(descanso.pausado)
        assertEquals(3 * min, descanso.restaMillis)
        assertEquals(30 * min, descanso.finTramoEnSesion)

        val foco2 = RelojSesion.enSesion(r, 31 * min)
        assertEquals(Foco(2, 25), foco2.actual)
        assertFalse(foco2.pausado)
        assertEquals(24 * min, foco2.restaMillis)
    }

    @Test
    fun `a pause exactly at the boundary is ignored`() {
        val plan = planSesion(bloques = 2, entrada = Entrada.DIRECTO)
        val r = registroSesion(plan, listOf(Evento.Pausa(25 * min)))
        val descanso = RelojSesion.enSesion(r, 26 * min)
        assertEquals(Descanso(1, false, 5), descanso.actual)
        assertFalse(descanso.pausado)
        assertEquals(30 * min, descanso.finTramoEnSesion)
    }

    @Test
    fun `a pause while breathing is ignored`() {
        val r = registroSesion(planSesion(bloques = 1), listOf(Evento.Pausa(10_000L)))
        val respirando = RelojSesion.enSesion(r, 20_000L)
        assertFalse(respirando.pausado)
        assertEquals(60_000L, respirando.finTramoEnSesion)
        assertEquals(Foco(1, 25), RelojSesion.enSesion(r, 60_000L).actual)
    }

    @Test
    fun `a pause in the break then skip starts the next focus running`() {
        val plan = planSesion(bloques = 2, entrada = Entrada.DIRECTO)
        val r = registroSesion(plan, listOf(Evento.Pausa(25 * min + 100), Evento.Saltar(26 * min)))
        val foco2 = RelojSesion.enSesion(r, 30 * min)
        assertEquals(Foco(2, 25), foco2.actual)
        assertFalse(foco2.pausado)
        assertEquals(21 * min, foco2.restaMillis)
        assertEquals(51 * min, foco2.finTramoEnSesion)
    }

    @Test
    fun `skip on a waiting break goes to the next focus`() {
        val plan = planSesion(bloques = 2, entrada = Entrada.DIRECTO, descansoSolo = false)
        val r = registroSesion(plan, listOf(Evento.Saltar(40 * min)))
        val foco2 = RelojSesion.enSesion(r, 41 * min)
        assertEquals(Foco(2, 25), foco2.actual)
        assertFalse(foco2.esperandoDescanso)
        assertEquals(24 * min, foco2.restaMillis)
        assertEquals(65 * min, foco2.finTramoEnSesion)
    }

    @Test
    fun `four default blocks give 1 h 40 min of focus`() {
        val fin = RelojSesion.enSesion(registroSesion(), 10 * 60 * min)
        assertNull(fin.actual)
        assertEquals(100 * min, fin.focoMillis)
        assertEquals(Texto(TextoMinutos.H_MIN, listOf(1, 40)), FormatoSesion.minutos(FormatoSesion.minutosDeFoco(fin.focoMillis)))
    }

    @Test
    fun `changing the wall clock mid-session does not move the timer`() {
        val r = registroSesion()
        val adelante = Ahora(INICIO_PARED + 10 * min + 60 * min, ANCLA_ELAPSED + 10 * min)
        assertEquals(10 * min, RelojSesion.transcurrido(r, adelante))
        assertEquals(10 * min, RelojSesion.en(r, adelante).transcurridoMillis)

        val atras = Ahora(INICIO_PARED + 10 * min - 3 * 60 * min, ANCLA_ELAPSED + 10 * min)
        assertEquals(10 * min, RelojSesion.transcurrido(r, atras))
    }

    @Test
    fun `after a reboot the wall clock decides`() {
        val r = registroSesion()
        val trasReiniciar = Ahora(INICIO_PARED + 28 * min, 30_000L)
        assertEquals(28 * min, RelojSesion.transcurrido(r, trasReiniciar))
        assertEquals(Descanso(1, false, 5), RelojSesion.en(r, trasReiniciar).actual)

        // Sesión empezada a los dos minutos de encender; tras reiniciar el elapsed ya pasó el ancla.
        val temprano = registroSesion(anclaElapsed = 2 * min)
        assertEquals(40 * min, RelojSesion.transcurrido(temprano, Ahora(INICIO_PARED + 40 * min, 5 * min)))
    }

    @Test
    fun `a forward jump larger than the uptime at start reads as a reboot without the counter`() {
        // Sesión empezada a los 2 min de encender; la hora de red adelanta una hora sin reiniciar.
        val r = registroSesion(anclaElapsed = 2 * min)
        val adelanto = Ahora(INICIO_PARED + 10 * min + 60 * min, 2 * min + 10 * min)
        assertEquals(70 * min, RelojSesion.transcurrido(r, adelanto))
    }

    @Test
    fun `with the boot counter a big forward jump keeps the same boot`() {
        val r = registroSesion(anclaElapsed = 2 * min, arranques = 7)
        val adelanto = Ahora(INICIO_PARED + 10 * min + 60 * min, 2 * min + 10 * min, arranques = 7)
        assertEquals(10 * min, RelojSesion.transcurrido(r, adelanto))
        assertEquals(10 * min, RelojSesion.en(r, adelanto).transcurridoMillis)
    }

    @Test
    fun `with the boot counter a reboot with the clock behind reads the wall clock`() {
        // Han pasado 30 min y el móvil se reinició sin red: la pared va 15 min atrasada y el
        // elapsed nuevo (20 min) ya pasó el ancla, así que la heurística lo tomaría por el mismo arranque.
        val sinContador = registroSesion(anclaElapsed = 2 * min)
        val trasReiniciar = Ahora(INICIO_PARED + 15 * min, 20 * min)
        assertEquals(18 * min, RelojSesion.transcurrido(sinContador, trasReiniciar))

        val conContador = registroSesion(anclaElapsed = 2 * min, arranques = 7)
        assertEquals(15 * min, RelojSesion.transcurrido(conContador, trasReiniciar.copy(arranques = 8)))
    }

    @Test
    fun `the counter only decides when both sides have it`() {
        val r = registroSesion(arranques = 7)
        val adelante = Ahora(INICIO_PARED + 10 * min + 60 * min, ANCLA_ELAPSED + 10 * min)
        assertEquals(10 * min, RelojSesion.transcurrido(r, adelante))
        val trasReiniciar = Ahora(INICIO_PARED + 28 * min, 30_000L)
        assertEquals(28 * min, RelojSesion.transcurrido(r, trasReiniciar))
        assertEquals(28 * min, RelojSesion.transcurrido(registroSesion(), trasReiniciar.copy(arranques = 8)))
    }

    @Test
    fun `elapsed time is never negative`() {
        val r = registroSesion()
        assertEquals(0L, RelojSesion.transcurrido(r, Ahora(INICIO_PARED - 5 * min, 1_000L)))
        assertEquals(0L, RelojSesion.enSesion(r, -1L).transcurridoMillis)
    }

    @Test
    fun `recovering after the break ended lands in the next block`() {
        val r = registroSesion(planSesion(entrada = Entrada.DIRECTO))
        // El proceso murió a mitad del primer descanso (26 min) y se vuelve a los 32.
        val vuelta = ahoraTras(32 * min)
        val i = RelojSesion.en(r, vuelta)
        assertEquals(2, i.tramo)
        assertEquals(Foco(2, 25), i.actual)
        assertEquals(23 * min, i.restaMillis)
        assertEquals(55 * min, i.finTramoEnSesion)
        assertEquals(INICIO_PARED + 55 * min, RelojSesion.paredDe(r, i.finTramoEnSesion!!, vuelta))
    }

    @Test
    fun `the wall time of a boundary follows the wall clock of now`() {
        val r = registroSesion()
        // Se adelantó la hora una hora: la alarma de pared tiene que ir una hora más tarde.
        val adelante = Ahora(INICIO_PARED + 10 * min + 60 * min, ANCLA_ELAPSED + 10 * min)
        assertEquals(INICIO_PARED + 60 * min + 26 * min, RelojSesion.paredDe(r, 26 * min, adelante))
    }

    @Test
    fun `breathing for entry is 60 s and at 7_5 s of Sleep it holds full`() {
        assertEquals(60_000L, BreathingRhythm.totalMillis(BreathingMode.CALM, 6))
        val r = registroSesion()
        val respirando = RelojSesion.enSesion(r, 25_000L)
        assertEquals(Tramo.Respirar(6), respirando.actual)
        assertEquals(60_000L, respirando.duracionTramoMillis)
        assertEquals(35_000L, respirando.restaMillis)
        val punto = respirando.respiracion!!
        assertEquals(3, punto.cycle)
        assertEquals(6, punto.totalCycles)
        assertEquals(BreathPhase.EXHALE, punto.phase)
        assertEquals(0L, respirando.focoMillis)
        assertNull(RelojSesion.enSesion(r, 60_000L).respiracion)

        val dormir = BreathingRhythm.at(BreathingMode.SLEEP, 7_500L, 6)
        assertEquals(BreathPhase.HOLD, dormir.phase)
        assertEquals(1f, dormir.fill, 1e-3f)
    }

    @Test
    fun `an ended session is quiet on the logical day it ended, not on the day it is read`() {
        val r = registroSesion()
        val fin = 7_860_000L
        val leida = RelojSesion.en(r, ahoraTras(fin + 100 * 60 * min))
        assertNull(leida.actual)

        val dia = RelojSesion.diaDeQuietud(r, leida, ahoraTras(fin + 100 * 60 * min), 0, ZoneOffset.UTC)

        assertEquals(LogicalDays.logicalDayOf(INICIO_PARED + fin, 0, ZoneOffset.UTC), dia)
    }

    @Test
    fun `a running session is not quiet`() {
        val r = registroSesion()
        val ahora = ahoraTras(10 * min)
        assertNull(RelojSesion.diaDeQuietud(r, RelojSesion.en(r, ahora), ahora, 0, ZoneOffset.UTC))
    }

    @Test
    fun `a paused session is quiet from its pause`() {
        val r = registroSesion(eventos = listOf(Evento.Pausa(10 * min)))
        val ahora = ahoraTras(3_000 * min)
        val inst = RelojSesion.en(r, ahora)
        assertTrue(inst.pausado)

        val dia = RelojSesion.diaDeQuietud(r, inst, ahora, 0, ZoneOffset.UTC)

        assertEquals(LogicalDays.logicalDayOf(INICIO_PARED + 10 * min, 0, ZoneOffset.UTC), dia)
    }
}
