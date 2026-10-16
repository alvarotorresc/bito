package com.alvarotc.bito.sesion

import android.app.Application
import android.app.NotificationManager
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.RegistroSesion
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.sesion.fuera.AudioFrontera
import com.alvarotc.bito.sesion.fuera.AvisosRetenidos
import com.alvarotc.bito.sesion.fuera.EntornoFuera
import com.alvarotc.bito.sesion.fuera.FueraAndroid
import com.alvarotc.bito.sesion.fuera.ServicioGrabado
import com.alvarotc.bito.sesion.fuera.SesionReceiver
import com.alvarotc.bito.ui.notifications.NotificationChannels
import com.alvarotc.bito.ui.notifications.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * El motor con el `FueraAndroid` de verdad y un servicio grabador, sobre un fondo y un aire que, como los reales, no
 * cambian `sonando` en el mismo instante en que se les pide (revisión final: C1, I1). El colector del motor corre en
 * un despachador de test: `runCurrent()` lo deja procesar lo emitido, sin hilos ni esperas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MotorSesionServicioTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoSesion
    private lateinit var servicio: ServicioGrabado
    private lateinit var alcance: CoroutineScope
    private lateinit var entorno: EntornoFuera
    private val manager get() = banco.context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        banco = BancoSesion(tmp.root)
        banco.permisoNoMolestar(false)
        shadowOf(manager).setNotificationsEnabled(true)
        NotificationChannels.ensure(banco.context)
        servicio = ServicioGrabado(manager)
    }

    @After
    fun tearDown() {
        if (::alcance.isInitialized) alcance.cancel()
        banco.cerrar()
    }

    /** Cuántos cambios de audio con la app fuera ha terminado de decidir lo de fuera. */
    private val audiosFuera = MutableStateFlow(0)

    /** Lo de fuera de verdad, contando lo que decide con la app fuera (lee el store en hilos reales: hay que esperarlo). */
    private fun fueraContada(): FueraDeLaApp {
        val real = fueraReal()
        return object : FueraDeLaApp by real {
            override suspend fun alCambiarElAudioFuera(
                guardado: RegistroSesion,
                suena: Boolean,
                fondoPendiente: Boolean,
            ) {
                real.alCambiarElAudioFuera(guardado, suena, fondoPendiente)
                audiosFuera.value++
            }
        }
    }

    /** Deja correr el motor hasta que lo de fuera haya decidido [n] cambios de audio con la app fuera. */
    private fun TestScope.esperarAudiosFuera(n: Int) {
        repeat(ESPERA) {
            runCurrent()
            if (audiosFuera.value >= n) return
            Thread.sleep(5)
        }
        fail("lo de fuera no decidió $n cambios de audio; van ${audiosFuera.value}")
    }

    private fun fueraReal(): FueraAndroid {
        entorno =
            EntornoFuera(
                context = banco.context,
                store = banco.store,
                noMolestar = banco.noMolestar,
                voz = { com.alvarotc.bito.domain.model.Personality.NEUTRA },
                titulo = { "Leer" },
                visible = { banco.delante },
                ahora = { banco.reloj.ahora() },
                tacto = { TactoGrabado() },
                servicio = servicio,
                retenidos = AvisosRetenidos(banco.context, sesionViva = { false }, diaDeHoy = { banco.hoy }, publicar = { _, _ -> }),
            )
        return FueraAndroid(entorno, CoroutineScope(Dispatchers.Unconfined))
    }

    private fun TestScope.motor(
        fondo: Fondo,
        aire: Aire = AireAsincrono(),
    ): MotorSesionAndroid {
        alcance = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        return banco.motor(Grabadores(), fuera = fueraContada(), fondo = fondo, aire = aire, alcanceMotor = alcance)
    }

    /** La alarma de una frontera con la app fuera, sobre el mismo store, servicio y audio que el motor. */
    private suspend fun receptor(
        tramoQueAcaba: Int,
        fondo: Fondo,
        aire: Aire,
    ) = SesionReceiver.procesar(entorno, AudioFrontera(fondo, aire, SonidoGrabado()), tramoQueAcaba)

    private suspend fun ajustes(
        entrada: Entrada,
        fondo: SessionBackground = SessionBackground.RAIN,
    ) = banco.settings.update { it.copy(sessionEntry = entrada, sessionBackground = fondo) }

    private suspend fun MotorSesionAndroid.empezarTarea(bloques: Int? = null) {
        banco.tasks.create(taskEntity(id = "t1"))
        preparar(Destino.Tarea("t1"))
        if (bloques != null) cambiarBorrador(borrador.value!!.copy(bloques = bloques))
        assertNull(empezar())
    }

    @Test
    fun `a direct start with the background still opening starts the service once it sounds`() =
        runTest {
            val fondo = FondoAsincrono()
            val motor = motor(fondo)
            ajustes(Entrada.DIRECTO)
            motor.empezarTarea()
            runCurrent()
            assertTrue("aun no suena: sin servicio", servicio.arrancados.isEmpty())

            fondo.completar()
            runCurrent()

            assertEquals(1, servicio.arrancados.size)
        }

    @Test
    fun `another five minutes with the background still opening starts the service once it sounds`() =
        runTest {
            val fondo = FondoAsincrono()
            val motor = motor(fondo)
            ajustes(Entrada.DIRECTO)
            motor.empezarTarea(bloques = 1)
            fondo.completar()
            runCurrent()
            // La sesión acaba con la app delante: el servicio se va y el fondo se calla.
            banco.reloj.avanzar(26 * MINUTO)
            motor.tic()
            runCurrent()
            assertTrue(motor.instante.value?.actual == null)
            assertFalse(servicio.vivo())

            motor.otraDeCinco()
            runCurrent()
            assertFalse("el fondo aun no suena", servicio.vivo())
            fondo.completar()
            runCurrent()

            assertTrue(servicio.vivo())
        }

    @Test
    fun `losing the background for good with the app in front stops the service for the rest of the block`() =
        runTest {
            val fondo = FondoAsincrono()
            val motor = motor(fondo)
            ajustes(Entrada.DIRECTO)
            motor.empezarTarea()
            fondo.completar()
            runCurrent()
            assertTrue(servicio.vivo())

            fondo.perder()
            runCurrent()

            assertFalse(servicio.vivo())
        }

    @Test
    fun `breathing in and then focus with no background does not keep the service through the first block`() =
        runTest {
            val fondo = FondoAsincrono()
            val aire = AireAsincrono()
            val motor = motor(fondo, aire)
            ajustes(Entrada.RESPIRAR, SessionBackground.NONE)
            motor.empezarTarea()
            runCurrent()
            assertTrue("respirar: servicio", servicio.vivo())

            banco.reloj.avanzar(61_000L)
            motor.tic()
            runCurrent()
            assertTrue(motor.instante.value?.actual is Tramo.Foco)
            aire.completarParada()
            runCurrent()

            assertFalse("sin fondo ni aire no suena nada", servicio.vivo())
        }

    @Test
    fun `a silent Bito with breathing in and a background does not keep the service all session`() =
        runTest {
            val fondo = FondoAsincrono(silencioso = true)
            val aire = AireAsincrono()
            val motor = motor(fondo, aire)
            ajustes(Entrada.RESPIRAR, SessionBackground.RAIN)
            motor.empezarTarea()
            runCurrent()
            assertTrue(servicio.vivo())

            banco.reloj.avanzar(61_000L)
            motor.tic()
            runCurrent()
            fondo.completar()
            aire.completarParada()
            runCurrent()

            assertTrue(motor.instante.value?.actual is Tramo.Foco)
            assertFalse(servicio.vivo())
        }

    @Test
    fun `the service stays through a break when the session has a background`() =
        runTest {
            val fondo = FondoAsincrono()
            val motor = motor(fondo)
            ajustes(Entrada.DIRECTO)
            motor.empezarTarea()
            fondo.completar()
            runCurrent()

            banco.reloj.avanzar(26 * MINUTO)
            motor.tic()
            runCurrent()

            assertTrue(motor.instante.value?.actual is Tramo.Descanso)
            assertTrue(servicio.vivo())
            assertEquals(0, servicio.paradas)
        }

    @Test
    fun `the engine hears by itself that the background is lost, without waiting for anything`() =
        runTest {
            alcance = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
            val g = Grabadores()
            val motor = banco.motor(g, alcanceMotor = alcance)
            ajustes(Entrada.DIRECTO)
            motor.empezarTarea()
            runCurrent()
            assertTrue(g.fuera.sonandos.last())
            g.fuera.sonandos.clear()

            g.fondo.perder()
            runCurrent()

            assertEquals(listOf(false), g.fuera.sonandos.toList())
        }

    // ── La regla del servicio (re-review R1, R2) ──

    @Test
    fun `crossing from a break to focus with the background still opening never stops the service`() =
        runTest {
            val fondo = FondoAsincrono()
            val motor = motor(fondo)
            ajustes(Entrada.DIRECTO)
            motor.empezarTarea()
            fondo.completar()
            runCurrent()
            banco.reloj.avanzar(26 * MINUTO)
            motor.tic()
            runCurrent()
            assertTrue(motor.instante.value?.actual is Tramo.Descanso)

            banco.reloj.avanzar(5 * MINUTO)
            motor.tic()
            runCurrent()
            assertTrue(motor.instante.value?.actual is Tramo.Foco)
            assertEquals("el fondo pedido y sin confirmar no suelta el servicio", 0, servicio.paradas)

            fondo.completar()
            runCurrent()
            assertEquals(0, servicio.paradas)
            assertEquals("sin parpadeo: un solo arranque", 1, servicio.arrancados.size)
        }

    @Test
    fun `crossing from breathing to focus with the background still opening never stops the service`() =
        runTest {
            val fondo = FondoAsincrono()
            val aire = AireAsincrono()
            val motor = motor(fondo, aire)
            ajustes(Entrada.RESPIRAR, SessionBackground.RAIN)
            motor.empezarTarea()
            runCurrent()
            assertTrue(servicio.vivo())

            banco.reloj.avanzar(61_000L)
            motor.tic()
            runCurrent()
            // El aire acaba de callarse antes de que el fondo suene.
            aire.completarParada()
            runCurrent()
            assertEquals(0, servicio.paradas)

            fondo.completar()
            runCurrent()
            assertEquals(0, servicio.paradas)
            assertEquals(1, servicio.arrancados.size)
        }

    @Test
    fun `a silent Bito on a direct start never starts the service`() =
        runTest {
            val fondo = FondoAsincrono(silencioso = true)
            val motor = motor(fondo)
            ajustes(Entrada.DIRECTO)
            motor.empezarTarea()
            fondo.completar()
            runCurrent()

            assertTrue(servicio.arrancados.isEmpty())
        }

    @Test
    fun `with the app away, losing the background in focus releases the service and keeps the notice`() =
        runTest {
            val fondo = FondoAsincrono()
            val motor = motor(fondo)
            ajustes(Entrada.DIRECTO)
            motor.empezarTarea()
            fondo.completar()
            runCurrent()
            assertTrue(servicio.vivo())

            banco.delante = false
            banco.reloj.avanzar(10 * MINUTO)
            fondo.perder()
            esperarAudiosFuera(1)

            assertFalse("con la app fuera, sin sonido no hay servicio", servicio.vivo())
            assertNotNull("el aviso de la sesión sigue", shadowOf(manager).getNotification(Notifier.FOCUS_ID))
        }

    @Test
    fun `with the app away, the receiver crossing from focus to a break does not stop the service`() =
        runTest {
            val fondo = FondoAsincrono()
            val aire = AireAsincrono()
            val motor = motor(fondo, aire)
            ajustes(Entrada.DIRECTO)
            motor.empezarTarea()
            fondo.completar()
            runCurrent()

            banco.delante = false
            banco.reloj.avanzar(25 * MINUTO + 1_000L)
            receptor(0, fondo, aire)
            esperarAudiosFuera(1)
            assertTrue(motor.instante.value != null)
            assertEquals("el descanso calla el fondo pero no suelta el servicio", 0, servicio.paradas)

            banco.reloj.avanzar(5 * MINUTO)
            receptor(1, fondo, aire)
            esperarAudiosFuera(2)
            fondo.completar()
            esperarAudiosFuera(3)
            assertEquals(0, servicio.paradas)
            assertTrue(servicio.vivo())
        }

    @Test
    fun `with the app away, breathing to focus with a silent Bito releases the service`() =
        runTest {
            val fondo = FondoAsincrono(silencioso = true)
            val aire = AireAsincrono()
            val motor = motor(fondo, aire)
            ajustes(Entrada.RESPIRAR, SessionBackground.RAIN)
            motor.empezarTarea()
            runCurrent()
            assertTrue(servicio.vivo())

            banco.delante = false
            banco.reloj.avanzar(61_000L)
            receptor(0, fondo, aire)
            runCurrent()
            aire.completarParada()
            esperarAudiosFuera(1)

            assertFalse("el aire calló y el fondo no sonará: nada que sostener", servicio.vivo())
        }

    private companion object {
        const val ESPERA = 1_000
    }
}
