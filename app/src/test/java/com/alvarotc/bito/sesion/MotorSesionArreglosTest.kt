package com.alvarotc.bito.sesion

import android.app.Application
import android.app.NotificationManager
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.Objetivo
import com.alvarotc.bito.domain.sesion.Tramo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
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

/** Los arreglos de la review del carril recorrido que viven en el motor (I2, I3, I4 y el fondo perdido con la app fuera). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MotorSesionArreglosTest {
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

    private suspend fun empezarTarea(bloques: Int? = null) {
        banco.tasks.create(taskEntity(id = "t1"))
        motor.preparar(Destino.Tarea("t1"))
        if (bloques != null) motor.cambiarBorrador(motor.borrador.value!!.copy(bloques = bloques))
        assertNull(motor.empezar())
    }

    private suspend fun avanzarYTic(ms: Long) {
        banco.reloj.avanzar(ms)
        motor.tic()
    }

    /**
     * Cada vez que el motor publica algo, mira si la UI se quedaría sin nada que pintar (ni sesión ni borrador): para el
     * Inicio, eso es «la sesión se acabó» y sale a Hoy. Los colectores corren al emitirse, en el hilo que emite.
     */
    private class VigilaHuecos(motor: MotorSesion) {
        private val alcance = CoroutineScope(Dispatchers.Unconfined)
        val huecos = mutableListOf<String>()

        @Volatile
        var armado = false

        init {
            fun mira(donde: String) {
                if (armado && motor.instante.value == null && motor.registro.value == null && motor.borrador.value == null) huecos += donde
            }
            alcance.launch { motor.instante.collect { mira("instante") } }
            alcance.launch { motor.registro.collect { mira("registro") } }
            alcance.launch { motor.borrador.collect { mira("borrador") } }
        }

        fun cerrar() = alcance.cancel()
    }

    private suspend fun vigilando(accion: suspend () -> Unit): List<String> {
        val vigila = VigilaHuecos(motor)
        vigila.armado = true
        try {
            accion()
        } finally {
            vigila.cerrar()
        }
        return vigila.huecos
    }

    @Test
    fun `another five never publishes a hole between the old session and the new one`() =
        runTest {
            directo()
            empezarTarea(bloques = 1)
            avanzarYTic(60_000L)
            avanzarYTic(25 * 60_000L)
            assertNull(motor.instante.value?.actual)

            val huecos = vigilando { motor.otraDeCinco() }

            assertEquals(emptyList<String>(), huecos)
            assertTrue(motor.instante.value?.actual is Tramo.Foco)
        }

    @Test
    fun `leaving it and starting another never publishes a hole`() =
        runTest {
            directo()
            empezarTarea()
            banco.tasks.create(taskEntity(id = "t2", title = "Otra"))
            motor.preparar(Destino.Tarea("t2"))
            assertNotNull(motor.empezar())

            val huecos = vigilando { motor.dejarlaYEmpezar() }

            assertEquals(emptyList<String>(), huecos)
            assertEquals(Objetivo.Tarea("t2"), motor.registro.value?.plan?.objetivo)
        }

    @Test
    fun `back to prepare puts the draft back before the session goes and never publishes a hole`() =
        runTest {
            empezarTarea()
            assertTrue(motor.instante.value?.actual is Tramo.Respirar)
            val plan = motor.registro.value!!.plan

            val huecos = vigilando { motor.volverAPreparar() }

            assertEquals(emptyList<String>(), huecos)
            assertEquals(plan, motor.borrador.value)
        }

    @Test
    fun `back to prepare keeps the screen in the session while the outside is told`() =
        runTest {
            empezarTarea()
            val plan = motor.registro.value!!.plan
            val puerta = kotlinx.coroutines.CompletableDeferred<Unit>()
            g.fuera.puerta = puerta
            val vigila = VigilaHuecos(motor).also { it.armado = true }

            val vuelta = launch(Dispatchers.Unconfined) { motor.volverAPreparar() }
            withContext(Dispatchers.Default) { withTimeout(5_000) { g.fuera.llegoALaPuerta.await() } }
            assertEquals("mientras se avisa fuera, la UI sigue en la sesión", emptyList<String>(), vigila.huecos.toList())
            puerta.complete(Unit)
            vuelta.join()
            vigila.cerrar()

            assertEquals(emptyList<String>(), vigila.huecos.toList())
            assertEquals(plan, motor.borrador.value)
        }

    /** Una sesión de 1 bloque con No molestar puesto por Bito, ya en Terminada y con el filtro devuelto. */
    private suspend fun terminadaConNoMolestar() {
        banco.permisoNoMolestar(true)
        banco.settings.update { it.copy(sessionDnd = true, sessionEntry = Entrada.DIRECTO) }
        empezarTarea(bloques = 1)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, banco.filtro())
        avanzarYTic(25 * 60_000L)
        assertNull(motor.instante.value?.actual)
        assertEquals("al llegar a Terminada se devuelve", NotificationManager.INTERRUPTION_FILTER_ALL, banco.filtro())
    }

    @Test
    fun `a do not disturb set by hand after finishing survives recovering and the answer`() =
        runTest {
            terminadaConNoMolestar()
            banco.ponerFiltro(NotificationManager.INTERRUPTION_FILTER_PRIORITY)

            motor.recuperar()
            assertEquals("recuperar no lo pisa", NotificationManager.INTERRUPTION_FILTER_PRIORITY, banco.filtro())
            banco.motor(Grabadores()).recuperar()
            assertEquals("ni otro proceso al recuperar", NotificationManager.INTERRUPTION_FILTER_PRIORITY, banco.filtro())

            motor.sigoLuego()
            assertEquals("ni la respuesta", NotificationManager.INTERRUPTION_FILTER_PRIORITY, banco.filtro())
        }

    @Test
    fun `yes finished does not step on a do not disturb set by hand either`() =
        runTest {
            terminadaConNoMolestar()
            banco.ponerFiltro(NotificationManager.INTERRUPTION_FILTER_PRIORITY)

            motor.siTerminado()

            assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, banco.filtro())
        }

    @Test
    fun `finishing the focus returns do not disturb once and the label goes with it`() =
        runTest {
            terminadaConNoMolestar()

            assertNull("sin filtro previo ya no hay etiqueta", motor.registro.value?.filtroPrevio)
            assertNull(banco.store.leer()?.filtroPrevio)
        }

    @Test
    fun `back from a late alarm returns with the break and silences the background`() =
        runTest {
            directo()
            empezarTarea()
            assertEquals(SessionBackground.RAIN, g.fondo.sonando.value)
            banco.reloj.avanzar(26 * 60_000L)

            motor.recuperar()

            assertTrue(motor.instante.value?.actual is Tramo.Descanso)
            assertNull("el descanso no suena a fondo", g.fondo.sonando.value)
            assertEquals(false, g.fuera.sonandos.last())
        }

    @Test
    fun `back from a late alarm after breathing stops the air and plays the background`() =
        runTest {
            empezarTarea()
            assertTrue(g.aire.sonando.value)
            banco.reloj.avanzar(3 * 60_000L)

            motor.recuperar()

            assertTrue(motor.instante.value?.actual is Tramo.Foco)
            assertEquals("el aire ya no suena", false, g.aire.sonando.value)
            assertEquals(SessionBackground.RAIN, g.fondo.sonando.value)
        }

    @Test
    fun `adopting the stretch on a tick fixes the audio and tells the outside`() =
        runTest {
            directo()
            empezarTarea()
            banco.delante = false
            avanzarYTic(26 * 60_000L)
            banco.delante = true
            g.fuera.cambios.clear()
            g.fuera.sonandos.clear()

            motor.tic()

            assertTrue(motor.instante.value?.actual is Tramo.Descanso)
            assertNull(g.fondo.sonando.value)
            assertEquals(listOf(motor.registro.value), g.fuera.cambios.toList())
            assertEquals(listOf(false), g.fuera.sonandos.toList())
        }

    @Test
    fun `losing the background with the app away tells nothing`() =
        runTest {
            banco.settings.update { it.copy(sessionBackground = SessionBackground.NONE, sessionEntry = Entrada.DIRECTO) }
            empezarTarea()
            g.fuera.cambios.clear()
            banco.delante = false

            motor.alCambiarElAudio()

            assertEquals(emptyList<Any?>(), g.fuera.cambios.toList())
        }

    @Test
    fun `leaving a free session to start another says nothing about an attempt`() =
        runTest {
            directo()
            motor.preparar(null)
            assertEquals(Objetivo.Libre, motor.borrador.value?.objetivo)
            assertNull(motor.empezar())
            banco.tasks.create(taskEntity(id = "t2", title = "Otra"))
            motor.preparar(Destino.Tarea("t2"))
            assertNotNull(motor.empezar())

            val resultado = motor.dejarlaYEmpezar()

            assertNull(resultado.aviso)
        }

    @Test
    fun `with the app out the ticker stops and coming back brings it up again`() =
        runTest {
            motor = banco.motor(g, conTicker = true)
            directo()
            empezarTarea()
            assertTrue("con la app delante el tic corre", motor.tickerActivo)

            banco.delante = false
            motor.tic()
            assertTrue("con la app fuera el tic se para", !motor.tickerActivo)

            banco.delante = true
            motor.recuperar()
            assertTrue("al volver, recuperar lo levanta", motor.tickerActivo)
        }

    private suspend fun comprobarCierreAlCambiarDeDia(dejarParada: suspend () -> Unit) {
        directo()
        empezarTarea(bloques = 2)
        dejarParada()
        val nuevo = Grabadores()
        val otro = banco.motor(nuevo)
        banco.reloj.avanzar(24 * 60 * MINUTO)

        otro.recuperar()

        assertNull(banco.store.leer())
        assertNull(otro.registro.value)
        assertEquals(com.alvarotc.bito.domain.sesion.ResultadoSesion.SIN_RESPUESTA.name, banco.db.sesionDao().all().single().resultado)
        assertEquals(1, banco.db.taskEventDao().all().count { it.kind == com.alvarotc.bito.domain.model.TaskEventKind.ATTEMPT })
        assertTrue(nuevo.sonido.sonidos.isEmpty())
        assertEquals(listOf(null), nuevo.fuera.cambios)
    }

    @Test
    fun `a paused session is closed as an attempt when the logical day changes`() =
        runTest {
            comprobarCierreAlCambiarDeDia {
                avanzarYTic(10 * MINUTO)
                motor.pausar()
                banco.reloj.avanzar(5 * MINUTO)
            }
        }

    @Test
    fun `a break waiting to start is closed as an attempt when the logical day changes`() =
        runTest {
            banco.settings.update { it.copy(pomoAutoBreak = false) }
            comprobarCierreAlCambiarDeDia {
                avanzarYTic(25 * MINUTO + 1_000)
                assertTrue(motor.instante.value?.esperandoDescanso == true)
            }
        }

    @Test
    fun `a paused session of today is kept`() =
        runTest {
            directo()
            empezarTarea(bloques = 2)
            avanzarYTic(10 * MINUTO)
            motor.pausar()
            val otro = banco.motor(Grabadores())
            banco.reloj.avanzar(5 * MINUTO)
            otro.recuperar()
            assertTrue(otro.instante.value?.pausado == true)
        }

    @Test
    fun `recovering with the app out does not make the engine sound`() =
        runTest {
            directo()
            empezarTarea(bloques = 2)
            val g2 = Grabadores()
            val otro = banco.motor(g2)
            banco.delante = false

            otro.recuperar()

            assertTrue("el fondo no suena desde fuera: ${g2.fondo.llamadas}", g2.fondo.llamadas.isEmpty())
            assertTrue(g2.aire.llamadas.isEmpty())
        }
}
