package com.alvarotc.bito.ui.sesion

import android.app.Application
import android.app.NotificationManager
import com.alvarotc.bito.data.taskEntity
import com.alvarotc.bito.domain.model.TaskEventKind
import com.alvarotc.bito.domain.propuestas.Destino
import com.alvarotc.bito.domain.sesion.Entrada
import com.alvarotc.bito.domain.sesion.ResultadoSesion
import com.alvarotc.bito.sesion.AvisoSesion
import com.alvarotc.bito.sesion.BancoSesion
import com.alvarotc.bito.sesion.Grabadores
import com.alvarotc.bito.sesion.MotorSesionAndroid
import com.alvarotc.bito.ui.inicio.AbrirSesion
import com.alvarotc.bito.ui.inicio.EntradaSesion
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** H11 contra el motor de verdad: abrir la respiración suelta con una sesión viva pregunta y dejarla cuenta como intento. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class EntradaRespirarTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var banco: BancoSesion
    private lateinit var motor: MotorSesionAndroid
    private var sueltas = 0
    private var ocupados = 0
    private val inicio get() = AbrirSesion(motor, {}, { sueltas++ }, { ocupados++ })

    @Before
    fun setUp() {
        banco = BancoSesion(tmp.root)
        motor = banco.motor(Grabadores())
        banco.permisoNoMolestar(false)
        banco.ponerFiltro(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    @After
    fun tearDown() {
        banco.cerrar()
        EntradaSesion.consumir()
    }

    private suspend fun sesionViva() {
        banco.settings.update { it.copy(sessionEntry = Entrada.DIRECTO) }
        banco.tasks.create(taskEntity(id = "t1"))
        motor.preparar(Destino.Tarea("t1"))
        assertNull(motor.empezar())
    }

    @Test
    fun `with nothing live it opens the loose breathing and drops a stale draft`() =
        runTest {
            banco.tasks.create(taskEntity(id = "t1"))
            motor.preparar(Destino.Tarea("t1"))
            assertNotNull(motor.borrador.value)

            EntradaSesion.abrir(Destino.Respirar, inicio)

            assertEquals(1, sueltas)
            assertEquals(0, ocupados)
            assertNull(motor.borrador.value)
        }

    @Test
    fun `with a live session it asks first`() =
        runTest {
            sesionViva()
            val id = motor.registro.value?.id

            EntradaSesion.abrir(Destino.Respirar, inicio)

            assertEquals(0, sueltas)
            assertEquals(1, ocupados)
            assertEquals(id, motor.registro.value?.id)
        }

    @Test
    fun `leaving it records an attempt and starts nothing`() =
        runTest {
            sesionViva()
            banco.reloj.avanzar(10 * 60_000L)
            EntradaSesion.abrir(Destino.Respirar, inicio)

            val resultado = motor.dejarlaYEmpezar()

            assertEquals(AvisoSesion.ComoIntento("Llamar al banco"), resultado.aviso)
            assertNull(motor.registro.value)
            assertNull(banco.store.leer())
            assertEquals(listOf("t1"), banco.db.taskEventDao().all().filter { it.kind == TaskEventKind.ATTEMPT }.map { it.taskId })
            val fila = banco.db.sesionDao().all().single()
            assertEquals(ResultadoSesion.CAMBIADA.name, fila.resultado)
        }
}
