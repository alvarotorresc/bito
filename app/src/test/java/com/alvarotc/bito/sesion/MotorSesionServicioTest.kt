package com.alvarotc.bito.sesion

import android.app.Application
import com.alvarotc.bito.data.settings.SessionBackground
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.Tramo
import com.alvarotc.bito.sesion.fuera.AvisosRetenidos
import com.alvarotc.bito.sesion.fuera.EntornoFuera
import com.alvarotc.bito.sesion.fuera.FueraAndroid
import com.alvarotc.bito.sesion.fuera.ServicioGrabado
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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

    @Before
    fun setUp() {
        banco = BancoSesion(tmp.root)
        banco.permisoNoMolestar(false)
        servicio = ServicioGrabado()
    }

    @After
    fun tearDown() {
        if (::alcance.isInitialized) alcance.cancel()
        banco.cerrar()
    }

    private fun fueraReal(): FueraAndroid {
        val entorno =
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
        return banco.motor(Grabadores(), fuera = fueraReal(), fondo = fondo, aire = aire, alcanceMotor = alcance)
    }

    private suspend fun ajustes(
        entrada: Entrada,
        fondo: SessionBackground = SessionBackground.RAIN,
    ) = banco.settings.update { it.copy(sessionEntry = entrada, sessionBackground = fondo) }

    private suspend fun MotorSesionAndroid.empezarTarea() {
        banco.tasks.create(taskEntity(id = "t1"))
        preparar(Destino.Tarea("t1"))
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
            motor.empezarTarea()
            fondo.completar()
            runCurrent()
            // La sesión acaba con la app delante: el servicio se va y el fondo se calla.
            motor.cambiarBorrador(motor.registro.value!!.plan.copy(bloques = 1))
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
}
