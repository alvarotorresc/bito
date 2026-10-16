package com.alvarotc.bito

import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.planSesion
import com.alvarotc.bito.domain.sesion.registroSesion
import com.alvarotc.bito.sesion.MotorSesionAndroid
import com.alvarotc.bito.ui.AppVisibility
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Recuperar el motor de la sesión desde el arranque de la app y cada vuelta a primer plano, sin esperar al Inicio. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppStartupSesionTest {
    @get:Rule
    val sesiones = ContenedoresDeSesion()

    private val app get() = ApplicationProvider.getApplicationContext<BitoApp>()

    @Before
    fun setUp() {
        AppStartup.quiesceForTests()
        AppVisibility.visible = true
        Settings.Global.putInt(app.contentResolver, Settings.Global.BOOT_COUNT, 7)
    }

    @After
    fun tearDown() {
        AppVisibility.visible = false
        AppStartup.quiesceForTests()
    }

    @Test
    fun `a live session is brought back to the engine without the home screen`() {
        val c = sesiones.registrar(app.container)
        runBlocking {
            val plan = planSesion(bloques = 2, entrada = Entrada.DIRECTO)
            c.sesionStore.guardar(
                registroSesion(
                    plan = plan,
                    inicioPared = System.currentTimeMillis() - 60_000,
                    anclaElapsed = SystemClock.elapsedRealtime() - 60_000,
                    arranques = 7,
                ),
            )
            assertNull(c.motorSesion.instante.value)

            AppStartup.recuperarMotor(c)

            assertNotNull(c.motorSesion.instante.value)
            assertEquals("s1", c.motorSesion.registro.value?.id)
        }
    }

    @Test
    fun `closing the session scope stops the engine ticker`() {
        val c = sesiones.registrar(app.container)
        runBlocking {
            val plan = planSesion(bloques = 2, entrada = Entrada.DIRECTO)
            c.sesionStore.guardar(
                registroSesion(
                    plan = plan,
                    inicioPared = System.currentTimeMillis() - 60_000,
                    anclaElapsed = SystemClock.elapsedRealtime() - 60_000,
                    arranques = 7,
                ),
            )
            AppStartup.recuperarMotor(c)
        }
        val motor = c.motorSesion as MotorSesionAndroid
        assertTrue("el tic corre con la sesión viva y la app delante", motor.tickerActivo)

        c.cerrarSesion()

        assertFalse("el tic no sobrevive al contenedor", motor.tickerActivo)
    }

    @Test
    fun `without a session the engine is not even created`() {
        val c = sesiones.registrar(app.container)
        runBlocking { AppStartup.recuperarMotor(c) }
        assertNull(c.motorSesion.instante.value)
    }
}
