package com.alvarotc.bito.domain.habla

import com.alvarotc.bito.domain.RealHabits
import com.alvarotc.bito.domain.TODAY
import com.alvarotc.bito.domain.domainState
import com.alvarotc.bito.domain.entryOn
import com.alvarotc.bito.domain.habla.reglas.ReglaAyerSeEscapo
import com.alvarotc.bito.domain.habla.reglas.ReglaGordoSinEmpezar
import com.alvarotc.bito.domain.propuestas.Ambito
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.propuestas.Respuesta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorTest {
    private val manana = 10 * 60
    private val noche = 21 * 60 + 45

    private fun minAntes(
        minutos: Int,
        ahoraMin: Int = manana,
    ): Long = ctx(ahoraMin = ahoraMin).ahoraMillis - minutos * 60_000L

    /** Un hábito que ayer se escapó: con las reglas reales sale `AYER_SE_ESCAPO` con hora a las 10:00. */
    private fun escapado(
        id: String = "h1",
        racha: Int = 0,
        tipo: TipoHabito = TipoHabito.CHECK,
        objetivo: Int = 1,
    ) = habito(id, tipo = tipo, objetivo = objetivo, racha = racha, falladoAyer = true, cumplidosTresAnteriores = 1)

    private fun elegir(
        h: Hechos,
        m: Memoria = Memoria(),
        ahoraMin: Int = manana,
        permisoAvisos: Boolean = true,
    ): Candidata = Motor.elegir(Ambito.HOY, h, m, ctx(ahoraMin = ahoraMin, permisoAvisos = permisoAvisos))!!

    private fun cuatroMostradas(n: Int = 4) = List(n) { mostrada(Situacion.TAREA_VENCE, minAntes(300 - it)) }

    private fun aviso(
        id: String,
        dia: Int = TODAY,
    ) = AvisoAceptado(id, dia, "hx", 18 * 60, 0, EstadoAviso.PENDIENTE, "p-$id", minAntes(200))

    /** Una regla de prueba que propone ese hábito, con hora y con «Vale». */
    private fun reglaQuePropone(id: String) =
        Regla { _, _, _ ->
            listOf(
                Candidata(
                    situacion = Situacion.AYER_SE_ESCAPO,
                    ref = id,
                    huecos = mapOf(Hueco.HABITO to ValorHueco.Texto(id)),
                    acciones = listOf(Respuesta.VALE, Respuesta.HOY_NO),
                    destino = Destino.Habito(id),
                    hora = 18 * 60,
                ),
            )
        }

    @Test
    fun `coming back wins over yesterday slipped`() {
        val h = hechos(habitos = listOf(escapado()), primerDiaTrasAusencia = true, diasSinActividad = 2)
        assertTrue(ReglaAyerSeEscapo.candidatas(h, Memoria(), ctx()).isNotEmpty())
        assertEquals(Situacion.VUELTA, elegir(h).situacion)
    }

    @Test
    fun `closing the day wins over the big thing not started`() {
        val h =
            hechos(
                habitos = listOf(habito()),
                tareas = listOf(tarea("t1")),
                gordo = listOf(RefGordo(TipoRefGordo.TAREA, "t1")),
                pendientesCierre = 2,
            )
        assertTrue(ReglaGordoSinEmpezar.candidatas(h, Memoria(), ctx(ahoraMin = noche)).isNotEmpty())
        assertEquals(Situacion.CERRAR_DIA, elegir(h, ahoraMin = noche).situacion)
    }

    @Test
    fun `inside a situation the heaviest wins and a tie goes to the ref`() {
        assertEquals("b", elegir(hechos(habitos = listOf(escapado("a", racha = 2), escapado("b", racha = 9)))).ref)
        assertEquals("a", elegir(hechos(habitos = listOf(escapado("b"), escapado("a")))).ref)
    }

    @Test
    fun `the fifth proposal of the day is a greeting`() {
        val h = hechos(habitos = listOf(escapado()))
        assertEquals(Situacion.AYER_SE_ESCAPO, elegir(h, Memoria(mostradasHoy = cuatroMostradas(3))).situacion)
        assertEquals(Situacion.SALUDO, elegir(h, Memoria(mostradasHoy = cuatroMostradas(4))).situacion)
    }

    @Test
    fun `the greeting at the cap keeps its name and count`() {
        val c = elegir(hechos(habitos = listOf(escapado())), Memoria(mostradasHoy = cuatroMostradas(4)))
        assertEquals(ValorHueco.Texto("Álvaro"), c.huecos[Hueco.NOMBRE])
        assertEquals(ValorHueco.Numero(1), c.huecos[Hueco.TOTAL])
    }

    @Test
    fun `proposals that do not count leave the cap alone and still come out at the cap`() {
        val h = hechos(habitos = listOf(escapado()), pendientesCierre = 1)
        val exentas = List(4) { mostrada(Situacion.CERRAR_DIA, minAntes(300 - it)) }
        assertEquals(Situacion.AYER_SE_ESCAPO, elegir(h, Memoria(mostradasHoy = exentas)).situacion)
        val tope = Memoria(mostradasHoy = List(4) { mostrada(Situacion.TAREA_VENCE, minAntes(300 - it, noche)) })
        assertEquals(Situacion.CERRAR_DIA, elegir(h, tope, ahoraMin = noche).situacion)
    }

    @Test
    fun `ninety minutes after answering nothing with buttons is proposed`() {
        val h = hechos(habitos = listOf(escapado()))
        val hace89 = Memoria(eventos = listOf(respuesta(Situacion.RACHA_CERCA, "otro", Respuesta.AHORA, minAntes(89))))
        val hace90 = Memoria(eventos = listOf(respuesta(Situacion.RACHA_CERCA, "otro", Respuesta.AHORA, minAntes(90))))
        assertEquals(Situacion.SALUDO, elegir(h, hace89).situacion)
        assertEquals(Situacion.AYER_SE_ESCAPO, elegir(h, hace90).situacion)
    }

    @Test
    fun `the wait does not hold back closing the day and Done does not start it`() {
        val h = hechos(habitos = listOf(escapado()), pendientesCierre = 1)
        val vale = Memoria(eventos = listOf(respuesta(Situacion.RACHA_CERCA, "otro", Respuesta.VALE, minAntes(10, noche))))
        assertEquals(Situacion.CERRAR_DIA, elegir(h, vale, ahoraMin = noche).situacion)
        val hecho = Memoria(eventos = listOf(respuesta(Situacion.VUELTA, "otro", Respuesta.HECHO, minAntes(10))))
        assertEquals(Situacion.AYER_SE_ESCAPO, elegir(h, hecho).situacion)
    }

    @Test
    fun `after answering, a proposal done from the list lets the next one come`() {
        val vale = Memoria(eventos = listOf(respuesta(Situacion.RACHA_CERCA, "h2", Respuesta.VALE, minAntes(10))))
        val hecho = hechos(habitos = listOf(escapado(), habito("h2", hechoHoy = true)))
        val sinHacer = hechos(habitos = listOf(escapado(), habito("h2")))
        assertEquals(Situacion.AYER_SE_ESCAPO, elegir(hecho, vale).situacion)
        assertEquals(Situacion.SALUDO, elegir(sinHacer, vale).situacion)
    }

    @Test
    fun `a task done from the list also lets the next one come`() {
        val vale = Memoria(eventos = listOf(respuesta(Situacion.TAREA_VENCE, "t1", Respuesta.AHORA, minAntes(10))))
        val h = hechos(habitos = listOf(escapado()), tareas = listOf(tarea("t1", hecha = true)))
        assertEquals(Situacion.AYER_SE_ESCAPO, elegir(h, vale).situacion)
    }

    @Test
    fun `today-no discards that situation and ref until tomorrow`() {
        val hoyNo = Memoria(eventos = listOf(respuesta(Situacion.AYER_SE_ESCAPO, "h1", Respuesta.HOY_NO, minAntes(180))))
        assertEquals(Situacion.SALUDO, elegir(hechos(habitos = listOf(escapado())), hoyNo).situacion)
        assertEquals("h2", elegir(hechos(habitos = listOf(escapado(), escapado("h2"))), hoyNo).ref)
        val ayer =
            Memoria(
                eventos = listOf(respuesta(Situacion.AYER_SE_ESCAPO, "h1", Respuesta.HOY_NO, minAntes(24 * 60), dia = TODAY - 1)),
            )
        assertEquals("h1", elegir(hechos(habitos = listOf(escapado())), ayer).ref)
    }

    @Test
    fun `later lets it come back after the wait`() {
        val h = hechos(habitos = listOf(escapado()))
        val reciente = Memoria(eventos = listOf(respuesta(Situacion.AYER_SE_ESCAPO, "h1", Respuesta.LUEGO, minAntes(30))))
        val pasada = Memoria(eventos = listOf(respuesta(Situacion.AYER_SE_ESCAPO, "h1", Respuesta.LUEGO, minAntes(90))))
        assertEquals(Situacion.SALUDO, elegir(h, reciente).situacion)
        assertEquals("h1", elegir(h, pasada).ref)
    }

    @Test
    fun `not yet holds back everything done for the wait`() {
        val h = hechos(habitos = listOf(habito(hechoHoy = true)))
        val hace89 = Memoria(eventos = listOf(respuesta(Situacion.TODO_HECHO, null, Respuesta.LUEGO, minAntes(89))))
        val hace90 = Memoria(eventos = listOf(respuesta(Situacion.TODO_HECHO, null, Respuesta.LUEGO, minAntes(90))))
        assertEquals(Situacion.TODO_HECHO, elegir(h).situacion)
        assertEquals(Situacion.SALUDO, elegir(h, hace89).situacion)
        assertEquals(Situacion.TODO_HECHO, elegir(h, hace90).situacion)
    }

    @Test
    fun `not yet holds back closing the day for the wait`() {
        val h = hechos(habitos = listOf(habito()), pendientesCierre = 1)
        val hace89 = Memoria(eventos = listOf(respuesta(Situacion.CERRAR_DIA, null, Respuesta.LUEGO, minAntes(89, noche))))
        val hace90 = Memoria(eventos = listOf(respuesta(Situacion.CERRAR_DIA, null, Respuesta.LUEGO, minAntes(90, noche))))
        assertEquals(Situacion.SALUDO, elegir(h, hace89, ahoraMin = noche).situacion)
        assertEquals(Situacion.CERRAR_DIA, elegir(h, hace90, ahoraMin = noche).situacion)
    }

    @Test
    fun `not yet on everything done does not hold back closing the day`() {
        val h = hechos(habitos = listOf(habito()), pendientesCierre = 1)
        val luego = Memoria(eventos = listOf(respuesta(Situacion.TODO_HECHO, null, Respuesta.LUEGO, minAntes(10, noche))))
        assertEquals(Situacion.CERRAR_DIA, elegir(h, luego, ahoraMin = noche).situacion)
    }

    @Test
    fun `two accepted notices remove the hour`() {
        val h = hechos(habitos = listOf(escapado()))
        val uno = elegir(h, Memoria(avisos = listOf(aviso("a"))))
        assertEquals(14 * 60 + 30, uno.hora)
        assertEquals(listOf(Respuesta.VALE, Respuesta.AHORA, Respuesta.HOY_NO), uno.acciones)
        val dos = elegir(h, Memoria(avisos = listOf(aviso("a"), aviso("b"))))
        assertEquals(Situacion.AYER_SE_ESCAPO, dos.situacion)
        assertNull(dos.hora)
        assertNull(dos.huecos[Hueco.HORA])
        assertEquals(listOf(Respuesta.AHORA, Respuesta.HOY_NO), dos.acciones)
    }

    @Test
    fun `a cancelled accepted notice does not count for the cap of two`() {
        // «Vale» y «Otra hora»: el primero queda CANCELADO y solo suena uno (PlanAvisos.cuentaParaElTope).
        val h = hechos(habitos = listOf(escapado()))
        val conCancelado = Memoria(avisos = listOf(aviso("a").copy(estado = EstadoAviso.CANCELADO), aviso("b")))
        val c = elegir(h, conCancelado)
        assertEquals(14 * 60 + 30, c.hora)
        assertEquals(listOf(Respuesta.VALE, Respuesta.AHORA, Respuesta.HOY_NO), c.acciones)
    }

    @Test
    fun `with two accepted notices an offer with only Vale gets Now instead`() {
        val dos = Memoria(avisos = listOf(aviso("a"), aviso("b")))
        val reglas = mapOf(Situacion.AYER_SE_ESCAPO to reglaQuePropone("h1"))
        val c = Motor.elegirHoy(hechos(habitos = listOf(habito("h1"))), dos, ctx(), reglas)
        assertEquals(listOf(Respuesta.AHORA, Respuesta.HOY_NO), c.acciones)
        assertNull(c.hora)
    }

    @Test
    fun `notices from yesterday and the permission offer are left alone`() {
        val h = hechos(habitos = listOf(escapado()))
        val deAyer = Memoria(avisos = listOf(aviso("a", TODAY - 1), aviso("b", TODAY - 1)))
        assertEquals(14 * 60 + 30, elegir(h, deAyer).hora)
        val permiso = elegir(h, Memoria(avisos = listOf(aviso("a"), aviso("b"))), permisoAvisos = false)
        assertEquals(Situacion.PERMISO_AVISOS, permiso.situacion)
        assertTrue(Respuesta.VALE in permiso.acciones)
    }

    @Test
    fun `a hard day proposes at most two`() {
        val h = hechos(habitos = listOf(escapado()), animoAyer = 2)
        val una = Memoria(mostradasHoy = cuatroMostradas(1))
        val dos = Memoria(mostradasHoy = cuatroMostradas(2))
        assertTrue(elegir(h, una).situacion.cuentaTope)
        assertEquals(Situacion.SALUDO, elegir(h, dos).situacion)
    }

    @Test
    fun `a hard day never offers choosing the big thing`() {
        val gordo =
            Regla { _, _, _ ->
                listOf(Candidata(Situacion.ELEGIR_GORDO, null, emptyMap(), listOf(Respuesta.ELEGIR_GORDO, Respuesta.HOY_NO)))
            }
        val reglas = mapOf(Situacion.ELEGIR_GORDO to gordo)
        assertEquals(Situacion.ELEGIR_GORDO, Motor.elegirHoy(hechos(), Memoria(), ctx(), reglas).situacion)
        assertEquals(Situacion.SALUDO, Motor.elegirHoy(hechos(animoAyer = 2), Memoria(), ctx(), reglas).situacion)
    }

    @Test
    fun `regular yesterday makes the first proposal the shortest`() {
        val largo = escapado("largo", racha = 9, tipo = TipoHabito.CONTADOR, objetivo = 20)
        val habitos = listOf(largo, escapado("corto", tipo = TipoHabito.CONTADOR, objetivo = 5))
        val primera = elegir(hechos(habitos = habitos, animoAyer = 1))
        assertEquals("corto", primera.ref)
        assertEquals(Bolsa.NU_REGULAR, primera.nucleo)
        val segunda = elegir(hechos(habitos = habitos, animoAyer = 1), Memoria(mostradasHoy = cuatroMostradas(1)))
        assertEquals("largo", segunda.ref)
        assertEquals(Bolsa.NU_AYER_ESCAPO, segunda.nucleo)
        val bien = elegir(hechos(habitos = habitos, animoAyer = 0))
        assertEquals("largo", bien.ref)
        assertEquals(Bolsa.NU_AYER_ESCAPO, bien.nucleo)
    }

    @Test
    fun `regular yesterday leaves a task proposal as it is`() {
        val h = hechos(habitos = listOf(habito("h9")), tareas = listOf(tarea("t1", venceHoy = true)), animoAyer = 1)
        val c = elegir(h)
        assertEquals(Situacion.TAREA_VENCE, c.situacion)
        assertEquals(Bolsa.NU_TAREA_VENCE, c.nucleo)
    }

    @Test
    fun `regular yesterday leaves the big thing as it is even when it is a habit`() {
        val h = hechos(habitos = listOf(habito("h1")), gordo = listOf(RefGordo(TipoRefGordo.HABITO, "h1")), animoAyer = 1)
        val c = elegir(h, ahoraMin = 13 * 60)
        assertEquals(Situacion.GORDO_SIN_EMPEZAR, c.situacion)
        assertEquals(Bolsa.NU_GORDO_SIN_EMPEZAR, c.nucleo)
    }

    @Test
    fun `quit and limit habits are never proposed`() {
        val reglas = mapOf(Situacion.AYER_SE_ESCAPO to reglaQuePropone("x"))
        for (tipo in listOf(TipoHabito.DEJAR, TipoHabito.LIMITE)) {
            val h = hechos(habitos = listOf(habito("x", tipo = tipo)))
            assertEquals(Situacion.SALUDO, Motor.elegirHoy(h, Memoria(), ctx(), reglas).situacion)
        }
        val check = hechos(habitos = listOf(habito("x", tipo = TipoHabito.CHECK)))
        assertEquals("x", Motor.elegirHoy(check, Memoria(), ctx(), reglas).ref)
    }

    @Test
    fun `a sealed day says good night`() {
        val c = elegir(hechos(habitos = listOf(escapado()), diaSellado = true), ahoraMin = noche)
        assertEquals(Situacion.NOCHE, c.situacion)
        assertTrue(c.acciones.isEmpty())
        assertFalse(c.situacion.conBotones)
    }

    // I1: un hábito con un «Vale, a las…» pendiente hoy no vuelve a recibir oferta de hora, ni en Hoy ni en su ficha.

    private val leer = RealHabits.readBeforeSleep

    /** Leer se escapó ayer tras cumplirse los tres días anteriores: «ayer se escapó» con hora a las 10:00. */
    private val escapado = domainState(habits = listOf(leer), entries = (2..4).map { entryOn(leer, TODAY - it) })

    private fun avisoDe(
        habitId: String,
        estado: EstadoAviso = EstadoAviso.PENDIENTE,
        dia: Int = TODAY,
    ) = AvisoAceptado("a:$habitId:$dia:$estado", dia, habitId, 14 * 60 + 30, 0, estado, "p", 1L)

    private fun hoyCon(m: Memoria): Candidata {
        val c = ctx()
        return Motor.elegir(Ambito.HOY, Hechos.de(escapado, m, c, emptyMap()), m, c)!!
    }

    private fun fichaCon(m: Memoria): Candidata? {
        val c = ctx()
        return Motor.elegir(Ambito.FICHA, Hechos.de(escapado, m, c, emptyMap()), m, c, leer.id)
    }

    @Test
    fun `a habit with a pending accepted notice today gets no hour in Hoy`() {
        val sinAviso = hoyCon(Memoria())
        assertEquals(Situacion.AYER_SE_ESCAPO, sinAviso.situacion)
        assertTrue(sinAviso.hora != null && Respuesta.VALE in sinAviso.acciones)

        val conAviso = hoyCon(Memoria(avisos = listOf(avisoDe(leer.id))))

        assertEquals(Situacion.AYER_SE_ESCAPO, conAviso.situacion)
        assertEquals(leer.id, conAviso.ref)
        assertNull(conAviso.hora)
        assertFalse(Respuesta.VALE in conAviso.acciones)
        assertFalse(Hueco.HORA in conAviso.huecos)
    }

    @Test
    fun `a habit with a pending accepted notice today has no today-missing note in its detail`() {
        assertEquals(Situacion.FI_HOY_FALTA, fichaCon(Memoria())?.situacion)

        val conAviso = fichaCon(Memoria(avisos = listOf(avisoDe(leer.id))))

        assertTrue(conAviso?.situacion != Situacion.FI_HOY_FALTA)
    }

    @Test
    fun `a fired, cancelled, old or other habit notice keeps the hour`() {
        for (aviso in listOf(
            avisoDe(leer.id, EstadoAviso.LANZADO),
            avisoDe(leer.id, EstadoAviso.CANCELADO),
            avisoDe(leer.id, dia = TODAY - 1),
            avisoDe("otro"),
        )) {
            val m = Memoria(avisos = listOf(aviso))
            assertTrue("$aviso", hoyCon(m).hora != null)
            assertEquals("$aviso", Situacion.FI_HOY_FALTA, fichaCon(m)?.situacion)
        }
    }
}
