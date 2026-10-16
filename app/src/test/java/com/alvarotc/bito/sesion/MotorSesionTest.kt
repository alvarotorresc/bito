package com.alvarotc.bito.sesion

import android.app.Application
import android.app.NotificationManager
import com.alvarotc.bito.data.habitEntity
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.model.LogMode
import com.alvarotc.bito.domain.model.Metric
import com.alvarotc.bito.domain.model.PointsReason
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.model.TaskStatus
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.RelojSesion
import com.alvarotc.bito.domain.sesion.ResultadoSesion
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.ui.sistema.Haptica
import com.alvarotc.bito.ui.sistema.sonido.Snd
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** El motor de la sesión: acciones, efectos, transiciones y recuperación (spec §4.1, §7, §10, §11.1, §15). */
@RunWith(RobolectricTestRunner::class)
// Application a secas: BitoApp arrancaría AppStartup en cada test y sus colectores acapararían los hilos compartidos.
@Config(sdk = [35], application = Application::class)
class MotorSesionTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoSesion
    private lateinit var g: Grabadores
    private lateinit var motor: MotorSesionAndroid

    @Before
    fun setUp() {
        banco = BancoSesion(tmp.root)
        g = Grabadores()
        motor = banco.motor(g)
        banco.permisoNoMolestar(false)
        banco.ponerFiltro(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    @After
    fun tearDown() = banco.cerrar()

    private suspend fun directo() = banco.settings.update { it.copy(sessionEntry = Entrada.DIRECTO) }

    /** Una tarea «t1» y su sesión ya empezada desde Preparar. */
    private suspend fun empezarTarea(
        id: String = "t1",
        crear: Boolean = true,
    ) {
        if (crear) banco.tasks.create(taskEntity(id = id))
        motor.preparar(Destino.Tarea(id))
        assertNull(motor.empezar())
    }

    private fun limpiarGrabaciones() {
        g.sonido.sonidos.clear()
        g.tacto.hapticas.clear()
        g.aire.llamadas.clear()
        g.fondo.llamadas.clear()
        g.fuera.cambios.clear()
    }

    private suspend fun avanzarYTic(ms: Long) {
        banco.reloj.avanzar(ms)
        motor.tic()
    }

    @Test
    fun `start with and without conflict`() =
        runTest {
            empezarTarea("t1")
            val r = assertNotNullAnd(motor.registro.value)
            assertEquals(Objetivo.Tarea("t1"), r.plan.objetivo)
            assertEquals(r, banco.store.leer())
            assertTrue(motor.instante.value?.actual is Tramo.Respirar)
            assertNull(motor.borrador.value)
            assertEquals(listOf("respirar:CALM:6"), g.aire.llamadas)
            assertEquals(listOf(Snd.BROTE), g.sonido.sonidos)
            assertEquals(listOf(Haptica.CONFIRM), g.tacto.hapticas)
            assertEquals(r, g.fuera.cambios.last())

            banco.tasks.create(taskEntity(id = "t2", title = "Otra"))
            motor.preparar(Destino.Tarea("t2"))
            val conflicto = motor.empezar()
            assertEquals(Conflicto.OtraEnMarcha(Objetivo.Tarea("t1"), 60_000L, 25), conflicto)
            assertEquals(r.id, banco.store.leer()?.id)
        }

    @Test
    fun `a direct start plays the background instead of breathing`() =
        runTest {
            directo()
            empezarTarea()
            assertTrue(motor.instante.value?.actual is Tramo.Foco)
            assertEquals(listOf("sonar:RAIN"), g.fondo.llamadas)
            assertTrue(g.aire.llamadas.isEmpty())
        }

    @Test
    fun `same target returns to the live one`() =
        runTest {
            empezarTarea("t1")
            val id = motor.registro.value?.id
            banco.reloj.avanzar(5 * MINUTO)
            motor.preparar(Destino.Tarea("t1"))
            assertNull(motor.empezar())
            assertEquals(id, motor.registro.value?.id)
            assertEquals(id, banco.store.leer()?.id)
            assertNull(motor.borrador.value)
            assertEquals(listOf(Snd.BROTE), g.sonido.sonidos)
        }

    @Test
    fun `leave it and start this one records an attempt with its minutes`() =
        runTest {
            directo()
            empezarTarea("t1")
            val vieja = motor.registro.value!!.id
            banco.reloj.avanzar(10 * MINUTO)
            banco.tasks.create(taskEntity(id = "t2", title = "Otra"))
            motor.preparar(Destino.Tarea("t2"))
            assertNotNull(motor.empezar())

            val resultado = motor.dejarlaYEmpezar()

            assertEquals(AvisoSesion.ComoIntento("Llamar al banco"), resultado.aviso)
            assertEquals(0, resultado.bitos)
            val intentos = banco.db.taskEventDao().all().filter { it.kind == TaskEventKind.ATTEMPT }
            assertEquals(listOf("t1"), intentos.map { it.taskId })
            val fila = banco.db.sesionDao().all().single()
            assertEquals(vieja, fila.id)
            assertEquals(10, fila.minutosFoco)
            assertEquals(ResultadoSesion.CAMBIADA.name, fila.resultado)
            assertEquals(Objetivo.Tarea("t2"), banco.store.leer()?.plan?.objetivo)
            assertEquals(TaskStatus.OPEN, banco.tasks.task("t1")?.status)
        }

    @Test
    fun `finishing marks reconciles once and writes sesiones`() =
        runTest {
            directo()
            empezarTarea()
            banco.reloj.avanzar(20 * MINUTO)
            limpiarGrabaciones()

            val resultado = motor.heTerminado()

            assertEquals(3, resultado.bitos)
            assertEquals(AvisoSesion.Hecho("Llamar al banco"), resultado.aviso)
            assertEquals(TaskStatus.DONE, banco.tasks.task("t1")?.status)
            val pagos = banco.domainState.snapshot().pointsLedger.filter { it.reason == PointsReason.TASK_DONE }
            assertEquals(listOf(3), pagos.map { it.delta })
            val fila = banco.db.sesionDao().all().single()
            assertEquals(20, fila.minutosFoco)
            assertEquals(0, fila.bloquesHechos)
            assertEquals(4, fila.bloquesPlan)
            assertEquals(ResultadoSesion.HECHA.name, fila.resultado)
            assertNull(banco.store.leer())
            assertNull(motor.registro.value)
            assertNull(motor.instante.value)
            assertEquals(listOf(Snd.ASENTAR), g.sonido.sonidos)
            assertEquals(listOf(Haptica.FALL), g.tacto.hapticas)
            assertEquals(listOf("parar"), g.aire.llamadas)
            assertEquals(listOf("callar:seco"), g.fondo.llamadas)
            assertEquals(listOf(null), g.fuera.cambios)
        }

    @Test
    fun `finishing twice marks and pays once`() =
        runTest {
            directo()
            empezarTarea()
            banco.reloj.avanzar(20 * MINUTO)
            limpiarGrabaciones()

            val a = async(banco.hilos) { motor.heTerminado() }
            val b = async(banco.hilos) { motor.heTerminado() }
            val resultados = listOf(a.await(), b.await())

            assertEquals(listOf(0, 3), resultados.map { it.bitos }.sorted())
            assertEquals(1, resultados.count { it.aviso != null })
            assertEquals(1, banco.domainState.snapshot().pointsLedger.count { it.reason == PointsReason.TASK_DONE })
            assertEquals(1, banco.db.sesionDao().all().size)
            assertEquals(listOf(Snd.ASENTAR), g.sonido.sonidos)
            assertEquals(listOf(null), g.fuera.cambios)
        }

    @Test
    fun `drop it and later record an attempt and pay nothing`() =
        runTest {
            directo()
            empezarTarea()
            banco.reloj.avanzar(12 * MINUTO)
            limpiarGrabaciones()

            val dejo = motor.loDejo()

            assertEquals(AvisoSesion.LoDejamos(12), dejo.aviso)
            assertEquals(0, dejo.bitos)
            assertEquals(listOf(Haptica.LOW), g.tacto.hapticas)
            assertEquals(ResultadoSesion.LO_DEJO.name, banco.db.sesionDao().all().single().resultado)

            // «Sigo luego» desde Terminada: una sesión de un bloque que ya acabó.
            motor.preparar(Destino.Tarea("t1"))
            motor.cambiarBorrador(motor.borrador.value!!.copy(bloques = 1, minutosFoco = 5))
            assertNull(motor.empezar())
            avanzarYTic(5 * MINUTO)
            assertNull(motor.instante.value?.actual)
            val luego = motor.sigoLuego()

            assertEquals(AvisoSesion.QuedaParaLuego, luego.aviso)
            assertEquals(0, luego.bitos)
            assertEquals(2, banco.db.taskEventDao().all().count { it.kind == TaskEventKind.ATTEMPT })
            assertTrue(banco.domainState.snapshot().pointsLedger.isEmpty())
            assertEquals(TaskStatus.OPEN, banco.tasks.task("t1")?.status)
            assertEquals(
                listOf(ResultadoSesion.LO_DEJO.name, ResultadoSesion.SIGO_LUEGO.name),
                banco.db.sesionDao().all().map { it.resultado }.sorted(),
            )
        }

    @Test
    fun `dropping it before a minute says another time and writes no row`() =
        runTest {
            directo()
            empezarTarea()
            banco.reloj.avanzar(40_000L)
            assertEquals(AvisoSesion.LoDejamos(0), motor.loDejo().aviso)
            assertTrue(banco.db.sesionDao().all().isEmpty())
        }

    @Test
    fun `another five starts direct to focus`() =
        runTest {
            banco.tasks.create(taskEntity(id = "t1"))
            motor.preparar(Destino.Tarea("t1"))
            motor.cambiarBorrador(motor.borrador.value!!.copy(bloques = 1, fondo = SessionBackground.CAFE))
            assertNull(motor.empezar())
            avanzarYTic(60_000L)
            avanzarYTic(25 * MINUTO)
            assertNull(motor.instante.value?.actual)
            val vieja = motor.registro.value!!.id

            motor.otraDeCinco()

            val r = motor.registro.value!!
            assertTrue(r.id != vieja)
            assertEquals(Objetivo.Tarea("t1"), r.plan.objetivo)
            assertEquals(5, r.plan.minutosFoco)
            assertEquals(1, r.plan.bloques)
            assertEquals(Entrada.DIRECTO, r.plan.entrada)
            assertEquals(SessionBackground.CAFE, r.plan.fondo)
            assertEquals(Tramo.Foco(1, 5), motor.instante.value?.actual)
            assertEquals(ResultadoSesion.SIN_RESPUESTA.name, banco.db.sesionDao().all().single().resultado)
        }

    @Test
    fun `a day change closes as no answer without notice`() =
        runTest {
            directo()
            banco.tasks.create(taskEntity(id = "t1"))
            motor.preparar(Destino.Tarea("t1"))
            motor.cambiarBorrador(motor.borrador.value!!.copy(bloques = 1))
            assertNull(motor.empezar())
            banco.reloj.avanzar(25 * MINUTO)
            val nuevo = Grabadores()
            val otro = banco.motor(nuevo)
            banco.reloj.avanzar(24 * 60 * MINUTO)

            otro.recuperar()

            assertNull(banco.store.leer())
            assertNull(otro.registro.value)
            assertNull(otro.instante.value)
            val fila = banco.db.sesionDao().all().single()
            assertEquals(ResultadoSesion.SIN_RESPUESTA.name, fila.resultado)
            assertEquals(25, fila.minutosFoco)
            assertEquals(1, fila.bloquesHechos)
            assertEquals(listOf("t1"), banco.db.taskEventDao().all().filter { it.kind == TaskEventKind.ATTEMPT }.map { it.taskId })
            assertTrue(nuevo.sonido.sonidos.isEmpty())
            assertTrue(nuevo.tacto.hapticas.isEmpty())
            assertEquals(listOf(null), nuevo.fuera.cambios)
        }

    @Test
    fun `a finished session the same day waits for an answer`() =
        runTest {
            directo()
            banco.tasks.create(taskEntity(id = "t1"))
            motor.preparar(Destino.Tarea("t1"))
            motor.cambiarBorrador(motor.borrador.value!!.copy(bloques = 1))
            assertNull(motor.empezar())
            banco.reloj.avanzar(30 * MINUTO)
            val otro = banco.motor()
            otro.recuperar()
            assertNotNull(banco.store.leer())
            assertNull(otro.instante.value?.actual)
            assertNotNull(otro.registro.value)
        }

    @Test
    fun `each transition plays its sound and haptic once`() =
        runTest {
            banco.settings.update { it.copy(sessionBackground = SessionBackground.BROWN) }
            empezarTarea()
            limpiarGrabaciones()

            avanzarYTic(60_000L)
            motor.tic()
            assertEquals(listOf(Snd.ALEJAR), g.sonido.sonidos)
            assertEquals(listOf(Haptica.TICK), g.tacto.hapticas)
            assertEquals(listOf("parar"), g.aire.llamadas)
            assertEquals(listOf("sonar:BROWN"), g.fondo.llamadas)

            avanzarYTic(25 * MINUTO)
            motor.tic()
            avanzarYTic(5 * MINUTO)
            motor.tic()
            for (i in 2..3) {
                avanzarYTic(25 * MINUTO)
                avanzarYTic(5 * MINUTO)
            }
            avanzarYTic(25 * MINUTO)
            assertEquals(Tramo.Descanso(4, largo = true, minutos = 15), motor.instante.value?.actual)
            avanzarYTic(15 * MINUTO)
            motor.tic()
            assertNull(motor.instante.value?.actual)

            assertEquals(
                listOf(Snd.ALEJAR) + List(3) { listOf(Snd.CAMPANILLA, Snd.BROTE) }.flatten() + listOf(Snd.CAMPANILLA, Snd.CAMPANILLA),
                g.sonido.sonidos,
            )
            assertEquals(
                listOf(Haptica.TICK) + List(3) { listOf(Haptica.CLICK, Haptica.RISE) }.flatten() + listOf(Haptica.CLICK, Haptica.CONFIRM),
                g.tacto.hapticas,
            )
            assertEquals(
                listOf("sonar:BROWN") + List(3) { listOf("callar", "sonar:BROWN") }.flatten() + listOf("callar", "callar"),
                g.fondo.llamadas,
            )
            // Cada frontera re-arma lo de fuera: 9 fronteras, 9 avisos.
            assertEquals(9, g.fuera.cambios.size)
            assertTrue(g.fuera.cambios.all { it != null })
        }

    @Test
    fun `skipping the break plays the return with a tick`() =
        runTest {
            directo()
            empezarTarea()
            avanzarYTic(25 * MINUTO)
            limpiarGrabaciones()
            motor.saltar()
            assertEquals(Tramo.Foco(2, 25), motor.instante.value?.actual)
            assertEquals(listOf(Snd.BROTE), g.sonido.sonidos)
            assertEquals(listOf(Haptica.TICK), g.tacto.hapticas)
            motor.tic()
            assertEquals(listOf(Snd.BROTE), g.sonido.sonidos)
        }

    @Test
    fun `pause resume and add only act in focus`() =
        runTest {
            empezarTarea()
            motor.pausar()
            motor.sumar(5)
            assertTrue(motor.registro.value!!.eventos.isEmpty())
            avanzarYTic(60_000L)
            limpiarGrabaciones()

            motor.pausar()
            banco.reloj.avanzar(10 * MINUTO)
            motor.tic()
            assertTrue(motor.instante.value!!.pausado)
            assertEquals(25 * MINUTO, motor.instante.value!!.restaMillis)
            motor.seguir()
            motor.sumar(15)
            assertEquals(40 * MINUTO, motor.instante.value!!.restaMillis)

            assertEquals(listOf(Snd.INTERRUPTOR_OFF, Snd.INTERRUPTOR_ON, Snd.TIC), g.sonido.sonidos)
            assertEquals(listOf(Haptica.TICK, Haptica.TICK, Haptica.SEG), g.tacto.hapticas)
            assertEquals(listOf("callar", "sonar:RAIN"), g.fondo.llamadas)
            assertEquals(3, banco.store.leer()!!.eventos.size)
        }

    @Test
    fun `dnd is set at start and restored at the end`() =
        runTest {
            banco.permisoNoMolestar(true)
            banco.settings.update { it.copy(sessionDnd = true, sessionEntry = Entrada.DIRECTO) }
            empezarTarea()
            assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, banco.filtro())
            assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, banco.store.leer()?.filtroPrevio)
            assertTrue(banco.noMolestar.activo(banco.store.leer()?.filtroPrevio))

            motor.heTerminado()

            assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, banco.filtro())
        }

    @Test
    fun `dnd off in the plan leaves the filter alone`() =
        runTest {
            banco.permisoNoMolestar(true)
            directo()
            empezarTarea()
            assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, banco.filtro())
            assertNull(banco.store.leer()?.filtroPrevio)
        }

    @Test
    fun `a filter changed by hand is not restored`() =
        runTest {
            banco.permisoNoMolestar(true)
            banco.settings.update { it.copy(sessionDnd = true, sessionEntry = Entrada.DIRECTO) }
            empezarTarea()
            banco.ponerFiltro(NotificationManager.INTERRUPTION_FILTER_ALARMS)

            motor.loDejo()

            assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, banco.filtro())
        }

    @Test
    fun `revoked permission ends without crashing`() =
        runTest {
            val filtro = FiltroRetirable()
            val otro = BancoSesion(tmp.newFolder(), NoMolestar(filtro))
            try {
                otro.settings.update { it.copy(sessionDnd = true, sessionEntry = Entrada.DIRECTO) }
                otro.tasks.create(taskEntity(id = "t1"))
                val m = otro.motor()
                m.preparar(Destino.Tarea("t1"))
                assertNull(m.empezar())
                val previo = otro.store.leer()?.filtroPrevio
                assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, previo)
                assertTrue(otro.noMolestar.activo(previo))

                filtro.retirado = true
                otro.reloj.avanzar(3 * MINUTO)
                // La etiqueta desaparece: con el permiso retirado ya no está activo.
                assertFalse(otro.noMolestar.activo(previo))

                val resultado = m.loDejo()

                assertEquals(AvisoSesion.LoDejamos(3), resultado.aviso)
                assertNull(otro.store.leer())
                assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, filtro.filtro)
            } finally {
                otro.cerrar()
            }
        }

    @Test
    fun `a failing dnd at start still starts the session without touching it`() =
        runTest {
            val filtro = FiltroRetirable().apply { fallaAlPoner = true }
            val otro = BancoSesion(tmp.newFolder(), NoMolestar(filtro))
            try {
                otro.settings.update { it.copy(sessionDnd = true, sessionEntry = Entrada.DIRECTO) }
                otro.tasks.create(taskEntity(id = "t1"))
                val m = otro.motor()
                m.preparar(Destino.Tarea("t1"))
                assertTrue(m.borrador.value!!.noMolestar)

                assertNull(m.empezar())

                assertTrue(m.instante.value?.actual is Tramo.Foco)
                assertNull(otro.store.leer()?.filtroPrevio)
                assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, filtro.filtro)
            } finally {
                otro.cerrar()
            }
        }

    @Test
    fun `a habit of duration adds the real focus minutes`() =
        runTest {
            directo()
            banco.habits.create(habitEntity(id = "h1", name = "Leer", metric = Metric.DURATION, target = 30, unit = null))
            motor.preparar(Destino.Habito("h1"))
            assertEquals(30, motor.borrador.value?.minutosFoco)
            assertNull(motor.empezar())
            banco.reloj.avanzar(17 * MINUTO + 20_000L)

            val resultado = motor.heTerminado()

            assertEquals(listOf(17), banco.db.entryDao().all().filter { it.habitId == "h1" }.map { it.value })
            assertEquals(AvisoSesion.Hecho("Leer"), resultado.aviso)
        }

    @Test
    fun `a check habit is marked once and pays from the ledger`() =
        runTest {
            directo()
            banco.habits.create(habitEntity(id = "h1", name = "Meditar", metric = Metric.CHECK, target = 1, logMode = LogMode.BINARY))
            motor.preparar(Destino.Habito("h1"))
            assertNull(motor.empezar())
            banco.reloj.avanzar(5 * MINUTO)

            val resultado = motor.heTerminado()

            assertEquals(listOf(1), banco.db.entryDao().all().map { it.value })
            val pagado = banco.domainState.snapshot().pointsLedger.filter { it.reason == PointsReason.HABIT_DONE }.sumOf { it.delta }
            assertEquals(pagado, resultado.bitos)
            assertTrue(resultado.bitos > 0)
        }

    @Test
    fun `entry breathing does not write breathing sessions`() =
        runTest {
            empezarTarea()
            avanzarYTic(60_000L)
            avanzarYTic(2 * MINUTO)
            motor.heTerminado()
            assertTrue(banco.db.breathingSessionDao().all().isEmpty())
        }

    @Test
    fun `with auto break off the break waits until started`() =
        runTest {
            banco.settings.update { it.copy(pomoAutoBreak = false, sessionEntry = Entrada.DIRECTO, pomoRounds = 2) }
            empezarTarea()
            avanzarYTic(25 * MINUTO)
            val esperando = motor.instante.value!!
            assertTrue(esperando.actual is Tramo.Descanso)
            assertTrue(esperando.esperandoDescanso)
            avanzarYTic(10 * MINUTO)
            assertTrue(motor.instante.value!!.esperandoDescanso)
            assertEquals(5 * MINUTO, motor.instante.value!!.restaMillis)

            motor.empezarDescanso()
            assertFalse(motor.instante.value!!.esperandoDescanso)
            avanzarYTic(5 * MINUTO)
            assertEquals(Tramo.Foco(2, 25), motor.instante.value?.actual)
        }

    @Test
    fun `a new motor over the same store recovers the exact instant`() =
        runTest {
            empezarTarea()
            avanzarYTic(60_000L)
            avanzarYTic(15 * MINUTO)
            motor.pausar()
            banco.reloj.avanzar(3 * MINUTO)
            val esperado = RelojSesion.en(banco.store.leer()!!, banco.reloj.ahora())

            val nuevo = Grabadores()
            val otro = banco.motor(nuevo)
            otro.recuperar()

            assertEquals(esperado, otro.instante.value)
            assertTrue(otro.instante.value!!.pausado)
            assertEquals(10 * MINUTO, otro.instante.value!!.restaMillis)
            assertTrue(nuevo.sonido.sonidos.isEmpty())
            assertTrue(nuevo.tacto.hapticas.isEmpty())
            assertEquals(listOf(banco.store.leer()), nuevo.fuera.cambios)
        }

    @Test
    fun `recovering after the break ended lands in the next block without replaying sounds`() =
        runTest {
            directo()
            empezarTarea()
            avanzarYTic(27 * MINUTO)
            assertTrue(motor.instante.value?.actual is Tramo.Descanso)
            // El proceso muere en el descanso y vuelve cuando ya acabó.
            banco.reloj.avanzar(9 * MINUTO)
            val nuevo = Grabadores()
            val otro = banco.motor(nuevo)

            otro.recuperar()
            otro.tic()
            banco.reloj.avanzar(1_000L)
            otro.tic()

            assertEquals(Tramo.Foco(2, 25), otro.instante.value?.actual)
            assertEquals(19 * MINUTO - 1_000L, otro.instante.value!!.restaMillis)
            assertTrue(nuevo.sonido.sonidos.isEmpty())
            assertTrue(nuevo.tacto.hapticas.isEmpty())
            assertEquals(listOf("sonar:RAIN"), nuevo.fondo.llamadas)
            assertEquals(banco.store.leer(), nuevo.fuera.cambios.single())
        }

    @Test
    fun `with the app away the boundary is not played on return`() =
        runTest {
            directo()
            empezarTarea()
            limpiarGrabaciones()
            banco.delante = false
            avanzarYTic(26 * MINUTO)
            banco.delante = true
            motor.tic()
            assertTrue(motor.instante.value?.actual is Tramo.Descanso)
            assertTrue(g.sonido.sonidos.isEmpty())
            avanzarYTic(4 * MINUTO)
            assertEquals(listOf(Snd.BROTE), g.sonido.sonidos)
        }

    private fun <T> assertNotNullAnd(valor: T?): T {
        assertNotNull(valor)
        return valor!!
    }
}
